param([string]$ServerAddress = '', [int]$Port = 7777)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force bin | Out-Null
    $sources = @(Get-ChildItem -LiteralPath src -Recurse -Filter *.java | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -d bin @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
    $options = @("-Dpvp.port=$Port")
    if ($ServerAddress) { $options += "-Dpvp.host=$ServerAddress" }
    & java @options -cp 'bin;res' engine.Core
    if ($LASTEXITCODE -ne 0) { throw 'Game exited with an error' }
} finally { Pop-Location }
