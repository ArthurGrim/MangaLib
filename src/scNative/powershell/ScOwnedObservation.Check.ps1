$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ScOwnedObservation.ps1')
function Assert-Check([bool] $Condition) { if (-not $Condition) { throw 'Owner fake assertion failed' } }
function New-FakeOwner([bool] $Ended, [int] $Code, [bool] $FailExit = $false) {
    $p = [pscustomobject]@{
        SafeHandle = [pscustomobject]@{ IsInvalid = $false; IsClosed = $false }
        HasExited = $Ended
        Code = $Code
        FailExit = $FailExit
        Waits = 0
    }
    $p | Add-Member ScriptProperty ExitCode { if ($this.FailExit) { throw 'SECRET' }; $this.Code }
    $p | Add-Member ScriptMethod WaitForExit { param($Milliseconds); $this.Waits++; $this.HasExited = $true; $true }
    $p
}
$clock = [pscustomobject]@{ ElapsedMilliseconds = 10 }
$p = New-FakeOwner $true 78
$r = Complete-ScOwnedObservation $p $clock 100 $true
Assert-Check ($r.HeldEndObserved -and $r.ExitCodeObserved -and $r.ExitCode -eq 78 -and $p.Waits -eq 0)
$p = New-FakeOwner $false 0
$r = Complete-ScOwnedObservation $p $clock 100 $true
Assert-Check ($r.HeldEndObserved -and $r.ExitCode -eq 0 -and $p.Waits -eq 1 -and $r.Outcome -eq 'STOP')
$p = New-FakeOwner $false 78
$r = Complete-ScOwnedObservation $p $clock 10 $true
Assert-Check (-not $r.HeldEndObserved -and -not $r.ExitCodeObserved -and $null -eq $r.ExitCode -and $p.Waits -eq 0)
$p = New-FakeOwner $true 78 $true
$r = Complete-ScOwnedObservation $p $clock 100 $true
Assert-Check ($r.HeldEndObserved -and -not $r.ExitCodeObserved -and $r.ObservationFailed)
$p = New-FakeOwner $false 0
$p.SafeHandle.IsClosed = $true
$r = Complete-ScOwnedObservation $p $clock 100 $true
Assert-Check ($r.ObservationFailed -and -not $r.HeldEndObserved -and $p.Waits -eq 0)
$p = New-FakeOwner $false 0
$p | Add-Member ScriptMethod WaitForExit { param($Milliseconds); throw 'SECRET' } -Force
$r = Complete-ScOwnedObservation $p $clock 100 $true
Assert-Check ($r.ObservationFailed -and -not $r.HeldEndObserved)
Assert-Check (-not $r.ResourceEndProved -and -not $r.RetryAllowed -and -not $r.OwnerHandleDisposed)
Assert-Check (-not (($r | ConvertTo-Json -Compress).Contains('SECRET')))
'Owner observation: 6 fake cases passed; no process started or controlled.'
