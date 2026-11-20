# Builds deploy/out/galeheart-deploy.zip from the plugin targets.
# Run after the jars are built (mvn package or javac).

$ErrorActionPreference = 'Stop'

$here = $PSScriptRoot
$root = Split-Path -Parent $here
$out = Join-Path $here 'out'
$stage = Join-Path $out 'galeheart-deploy'

$jars = [ordered]@{
    'plugins/galeheart-core/target/galeheart-core-1.0.0.jar'          = 'GaleHeartCore.jar'
    'plugins/galeheart-arena/target/galeheart-arena-1.0.0.jar'        = 'GaleHeartArena.jar'
    'plugins/anomalous-weather/target/anomalous-weather-1.3.0.jar'    = 'AnomalousWeather.jar'
    'plugins/portal-block/target/PortalBlock.jar'                     = 'PortalBlock.jar'
    'plugins/first-join-class/target/FirstJoinClass.jar'              = 'FirstJoinClass.jar'
}

$configs = [ordered]@{
    'plugins/galeheart-core/src/main/resources/config.yml'       = 'GaleHeartCore/config.yml'
    'plugins/galeheart-arena/src/main/resources/config.yml'      = 'GaleHeartArena/config.yml'
    'plugins/anomalous-weather/src/main/resources/config.yml'    = 'AnomalousWeather/config.yml'
    'plugins/portal-block/src/main/resources/config.yml'         = 'PortalBlock/config.yml'
    'plugins/first-join-class/src/main/resources/config.yml'     = 'FirstJoinClass/config.yml'
}

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $stage 'plugins') | Out-Null

$missing = @()
foreach ($source in $jars.Keys) {
    $full = Join-Path $root $source
    if (-not (Test-Path $full)) {
        $missing += $source
        continue
    }
    Copy-Item $full (Join-Path $stage ('plugins/' + $jars[$source])) -Force
}
if ($missing.Count -gt 0) {
    Write-Host 'Missing jars:' -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  $_" }
    Write-Host 'Build the plugins first (mvn package or javac), then rerun.' -ForegroundColor Yellow
    exit 1
}

foreach ($source in $configs.Keys) {
    $full = Join-Path $root $source
    if (-not (Test-Path $full)) {
        Write-Host "Missing config: $source" -ForegroundColor Yellow
        continue
    }
    $target = Join-Path $stage ('plugins/' + $configs[$source])
    New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
    Copy-Item $full $target -Force
}

Copy-Item (Join-Path $here 'server.properties') $stage -Force
Copy-Item (Join-Path $here 'eula.txt') $stage -Force
Copy-Item (Join-Path $here 'README.md') (Join-Path $stage 'README-DEPLOY.md') -Force

$zip = Join-Path $out 'galeheart-deploy.zip'
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -Force

Write-Host ''
Write-Host "Built $zip" -ForegroundColor Green
Get-ChildItem (Join-Path $stage 'plugins') | Select-Object Name, Length | Format-Table -AutoSize
