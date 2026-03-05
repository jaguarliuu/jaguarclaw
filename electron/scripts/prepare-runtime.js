/**
 * Prepare bundled runtime automatically (Windows target).
 *
 * What it does:
 * 1. Resolve exact Node/Python versions from runtime/manifest.json
 * 2. Download official archives
 * 3. Extract to runtime/staging/node and runtime/staging/python
 * 4. Install runtime/requirements.txt packages into embedded Python
 *    - Windows host: run embedded python.exe + pip directly
 *    - macOS/Linux host: download win_amd64 wheels and unpack into Lib/site-packages
 *
 * Usage:
 *   node electron/scripts/prepare-runtime.js
 *   node electron/scripts/prepare-runtime.js --force
 *   node electron/scripts/prepare-runtime.js --dry-run
 */

const https = require('https');
const fs = require('fs');
const path = require('path');
const { execSync, spawnSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..', '..');
const RUNTIME_DIR = path.join(ROOT, 'runtime');
const MANIFEST_PATH = path.join(RUNTIME_DIR, 'manifest.json');
const REQUIREMENTS_PATH = path.join(RUNTIME_DIR, 'requirements.txt');
const STAGING_DIR = path.join(RUNTIME_DIR, 'staging');
const DOWNLOADS_DIR = path.join(RUNTIME_DIR, '_downloads');
const TMP_DIR = path.join(RUNTIME_DIR, '_tmp');
const PREPARED_MARKER = path.join(STAGING_DIR, '.prepared.json');

function parseArgs(argv) {
  const set = new Set(argv);
  return {
    force: set.has('--force'),
    dryRun: set.has('--dry-run'),
  };
}

function ensureExists(filePath, label) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`${label} not found: ${filePath}`);
  }
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function toSemverParts(ver) {
  return String(ver).split('.').map((v) => parseInt(v, 10) || 0);
}

function compareSemver(a, b) {
  const pa = toSemverParts(a);
  const pb = toSemverParts(b);
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const av = pa[i] || 0;
    const bv = pb[i] || 0;
    if (av !== bv) return av - bv;
  }
  return 0;
}

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function cleanDir(dirPath) {
  fs.rmSync(dirPath, { recursive: true, force: true });
  fs.mkdirSync(dirPath, { recursive: true });
}

function httpsGet(url, retries = 2) {
  return new Promise((resolve, reject) => {
    const attempt = (left) => {
      const request = (current) => {
        https.get(current, { headers: { 'User-Agent': 'JaguarClaw-RuntimeBuilder' } }, (res) => {
          if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
            request(res.headers.location);
            return;
          }
          if (res.statusCode >= 500 && left > 0) {
            res.resume();
            setTimeout(() => attempt(left - 1), 1500);
            return;
          }
          if (res.statusCode !== 200) {
            res.resume();
            reject(new Error(`HTTP ${res.statusCode} for ${current}`));
            return;
          }
          resolve(res);
        }).on('error', (err) => {
          if (left > 0) {
            setTimeout(() => attempt(left - 1), 1500);
          } else {
            reject(err);
          }
        });
      };
      request(url);
    };
    attempt(retries);
  });
}

async function httpsGetText(url) {
  const res = await httpsGet(url);
  let body = '';
  for await (const chunk of res) {
    body += chunk;
  }
  return body;
}

function httpsHead(url) {
  return new Promise((resolve) => {
    const request = (current) => {
      const req = https.request(current, {
        method: 'HEAD',
        headers: { 'User-Agent': 'JaguarClaw-RuntimeBuilder' },
      }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          request(res.headers.location);
          return;
        }
        resolve(res.statusCode >= 200 && res.statusCode < 300);
      });
      req.on('error', () => resolve(false));
      req.end();
    };
    request(url);
  });
}

async function downloadFile(url, destPath) {
  return new Promise((resolve, reject) => {
    ensureDir(path.dirname(destPath));
    const file = fs.createWriteStream(destPath);

    const request = (current) => {
      https.get(current, { headers: { 'User-Agent': 'JaguarClaw-RuntimeBuilder' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          request(res.headers.location);
          return;
        }
        if (res.statusCode !== 200) {
          file.close();
          fs.rmSync(destPath, { force: true });
          reject(new Error(`HTTP ${res.statusCode} for ${current}`));
          return;
        }

        const totalBytes = parseInt(res.headers['content-length'] || '0', 10);
        let downloaded = 0;

        res.on('data', (chunk) => {
          downloaded += chunk.length;
          if (totalBytes > 0) {
            const pct = ((downloaded / totalBytes) * 100).toFixed(1);
            process.stdout.write(`\rDownloading ${path.basename(destPath)}... ${pct}%`);
          }
        });

        res.pipe(file);
        file.on('finish', () => {
          file.close();
          process.stdout.write('\n');
          resolve();
        });
      }).on('error', (err) => {
        file.close();
        fs.rmSync(destPath, { force: true });
        reject(err);
      });
    };

    request(url);
  });
}

function unzipArchive(archivePath, destinationDir) {
  ensureDir(destinationDir);
  if (process.platform === 'win32') {
    const script = `Expand-Archive -LiteralPath '${archivePath.replace(/'/g, "''")}' -DestinationPath '${destinationDir.replace(/'/g, "''")}' -Force`;
    execSync(
      `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "${script}"`,
      { stdio: 'inherit' }
    );
    return;
  }
  execSync(`unzip -q "${archivePath}" -d "${destinationDir}"`, { stdio: 'inherit' });
}

function firstSubdirectory(dirPath) {
  const list = fs.readdirSync(dirPath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory());
  if (list.length === 0) return null;
  return path.join(dirPath, list[0].name);
}

function resolvePathSpec(spec, resolverFn) {
  const raw = String(spec || '').trim();
  if (!raw) return resolverFn('');
  if (!raw.includes('x')) return raw;
  return resolverFn(raw);
}

async function resolveNodeVersion(nodeSpec) {
  if (!nodeSpec.includes('x')) return nodeSpec;
  const majorPrefix = nodeSpec.split('.x')[0];
  const text = await httpsGetText('https://nodejs.org/dist/index.json');
  const list = JSON.parse(text);
  for (const item of list) {
    const ver = String(item.version || '').replace(/^v/, '');
    if (ver.startsWith(`${majorPrefix}.`)) {
      return ver;
    }
  }
  throw new Error(`Failed to resolve Node version from spec: ${nodeSpec}`);
}

async function resolvePythonVersion(pySpec) {
  if (!pySpec.includes('x')) return pySpec;
  const prefix = pySpec.replace('.x', '');
  const html = await httpsGetText('https://www.python.org/ftp/python/');
  const prefixPattern = prefix.replace(/\./g, '\\.');
  const regex = new RegExp(`href="(${prefixPattern}\\.\\d+)/"`, 'g');
  const versions = [];
  let match;
  while ((match = regex.exec(html)) !== null) {
    if (match[1].startsWith(`${prefix}.`)) {
      versions.push(match[1]);
    }
  }
  if (versions.length === 0) {
    throw new Error(`Failed to resolve Python version from spec: ${pySpec}`);
  }
  versions.sort(compareSemver).reverse();
  for (const candidate of versions) {
    const url = `https://www.python.org/ftp/python/${candidate}/python-${candidate}-embed-amd64.zip`;
    // 部分 patch 版本可能没有 embeddable 包，需逐个探测
    if (await httpsHead(url)) {
      return candidate;
    }
  }
  throw new Error(`No embeddable Python zip found for spec: ${pySpec}`);
}

function ensureManifestTarget(manifest) {
  const targetOs = String(manifest?.target?.os || 'win32');
  const arch = String(manifest?.target?.arch || 'x64');
  if (targetOs !== 'win32' || arch !== 'x64') {
    throw new Error(
      `Only win32/x64 runtime preparation is supported currently (got ${targetOs}/${arch})`
    );
  }
}

function updatePythonPth(pythonDir) {
  const pthFile = fs.readdirSync(pythonDir).find((name) => /^python\d+\._pth$/i.test(name));
  if (!pthFile) {
    throw new Error('Could not find python*. _pth file in embedded runtime');
  }
  const pthPath = path.join(pythonDir, pthFile);
  const raw = fs.readFileSync(pthPath, 'utf8');
  const lines = raw.split(/\r?\n/);

  let hasSitePackages = false;
  let hasImportSite = false;
  const out = lines.map((line) => {
    const trimmed = line.trim();
    if (trimmed.toLowerCase() === 'lib\\site-packages' || trimmed.toLowerCase() === 'lib/site-packages') {
      hasSitePackages = true;
      return 'Lib\\site-packages';
    }
    if (trimmed === '#import site' || trimmed === 'import site') {
      hasImportSite = true;
      return 'import site';
    }
    return line;
  });

  if (!hasSitePackages) out.push('Lib\\site-packages');
  if (!hasImportSite) out.push('import site');

  fs.writeFileSync(pthPath, `${out.join('\n').trim()}\n`, 'utf8');
}

function runCommand(cmd, cwd) {
  execSync(cmd, { cwd, stdio: 'inherit' });
}

function getPythonTag(pythonVersion) {
  const parts = String(pythonVersion).split('.');
  if (parts.length < 2) {
    throw new Error(`Invalid python version: ${pythonVersion}`);
  }
  const major = parts[0];
  const minor = parts[1];
  return {
    pyVersion: `${major}.${minor}`,
    abiTag: `cp${major}${minor}`,
  };
}

function findHostPython() {
  const fromEnv = String(process.env.PYTHON || '').trim();
  const candidates = [fromEnv, 'python3', 'python'].filter(Boolean);
  for (const candidate of candidates) {
    const check = spawnSync(candidate, ['--version'], { stdio: 'pipe' });
    if (check.status === 0) {
      return candidate;
    }
  }
  throw new Error(
    'No host Python found. Install python3 (with pip) on macOS/Linux, or set PYTHON=/path/to/python3'
  );
}

function installPackagesByWheelExtraction(pythonVersion, pythonDir) {
  const hostPython = findHostPython();
  const { pyVersion, abiTag } = getPythonTag(pythonVersion);
  const sitePackagesDir = path.join(pythonDir, 'Lib', 'site-packages');
  const wheelsDir = path.join(TMP_DIR, 'wheels');
  cleanDir(wheelsDir);
  ensureDir(sitePackagesDir);

  console.log(`Using host Python for wheel download: ${hostPython}`);
  console.log('Downloading Windows wheels for requirements...');
  runCommand(
    `"${hostPython}" -m pip download --disable-pip-version-check --only-binary=:all: ` +
    `--platform win_amd64 --implementation cp --python-version "${pyVersion}" --abi "${abiTag}" ` +
    `--dest "${wheelsDir}" -r "${REQUIREMENTS_PATH}"`,
    ROOT
  );

  const wheelFiles = fs.readdirSync(wheelsDir).filter((name) => name.toLowerCase().endsWith('.whl'));
  if (wheelFiles.length === 0) {
    throw new Error('No wheel files were downloaded for runtime requirements');
  }

  console.log(`Extracting ${wheelFiles.length} wheel(s) into embedded site-packages...`);
  for (const wheelFile of wheelFiles) {
    const wheelPath = path.join(wheelsDir, wheelFile);
    unzipArchive(wheelPath, sitePackagesDir);
  }
}

function readPreparedMarker() {
  if (!fs.existsSync(PREPARED_MARKER)) return null;
  try {
    return readJson(PREPARED_MARKER);
  } catch {
    return null;
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  ensureExists(MANIFEST_PATH, 'Runtime manifest');
  ensureExists(REQUIREMENTS_PATH, 'Runtime requirements');

  const manifest = readJson(MANIFEST_PATH);
  ensureManifestTarget(manifest);

  const nodeSpec = String(manifest?.node?.version || '20.x');
  const pythonSpec = String(manifest?.python?.version || '3.11.x');

  const nodeVersion = await resolvePathSpec(nodeSpec, resolveNodeVersion);
  const pythonVersion = await resolvePathSpec(pythonSpec, resolvePythonVersion);
  const hostIsWindows = process.platform === 'win32';

  const nodeUrl = `https://nodejs.org/dist/v${nodeVersion}/node-v${nodeVersion}-win-x64.zip`;
  const pythonUrl = `https://www.python.org/ftp/python/${pythonVersion}/python-${pythonVersion}-embed-amd64.zip`;
  const getPipUrl = 'https://bootstrap.pypa.io/get-pip.py';

  if (args.dryRun) {
    console.log(JSON.stringify({
      mode: 'dry-run',
      nodeVersion,
      pythonVersion,
      hostPlatform: process.platform,
      packageInstallMode: hostIsWindows ? 'native-pip' : 'cross-wheel-extract',
      nodeUrl,
      pythonUrl,
      getPipUrl,
      stagingDir: STAGING_DIR,
      requirements: REQUIREMENTS_PATH,
    }, null, 2));
    return;
  }

  const prepared = readPreparedMarker();
  if (!args.force && prepared
      && prepared.nodeVersion === nodeVersion
      && prepared.pythonVersion === pythonVersion
      && prepared.requirementsSha256) {
    console.log('Runtime staging already prepared and version-matched. Use --force to rebuild.');
    return;
  }

  cleanDir(DOWNLOADS_DIR);
  cleanDir(TMP_DIR);
  cleanDir(STAGING_DIR);
  ensureDir(path.join(STAGING_DIR, 'node'));
  ensureDir(path.join(STAGING_DIR, 'python'));

  const nodeZip = path.join(DOWNLOADS_DIR, `node-v${nodeVersion}-win-x64.zip`);
  const pyZip = path.join(DOWNLOADS_DIR, `python-${pythonVersion}-embed-amd64.zip`);
  const getPipPy = path.join(DOWNLOADS_DIR, 'get-pip.py');

  console.log(`Downloading Node ${nodeVersion}...`);
  await downloadFile(nodeUrl, nodeZip);

  console.log(`Downloading Python ${pythonVersion} embeddable...`);
  await downloadFile(pythonUrl, pyZip);

  if (hostIsWindows) {
    console.log('Downloading get-pip.py...');
    await downloadFile(getPipUrl, getPipPy);
  }

  console.log('Extracting Node...');
  const nodeExtractTmp = path.join(TMP_DIR, 'node');
  cleanDir(nodeExtractTmp);
  unzipArchive(nodeZip, nodeExtractTmp);
  const nodeRoot = firstSubdirectory(nodeExtractTmp);
  if (!nodeRoot) {
    throw new Error('Failed to find extracted Node root directory');
  }
  const finalNodeDir = path.join(STAGING_DIR, 'node');
  fs.rmSync(finalNodeDir, { recursive: true, force: true });
  fs.renameSync(nodeRoot, finalNodeDir);

  console.log('Extracting Python...');
  unzipArchive(pyZip, path.join(STAGING_DIR, 'python'));

  const pythonDir = path.join(STAGING_DIR, 'python');
  const pythonExe = path.join(pythonDir, 'python.exe');
  const nodeExe = path.join(STAGING_DIR, 'node', 'node.exe');
  ensureExists(pythonExe, 'python.exe');
  ensureExists(nodeExe, 'node.exe');

  updatePythonPth(pythonDir);
  ensureDir(path.join(pythonDir, 'Lib', 'site-packages'));

  if (hostIsWindows) {
    console.log('Bootstrapping pip...');
    runCommand(`"${pythonExe}" "${getPipPy}" --no-warn-script-location`, ROOT);

    console.log('Installing Python packages...');
    runCommand(
      `"${pythonExe}" -m pip install --disable-pip-version-check --no-warn-script-location -r "${REQUIREMENTS_PATH}"`,
      ROOT
    );

    console.log('Verifying runtime binaries...');
    runCommand(`"${pythonExe}" -V`, ROOT);
    runCommand(`"${nodeExe}" -v`, ROOT);
  } else {
    console.log('Installing Python packages by wheel extraction (cross-platform mode)...');
    installPackagesByWheelExtraction(pythonVersion, pythonDir);
    console.log('Skipping direct execution checks for node.exe/python.exe on non-Windows host.');
  }

  const requirementsData = fs.readFileSync(REQUIREMENTS_PATH);
  const requirementsSha256 = require('crypto').createHash('sha256').update(requirementsData).digest('hex');
  fs.writeFileSync(PREPARED_MARKER, JSON.stringify({
    nodeVersion,
    pythonVersion,
    hostPlatform: process.platform,
    packageInstallMode: hostIsWindows ? 'native-pip' : 'cross-wheel-extract',
    requirementsSha256,
    preparedAt: new Date().toISOString(),
  }, null, 2), 'utf8');

  fs.rmSync(TMP_DIR, { recursive: true, force: true });
  console.log('\nRuntime staging prepared successfully.');
  console.log(`- staging: ${STAGING_DIR}`);
  console.log(`- node:    ${nodeVersion}`);
  console.log(`- python:  ${pythonVersion}`);
}

main().catch((err) => {
  console.error(`Failed: ${err.message}`);
  process.exit(1);
});
