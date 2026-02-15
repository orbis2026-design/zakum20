[CmdletBinding()]
param(
  [switch]$IncludeApiTests,
  [switch]$IncludeCoreCompile
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

Push-Location $root
try {
  $tasks = @('verifyPlatformInfrastructure')
  if ($IncludeApiTests) { $tasks += ':zakum-api:test' }
  if ($IncludeCoreCompile) { $tasks += ':zakum-core:compileJava' }

  $joined = $tasks -join ' '
  Write-Host "Running process gates: $joined"
  & .\gradlew $tasks
  if ($LASTEXITCODE -ne 0) {
    throw "Process gates failed with exit code $LASTEXITCODE"
  }
} finally {
  Pop-Location
}
