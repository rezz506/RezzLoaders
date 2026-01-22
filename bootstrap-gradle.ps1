$ErrorActionPreference = 'Stop'

$gradleVersion = '8.11.1'
$distUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$distDir = Join-Path $PSScriptRoot '.gradle-dist'
$zipPath = Join-Path $distDir "gradle-$gradleVersion-bin.zip"
$unzipDir = Join-Path $distDir "gradle-$gradleVersion"

if (!(Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir | Out-Null }

if (!(Test-Path $zipPath)) {
  Write-Host "Downloading Gradle $gradleVersion..."
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  Invoke-WebRequest -Uri $distUrl -OutFile $zipPath
}

if (!(Test-Path $unzipDir)) {
  Write-Host "Extracting Gradle..."
  Expand-Archive -Path $zipPath -DestinationPath $distDir -Force
}

$gradleBat = Join-Path $unzipDir 'bin\gradle.bat'
if (!(Test-Path $gradleBat)) {
  throw "Gradle executable not found at $gradleBat"
}

Write-Host 'Generating Gradle wrapper...'
& $gradleBat wrapper --gradle-version $gradleVersion

Write-Host 'Wrapper generated.'
