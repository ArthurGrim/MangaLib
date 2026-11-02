param([string]$AttemptId,[string]$ApprovalHash,[switch]$ValidateOnly,[switch]$ExecuteOnce)
$ErrorActionPreference='Stop'
if(($ValidateOnly -and $ExecuteOnce) -or (-not $ValidateOnly -and -not $ExecuteOnce)){throw 'OWNER_SELECT_EXPLICIT_MODE'}
. (Join-Path $PSScriptRoot 'OwnerProbe.ps1')
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
try{$context=Get-OwnerContext $root $AttemptId}
catch{
    [pscustomobject]@{Outcome='BLOCKED';StartRequested=$false;Failure=(Get-OwnerFailure $_ 'PREFLIGHT' 'PREFLIGHT')}
    throw 'OWNER_PREFLIGHT_STOP'
}
if($ValidateOnly){
    [pscustomobject]@{AttemptId=$context.AttemptId;ArtifactHash=$context.Artifact;ManifestHash=$context.ManifestHash;
        Approved=$false;Consumed=$false;NativeCalls=$false;OwnerStarts=0;WorkerStarts=0;JobCreates=0;HistoricalBoundary=$context.HistoricalBoundary}
    return
}
if($null -ne $script:OwnerProbeRetainedProcess -or $null -ne $script:OwnerProbeRetainedSubscription -or
        $null -ne ('MangaLib.OwnerProbe.InspectionV1' -as [type])){throw 'OWNER_FRESH_SESSION_REQUIRED'}
$ports=@{
    Fingerprint={param($c);Get-OwnerFingerprint $c}
    Clock={[Diagnostics.Stopwatch]::StartNew()}
    NativeInit={Initialize-OwnerNativeInspection}
    Subscribe={
        param($c,$receipt)
        $source=$receipt.Source;$receipt.Step='CHECK_EXISTING'
        # Leere lokale Bestandsliste ist regulär; unerwartete Abfragefehler nicht verschlucken.
        if(@(Get-EventSubscriber -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $source).Count -ne 0){throw 'OWNER_SUBSCRIBER_EXISTS'}
        $script:OwnerProbeRetainedSubscription=$receipt
        $receipt.Step='REGISTER_CIM';$receipt.Acquisition='UNCONFIRMED'
        Register-CimIndicationEvent -Query 'SELECT * FROM Win32_ProcessStartTrace' -SourceIdentifier $source -ErrorAction Stop | Out-Null
        $receipt.Step='VERIFY_SUBSCRIBER'
        $sub=@(Get-EventSubscriber -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $source)
        if($sub.Count -ne 1){throw 'OWNER_SUBSCRIPTION'}
        $receipt.Subscriber=$sub[0];$receipt.Acquisition='CONFIRMED';$receipt.Step='BOUND'
    }
    Start={
        param($c)
        $psi=[Diagnostics.ProcessStartInfo]::new();$psi.FileName=$c.Java;$psi.WorkingDirectory=$c.Root
        $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
        $psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
        $psi.StandardOutputEncoding=[Text.UTF8Encoding]::new($false,$true);$psi.StandardErrorEncoding=[Text.UTF8Encoding]::new($false,$true)
        $psi.Environment.Clear();$psi.Environment['SystemRoot']='C:\Windows';$psi.Environment['TEMP']=$c.Directory;$psi.Environment['TMP']=$c.Directory
        foreach($arg in @('-Xmx256m','-cp',$c.Classes,'de.mangalib.ownerprobe.OwnerProvenanceMain','--owner-provenance-v1',$c.AttemptId,$c.Artifact)){$psi.ArgumentList.Add($arg)}
        $p=[Diagnostics.Process]::new();$p.StartInfo=$psi;$script:OwnerProbeRetainedProcess=$p
        $confirmed=$false;$failure=$null;try{$confirmed=$p.Start()}catch{$failure=Get-OwnerFailure $_ 'START' 'START'}
        [pscustomobject]@{Owner=$p;Confirmed=$confirmed;Failure=$failure}
    }
    Identity={
        param($p,$safe)
        if(-not [object]::ReferenceEquals($p.SafeHandle,$safe)){throw 'OWNER_HANDLE_CHANGED'}
        $id=[MangaLib.OwnerProbe.InspectionV1]::Identity($safe)
        if($id.Pid -ne $p.Id){throw 'OWNER_PID_BINDING'}
        [pscustomobject]@{Pid=$id.Pid;Birth=$id.Birth.ToString([Globalization.CultureInfo]::InvariantCulture);Image=$id.Image;
            Held=$true;Source='OWN_PROCESS_START_HANDLE_NATIVE'}
    }
    End={
        param($p,$safe,$id)
        if(-not [object]::ReferenceEquals($p.SafeHandle,$safe)){throw 'OWNER_HANDLE_CHANGED'}
        $e=[MangaLib.OwnerProbe.InspectionV1]::End($safe,[uint32]$id.Pid,[long]$id.Birth)
        [pscustomobject]@{Ended=$e.Ended;ExitObserved=$e.ExitObserved;ExitCode=$(if($e.ExitObserved){$e.ExitCode}else{$null})}
    }
    BeginReads={
        param($p)
        $p.StandardInput.Close()
        $out=[char[]]::new(1);$err=[char[]]::new(129)
        [pscustomobject]@{Out=$out;Err=$err;ErrorReader=$p.StandardError;
            OutTask=$p.StandardOutput.ReadAsync($out,0,1);ErrTask=$p.StandardError.ReadAsync($err,0,129)}
    }
    BadOutput={
        param($r)
        $r.OutTask.IsFaulted -or $r.ErrTask.IsFaulted -or $r.OutTask.IsCanceled -or $r.ErrTask.IsCanceled -or
            ($r.OutTask.IsCompletedSuccessfully -and $r.OutTask.Result -ne 0) -or
            ($r.ErrTask.IsCompletedSuccessfully -and $r.ErrTask.Result -gt 128)
    }
    Poll={
        param($s,$id,$c)
        $events=@(Get-Event -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $s.Source)
        if($events.Count+$s.Trace.Count -gt 256){return [pscustomobject]@{Healthy=$false;RootSeen=$s.RootSeen;Findings=@()}}
        foreach($event in $events){
            $e=$event.SourceEventArgs.NewEvent
            $s.Trace.Add([pscustomobject]@{Pid=[uint32]$e.ProcessID;Parent=[uint32]$e.ParentProcessID;Name=[string]$e.ProcessName;Time=[string]$e.TIME_CREATED})
            Remove-Event -EventIdentifier $event.EventIdentifier -ErrorAction Stop
        }
        $related=[Collections.Generic.HashSet[uint32]]::new();[void]$related.Add([uint32]$id.Pid)
        $changed=$true
        while($changed){$changed=$false;foreach($e in $s.Trace){
            if($e.Pid -eq $id.Pid){
                if($e.Parent -ne $c.ObserverPid -or $e.Name -ine 'java.exe'){return [pscustomobject]@{Healthy=$false;RootSeen=$false;Findings=@()}}
                $s.RootSeen=$true
            }elseif($related.Contains($e.Parent) -and $related.Add($e.Pid)){$changed=$true}
        }}
        $findings=[Collections.Generic.List[object]]::new()
        foreach($e in $s.Trace){
            # Ein neuer Konsolenhost kann auch außerhalb des erwarteten Parentbaums erscheinen.
            # Zeitliche Nähe beweist keinen Besitz: vorsichtshalber STOP, niemals fremd beenden.
            $consoleCandidate=$e.Name -iin @('conhost.exe','OpenConsole.exe')
            if($e.Pid -ne $id.Pid -and ($related.Contains($e.Parent) -or $consoleCandidate -or $e.Parent -eq $c.ObserverPid) -and $s.Seen.Add(('trace|'+$e.Pid+'|'+$e.Time))){
                $findings.Add([pscustomobject]@{Kind='UNPROVEN_RELATED_PROCESS';Pid=$e.Pid;Parent=$e.Parent;Name=$e.Name;
                    EventTime=$e.Time;Source='CIM_START_TRACE';Owned=$false;RequiresStop=$true})
            }
        }
        # Ergänzende Momentaufnahme; keine native Identität ersetzen oder aus Name/Parent Besitz ableiten.
        Get-CimInstance Win32_Process -Filter ('ParentProcessId = '+$id.Pid) -Property ProcessId,ParentProcessId,ExecutablePath,CreationDate -OperationTimeoutSec 1 -ErrorAction Stop |
            ForEach-Object {
                $birth=$(if($null -ne $_.CreationDate){$_.CreationDate.ToUniversalTime().ToFileTimeUtc().ToString()}else{'unknown'})
                if($s.Seen.Add(('snapshot|'+$_.ProcessId+'|'+$birth))){
                    $findings.Add([pscustomobject]@{Kind='UNPROVEN_CHILD_SNAPSHOT';Pid=$_.ProcessId;Parent=$_.ParentProcessId;
                        Image=$_.ExecutablePath;CimBirth=$birth;Source='CIM_SNAPSHOT';Owned=$false;RequiresStop=$true})
                }
            }
        $subscribers=@(Get-EventSubscriber -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $s.Source)
        [pscustomobject]@{Healthy=($subscribers.Count -eq 1 -and [object]::ReferenceEquals($subscribers[0],$s.Subscriber));
            RootSeen=$s.RootSeen;Findings=$findings.ToArray();ObservedTrace=$s.Trace.ToArray()}
    }
    Reads={
        param($r,$clock,$deadline)
        $text='';$eof=$false
        while($clock.ElapsedMilliseconds -lt $deadline){
            if($r.ErrTask.IsFaulted -or $r.ErrTask.IsCanceled){throw 'OWNER_READ'}
            if($r.ErrTask.IsCompletedSuccessfully){
                $n=[int]$r.ErrTask.Result;if($n -eq 0){$eof=$true;break}
                if($n -lt 0 -or $n -gt 129 -or $text.Length+$n -gt 128){throw 'OWNER_DIAGNOSTIC_LIMIT'}
                $text+=[string]::new($r.Err,0,$n);$r.ErrTask=$r.ErrorReader.ReadAsync($r.Err,0,129)
            }else{[Threading.Thread]::Sleep(1)}
        }
        while(-not $r.OutTask.IsCompleted -and $clock.ElapsedMilliseconds -lt $deadline){[Threading.Thread]::Sleep(1)}
        $valid=$text -ceq '' -or $text -ceq "OWNER_STOP_V1`n" -or $text -ceq "OWNER_STOP_V1`r`n"
        [pscustomobject]@{StdoutEof=($r.OutTask.IsCompletedSuccessfully -and $r.OutTask.Result -eq 0);StdoutEmpty=($r.OutTask.IsCompletedSuccessfully -and $r.OutTask.Result -eq 0);
            StderrEof=$eof;DiagnosticsValid=$valid;Diagnostics=$(if($valid -and $text.Length -gt 0){@('OWNER_STOP_V1')}else{@()});RawTextDiscarded=$true}
    }
    CloseOwner={
        param($p,$safe,$id)
        if(-not [object]::ReferenceEquals($p.SafeHandle,$safe)){throw 'OWNER_HANDLE_CHANGED'}
        if(-not [MangaLib.OwnerProbe.InspectionV1]::CloseEndedOwner($safe,[uint32]$id.Pid,[long]$id.Birth)){return $false}
        $p.Dispose();$safe.IsClosed
    }
    Unsubscribe={
        param($s)
        $s.Step='CHECK_ORIGINAL'
        $subs=@(Get-EventSubscriber -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $s.Source)
        if($subs.Count -ne 1 -or -not [object]::ReferenceEquals($subs[0],$s.Subscriber)){throw 'OWNER_SUBSCRIBER_CHANGED'}
        $s.Step='UNREGISTER'
        Unregister-Event -SubscriptionId $s.Subscriber.SubscriptionId -ErrorAction Stop
        $s.Step='REMOVE_EVENTS'
        Get-Event -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $s.Source|Remove-Event -ErrorAction Stop
        $s.Step='VERIFY_RELEASE'
        $noSubscriber=@(Get-EventSubscriber -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $s.Source).Count -eq 0
        $noQueuedEvents=@(Get-Event -ErrorAction Stop|Where-Object SourceIdentifier -CEQ $s.Source).Count -eq 0
        $noSubscriber -and $noQueuedEvents
    }
    Pause={[Threading.Thread]::Sleep(1)}
}
try{
    $answer=Invoke-OwnerProbe $context $ports -ApprovalHash $ApprovalHash -ExecuteOnce:$ExecuteOnce
    if($answer.Report.OwnerHandleClosed){$script:OwnerProbeRetainedProcess=$null}
    elseif($null -ne $answer.RetainedOwner){$script:OwnerProbeRetainedProcess=$answer.RetainedOwner}
    if($answer.Report.SubscriptionClosed){$script:OwnerProbeRetainedSubscription=$null}
    elseif($answer.Report.ObservationAcquisition -ceq 'NOT_ATTEMPTED'){$script:OwnerProbeRetainedSubscription=$null}
    elseif($null -ne $answer.RetainedSubscription){$script:OwnerProbeRetainedSubscription=$answer.RetainedSubscription}
    try{Write-OwnerNew (Join-Path $context.Directory 'result.json') ($answer.Report|ConvertTo-Json -Depth 8)}
    catch{$answer.Report.Outcome='PARTIAL/STOP';$answer.Report.FailurePhase='REPORT_WRITE';$answer.Report.ObserverFailed=$true;
        if(@($answer.Report.Failures).Count -lt 8){$answer.Report.Failures=@($answer.Report.Failures)+@((Get-OwnerFailure $_ 'REPORT_WRITE' 'REPORT_WRITE'))}
        $answer.Report.FailureLimitReached=(@($answer.Report.Failures).Count -eq 8)}
    $answer.Report
}catch{
    [pscustomobject]@{Outcome='PARTIAL/STOP';StartState='UNCONFIRMED';Failure=(Get-OwnerFailure $_ 'HARNESS' 'HARNESS');RetryAllowed=$false}
}finally{
    # Auch bei Report-/unerwartetem Harnessfehler die originale Besitzsitzung behalten.
    # Das Beobachtungsbudget wird nicht erneuert; hier erfolgen KEINE OS-Nachversuche.
    if($null -ne $script:OwnerProbeRetainedProcess -or $null -ne $script:OwnerProbeRetainedSubscription){
        Write-Output 'OWNER_RETAINED_SESSION_OPEN'
        while($true){[Threading.Thread]::Sleep(1000)}
    }
}
