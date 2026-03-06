const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const config = require('../builder.local.json');

const electronDir = path.resolve(__dirname, '..');

function expectResourceExists(relativePath) {
  const absolutePath = path.join(electronDir, relativePath);
  assert.equal(fs.existsSync(absolutePath), true, `Missing resource: ${relativePath}`);
}

test('builder.local.json icon resources exist on disk', () => {
  expectResourceExists(config.extraResources[0].from);
  expectResourceExists(config.win.icon);
  expectResourceExists(config.mac.icon);
});
