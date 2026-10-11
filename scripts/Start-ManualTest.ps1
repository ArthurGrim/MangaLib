[CmdletBinding()]
param(
    [switch]$ValidateOnly,
    [string]$JavaPath = '',
    [string]$ArtifactPath = (Join-Path $PSScriptRoot '../build/libs/mangalib-manual-test.jar')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Kein transparenter Override und keine JVM-Optionen, die schon vor dem Launcher wirken.
$unsafeNames = @(Get-ChildItem Env: | Where-Object {
    $_.Name -match '^(SPRING|SERVER|MANAGEMENT|HIBERNATE|JAKARTA|LOGGING|TOMCAT|JETTY|UNDERTOW)_' -or
    $_.Name -in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')
})
if ($unsafeNames.Count -gt 0) { throw 'Externe Framework-/JVM-Konfiguration vorhanden. Separaten sauberen Prozess verwenden; keine Werte ausgeben.' }
if (-not $JavaPath) {
    if ($env:JAVA_HOME) { $JavaPath = Join-Path $env:JAVA_HOME 'bin/java.exe' }
    else { $JavaPath = (Get-Command java.exe -ErrorAction Stop).Source }
}
$JavaPath = (Resolve-Path -LiteralPath $JavaPath).Path
$versionInfo = [System.Diagnostics.ProcessStartInfo]::new()
$versionInfo.FileName = $JavaPath
$versionInfo.Arguments = '-version'
$versionInfo.UseShellExecute = $false
$versionInfo.RedirectStandardError = $true
$versionInfo.RedirectStandardOutput = $true
$versionProcess = [System.Diagnostics.Process]::Start($versionInfo)
try {
    if (-not $versionProcess.WaitForExit(10000)) { $versionProcess.Kill(); throw 'Java-Versionsprüfung hat das Zeitlimit überschritten; expliziten JDK-21-Pfad verwenden.' }
    $javaVersion = $versionProcess.StandardError.ReadToEnd() + $versionProcess.StandardOutput.ReadToEnd()
    if ($versionProcess.ExitCode -ne 0 -or $javaVersion -notmatch 'version "21(?:[.]|\")') { throw 'Java 21 erforderlich.' }
} finally { $versionProcess.Dispose() }
$ArtifactPath = (Resolve-Path -LiteralPath $ArtifactPath).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($ArtifactPath)
try {
    $manifest = $archive.GetEntry('META-INF/MANIFEST.MF')
    if (-not $manifest) { throw 'Artefakt ohne Manifest.' }
    $reader = [System.IO.StreamReader]::new($manifest.Open())
    try { $manifestText = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($manifestText -notmatch '(?m)^Start-Class: de[.]mangalibmanual[.]ManualTestLauncher\r?$') { throw 'Falsches Artefakt.' }
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/classes/(application[.]properties|de/mangalib/MangalibApplication[.]class|de/mangalib/service/(MangaScraper|BandScraper|LiveScraperGateway).*\.class)$' -or
            $entry.FullName -match '^BOOT-INF/lib/(selenium-|webdrivermanager-|spring-boot-devtools-)') { throw 'Live-/Standardbestandteil im manuellen Artefakt.' }
    }
    foreach ($required in @('BOOT-INF/classes/application-manual-test.properties',
        'BOOT-INF/classes/de/mangalibmanual/SyntheticScraperGateway.class')) {
        if (-not $archive.GetEntry($required)) { throw 'Manueller Schutz oder Double fehlt.' }
    }
} finally { $archive.Dispose() }
if (-not $ValidateOnly) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 18080 -ErrorAction SilentlyContinue)
    if ($listeners.Count -ne 0) { throw 'Port 18080 bereits belegt; keine Prozesse automatisch beenden.' }
}

$manualCredential = $null
$process = $null
$startInfo = $null
try {
    $manualPassword = [Environment]::GetEnvironmentVariable('MANGALIB_MANUAL_DB_PASSWORD', 'Process')
    if ([string]::IsNullOrWhiteSpace($manualPassword)) {
        $manualCredential = Get-Credential -UserName 'mangalib_manual_test_user' -Message 'Passwort nur für mangalib_manual_test'
        if (-not $manualCredential -or $manualCredential.UserName -ne 'mangalib_manual_test_user') { throw 'Manueller Benutzer erforderlich.' }
        $manualPassword = $manualCredential.GetNetworkCredential().Password
    }
    if ([string]::IsNullOrWhiteSpace($manualPassword)) { throw 'Manuelles Passwort fehlt.' }
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $JavaPath
    $startInfo.UseShellExecute = $false
    $startInfo.WorkingDirectory = Split-Path -Parent $ArtifactPath
    # Pfad aus Resolve-Path; Windows-Dateinamen können keine Anführungszeichen enthalten.
    $startInfo.Arguments = '-jar "' + $ArtifactPath + '"' + $(if ($ValidateOnly) { ' --validate-only' } else { '' })
    foreach ($name in @($startInfo.EnvironmentVariables.Keys)) {
        if ($name -like 'MANGALIB_*') { $startInfo.EnvironmentVariables.Remove($name) }
    }
    $startInfo.EnvironmentVariables['MANGALIB_MANUAL_DB_PASSWORD'] = $manualPassword
    Write-Host 'MANUELLE TESTUMGEBUNG — 127.0.0.1:18080 — ausschließlich synthetische Scraper'
    $process = [System.Diagnostics.Process]::Start($startInfo)
    Write-Host ('Java-PID: ' + $process.Id)
    if ($ValidateOnly) {
        if (-not $process.WaitForExit(30000)) { $process.Kill(); throw 'ValidateOnly-Zeitlimit überschritten.' }
    } else { $process.WaitForExit() }
    if ($process.ExitCode -ne 0) { throw 'Manuelle Prüfung/Start abgebrochen. Keine Konfigurationswerte protokollieren.' }
} finally {
    # Keine dauerhafte Environment-Änderung im aufrufenden Prozess.
    $manualPassword = $null
    $manualCredential = $null
    if ($startInfo) { $startInfo.EnvironmentVariables.Clear() }
    if ($process) { $process.Dispose() }
}
