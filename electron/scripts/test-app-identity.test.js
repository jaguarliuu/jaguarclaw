const test = require('node:test');
const assert = require('node:assert/strict');

const { resolveAppIdentity } = require('./lib/app-identity');

test('production packaged app resolves JaguarClaw identity from desktop package name', () => {
  const identity = resolveAppIdentity({
    appName: 'jaguarclaw-desktop',
    exePath: 'C:/Program Files/JaguarClaw/JaguarClaw.exe',
    isPackaged: true,
  });

  assert.equal(identity.displayName, 'JaguarClaw');
  assert.equal(identity.dataDirName, 'JaguarClaw');
  assert.equal(identity.appUserModelId, 'com.jaguarliu.jaguarclaw');
});

test('local packaged app resolves MiniClaw identity from executable name', () => {
  const identity = resolveAppIdentity({
    appName: 'jaguarclaw-desktop',
    exePath: 'C:/Program Files/MiniClaw/miniclaw.exe',
    isPackaged: true,
  });

  assert.equal(identity.displayName, 'MiniClaw');
  assert.equal(identity.dataDirName, 'MiniClaw');
  assert.equal(identity.appUserModelId, 'com.local.miniclaw');
});
