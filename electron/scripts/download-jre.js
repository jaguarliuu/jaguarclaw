/**
 * Download Adoptium Temurin JRE for Electron bundling.
 * Detects platform (Windows/macOS) and architecture (x64/aarch64) automatically.
 * Falls back: JRE 24 → JDK 24 → JRE 21 → JDK 21
 * Extracts to electron/resources/jre/
 *
 * Usage: node scripts/download-jre.js
 */

const https = require('https');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const IS_WINDOWS = process.platform === 'win32';
const IS_MAC = process.platform === 'darwin';
const OS = IS_WINDOWS ? 'windows' : IS_MAC ? 'mac' : 'linux';
const ARCH = process.arch === 'arm64' ? 'aarch64' : 'x64';
const ARCHIVE_EXT = IS_WINDOWS ? '.zip' : '.tar.gz';
const JAVA_BIN = IS_WINDOWS ? 'java.exe' : 'java';

const RESOURCES_DIR = path.resolve(__dirname, '..', 'resources');
const JRE_DIR = path.join(RESOURCES_DIR, 'jre');
const TEMP_DIR = path.join(RESOURCES_DIR, '_jre_temp');

// Fallback chain: try each combination in order
const FALLBACK_CHAIN = [
  { version: '24', imageType: 'jre' },
  { version: '24', imageType: 'jdk' },
  { version: '21', imageType: 'jre' },
  { version: '21', imageType: 'jdk' },
];

function buildApiUrl(version, imageType) {
  return (
    `https://api.adoptium.net/v3/assets/latest/${version}/hotspot` +
    `?architecture=${ARCH}&image_type=${imageType}&os=${OS}&vendor=eclipse`
  );
}

function httpsGet(url, retries = 2) {
  return new Promise((resolve, reject) => {
    const attempt = (retriesLeft) => {
      const request = (url) => {
        https.get(url, { headers: { 'User-Agent': 'JaguarClaw-Build' } }, (res) => {
          if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
            request(res.headers.location);
            return;
          }
          if (res.statusCode >= 500 && retriesLeft > 0) {
            console.log(`  HTTP ${res.statusCode}, retrying in 3s... (${retriesLeft} left)`);
            res.resume();
            setTimeout(() => attempt(retriesLeft - 1), 3000);
            return;
          }
          if (res.statusCode !== 200) {
            res.resume();
            return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
          }
          resolve(res);
        }).on('error', (err) => {
          if (retriesLeft > 0) {
            console.log(`  Network error, retrying in 3s... (${retriesLeft} left)`);
            setTimeout(() => attempt(retriesLeft - 1), 3000);
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

function downloadFile(url, destPath) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(destPath);
    const request = (url) => {
      https.get(url, { headers: { 'User-Agent': 'JaguarClaw-Build' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          request(res.headers.location);
          return;
        }
        if (res.statusCode !== 200) {
          file.close();
          fs.unlinkSync(destPath);
          return reject(new Error(`HTTP ${res.statusCode}`));
        }

        const totalBytes = parseInt(res.headers['content-length'], 10);
        let downloaded = 0;

        res.on('data', (chunk) => {
          downloaded += chunk.length;
          if (totalBytes) {
            const pct = ((downloaded / totalBytes) * 100).toFixed(1);
            process.stdout.write(`\rDownloading... ${pct}% (${(downloaded / 1048576).toFixed(1)} MB)`);
          }
        });

        res.pipe(file);
        file.on('finish', () => {
          file.close();
          console.log('\nDownload complete.');
          resolve();
        });
      }).on('error', (err) => {
        file.close();
        fs.unlinkSync(destPath);
        reject(err);
      });
    };
    request(url);
  });
}

async function tryFetchAsset(version, imageType) {
  const url = buildApiUrl(version, imageType);
  console.log(`Trying Adoptium ${imageType.toUpperCase()} ${version} for ${OS}/${ARCH}...`);

  try {
    const res = await httpsGet(url);
    let body = '';
    for await (const chunk of res) {
      body += chunk;
    }

    const assets = JSON.parse(body);
    if (!assets || assets.length === 0) {
      console.log(`  No ${imageType.toUpperCase()} ${version} assets found.`);
      return null;
    }

    const asset = assets.find((a) => a.binary?.package?.name?.endsWith(ARCHIVE_EXT));
    if (!asset) {
      console.log(`  No ${ARCHIVE_EXT} package found.`);
      return null;
    }

    return {
      downloadUrl: asset.binary.package.link,
      fileName: asset.binary.package.name,
      version,
      imageType,
    };
  } catch (err) {
    console.log(`  Failed: ${err.message}`);
    return null;
  }
}

async function main() {
  // Try each fallback option
  let asset = null;
  for (const { version, imageType } of FALLBACK_CHAIN) {
    asset = await tryFetchAsset(version, imageType);
    if (asset) break;
  }

  if (!asset) {
    throw new Error(
      'Could not find any Adoptium JRE/JDK build. ' +
      `Tried: ${FALLBACK_CHAIN.map((f) => `${f.imageType}-${f.version}`).join(', ')}`
    );
  }

  console.log(`\nUsing: ${asset.fileName}`);

  // Prepare directories
  fs.mkdirSync(RESOURCES_DIR, { recursive: true });
  const archivePath = path.join(RESOURCES_DIR, asset.fileName);

  // Download
  if (fs.existsSync(archivePath)) {
    console.log('Archive already downloaded, skipping download.');
  } else {
    await downloadFile(asset.downloadUrl, archivePath);
  }

  // Extract
  console.log('Extracting...');
  if (fs.existsSync(TEMP_DIR)) {
    fs.rmSync(TEMP_DIR, { recursive: true });
  }
  fs.mkdirSync(TEMP_DIR, { recursive: true });

  if (IS_WINDOWS) {
    execSync(
      `powershell -NoProfile -Command "Expand-Archive -Path '${archivePath}' -DestinationPath '${TEMP_DIR}' -Force"`,
      { stdio: 'inherit' }
    );
  } else {
    execSync(`tar xzf "${archivePath}" -C "${TEMP_DIR}"`, { stdio: 'inherit' });
  }

  // The extracted folder usually has a name like jdk-24.0.1+9-jre
  const extracted = fs.readdirSync(TEMP_DIR);
  const jreFolder = extracted.find((d) =>
    fs.statSync(path.join(TEMP_DIR, d)).isDirectory()
  );

  if (!jreFolder) {
    throw new Error('Could not find extracted JRE directory');
  }

  // On macOS, the JRE/JDK has a Contents/Home structure
  const extractedRoot = path.join(TEMP_DIR, jreFolder);
  const contentsHome = path.join(extractedRoot, 'Contents', 'Home');
  const jreHome = IS_MAC && fs.existsSync(contentsHome) ? contentsHome : extractedRoot;

  // Move to final location
  if (fs.existsSync(JRE_DIR)) {
    fs.rmSync(JRE_DIR, { recursive: true });
  }
  fs.renameSync(jreHome, JRE_DIR);

  // Cleanup
  fs.rmSync(TEMP_DIR, { recursive: true, force: true });
  fs.unlinkSync(archivePath);

  // Verify
  const javaBin = path.join(JRE_DIR, 'bin', JAVA_BIN);
  if (!fs.existsSync(javaBin)) {
    throw new Error(`${JAVA_BIN} not found after extraction`);
  }

  console.log(`\nJava runtime installed to: ${JRE_DIR}`);
  execSync(`"${javaBin}" -version`, { stdio: 'inherit' });

  if (asset.version !== '24') {
    console.log(
      `\nNote: Using Java ${asset.version} (LTS) runtime because Java 24 was not available for ${OS}/${ARCH}.`
    );
  }
  if (asset.imageType === 'jdk') {
    console.log(
      `Note: Using JDK instead of JRE because JRE was not available for Java ${asset.version} ${OS}/${ARCH}.`
    );
  }
}

main().catch((err) => {
  console.error('Failed:', err.message);
  process.exit(1);
});
