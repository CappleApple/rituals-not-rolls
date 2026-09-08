$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$formatterPath = Join-Path $projectRoot 'build/tools/google-java-format-1.36.0-all-deps.jar'
if (-not (Test-Path -LiteralPath $formatterPath)) {
    New-Item -ItemType Directory -Force (Split-Path -Parent $formatterPath) | Out-Null
    Invoke-WebRequest 'https://github.com/google/google-java-format/releases/download/v1.36.0/google-java-format-1.36.0-all-deps.jar' -OutFile $formatterPath
}
$javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$javaFiles = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& $javaCommand -jar $formatterPath --replace $javaFiles
exit $LASTEXITCODE
