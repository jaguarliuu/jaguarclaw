const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const { createLogManager } = require('./lib/log-manager');

function makeTempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'jc-log-manager-'));
}

test('writes each channel into its own file', () => {
  const logDir = makeTempDir();
  const manager = createLogManager({ logDir, maxFileSizeBytes: 4096, maxHistory: 20, maxAgeDays: 7 });

  manager.append('startup', 'info', 'startup ready');
  manager.append('backend-bridge', 'error', 'java stacktrace');

  const startupLog = fs.readFileSync(path.join(logDir, 'startup.log'), 'utf8');
  const backendLog = fs.readFileSync(path.join(logDir, 'backend-bridge.log'), 'utf8');

  assert.match(startupLog, /startup ready/);
  assert.doesNotMatch(startupLog, /java stacktrace/);
  assert.match(backendLog, /java stacktrace/);
});

test('rotates channel file when size limit is exceeded', () => {
  const logDir = makeTempDir();
  let tick = 0;
  const manager = createLogManager({
    logDir,
    maxFileSizeBytes: 120,
    maxHistory: 20,
    maxAgeDays: 7,
    now: () => new Date(Date.UTC(2026, 2, 6, 12, 0, 0, tick++)),
  });

  manager.append('startup', 'info', 'A'.repeat(70));
  manager.append('startup', 'info', 'B'.repeat(70));

  const files = fs.readdirSync(logDir).filter((name) => name.startsWith('startup'));
  assert.equal(files.includes('startup.log'), true);
  assert.equal(files.some((name) => /^startup\.\d{8}-\d{6}-\d{3}\.log$/.test(name)), true);

  const currentLog = fs.readFileSync(path.join(logDir, 'startup.log'), 'utf8');
  assert.match(currentLog, /B+/);
});

test('prunes old archives by age and history limit', () => {
  const logDir = makeTempDir();
  const manager = createLogManager({
    logDir,
    maxFileSizeBytes: 120,
    maxHistory: 2,
    maxAgeDays: 7,
    now: () => new Date(Date.UTC(2026, 2, 10, 8, 0, 0, 0)),
  });

  const freshOne = path.join(logDir, 'startup.20260309-080000-000.log');
  const freshTwo = path.join(logDir, 'startup.20260309-080001-000.log');
  const stale = path.join(logDir, 'startup.20260220-080000-000.log');
  const overflow = path.join(logDir, 'startup.20260309-080002-000.log');
  for (const file of [freshOne, freshTwo, stale, overflow]) {
    fs.writeFileSync(file, 'x');
  }
  fs.utimesSync(stale, new Date('2026-02-20T08:00:00Z'), new Date('2026-02-20T08:00:00Z'));
  fs.utimesSync(freshOne, new Date('2026-03-09T08:00:00Z'), new Date('2026-03-09T08:00:00Z'));
  fs.utimesSync(freshTwo, new Date('2026-03-09T08:00:01Z'), new Date('2026-03-09T08:00:01Z'));
  fs.utimesSync(overflow, new Date('2026-03-09T08:00:02Z'), new Date('2026-03-09T08:00:02Z'));

  manager.append('startup', 'info', 'trigger prune');

  const archives = fs.readdirSync(logDir)
    .filter((name) => /^startup\.\d{8}-\d{6}-\d{3}\.log$/.test(name))
    .sort();

  assert.equal(archives.includes(path.basename(stale)), false);
  assert.equal(archives.length, 2);
  assert.deepEqual(archives, [path.basename(freshTwo), path.basename(overflow)]);
});
