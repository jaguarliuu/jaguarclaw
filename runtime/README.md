# Runtime Bundle Guide

This folder defines the bundled runtime payload used by the desktop app.

## Directory Layout

```text
runtime/
  manifest.json          # runtime metadata + package list
  requirements.txt       # Python package baseline
  staging/               # unpacked runtime root (local only, gitignored)
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
2. `prepare-runtime` bootstraps pip and installs `runtime/requirements.txt`.
3. `package-runtime` validates staging and creates `runtime/runtime.zip`.
4. `package-runtime` writes `runtime/runtime.version` from `runtime/manifest.json`.

## Notes

- This repo does **not** commit binary runtime payloads.
- `electron/scripts/build.js` and `build-local.js` auto-run `prepare-runtime` when runtime bundle is missing.
- If `runtime/runtime.zip` is missing, Electron build still works (runtime bundle is optional).
