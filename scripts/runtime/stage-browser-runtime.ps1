param(
  [string]$AgentSource = "",
  [string]$ChromiumSource = "",
  [switch]$NoClean
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir "..\..")
$ManifestPath = Join-Path $RootDir "runtime\manifest.json"
$DownloadDir = Join-Path $RootDir "runtime\_downloads"
$StagingDir = Join-Path $RootDir "runtime\staging"
$TmpDir = Join-Path $RootDir "runtime\_tmp\browser-stage"

if (-not (Test-Path $ManifestPath)) {
  throw "manifest not found: $ManifestPath"
}

$Manifest = Get-Content -Raw $ManifestPath | ConvertFrom-Json

function Get-Required([string]$Value, [string]$FieldName) {
  if ([string]::IsNullOrWhiteSpace($Value)) {
    throw "manifest.$FieldName is required"
  }
  return $Value.Trim()
}

function Is-Url([string]$Value) {
  return $Value -match '^https?://'
}

function Resolve-SourcePath([string]$Source) {
  if (Is-Url $Source) { return $Source }
  if ([System.IO.Path]::IsPathRooted($Source)) { return $Source }
  return (Join-Path $RootDir $Source)
}

function Ensure-Dir([string]$PathValue) {
  if (-not (Test-Path $PathValue)) {
    New-Item -Path $PathValue -ItemType Directory -Force | Out-Null
  }
}

function Download-File([string]$Url, [string]$Dest) {
  Ensure-Dir (Split-Path -Parent $Dest)
  Invoke-WebRequest -Uri $Url -OutFile $Dest
}

function Copy-DirectoryContents([string]$SourceDir, [string]$DestDir) {
  Ensure-Dir $DestDir
  Copy-Item -Path (Join-Path $SourceDir "*") -Destination $DestDir -Recurse -Force
}

function Save-SourceToFile([string]$Source, [string]$Dest) {
  Ensure-Dir (Split-Path -Parent $Dest)
  if (Is-Url $Source) {
    Write-Host "Downloading: $Source"
    Download-File -Url $Source -Dest $Dest
    return
  }
  if (-not (Test-Path $Source)) {
    throw "Source file not found: $Source"
  }
  Copy-Item -Path $Source -Destination $Dest -Force
}

$AgentBinRel = Get-Required -Value $Manifest.agentBrowser.bin -FieldName "agentBrowser.bin"
$AgentManifestSource = if (-not [string]::IsNullOrWhiteSpace($Manifest.agentBrowser.localPath)) {
  $Manifest.agentBrowser.localPath
} else {
  $Manifest.agentBrowser.downloadUrl
}
$AgentSourceRaw = if (-not [string]::IsNullOrWhiteSpace($AgentSource)) { $AgentSource } else { $AgentManifestSource }
if ([string]::IsNullOrWhiteSpace($AgentSourceRaw)) {
  throw "No agent-browser source configured (agentBrowser.localPath / agentBrowser.downloadUrl)"
}
$AgentSourceResolved = Resolve-SourcePath $AgentSourceRaw

$ChromiumRootRel = Get-Required -Value $Manifest.chromium.root -FieldName "chromium.root"
$ChromiumBinRel = Get-Required -Value $Manifest.chromium.bin -FieldName "chromium.bin"
$ChromiumArchiveRoot = if ($Manifest.chromium.archiveRoot) { [string]$Manifest.chromium.archiveRoot } else { "" }
$ChromiumManifestSource = if (-not [string]::IsNullOrWhiteSpace($Manifest.chromium.localPath)) {
  $Manifest.chromium.localPath
} else {
  $Manifest.chromium.downloadUrl
}
$ChromiumSourceRaw = if (-not [string]::IsNullOrWhiteSpace($ChromiumSource)) { $ChromiumSource } else { $ChromiumManifestSource }
if ([string]::IsNullOrWhiteSpace($ChromiumSourceRaw)) {
  throw "No chromium source configured (chromium.localPath / chromium.downloadUrl)"
}
$ChromiumSourceResolved = Resolve-SourcePath $ChromiumSourceRaw

Ensure-Dir $DownloadDir
Ensure-Dir $StagingDir
if (Test-Path $TmpDir) {
  Remove-Item -Path $TmpDir -Recurse -Force
}
Ensure-Dir $TmpDir

# Stage agent-browser
$AgentDest = Join-Path $StagingDir $AgentBinRel
$AgentExt = [System.IO.Path]::GetExtension($AgentDest)
if ([string]::IsNullOrWhiteSpace($AgentExt)) { $AgentExt = ".bin" }
$AgentDownloaded = Join-Path $DownloadDir ("agent-browser" + $AgentExt)
Save-SourceToFile -Source $AgentSourceResolved -Dest $AgentDownloaded
Ensure-Dir (Split-Path -Parent $AgentDest)
Copy-Item -Path $AgentDownloaded -Destination $AgentDest -Force

$AgentCmdShim = $null
if ($AgentDest.ToLowerInvariant().EndsWith(".exe")) {
  $AgentBase = [System.IO.Path]::GetFileNameWithoutExtension($AgentDest)
  $AgentCmdShim = Join-Path (Split-Path -Parent $AgentDest) ($AgentBase + ".cmd")
  @"
@echo off
"%~dp0$AgentBase.exe" %*
"@ | Out-File -FilePath $AgentCmdShim -Encoding ascii -Force
}

# Stage chromium
$ChromiumRootDest = Join-Path $StagingDir $ChromiumRootRel
if (Test-Path $ChromiumRootDest) {
  Remove-Item -Path $ChromiumRootDest -Recurse -Force
}
Ensure-Dir $ChromiumRootDest

if ((Test-Path $ChromiumSourceResolved) -and (Get-Item $ChromiumSourceResolved).PSIsContainer) {
  Copy-DirectoryContents -SourceDir $ChromiumSourceResolved -DestDir $ChromiumRootDest
} else {
  $ChromiumZip = Join-Path $DownloadDir "chromium.zip"
  Save-SourceToFile -Source $ChromiumSourceResolved -Dest $ChromiumZip

  $ExtractDir = Join-Path $TmpDir "extract"
  Ensure-Dir $ExtractDir
  Expand-Archive -Path $ChromiumZip -DestinationPath $ExtractDir -Force

  $SourceDir = $ExtractDir
  if (-not [string]::IsNullOrWhiteSpace($ChromiumArchiveRoot)) {
    $Candidate = Join-Path $ExtractDir $ChromiumArchiveRoot
    if (Test-Path $Candidate) {
      $SourceDir = $Candidate
    }
  } else {
    $TopDirs = Get-ChildItem -Path $ExtractDir -Directory
    if ($TopDirs.Count -eq 1) {
      $SourceDir = $TopDirs[0].FullName
    }
  }

  Copy-DirectoryContents -SourceDir $SourceDir -DestDir $ChromiumRootDest
}

if (-not (Test-Path $AgentDest)) {
  throw "agent-browser staged binary missing: $AgentDest"
}

$ChromiumBinDest = Join-Path $StagingDir $ChromiumBinRel
if (-not (Test-Path $ChromiumBinDest)) {
  throw "chromium staged binary missing: $ChromiumBinDest"
}

Write-Host "Staging complete:"
Write-Host "  agent-browser: $AgentDest"
if ($AgentCmdShim) {
  Write-Host "  agent-browser shim: $AgentCmdShim"
}
Write-Host "  chromium root: $ChromiumRootDest"
Write-Host "  chromium bin: $ChromiumBinDest"

if (-not $NoClean) {
  Remove-Item -Path $TmpDir -Recurse -Force -ErrorAction SilentlyContinue
}
