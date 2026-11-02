# Enger lokaler ADMIT/STOP-Briefkasten; keine PID-Steuerung und kein Netzwerk.
function Read-ScControlFile([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    Assert-ScCanonical $Path
    $f=[IO.FileStream]::new($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
    try {
        $buffer=[byte[]]::new(513);$count=0
        while($count -lt 513){$n=$f.Read($buffer,$count,513-$count);if($n -eq 0){break};$count+=$n}
        if($count -gt 512){throw 'SC_CONTROL_SIZE'}
        if($count -eq 0 -or $buffer[$count-1] -ne 10){return $null}
        [Text.Encoding]::ASCII.GetString($buffer,0,$count)
    } finally {$f.Dispose()}
}
function Get-ScControlCapability($Context,$Identity) {
    if($null -eq $Identity -or -not $Identity.Held -or $Identity.Image -ine $Context.Java){throw 'SC_CONTROL_OWNER'}
    $text=Read-ScControlFile (Join-Path $Context.Directory 'outer-binding')
    if($null -eq $text){return $null}
    $parts=$text.TrimEnd("`n").Split('|')
    if($parts.Count -ne 9 -or $parts[0] -cne 'BIND' -or $parts[1] -cne 'SCSTOP1' -or
        $parts[2] -cne $Context.AttemptId -or $parts[5] -cne '1' -or $parts[6] -cnotmatch '^[a-f0-9]{64}$' -or
        $parts[7] -cne [string]$Identity.ProcessId -or $parts[8] -cne [string]$Identity.Birth){throw 'SC_CONTROL_BINDING'}
    foreach($index in 2..4){$id=[guid]::Empty;if(-not [guid]::TryParseExact($parts[$index],'D',[ref]$id) -or
        $id -eq [guid]::Empty -or $id.ToString() -cne $parts[$index]){throw 'SC_CONTROL_BINDING'}}
    $key=$parts[1..8] -join '|'
    if($text -cne ("BIND|"+$key+"`n")){throw 'SC_CONTROL_BINDING'}
    [pscustomobject]@{Directory=$Context.Directory;Key=$key;Revision=1;Bound=$false;StopSent=$false}
}
function Open-ScOuterControl($Context,$Identity,$Clock,[long]$Deadline) {
    $cap=$null
    $until=[Math]::Min($Deadline,$Clock.ElapsedMilliseconds+5000)
    while($Clock.ElapsedMilliseconds -lt $until){
        $cap=Get-ScControlCapability $Context $Identity
        if($null -ne $cap){break};[Threading.Thread]::Sleep(1)
    }
    if($null -eq $cap){throw 'SC_CONTROL_UNAVAILABLE'}
    if(Test-Path -LiteralPath (Join-Path $cap.Directory 'outer-stop')){throw 'SC_CONTROL_REVOKED'}
    Write-ScNewDurable (Join-Path $cap.Directory 'outer-admit') ("ADMIT|"+$cap.Key+"`n")
    while($Clock.ElapsedMilliseconds -lt $until){
        $text=Read-ScControlFile (Join-Path $cap.Directory 'outer-bound')
        if($null -ne $text){if($text -cne ("BOUND|"+$cap.Key+"`n")){throw 'SC_CONTROL_BINDING'};$cap.Bound=$true;return $cap}
        [Threading.Thread]::Sleep(1)
    }
    throw 'SC_CONTROL_BIND_UNCONFIRMED'
}
function Request-ScOuterStop($Context,$Identity,$Capability,$Clock,[long]$Deadline) {
    $result=[pscustomobject]@{Requested=$true;Sent=$false;Acknowledged=$false;Effective=$false;Outstanding=$null;State='UNKNOWN/STOP'}
    try {
        if($Clock.ElapsedMilliseconds -ge $Deadline){return $result}
        $cap=$Capability
        if($null -eq $cap){$cap=Get-ScControlCapability $Context $Identity}
        if($null -eq $cap){return $result} # Keine ADMIT-Berechtigung erfinden.
        # Herkunft erneut prüfen; nie einen fremden/alten Capabilitywert übernehmen.
        $actual=Get-ScControlCapability $Context $Identity
        if($null -eq $actual -or $cap.Directory -cne $actual.Directory -or $cap.Key -cne $actual.Key){throw 'SC_CONTROL_OWNER'}
        $path=Join-Path $cap.Directory 'outer-stop';$message="STOP|"+$cap.Key+"`n"
        if(Test-Path -LiteralPath $path){if((Read-ScControlFile $path) -cne $message){throw 'SC_CONTROL_REJECTED'}}
        else {Write-ScNewDurable $path $message}
        $cap.StopSent=$true;$result.Sent=$true
        $until=[Math]::Min($Deadline,$Clock.ElapsedMilliseconds+1000)
        while($Clock.ElapsedMilliseconds -lt $until){
            $text=Read-ScControlFile (Join-Path $cap.Directory 'outer-ack')
            if($null -ne $text){
                $prefix="REVOKED|"+$cap.Key+'|'
                if(-not $text.StartsWith($prefix,[StringComparison]::Ordinal) -or $text.Substring($prefix.Length) -cnotmatch '^[0-9]{1,5}\n$'){throw 'SC_CONTROL_ACK'}
                $result.Acknowledged=$true;$result.Effective=$true;$result.Outstanding=[int]$text.Substring($prefix.Length).Trim()
                $result.State='REVOKED';return $result
            }
            [Threading.Thread]::Sleep(1)
        }
    } catch { } # Keine Rohinhalte/Exceptiontexte; fehlendes ACK bleibt UNKNOWN, kein Retry.
    $result
}
function Test-ScOuterControlEnd($Capability) {
    if($null -eq $Capability -or -not $Capability.Bound){return $false}
    (Read-ScControlFile (Join-Path $Capability.Directory 'outer-end')) -ceq ("CLOSED|"+$Capability.Key+"`n")
}
