/**
 * Package bundled runtime payload.
 *
 * Input:  <repo>/runtime/staging/
 * Output: <repo>/runtime/runtime.zip + runtime.version
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const RUNTIME_DIR = path.join(ROOT, 'runtime');

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const current = argv[i];
    if (!current.startsWith('--')) continue;
    const key = current.slice(2);
    const value = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : 'true';
    args[key] = value;
  }
  return args;
}

function ensureExists(filePath, label) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`${label} not found: ${filePath}`);
  }
}

function defaultBinaryLayout(targetOs) {
  if (targetOs === 'win32') {
    return {
      pythonBin: 'python/python.exe',
      nodeBin: 'node/node.exe',
    };
  }
  if (targetOs === 'darwin') {
    return {
      pythonBin: 'python/bin/python3',
      nodeBin: 'node/bin/node',
    };
  }
  return {
    pythonBin: 'python/bin/python3',
    nodeBin: 'node/bin/node',
  };
}

function toWindowsPath(value) {
  return value.replace(/\//g, '\\');
}

function psQuote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function zipDirectory(sourceDir, outputZip) {
  fs.rmSync(outputZip, { force: true });

  if (process.platform === 'win32') {
    const srcGlob = `${toWindowsPath(sourceDir)}\\*`;
    const cmd = `Compress-Archive -Path ${psQuote(srcGlob)} -DestinationPath ${psQuote(toWindowsPath(outputZip))} -Force`;
    execSync(
      `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command ${psQuote(cmd)}`,
      { stdio: 'inherit' }
    );
    return;
  }

  execSync(`zip -qr "${outputZip}" .`, { cwd: sourceDir, stdio: 'inherit' });
}

function main() {
  const cli = parseArgs(process.argv.slice(2));

  const manifestPath = path.resolve(ROOT, cli.manifest || path.join('runtime', 'manifest.json'));
  const sourceDir = path.resolve(ROOT, cli.src || path.join('runtime', 'staging'));
  const outputZip = path.resolve(ROOT, cli.out || path.join('runtime', 'runtime.zip'));
  const versionFile = path.resolve(ROOT, cli.version || path.join('runtime', 'runtime.version'));

  ensureExists(manifestPath, 'Manifest');
  ensureExists(sourceDir, 'Runtime staging directory');

  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const version = String(manifest.version || '').trim();
  if (!version) {
    throw new Error('manifest.version is required');
  }

  const targetOs = manifest.target && manifest.target.os ? String(manifest.target.os) : 'win32';
  const defaults = defaultBinaryLayout(targetOs);
  const pythonBinRel = (manifest.python && manifest.python.bin) || defaults.pythonBin;
  const nodeBinRel = (manifest.node && manifest.node.bin) || defaults.nodeBin;

  const pythonBinAbs = path.join(sourceDir, pythonBinRel);
  const nodeBinAbs = path.join(sourceDir, nodeBinRel);
  ensureExists(pythonBinAbs, 'Python executable');
  ensureExists(nodeBinAbs, 'Node executable');

  fs.mkdirSync(path.dirname(outputZip), { recursive: true });
  zipDirectory(sourceDir, outputZip);
  fs.writeFileSync(versionFile, `${version}\n`, 'utf8');

  const summary = {
    version,
    target: manifest.target || null,
    sourceDir,
    outputZip,
    versionFile,
    pythonBin: pythonBinRel,
    nodeBin: nodeBinRel,
    pythonPackages: manifest.python && Array.isArray(manifest.python.packages)
      ? manifest.python.packages.length
      : 0,
  };
  console.log(JSON.stringify(summary, null, 2));
}

try {
  main();
} catch (error) {
  console.error(`Package runtime failed: ${error.message}`);
  process.exit(1);
}
