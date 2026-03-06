# Runtime Bundle Guide

This folder defines the bundled runtime payload used by the desktop app.

## Directory Layout

```text
runtime/
  manifest.json          # runtime metadata + package list
  requirements.txt       # Python package baseline
  staging/               # unpacked runtime root (local only, gitignored)
    bin/
      agent-browser.cmd  # bundled browser CLI
      agent-browser.exe  # optional (if distributed as native exe)
    browser/
      chromium/
        chrome.exe       # bundled Chromium kernel
    python/
      python.exe
      Scripts/
    node/
      node.exe
      npm.cmd
      npx.cmd
  runtime.zip            # generated artifact (gitignored)
  runtime.version        # generated from manifest version (gitignored)
```

## Build Runtime Zip

From repository root:

```bash
cd electron
npm run prepare-runtime
npm run package-runtime
```

Or directly:

```bash
node electron/scripts/prepare-runtime.js
node electron/scripts/package-runtime.js
```

The script:

1. `prepare-runtime` downloads Node/Python and builds `runtime/staging/`.
2. `prepare-runtime` stages bundled browser runtime from `manifest.json`:
   - `agentBrowser` (CLI binary)
   - `chromium` (browser kernel)
3. `prepare-runtime` installs `runtime/requirements.txt`:
   - on Windows host: bootstraps pip via embedded `python.exe`
   - on macOS/Linux host: downloads `win_amd64` wheels and unpacks into `Lib/site-packages`
4. `package-runtime` validates staging and creates `runtime/runtime.zip`.
5. `package-runtime` writes `runtime/runtime.version` from `runtime/manifest.json`.

## Browser Runtime Source

Browser artifacts are defined in `runtime/manifest.json`:

- `agentBrowser.downloadUrl` or `agentBrowser.localPath`
- `chromium.downloadUrl` or `chromium.localPath`

`localPath` can point to a local archive/binary for offline packaging.

## Notes

- This repo does **not** commit binary runtime payloads.
- `electron/scripts/build.js` and `build-local.js` auto-run `prepare-runtime` when runtime bundle is missing.
- If `runtime/runtime.zip` is missing, Electron build still works (runtime bundle is optional).
- Cross-platform prepare from macOS/Linux requires host `python3` with `pip`.
