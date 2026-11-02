param([string]$AttemptId,[string]$ApprovalHash,[switch]$ValidateOnly,[switch]$ExecuteOnce)
$ErrorActionPreference='Stop'
if(($ValidateOnly -and $ExecuteOnce) -or (-not $ValidateOnly -and -not $ExecuteOnce)){throw 'OWNER_SELECT_EXPLICIT_MODE'}
. (Join-Path $PSScriptRoot 'OwnerCompletion.ps1')
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
try{$context=Get-OwnerCompletionContext $root $AttemptId}
catch{[pscustomobject]@{Outcome='BLOCKED';StartRequested=$false;Failure=(Get-OwnerFailure $_ 'PREFLIGHT' 'PREFLIGHT')};throw 'OWNER_PREFLIGHT_STOP'}
if($ValidateOnly){
    [pscustomobject]@{Profile=$context.Profile;AttemptId=$context.AttemptId;ArtifactHash=$context.Artifact;ManifestHash=$context.ManifestHash;
        Approved=$false;Consumed=$false;NativeCalls=$false;OwnerStarts=0;WorkerStarts=0;JobCreates=0;GlobalProvenance='NOT_MEASURED'};return
}
if($null -ne $script:OwnerCompletionRetained -or $null -ne ('MangaLib.OwnerProbe.InspectionV1' -as [type])){throw 'OWNER_FRESH_SESSION_REQUIRED'}
$ports=@{
    Fingerprint={param($c);Get-OwnerFingerprint $c}
    Clock={[Diagnostics.Stopwatch]::StartNew()}
    NativeInit={Initialize-OwnerNativeInspection}
    Start={
        param($c,$r)
        $p=[Diagnostics.Process]::new();$r.Owner=$p;$script:OwnerCompletionRetained=$r
        $s=[Diagnostics.ProcessStartInfo]::new();$s.FileName=$c.Java;$s.WorkingDirectory=$c.Root
        $s.UseShellExecute=$false;$s.CreateNoWindow=$true;$s.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
        $s.RedirectStandardInput=$true;$s.RedirectStandardOutput=$true;$s.RedirectStandardError=$true
        $s.StandardOutputEncoding=[Text.UTF8Encoding]::new($false,$true);$s.StandardErrorEncoding=[Text.UTF8Encoding]::new($false,$true)
        $s.Environment.Clear();$s.Environment['SystemRoot']='C:\Windows';$s.Environment['TEMP']=$c.Directory;$s.Environment['TMP']=$c.Directory
        foreach($a in @('-Xmx256m','-cp',$c.Classes,'de.mangalib.ownerprobe.OwnerCompletionMain','--owner-completion-v1',$c.AttemptId,$c.Artifact)){$s.ArgumentList.Add($a)}
        $p.StartInfo=$s;$r.StartMayHaveOccurred=$true;$r.StartConfirmed=$p.Start()
    }
    Identity={
        param($r)
        if(-not [object]::ReferenceEquals($r.Owner.SafeHandle,$r.SafeHandle)){throw 'OWNER_HANDLE_CHANGED'}
        $i=[MangaLib.OwnerProbe.InspectionV1]::Identity($r.SafeHandle)
        if($i.Pid -ne $r.Owner.Id){throw 'OWNER_PID_BINDING'}
        [pscustomobject]@{Pid=$i.Pid;Birth=$i.Birth.ToString([Globalization.CultureInfo]::InvariantCulture);Image=$i.Image;Held=$true;Source='OWN_PROCESS_START_HANDLE_NATIVE'}
    }
    End={
        param($r)
        if(-not [object]::ReferenceEquals($r.Owner.SafeHandle,$r.SafeHandle)){throw 'OWNER_HANDLE_CHANGED'}
        $e=[MangaLib.OwnerProbe.InspectionV1]::End($r.SafeHandle,[uint32]$r.Identity.Pid,[long]$r.Identity.Birth)
        [pscustomobject]@{Ended=$e.Ended;ExitObserved=$e.ExitObserved;ExitCode=$(if($e.ExitObserved){$e.ExitCode}else{$null})}
    }
    BeginReads={
        param($r)
        $r.Reads=[pscustomobject]@{Out=[char[]]::new(1);Err=[char[]]::new(129);OutTask=$null;ErrTask=$null;InputClosed=$false}
        $r.Owner.StandardInput.Dispose();$r.Reads.InputClosed=$true
        $r.Reads.OutTask=$r.Owner.StandardOutput.ReadAsync($r.Reads.Out,0,1)
        $r.Reads.ErrTask=$r.Owner.StandardError.ReadAsync($r.Reads.Err,0,129)
    }
    BadOutput={
        param($r);$s=$r.Reads
        $s.OutTask.IsFaulted -or $s.ErrTask.IsFaulted -or $s.OutTask.IsCanceled -or $s.ErrTask.IsCanceled -or
            ($s.OutTask.IsCompletedSuccessfully -and $s.OutTask.Result -ne 0) -or ($s.ErrTask.IsCompletedSuccessfully -and $s.ErrTask.Result -gt 128)
    }
    Reads={
        param($r,$clock,$deadline);$s=$r.Reads;$text='';$eof=$false
        if($null -eq $s.OutTask -or $null -eq $s.ErrTask){throw 'OWNER_READ'}
        while($clock.ElapsedMilliseconds -lt $deadline){
            if($s.ErrTask.IsFaulted -or $s.ErrTask.IsCanceled){throw 'OWNER_READ'}
            if($s.ErrTask.IsCompletedSuccessfully){
                $n=[int]$s.ErrTask.Result;if($n -eq 0){$eof=$true;break}
                if($n -lt 0 -or $n -gt 129 -or $text.Length+$n -gt 128){throw 'OWNER_DIAGNOSTIC_LIMIT'}
                $text+=[string]::new($s.Err,0,$n);$s.ErrTask=$r.Owner.StandardError.ReadAsync($s.Err,0,129)
            }else{[Threading.Thread]::Sleep(1)}
        }
        while(-not $s.OutTask.IsCompleted -and $clock.ElapsedMilliseconds -lt $deadline){[Threading.Thread]::Sleep(1)}
        $outEof=$s.OutTask.IsCompletedSuccessfully -and $s.OutTask.Result -eq 0
        $valid=$text -ceq '' -or $text -ceq "OWNER_STOP_V1`n" -or $text -ceq "OWNER_STOP_V1`r`n"
        [pscustomobject]@{StdoutEof=$outEof;StdoutEmpty=$outEof;StderrEof=$eof;DiagnosticsValid=$valid;
            Diagnostics=$(if($valid -and $text.Length -gt 0){@('OWNER_STOP_V1')}else{@()});RawTextDiscarded=$true}
    }
    CloseStreams={
        param($r)
        if(-not $r.Reads.InputClosed -or -not $r.Reads.OutTask.IsCompletedSuccessfully -or -not $r.Reads.ErrTask.IsCompletedSuccessfully){return $false}
        $r.Owner.StandardOutput.Dispose();$r.Owner.StandardError.Dispose();$true
    }
    CloseUnstarted={
        param($r)
        if($r.StartMayHaveOccurred){throw 'OWNER_START_UNKNOWN'}
        if($null -ne $r.Owner){$r.Owner.Dispose()};$true
    }
    CloseOwner={
        param($r)
        if(-not [object]::ReferenceEquals($r.Owner.SafeHandle,$r.SafeHandle)){throw 'OWNER_HANDLE_CHANGED'}
        if(-not [MangaLib.OwnerProbe.InspectionV1]::CloseEndedOwner($r.SafeHandle,[uint32]$r.Identity.Pid,[long]$r.Identity.Birth)){return $false}
        $r.Owner.Dispose();$r.SafeHandle.IsClosed
    }
    Pause={[Threading.Thread]::Sleep(1)}
}
try{
    $answer=Invoke-OwnerCompletion $context $ports $ApprovalHash -ExecuteOnce
    if(-not $answer.Report.ResourcesUnconfirmed){$script:OwnerCompletionRetained=$null}
    elseif($null -ne $answer.Retained){$script:OwnerCompletionRetained=$answer.Retained}
    try{Write-OwnerNew (Join-Path $context.Directory 'result.json') ($answer.Report|ConvertTo-Json -Depth 8)}
    catch{$answer.Report.Outcome='PARTIAL/STOP';$answer.Report.Failures=@($answer.Report.Failures)+@((Get-OwnerFailure $_ 'REPORT_WRITE' 'REPORT_WRITE'))}
    $answer.Report
}catch{[pscustomobject]@{Outcome='PARTIAL/STOP';StartState='UNCONFIRMED';Failure=(Get-OwnerFailure $_ 'HARNESS' 'HARNESS');RetryAllowed=$false}}
finally{
    # Nur die eigene ursprüngliche Besitzsitzung behalten; kein Query/Reopen/Kill und kein neues Budget.
    if($null -ne $script:OwnerCompletionRetained){Write-Output 'OWNER_COMPLETION_RETAINED_SESSION_OPEN';while($true){[Threading.Thread]::Sleep(1000)}}
}
