$ErrorActionPreference='Stop'
# Vorhandene 48 Fälle unverändert mitprüfen und deren Fake-Fixtures wiederverwenden.
. (Join-Path $PSScriptRoot 'ScAttempt.Check.ps1')
$script:cases=0
function New-ScControlFake {
    $f=New-ScFake
    $key='SCSTOP1|' + $f.Context.AttemptId + '|' + [guid]::NewGuid() + '|' + [guid]::NewGuid() + '|1|' + ('a' * 64) + '|51|100'
    $identity=[pscustomobject]@{ProcessId=51;Birth='100';Image=$f.Context.Java;Held=$true}
    [IO.File]::WriteAllText((Join-Path $f.Context.Directory 'outer-binding'),("BIND|$key`n"))
    $f | Add-Member NoteProperty Key $key
    $f | Add-Member NoteProperty Identity $identity
    $f
}
function New-ScAdvancingClock {
    $clock=[pscustomobject]@{Value=0}
    $clock | Add-Member ScriptProperty ElapsedMilliseconds {$this.Value+=200;$this.Value}
    $clock
}
Test-ScCase 'binding-and-admission-of-held-own-S' {
    $f=New-ScControlFake
    [IO.File]::WriteAllText((Join-Path $f.Context.Directory 'outer-bound'),("BOUND|"+$f.Key+"`n"))
    $cap=Open-ScOuterControl $f.Context $f.Identity $f.State.Clock 25000
    Assert-ScCheck ($cap.Bound -and $cap.Revision -eq 1)
    Assert-ScCheck ((Read-ScControlFile (Join-Path $cap.Directory 'outer-admit')) -ceq ("ADMIT|"+$f.Key+"`n"))
}
foreach($fault in @('session','revision','pid','birth','image','unheld','oversize','command','partial')) {
    Test-ScCase ('control-' + $fault) {
        $f=New-ScControlFake;$text="BIND|"+$f.Key+"`n"
        switch($fault){
            'session' {$text=$text.Replace($f.Context.AttemptId,[guid]::NewGuid().ToString())}
            'revision' {$text=$text.Replace('|1|','|2|')}
            'pid' {$text=$text.Replace('|51|','|52|')}
            'birth' {$text=$text.Replace("|100`n","|101`n")}
            'image' {$f.Identity.Image='C:\foreign\java.exe'}
            'unheld' {$f.Identity.Held=$false}
            'oversize' {$text='x' * 513 + "`n"}
            'command' {$text=$text.Replace('BIND|','EXEC|')}
            'partial' {$text=$text.TrimEnd("`n")}
        }
        [IO.File]::WriteAllText((Join-Path $f.Context.Directory 'outer-binding'),$text)
        if($fault -eq 'partial'){Assert-ScCheck ($null -eq (Get-ScControlCapability $f.Context $f.Identity))}
        else {Assert-ScThrows {Get-ScControlCapability $f.Context $f.Identity}}
        Assert-ScCheck (-not (Test-Path (Join-Path $f.Context.Directory 'outer-admit')))
    }
}
Test-ScCase 'monotone-stop-and-ack-are-not-native-or-resource-end' {
    $f=New-ScControlFake;$cap=Get-ScControlCapability $f.Context $f.Identity
    [IO.File]::WriteAllText((Join-Path $f.Context.Directory 'outer-ack'),("REVOKED|"+$f.Key+"|3`n"))
    $r=Request-ScOuterStop $f.Context $f.Identity $cap $f.State.Clock 25000
    Assert-ScCheck ($r.Effective -and $r.Acknowledged -and $r.Outstanding -eq 3)
    Assert-ScCheck (-not $f.State.Process.HasExited -and -not (Test-ScOuterControlEnd $cap))
    $before=(Get-FileHash (Join-Path $cap.Directory 'outer-stop')).Hash
    $again=Request-ScOuterStop $f.Context $f.Identity $cap $f.State.Clock 25000
    Assert-ScCheck ($again.Effective -and $before -eq (Get-FileHash (Join-Path $cap.Directory 'outer-stop')).Hash)
    Assert-ScCheck (-not ($r | ConvertTo-Json -Compress).Contains($f.Key))
}
foreach($fault in @('no-ack','foreign-ack','partial-ack','missing-binding','wrong-capability','failed-S')) {
    Test-ScCase $fault {
        $f=New-ScControlFake;$cap=Get-ScControlCapability $f.Context $f.Identity
        switch($fault){
            'foreign-ack' {[IO.File]::WriteAllText((Join-Path $cap.Directory 'outer-ack'),("REVOKED|"+$f.Key.Replace('|51|','|52|')+"|0`n"))}
            'partial-ack' {[IO.File]::WriteAllText((Join-Path $cap.Directory 'outer-ack'),("REVOKED|"+$f.Key+'|0'))}
            'missing-binding' {$f.Identity=$null}
            'wrong-capability' {$cap.Key=$cap.Key.Replace('|51|','|52|')}
            'failed-S' {$cap=$null;$f.Identity.Held=$false}
        }
        $r=Request-ScOuterStop $f.Context $f.Identity $cap (New-ScAdvancingClock) 25000
        Assert-ScCheck (-not $r.Effective -and -not $r.Acknowledged -and $r.State -ceq 'UNKNOWN/STOP')
    }
}
foreach($fault in @('normal','observer','no-ack','open-failed','stop-send-failed','control-end-missing','null-stop')) {
    Test-ScCase ('integrated-' + $fault) {
        $f=New-ScFake
        $state=[pscustomobject]@{Stops=0;Mode=$fault}
        $f.Context | Add-Member NoteProperty ControlState $state
        $f.Ports.OpenControl={param($c,$s,$clock,$deadline);if($c.ControlState.Mode -eq 'open-failed'){throw 'SECRET'};[pscustomobject]@{Bound=$true}}
        $f.Ports.StopControl={param($c,$s,$cap,$clock,$deadline);$c.ControlState.Stops++;if($c.ControlState.Mode -eq 'stop-send-failed'){throw 'SECRET'};if($c.ControlState.Mode -eq 'null-stop'){return $null};
            [pscustomobject]@{Requested=$true;Sent=$true;Acknowledged=($c.ControlState.Mode -ne 'no-ack');Effective=($c.ControlState.Mode -ne 'no-ack');Outstanding=1;State='FAKE'}}
        $f.Ports.ControlEnd={param($cap);$fault -ne 'control-end-missing'}
        if($fault -in @('observer','no-ack','stop-send-failed','null-stop')){$f.State.ChildFailure=$true}
        $r=Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce -RequireOuterControl
        Assert-ScCheck ($f.State.Starts -eq 1 -and $r.Report.OuterControlRequired)
        if($fault -eq 'normal'){Assert-ScCheck ($r.Report.Outcome -ceq 'PASS' -and $state.Stops -eq 0)}
        else {
            Assert-ScCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP')
            if($fault -ne 'control-end-missing'){Assert-ScCheck ($state.Stops -eq 1 -and $r.Report.Revocation.Requested)}
            Assert-ScThrows {Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce -RequireOuterControl}
            Assert-ScCheck ($f.State.Starts -eq 1)
        }
        Assert-ScCheck (-not ($r.Report | ConvertTo-Json -Depth 8 -Compress).Contains('SECRET'))
    }
}
"Outer STOP: $script:cases new fake cases passed; no native/process operations."
