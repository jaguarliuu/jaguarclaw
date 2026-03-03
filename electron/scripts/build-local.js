/**
 * JaguarClaw Local Build Script (Windows only)
 *
 * Usage: node electron/scripts/build-local.js [--name "Custom App Name"]
 *
 * - Reads local-icon.png from project root as icon (falls back to default if absent)
 * - Builds Windows NSIS installer only, no publish
 * - Same pipeline as build.js: Maven → Vue → copy resources → electron-builder
 */

const { execSync, spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// ── Parse args ────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
let customName = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--name' && args[i + 1]) {
    customName = args[i + 1].trim();
    i++;
  }
}

// ── Paths ─────────────────────────────────────────────────────────────────────

const ROOT = path.resolve(__dirname, '..', '..');
const ELECTRON_DIR = path.resolve(__dirname, '..');
const RESOURCES_DIR = path.join(ELECTRON_DIR, 'resources');
const UI_DIR = path.join(ROOT, 'jaguarclaw-ui');
const LOCAL_ICON = path.join(ELECTRON_DIR, 'assets','local-icon.ico');
const DEFAULT_ICON = path.join(ELECTRON_DIR, 'assets', 'icon.ico');

// ── Helpers ───────────────────────────────────────────────────────────────────

function run(cmd, cwd = ROOT) {
  console.log(`\n> ${cmd}`);
  execSync(cmd, { cwd, stdio: 'inherit' });
}

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

function slugify(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

// ── Config ────────────────────────────────────────────────────────────────────

const productName = customName || 'JaguarClaw';
const useCustomName = !!customName;

const iconPath = fs.existsSync(LOCAL_ICON) ? LOCAL_ICON : DEFAULT_ICON;
const iconLabel = fs.existsSync(LOCAL_ICON) ? 'local-icon.ico' : 'default icon.ico';

console.log('');
console.log('=== JaguarClaw Local Build (Windows) ===');
console.log(`  App name : ${productName}${useCustomName ? '' : ' (default)'}`);
console.log(`  Icon     : ${iconLabel}`);
console.log(`  Platform : Windows (NSIS)`);
console.log(`  Publish  : none`);
console.log('');

// ── Build ─────────────────────────────────────────────────────────────────────

try {
  // Step 1: Build Spring Boot JAR
  console.log('=== Step 1: Building Spring Boot JAR ===');
  run('mvn clean package -DskipTests');

  const targetDir = path.join(ROOT, 'target');
  const jars = fs.readdirSync(targetDir).filter(
    (f) => f.endsWith('.jar') && !f.endsWith('-plain.jar') && !f.includes('original')
  );
  if (jars.length === 0) throw new Error('No JAR found in target/');
  console.log(`Found JAR: ${jars[0]}`);

  // Step 2: Build Vue frontend
  console.log('\n=== Step 2: Building Vue frontend ===');
  run('npm install', UI_DIR);
  run('npm run build', UI_DIR);

  // Step 3: Copy JAR
  console.log('\n=== Step 3: Copying JAR ===');
  fs.mkdirSync(RESOURCES_DIR, { recursive: true });
  fs.copyFileSync(path.join(targetDir, jars[0]), path.join(RESOURCES_DIR, 'app.jar'));
  console.log(`Copied ${jars[0]} → resources/app.jar`);

  // Step 4: Copy webapp
  console.log('\n=== Step 4: Copying webapp ===');
  const webappDir = path.join(RESOURCES_DIR, 'webapp');
  if (fs.existsSync(webappDir)) fs.rmSync(webappDir, { recursive: true });
  copyDir(path.join(UI_DIR, 'dist'), webappDir);
  console.log('Copied jaguarclaw-ui/dist/ → resources/webapp/');

  // Step 5: Copy built-in skills
  console.log('\n=== Step 5: Copying built-in skills ===');
  const srcSkillsDir = path.join(ROOT, '.jaguarclaw', 'skills');
  const destSkillsDir = path.join(RESOURCES_DIR, 'skills');
  if (fs.existsSync(srcSkillsDir)) {
    if (fs.existsSync(destSkillsDir)) fs.rmSync(destSkillsDir, { recursive: true });
    copyDir(srcSkillsDir, destSkillsDir);
    console.log('Copied .jaguarclaw/skills/ → resources/skills/');
  } else {
    console.log('No .jaguarclaw/skills/ found, skipping');
  }

  // Step 6: Check JRE
  console.log('\n=== Step 6: Checking JRE ===');
  const javaExe = path.join(RESOURCES_DIR, 'jre', 'bin', 'java.exe');
  if (!fs.existsSync(javaExe)) {
    throw new Error(
      'Windows JRE not found at resources/jre/\n' +
      'Run "npm run download-jre-win" in the electron/ directory first.\n' +
      '(This downloads a Windows x64 JRE — required even when building from macOS.)'
    );
  }
  console.log('JRE found.');

  // Step 7: electron-builder (Windows only, no publish)
  // Use spawnSync with args array to safely pass user-provided productName
  console.log('\n=== Step 7: Building Windows installer ===');

  const ebArgs = [
    'electron-builder',
    '--win',
    `--config.productName=${productName}`,
    `--config.win.icon=${iconPath.replace(/\\/g, '/')}`,
  ];
  if (useCustomName) {
    ebArgs.push(`--config.appId=com.local.${slugify(productName)}`);
  }

  console.log(`\n> npx ${ebArgs.join(' ')}`);
  const result = spawnSync('npx', ebArgs, {
    cwd: ELECTRON_DIR,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });

  if (result.status !== 0) {
    throw new Error(`electron-builder exited with code ${result.status}`);
  }

  console.log('\n=== Build complete! ===');
  console.log(`Installer is in: ${path.join(ELECTRON_DIR, 'release')}`);
} catch (err) {
  console.error('\nBuild failed:', err.message);
  process.exit(1);
}
