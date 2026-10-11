[CmdletBinding()]
param([string]$JavaPath = '')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checks = 0
function Assert-Manual([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
foreach ($scriptFile in @('Start-ManualTest.ps1', 'Prepare-ManualSchema.ps1', 'Test-ManualScripts.ps1')) {
    $tokens = $null; $parseErrors = $null
    [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $scriptFile), [ref]$tokens, [ref]$parseErrors)
    Assert-Manual ($parseErrors.Count -eq 0) ('Syntaxfehler: ' + $scriptFile)
}
$directory = Join-Path ([IO.Path]::GetTempPath()) ('mangalib-schema-unit-' + [guid]::NewGuid())
[void][IO.Directory]::CreateDirectory($directory)
try {
    $names = @('verlage', 'status', 'typen', 'formate', 'sammelbaende', 'mangareihe', 'mangadetails', 'baende', 'einkaufsliste', 'einkaufslistedetails')
    $synthetic = ($names | ForEach-Object {
        'CREATE TABLE `' + $_ + "`` (`n  ``id`` bigint NOT NULL,`n  PRIMARY KEY (``id``)`n) ENGINE=InnoDB AUTO_INCREMENT=99 DEFAULT CHARSET=utf8mb4;"
    }) -join "`n"
    $source = Join-Path $directory 'synthetic.sql'
    $output = Join-Path $directory 'prepared.sql'
    [IO.File]::WriteAllText($source, $synthetic)
    & (Join-Path $PSScriptRoot 'Prepare-ManualSchema.ps1') -SourceFile $source -OutputFile $output
    $prepared = [IO.File]::ReadAllText($output)
    Assert-Manual (([regex]::Matches($prepared, 'CREATE TABLE `mangalib_manual_test`[.]')).Count -eq 10) 'Qualifizierung fehlt.'
    Assert-Manual ($prepared -notmatch 'AUTO_INCREMENT=99') 'Zähler aus Quelle übernommen.'
    $refSource = $synthetic.Replace('PRIMARY KEY (`id`)', 'PRIMARY KEY (`id`), FOREIGN KEY (`id`) REFERENCES `status` (`id`)')
    [IO.File]::WriteAllText($source, $refSource)
    $refOutput = Join-Path $directory 'references.sql'
    & (Join-Path $PSScriptRoot 'Prepare-ManualSchema.ps1') -SourceFile $source -OutputFile $refOutput
    Assert-Manual ([IO.File]::ReadAllText($refOutput).Contains('REFERENCES `mangalib_manual_test`.`status`')) 'Fremdschlüssel nicht qualifiziert.'
    foreach ($bad in @(
        $synthetic.Replace('ENGINE=InnoDB', 'ENGINE=MyISAM'),
        $synthetic.Replace('`baende`', '`unknown`'),
        $synthetic.Replace('CREATE TABLE `baende`', 'CREATE TABLE `mangalib`.`baende`'),
        ($synthetic + "`nDROP DATABASE mangalib;"),
        ($synthetic + "`nINSERT INTO mangalib.baende VALUES (1);"),
        ($synthetic + "`nUSE mangalib;"),
        ($synthetic + "`nCREATE TRIGGER unexpected;"),
        $synthetic.Replace('PRIMARY KEY (`id`)', 'PRIMARY KEY (`id`), FOREIGN KEY (`id`) REFERENCES `unknown` (`id`)')
    )) {
        [IO.File]::WriteAllText($source, $bad)
        $failed = $false
        try { & (Join-Path $PSScriptRoot 'Prepare-ManualSchema.ps1') -SourceFile $source -OutputFile (Join-Path $directory 'rejected.sql') }
        catch { $failed = $true }
        Assert-Manual $failed 'Unsicherer Export akzeptiert.'
        Assert-Manual (-not (Test-Path -LiteralPath (Join-Path $directory 'rejected.sql'))) 'Datei trotz Ablehnung geschrieben.'
    }
    $failed = $false
    try { & (Join-Path $PSScriptRoot 'Prepare-ManualSchema.ps1') -SourceFile $source -OutputFile $output } catch { $failed = $true }
    Assert-Manual $failed 'Vorhandene Datei überschrieben.'
} finally {
    # Nur das hier erzeugte, aufgelöste Testverzeichnis löschen.
    $resolved = [IO.Path]::GetFullPath($directory)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolved) -notlike 'mangalib-schema-unit-*') { throw 'Ungültiger temporärer Löschpfad.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
$previousPassword = [Environment]::GetEnvironmentVariable('MANGALIB_MANUAL_DB_PASSWORD', 'Process')
$previousAddress = [Environment]::GetEnvironmentVariable('SERVER_ADDRESS', 'Process')
try {
    $env:MANGALIB_MANUAL_DB_PASSWORD = 'synthetic-wrapper-validation-only'
    & (Join-Path $PSScriptRoot 'Start-ManualTest.ps1') -ValidateOnly -JavaPath $JavaPath
    Assert-Manual $true 'ValidateOnly funktioniert.'
    $env:SERVER_ADDRESS = 'synthetic-unsafe'
    $failed = $false
    try { & (Join-Path $PSScriptRoot 'Start-ManualTest.ps1') -ValidateOnly -JavaPath $JavaPath } catch { $failed = $true }
    Assert-Manual $failed 'Startskript akzeptiert externe Konfiguration.'
} finally {
    [Environment]::SetEnvironmentVariable('MANGALIB_MANUAL_DB_PASSWORD', $previousPassword, 'Process')
    [Environment]::SetEnvironmentVariable('SERVER_ADDRESS', $previousAddress, 'Process')
    $previousPassword = $null
}
Write-Host ("$checks datenbankfreie PowerShell-Prüfungen bestanden; keine SQL-Ausführung.")
