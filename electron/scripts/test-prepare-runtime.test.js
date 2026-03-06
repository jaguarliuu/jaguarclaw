const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const { ensureWindowsCmdShimForExe } = require('./prepare-runtime');

test('windows agent-browser cmd shim seeds AGENT_BROWSER_HOME', () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaguar-prepare-runtime-'));
  const exePath = path.join(tempDir, 'agent-browser.exe');
  fs.writeFileSync(exePath, '');

  const cmdPath = ensureWindowsCmdShimForExe(exePath);
  assert.equal(cmdPath, path.join(tempDir, 'agent-browser.cmd'));

  const content = fs.readFileSync(cmdPath, 'utf8');
  assert.match(content, /AGENT_BROWSER_HOME/);
  assert.match(content, /%~dp0\.\./);
});
