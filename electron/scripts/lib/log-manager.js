const fs = require('node:fs');
const path = require('node:path');

const DEFAULT_MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const DEFAULT_MAX_HISTORY = 20;
const DEFAULT_MAX_AGE_DAYS = 7;

function pad(value, width = 2) {
  return String(value).padStart(width, '0');
}

function formatArchiveTimestamp(date) {
  return [
    date.getUTCFullYear(),
    pad(date.getUTCMonth() + 1),
    pad(date.getUTCDate()),
  ].join('') + '-' + [
    pad(date.getUTCHours()),
    pad(date.getUTCMinutes()),
    pad(date.getUTCSeconds()),
  ].join('') + '-' + pad(date.getUTCMilliseconds(), 3);
}

function createLogManager(options = {}) {
  const logDir = options.logDir;
  const maxFileSizeBytes = options.maxFileSizeBytes || DEFAULT_MAX_FILE_SIZE_BYTES;
  const maxHistory = options.maxHistory || DEFAULT_MAX_HISTORY;
  const maxAgeDays = options.maxAgeDays || DEFAULT_MAX_AGE_DAYS;
  const now = typeof options.now === 'function' ? options.now : () => new Date();

  if (!logDir) {
    throw new Error('logDir is required');
  }

  function ensureDir() {
    fs.mkdirSync(logDir, { recursive: true });
  }

  function getChannelPath(channel) {
    return path.join(logDir, `${channel}.log`);
  }

  function listArchives(channel) {
    const prefix = `${channel}.`;
    return fs.readdirSync(logDir, { withFileTypes: true })
      .filter((entry) => entry.isFile())
      .map((entry) => entry.name)
      .filter((name) => name.startsWith(prefix) && name.endsWith('.log') && name !== `${channel}.log`)
      .map((name) => ({
        name,
        path: path.join(logDir, name),
        mtimeMs: fs.statSync(path.join(logDir, name)).mtimeMs,
      }));
  }

  function pruneArchives(channel, referenceDate) {
    const archives = listArchives(channel);
    const maxAgeMs = maxAgeDays * 24 * 60 * 60 * 1000;
    for (const archive of archives) {
      if (referenceDate.getTime() - archive.mtimeMs > maxAgeMs) {
        fs.rmSync(archive.path, { force: true });
      }
    }

    const remaining = listArchives(channel)
      .sort((left, right) => right.mtimeMs - left.mtimeMs);
    for (const archive of remaining.slice(maxHistory)) {
      fs.rmSync(archive.path, { force: true });
    }
  }

  function rotateIfNeeded(channel, nextLine) {
    const channelPath = getChannelPath(channel);
    if (!fs.existsSync(channelPath)) {
      return;
    }

    const currentSize = fs.statSync(channelPath).size;
    if (currentSize + Buffer.byteLength(nextLine, 'utf8') <= maxFileSizeBytes) {
      return;
    }

    const archiveName = `${channel}.${formatArchiveTimestamp(now())}.log`;
    fs.renameSync(channelPath, path.join(logDir, archiveName));
  }

  function append(channel, level, message) {
    if (!channel || !message) {
      return;
    }

    ensureDir();
    const timestamp = now().toISOString();
    const lines = String(message)
      .replace(/\r/g, '')
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean);

    for (const line of lines) {
      const logLine = `[${timestamp}] [${String(level || 'info').toUpperCase()}] ${line}\n`;
      rotateIfNeeded(channel, logLine);
      fs.appendFileSync(getChannelPath(channel), logLine, 'utf8');
    }

    pruneArchives(channel, now());
  }

  ensureDir();

  return {
    append,
    getChannelPath,
    getLogDirectory: () => logDir,
  };
}

module.exports = {
  createLogManager,
};
