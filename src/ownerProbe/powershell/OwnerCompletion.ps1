# Enger Messumfang. Dot-sourcing definiert nur Funktionen, keine Prozesse oder nativen Aufrufe.
. (Join-Path $PSScriptRoot 'OwnerProbe.ps1')

function Get-OwnerCompletionDirectory([string]$Root,[string]$Id){
    [void](Get-OwnerDirectory $Root $Id) # Bestehende kanonische UUID-Prüfung.
    Join-Path ([IO.Path]::GetFullPath($Root)) ('.gradle/t02-owner-completion/'+$Id)
}
function Get-OwnerCompletionContext([string]$Root,[string]$AttemptId){
    $root=[IO.Path]::GetFullPath($Root)
    if($PSVersionTable.PSVersion.Major -lt 7 -or [IntPtr]::Size -ne 8 -or -not [OperatingSystem]::IsWindows() -or
        [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() -cne 'X64' -or
        $root -match '[^\x20-\x7e]' -or $root -notmatch '^[A-Za-z]:\\'){throw 'OWNER_PLATFORM'}
    foreach($key in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){
        if(Test-Path ('Env:'+$key)){throw 'OWNER_JVM_OVERRIDE'}
    }
    if(Test-Path -LiteralPath (Get-OwnerDirectory $root $AttemptId)){throw 'OWNER_PROFILE_REUSE'}
    $c=[pscustomobject]@{Profile='OWNER_COMPLETION_V1';Root=$root;AttemptId=$AttemptId;
        Directory=(Get-OwnerCompletionDirectory $root $AttemptId);Java='C:\Program Files\Java\jdk-21\bin\java.exe';
        Dll='C:\Windows\System32\kernel32.dll';Classes=(Join-Path $root 'build/classes/java/ownerProbe');
        Scripts=(Join-Path $root 'src/ownerProbe/powershell');Artifact=$null;ManifestHash=$null}
    if((Get-FileHash $c.Java).Hash -cne '7272CC277ABDAFC7FA9FDFAD2CA04639824A2D31338CD7010CBCB4006EE7FBA8'){throw 'OWNER_JAVA_PIN'}
    $c.Artifact=Get-OwnerFingerprint $c;Assert-OwnerPath $c.Directory
    $manifest=Join-Path $c.Directory 'manifest.txt';Assert-OwnerPath $manifest
    $expected="OWNER_COMPLETION_V1`n"+$AttemptId+"`n"+$c.Artifact+"`n7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8`n"+
        (Get-FileHash $c.Dll).Hash.ToLowerInvariant()+"`n"+$root+"`n"
    if((Get-Item $manifest).Length -gt 1024 -or [IO.File]::ReadAllText($manifest) -cne $expected){throw 'OWNER_MANIFEST'}
    $c.ManifestHash=(Get-FileHash $manifest).Hash.ToLowerInvariant()
    foreach($name in @('consumed','binding','ready','finish','stop','closed','result.json')){
        if(Test-Path -LiteralPath (Join-Path $c.Directory $name)){throw 'OWNER_ALREADY_USED'}
    };$c
}
function Use-OwnerCompletionApproval($c,[string]$ApprovalHash){
    if($c.Profile -cne 'OWNER_COMPLETION_V1' -or $c.Directory -cne (Get-OwnerCompletionDirectory $c.Root $c.AttemptId) -or
        $c.Artifact -cnotmatch '^[a-f0-9]{64}$' -or $c.ManifestHash -cnotmatch '^[a-f0-9]{64}$' -or $ApprovalHash -cnotmatch '^[a-f0-9]{64}$'){throw 'OWNER_APPROVAL_SCOPE'}
    Assert-OwnerPath $c.Directory
    $m=Join-Path $c.Directory 'manifest.txt';$p=Join-Path $c.Directory 'approval.txt';Assert-OwnerPath $m;Assert-OwnerPath $p
    if((Get-Item $m).Length -gt 1024 -or (Get-FileHash $m).Hash.ToLowerInvariant() -cne $c.ManifestHash -or
        (Get-Item $p).Length -gt 256 -or (Get-FileHash $p).Hash.ToLowerInvariant() -cne $ApprovalHash -or
        [IO.File]::ReadAllText($p) -cne ("OWNER_COMPLETION_APPROVED_V1`n"+$c.AttemptId+"`n"+$c.Artifact+"`n"+$c.ManifestHash+"`n")){throw 'OWNER_APPROVAL'}
    $parts=[IO.File]::ReadAllText($m).Split([char]10)
    if($parts.Count -ne 7 -or $parts[0] -cne 'OWNER_COMPLETION_V1' -or $parts[1] -cne $c.AttemptId -or $parts[2] -cne $c.Artifact -or
        $parts[3] -cne '7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8' -or $parts[4] -cnotmatch '^[a-f0-9]{64}$' -or
        $parts[5] -cne $c.Root -or $parts[6] -cne ''){throw 'OWNER_MANIFEST'}
    Write-OwnerNew (Join-Path $c.Directory 'consumed') ("OWNER_COMPLETION_CONSUMED_V1`n"+$c.AttemptId+"`n"+$c.Artifact+"`n"+$ApprovalHash+"`n")
    (Get-FileHash (Join-Path $c.Directory 'consumed')).Hash.ToLowerInvariant()
}
function Assert-OwnerCompletionEnd($e){
    if($null -eq $e -or $e.Ended -isnot [bool] -or $e.ExitObserved -isnot [bool] -or $e.Ended -ne $e.ExitObserved -or
        ($e.ExitObserved -and $e.ExitCode -isnot [int]) -or (-not $e.ExitObserved -and $null -ne $e.ExitCode)){throw 'OWNER_END_UNKNOWN'}
}
function Invoke-OwnerCompletion($Context,$Ports,[string]$ApprovalHash,[switch]$ExecuteOnce){
    if(-not $ExecuteOnce){throw 'OWNER_EXPLICIT_APPROVAL_REQUIRED'}
    if((& $Ports.Fingerprint $Context) -cne $Context.Artifact){throw 'OWNER_ARTIFACT'}
    $clock=& $Ports.Clock;$deadline=25000;$consumed=Use-OwnerCompletionApproval $Context $ApprovalHash
    $receipt=[pscustomobject]@{Owner=$null;SafeHandle=$null;Identity=$null;IdentityValidated=$false;Reads=$null;
        StartMayHaveOccurred=$false;StartConfirmed=$false}
    $preparationClosed=$false
    $requested=$false;$streamsClosed=$false;$ownerClosed=$false;$ready=$false;$finish=$false;$stop=$false;$stopSent=$false;$key=$null
    $end=[pscustomobject]@{Ended=$false;ExitObserved=$false;ExitCode=$null}
    $io=[pscustomobject]@{StdoutEof=$false;StderrEof=$false;StdoutEmpty=$false;DiagnosticsValid=$false;Diagnostics=@()}
    $failures=[Collections.Generic.List[object]]::new();$phase='PREFLIGHT'
    try{
        if((& $Ports.Fingerprint $Context) -cne $Context.Artifact -or $clock.ElapsedMilliseconds -ge $deadline){throw 'OWNER_ARTIFACT_CHANGED'}
        $phase='NATIVE_DECLARATION';& $Ports.NativeInit
        if((& $Ports.Fingerprint $Context) -cne $Context.Artifact -or $clock.ElapsedMilliseconds -ge $deadline -or
            (Get-FileHash (Join-Path $Context.Directory 'manifest.txt')).Hash.ToLowerInvariant() -cne $Context.ManifestHash -or
            (Get-FileHash (Join-Path $Context.Directory 'approval.txt')).Hash.ToLowerInvariant() -cne $ApprovalHash){throw 'OWNER_PRESTART_CHANGED'}
        $phase='START';$requested=$true;& $Ports.Start $Context $receipt
        if($receipt.StartConfirmed -isnot [bool] -or -not $receipt.StartConfirmed -or $null -eq $receipt.Owner){throw 'OWNER_START_UNKNOWN'}
        $phase='IDENTITY';$receipt.SafeHandle=$receipt.Owner.SafeHandle;$receipt.Identity=& $Ports.Identity $receipt
        $id=$receipt.Identity
        if($null -eq $id -or -not $id.Held -or $id.Source -cne 'OWN_PROCESS_START_HANDLE_NATIVE' -or $id.Pid -le 0 -or
            $id.Image -ine $Context.Java -or $id.Birth -cnotmatch '^[1-9][0-9]{0,18}$' -or [long]$id.Birth -le 0){throw 'OWNER_IDENTITY'}
        $receipt.IdentityValidated=$true
        $phase='READ_SETUP';& $Ports.BeginReads $receipt
        $key='OP1|'+$Context.AttemptId+'|'+$Context.Artifact+'|'+$id.Pid+'|'+$id.Birth
        Write-OwnerNew (Join-Path $Context.Directory 'binding') ($key+"`n")
        while($clock.ElapsedMilliseconds -lt $deadline){
            $phase='READ_SETUP'
            if(& $Ports.BadOutput $receipt){throw 'OWNER_MESSAGE'}
            $message=Read-OwnerMessage (Join-Path $Context.Directory 'ready')
            if($null -ne $message){if($message -cne ("READY|"+$key+"`n")){throw 'OWNER_READY'};$ready=$true}
            if($ready -and -not $finish){Write-OwnerNew (Join-Path $Context.Directory 'finish') ("FINISH|"+$key+"`n");$finish=$true}
            $phase='NATIVE_END';$end=& $Ports.End $receipt
            Assert-OwnerCompletionEnd $end
            if($end.Ended){break};& $Ports.Pause
        }
        if($clock.ElapsedMilliseconds -ge $deadline -or -not $finish){$stop=$true}
    }catch{Add-OwnerFailure $failures $_ $phase $phase;$stop=$true}
    if(-not $receipt.StartMayHaveOccurred){
        if($null -eq $receipt.Owner){$preparationClosed=$true}
        elseif($clock.ElapsedMilliseconds -lt $deadline){
            try{$preparationClosed=& $Ports.CloseUnstarted $receipt}catch{Add-OwnerFailure $failures $_ 'OWNER_CLOSE' 'OWNER_CLOSE'}
        }
    }
    if($stop -and $null -ne $key -and $clock.ElapsedMilliseconds -lt $deadline){
        try{Write-OwnerNew (Join-Path $Context.Directory 'stop') ("STOP|"+$key+"`n");$stopSent=$true}
        catch{Add-OwnerFailure $failures $_ 'STOP_WRITE' 'STOP_WRITE'}
    }
    try{
        $phase='NATIVE_END'
        while($receipt.IdentityValidated -and -not $end.Ended -and $clock.ElapsedMilliseconds -lt $deadline){
            $end=& $Ports.End $receipt
            Assert-OwnerCompletionEnd $end
            if(-not $end.Ended){& $Ports.Pause}
        }
        $phase='READ_SETUP'
        if($null -ne $receipt.Reads){$io=& $Ports.Reads $receipt $clock $deadline}
        Assert-OwnerCompletionEnd $end
        if($receipt.IdentityValidated -and $end.Ended -and $end.ExitObserved -and $io.StdoutEof -and $io.StderrEof -and $clock.ElapsedMilliseconds -lt $deadline){
            $phase='OWNER_CLOSE';$streamsClosed=& $Ports.CloseStreams $receipt
            if($streamsClosed -and $clock.ElapsedMilliseconds -lt $deadline){$ownerClosed=& $Ports.CloseOwner $receipt}
        }
    }catch{Add-OwnerFailure $failures $_ $phase $phase;$stop=$true}
    $closed=$null
    try{$closed=Read-OwnerMessage (Join-Path $Context.Directory 'closed')}catch{Add-OwnerFailure $failures $_ 'CLOSED_READ' 'CLOSED_READ';$stop=$true}
    $normal=$null -ne $key -and $closed -ceq ("CLOSED|"+$key+"|NORMAL`n")
    $complete=$end.Ended -and $end.ExitObserved -and $io.StdoutEof -and $io.StderrEof -and $streamsClosed -and $ownerClosed
    $pass=$complete -and -not $stop -and $failures.Count -eq 0 -and $ready -and $finish -and $normal -and $end.ExitCode -eq 0
    $pass=$pass -and $io.StdoutEmpty -and $io.DiagnosticsValid -and @($io.Diagnostics).Count -eq 0 -and $clock.ElapsedMilliseconds -lt $deadline
    [pscustomobject]@{Report=[pscustomobject]@{Profile='OWNER_COMPLETION_V1';AttemptId=$Context.AttemptId;ArtifactHash=$Context.Artifact;
        ManifestHash=$Context.ManifestHash;ApprovalHash=$ApprovalHash;ConsumedHash=$consumed;
        Outcome=$(if($pass){'OWNER_COMPLETION_PASS'}else{'PARTIAL/STOP'});StartRequested=$requested;StartMayHaveOccurred=$receipt.StartMayHaveOccurred;StartConfirmed=$receipt.StartConfirmed;
        Identity=$receipt.Identity;HeldEnd=$end;Streams=$io;StreamsClosed=$streamsClosed;OwnerHandleClosed=$ownerClosed;
        OwnerCompletionConfirmed=$complete;ReadyObserved=$ready;FinishSent=$finish;NormalClosure=$normal;StopSent=$stopSent;
        Failures=$failures.ToArray();DeadlineExpired=($clock.ElapsedMilliseconds -ge $deadline);ElapsedMs=$clock.ElapsedMilliseconds;
        PreparationClosed=$preparationClosed;
        ResourcesUnconfirmed=($requested -and -not (($streamsClosed -and $ownerClosed) -or (-not $receipt.StartMayHaveOccurred -and $preparationClosed)));RetryAllowed=$false;
        GlobalProcessProvenance='NOT_MEASURED';ConsoleProvenance='NOT_MEASURED';ProvenanceAccepted=$false;
        HistoricalResources='UNKNOWN_NOT_QUERIED';WorkerStarts=0;JobCreates=0;GlobalEnumerationProved=$false};
        Retained=$(if($requested -and -not (($streamsClosed -and $ownerClosed) -or (-not $receipt.StartMayHaveOccurred -and $preparationClosed))){$receipt}else{$null})}
}
