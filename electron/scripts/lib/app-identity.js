const path = require('path');

const KNOWN_IDENTITIES = Object.freeze({
  jaguarclaw: {
    displayName: 'JaguarClaw',
    dataDirName: 'JaguarClaw',
    appUserModelId: 'com.jaguarliu.jaguarclaw',
  },
  'jaguarclaw-desktop': {
    displayName: 'JaguarClaw',
    dataDirName: 'JaguarClaw',
    appUserModelId: 'com.jaguarliu.jaguarclaw',
  },
  miniclaw: {
    displayName: 'MiniClaw',
    dataDirName: 'MiniClaw',
    appUserModelId: 'com.local.miniclaw',
  },
});

function normalizeIdentityKey(value) {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/\.exe$/i, '');
}

function toDisplayNameFromKey(key) {
  const normalized = normalizeIdentityKey(key);
  if (!normalized) {
    return 'JaguarClaw';
  }

  const known = KNOWN_IDENTITIES[normalized];
  if (known) {
    return known.displayName;
  }

  return normalized
    .replace(/[-_]+/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function resolveAppIdentity(options = {}) {
  const appNameKey = normalizeIdentityKey(options.appName);
  const exeNameKey = normalizeIdentityKey(path.basename(options.exePath || '', path.extname(options.exePath || '')));

  const known = KNOWN_IDENTITIES[exeNameKey] || KNOWN_IDENTITIES[appNameKey];
  if (known) {
    return { ...known };
  }

  const displayName = toDisplayNameFromKey(exeNameKey || appNameKey);
  return {
    displayName,
    dataDirName: displayName,
    appUserModelId: displayName === 'MiniClaw' ? 'com.local.miniclaw' : 'com.jaguarliu.jaguarclaw',
  };
}

module.exports = {
  resolveAppIdentity,
};
