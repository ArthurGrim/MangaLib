[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SourceFile,
    [Parameter(Mandatory)][string]$OutputFile
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Rein lokale Prüfung/Umformung eines separat exportierten Schemas. Kein mysql-Aufruf.
$source = (Resolve-Path -LiteralPath $SourceFile).Path
$target = [IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $target) { throw 'Ausgabedatei existiert; kein Überschreiben.' }
$sql = [IO.File]::ReadAllText($source)
$tables = @('verlage', 'status', 'typen', 'formate', 'sammelbaende', 'mangareihe', 'mangadetails', 'baende', 'einkaufsliste', 'einkaufslistedetails')
$statements = [regex]::Matches($sql, '(?ms)^CREATE TABLE `(?<table>[a-z]+)` \(\r?\n.*?^\) ENGINE=InnoDB[^;]*;')
if ($statements.Count -ne 10) { throw 'Exakt zehn erwartete InnoDB-CREATE-TABLE-Statements erforderlich.' }
$byName = @{}
foreach ($statement in $statements) {
    $table = $statement.Groups['table'].Value
    if ($table -notin $tables -or $byName.ContainsKey($table)) { throw 'Unbekannte/doppelte Tabelle.' }
    $ddl = $statement.Value
    $inspection = $ddl -replace '(?i)ON UPDATE CURRENT_TIMESTAMP(?:\(\))?', ''
    if ($inspection.TrimEnd(';') -match ';' -or $inspection -match '(?i)\b(DROP|ALTER|TRIGGER|PROCEDURE|FUNCTION|EVENT|DEFINER|INSERT|UPDATE|DELETE|REPLACE|LOAD|SOURCE|GRANT|REVOKE|USE)\b' -or
        $inspection -match '(?i)\b(CREATE|SELECT)\b.*\b(CREATE|SELECT)\b' -or $ddl -match '`\s*\.' -or $ddl -match '/\*|--|\\x00') {
        throw 'Nicht unterstützte oder gefährliche Schemaanweisung; manuell untersuchen.'
    }
    foreach ($reference in [regex]::Matches($ddl, '(?i)REFERENCES `([^`]+)`')) {
        if ($reference.Groups[1].Value -notin $tables) { throw 'Unbekannter Fremdschlüssel.' }
    }
    $ddl = $ddl -replace '(?i) AUTO_INCREMENT=\d+', ''
    $ddl = $ddl.Replace('CREATE TABLE `' + $table + '`', 'CREATE TABLE `mangalib_manual_test`.`' + $table + '`')
    $ddl = [regex]::Replace($ddl, '(?i)REFERENCES `([^`]+)`', 'REFERENCES `mangalib_manual_test`.`$1`')
    $byName[$table] = $ddl
}
$residue = [regex]::Replace($sql, '(?ms)^CREATE TABLE `[a-z]+` \(\r?\n.*?^\) ENGINE=InnoDB[^;]*;', '')
$residue = [regex]::Replace($residue, '(?m)^--[^\r\n]*', '')
# mysqldump-Sitzungseinstellungen werden nicht übernommen. Unbekannter Rest führt zum Abbruch.
$residue = [regex]::Replace($residue, '(?m)^/\*!\d+ SET [^;\r\n]+ \*/;\s*', '')
if (-not [string]::IsNullOrWhiteSpace($residue)) { throw 'Unbekannte Zusatzanweisungen im Export.' }
$output = "-- Ausschließlich neue leere mangalib_manual_test. Kein Datenimport, kein Reset.`n" + (($tables | ForEach-Object { $byName[$_] }) -join "`n`n") + "`n"
[IO.File]::WriteAllText($target, $output, [Text.UTF8Encoding]::new($false))
Write-Host 'Zehn InnoDB-Tabellen geprüft; vollständig auf mangalib_manual_test qualifiziert. Nicht ausgeführt.'
