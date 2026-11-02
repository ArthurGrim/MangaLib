# Dot-sourcing definiert ausschließlich Dateipreflight und injizierbaren Harness.
. (Join-Path $PSScriptRoot 'OwnerNativeInspection.ps1')

# Erwerbsbeleg existiert VOR dem Portaufruf; eine Ausnahme darf Teilressourcen nicht verbergen.
function New-OwnerObservationReceipt([string]$AttemptId) {
    [pscustomobject]@{Source=('MangaLib.OwnerProvenance.'+$AttemptId);Subscriber=$null;
        Acquisition='NOT_ATTEMPTED';Step='CHECK_EXISTING';ReleaseAttempted=$false;ReleaseConfirmed=$false;
        RootSeen=$false;Trace=[Collections.Generic.List[object]]::new();Seen=[Collections.Generic.HashSet[string]]::new()}
}
function Get-OwnerFailure($Record,[string]$Phase,[string]$Step) {
    # Keine Message/Stack/Commandline/ErrorDetails: numerische Ursachen und begrenzte Typkette behalten.
    $allowed=@('PREFLIGHT','HARNESS','NATIVE_DECLARATION','SUBSCRIBE','START','IDENTITY','READ_SETUP','OBSERVATION','NATIVE_END',
        'END_READ_OBSERVATION','STOP_WRITE','OWNER_CLOSE','SUBSCRIPTION_CLOSE','CLOSED_READ','REPORT_WRITE',
        'CHECK_EXISTING','REGISTER_CIM','VERIFY_SUBSCRIBER','BOUND','CHECK_ORIGINAL','UNREGISTER','REMOVE_EVENTS','VERIFY_RELEASE')
    if($Phase -cnotin $allowed){$Phase='UNCLASSIFIED'};if($Step -cnotin $allowed){$Step='UNCLASSIFIED'}
    $codes=@('OWNER_PLATFORM','OWNER_JVM_OVERRIDE','OWNER_ID','OWNER_JAVA_PIN','OWNER_REPARSE','OWNER_CLASSES',
        'OWNER_CLASS_SIZE','OWNER_CLASS_SCOPE','OWNER_CLASS_VERSION','OWNER_SCRIPT_SIZE','OWNER_MANIFEST_SIZE','OWNER_MANIFEST',
        'OWNER_ALREADY_USED','OWNER_APPROVAL_HASH','OWNER_APPROVAL_SCOPE','OWNER_MANIFEST_CHANGED','OWNER_APPROVAL',
        'OWNER_WRITE','OWNER_ARTIFACT','OWNER_ARTIFACT_CHANGED','OWNER_PREFLIGHT_DEADLINE','OWNER_SUBSCRIPTION',
        'OWNER_SUBSCRIBER_EXISTS','OWNER_PRESTART_CHANGED','OWNER_START_UNKNOWN','OWNER_IDENTITY','OWNER_END_UNKNOWN',
        'OWNER_OBSERVER','OWNER_READY','OWNER_FINDINGS_LIMIT','OWNER_SUBSCRIBER_CHANGED','OWNER_MESSAGE',
        'OWNER_HISTORY_SCOPE','OWNER_HISTORY_CHANGED','OWNER_HISTORY_NOT_PRESTART',
        'OWNER_PROFILE_REUSE','OWNER_HANDLE_CHANGED','OWNER_PID_BINDING','OWNER_INSTANCE','OWNER_WAIT','OWNER_EXIT',
        'OWNER_READ','OWNER_DIAGNOSTIC_LIMIT','OWNER_HANDLE','OWNER_NATIVE_ALREADY_LOADED','OWNER_FRESH_SESSION_REQUIRED')
    $exception=$Record.Exception;$chain=[Collections.Generic.List[object]]::new()
    while($null -ne $exception -and $chain.Count -lt 4){
        $type=$exception.GetType().FullName
        if($type.Length -gt 160 -or $type -cnotmatch '^(System\.|Microsoft\.Management\.Infrastructure\.)[A-Za-z0-9_.+]+$'){$type='OTHER_EXCEPTION'}
        $native=$null;$cim=$null
        if($exception -is [ComponentModel.Win32Exception]){$native=$exception.NativeErrorCode}
        if($exception.GetType().FullName -ceq 'Microsoft.Management.Infrastructure.CimException'){$cim=[int]$exception.NativeErrorCode}
        $chain.Add([pscustomobject]@{Type=$type;HResult=('0x'+[Convert]::ToHexString([BitConverter]::GetBytes([int]$exception.HResult)[3..0]));NativeErrorCode=$native;CimStatus=$cim;
            Code=$(if($exception.Message.Length -le 96 -and $exception.Message -cin $codes){$exception.Message}else{$null})})
        $exception=$exception.InnerException
    }
    $id=[string]$Record.FullyQualifiedErrorId;$truncated=$id.Length -gt 4096
    if($truncated){$id=$id.Substring(0,4096)}
    [pscustomobject]@{Phase=$Phase;Step=$Step;Category=[string]$Record.CategoryInfo.Category;
        ErrorIdHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($id))).ToLowerInvariant();
        ErrorIdTruncated=$truncated;Chain=$chain.ToArray();ChainTruncated=($null -ne $exception);RawDetailsOmitted=$true}
}
function Add-OwnerFailure($Failures,$Record,[string]$Phase,[string]$Step) {
    # Mindestens die erste Ursache bleibt erhalten, auch wenn weitere Cleanupfehler auftreten.
    if($Failures.Count -lt 8){$Failures.Add((Get-OwnerFailure $Record $Phase $Step))}
}

function Assert-OwnerPath([string]$Path) {
    $f=Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    while($null -ne $f){
        if(($f.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'OWNER_REPARSE'}
        $f=$(if($f -is [IO.FileInfo]){$f.Directory}else{$f.Parent})
    }
}
function Get-OwnerDirectory([string]$Root,[string]$Id) {
    $guid=[guid]::Empty
    if(-not [guid]::TryParseExact($Id,'D',[ref]$guid) -or $guid -eq [guid]::Empty -or $guid.ToString() -cne $Id){throw 'OWNER_ID'}
    Join-Path ([IO.Path]::GetFullPath($Root)) ('.gradle/t02-owner-provenance/'+$Id)
}
# Reine Fallklassifikation, selbst KEIN Evidenz- oder Freigabenachweis.
function Get-OwnerHistoryClassification($e,[string]$AttemptId) {
    $old='07574fb1-9fc5-41de-8a9b-d77c8841edf2'
    $id=[guid]::Empty
    if(-not [guid]::TryParseExact($AttemptId,'D',[ref]$id) -or $id -eq [guid]::Empty -or
        $id.ToString() -cne $AttemptId -or $AttemptId -ceq $old){throw 'OWNER_HISTORY_SCOPE'}
    if($e.AttemptId -cne $old -or $e.Profile -cne 'OWNER_PROVENANCE_V1' -or $e.FailurePhase -cne 'SUBSCRIBE' -or
        $e.Outcome -cne 'PARTIAL/STOP' -or $e.StartRequested -isnot [bool] -or $e.StartRequested -or
        $e.StartConfirmed -isnot [bool] -or $e.StartConfirmed -or $null -ne $e.Identity -or
        ($e.WorkerStarts -isnot [int] -and $e.WorkerStarts -isnot [long]) -or $e.WorkerStarts -ne 0 -or
        ($e.JobCreates -isnot [int] -and $e.JobCreates -isnot [long]) -or $e.JobCreates -ne 0){throw 'OWNER_HISTORY_NOT_PRESTART'}
    [pscustomobject]@{HistoricalAttempt=$old;HistoricalResultHash=$null;EvidenceValidated=$false;
        HistoricalSessionState='UNKNOWN';HistoricalCleanupConfirmed=$false;HistoricalImportStarted=$false;
        NewAttempt=$AttemptId;IndependentProbeNamespace=$true;RequiresHistoricalCleanup=$false;
        GrantsExecution=$false;MayClearImportQuarantine=$false;MayTerminateHistoricalProcess=$false}
}
# Ausschließlich dieses belegte Vorstart-Ereignis abgrenzen, keine allgemeine Recoveryregel.
# Die alte Beobachtersitzung bleibt unbekannt; ein tatsächlich gestarteter Importjob wäre NICHT ausgenommen.
function Get-OwnerHistoricalBoundary([string]$Root,[string]$AttemptId) {
    $old='07574fb1-9fc5-41de-8a9b-d77c8841edf2'
    $newDirectory=Get-OwnerDirectory $Root $AttemptId
    $oldDirectory=Get-OwnerDirectory $Root $old
    if($newDirectory -ieq $oldDirectory){throw 'OWNER_HISTORY_SCOPE'}
    $file=Join-Path $oldDirectory 'result.json';Assert-OwnerPath $file
    $stream=[IO.FileStream]::new($file,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    try{
        $buffer=[byte[]]::new(8193);$count=0
        while($count -lt $buffer.Length){$n=$stream.Read($buffer,$count,$buffer.Length-$count);if($n -eq 0){break};$count+=$n}
        if($count -eq 0 -or $count -gt 8192){throw 'OWNER_HISTORY_CHANGED'}
        $bytes=[byte[]]::new($count);[Array]::Copy($buffer,$bytes,$count)
    }finally{$stream.Dispose()}
    # Geprüfte und ausgewertete Bytes identisch halten; keine zweite, ungebundene Dateilesung.
    if([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) -cne
        '4CCDCE6CC71CBD165EF11052D19F7FAD44EAC3859DA96F22726B5F4A3A6764E8'){throw 'OWNER_HISTORY_CHANGED'}
    $e=[Text.UTF8Encoding]::new($false,$true).GetString($bytes)|ConvertFrom-Json -ErrorAction Stop
    $boundary=Get-OwnerHistoryClassification $e $AttemptId
    $boundary.HistoricalResultHash='4ccdce6cc71cbd165ef11052d19f7fad44eac3859da96f22726b5f4a3a6764e8'
    $boundary.EvidenceValidated=$true;$boundary
}
function Write-OwnerNew([string]$Path,[string]$Text) {
    $bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text)
    $f=[IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{$f.Write($bytes,0,$bytes.Length);$f.Flush($true)}finally{$f.Dispose()}
    if((Get-FileHash -LiteralPath $Path).Hash -cne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))){throw 'OWNER_WRITE'}
}
function Read-OwnerMessage([string]$Path) {
    if(-not (Test-Path -LiteralPath $Path)){return $null};Assert-OwnerPath $Path
    $f=[IO.FileStream]::new($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
    try{
        $bytes=[byte[]]::new(513);$count=0
        while($count -lt 513){$n=$f.Read($bytes,$count,513-$count);if($n -eq 0){break};$count+=$n}
        if($count -gt 512 -or @($bytes[0..([Math]::Max(0,$count-1))]|Where-Object {$_ -gt 127}).Count -ne 0){throw 'OWNER_MESSAGE'}
        if($count -eq 0 -or $bytes[$count-1] -ne 10){return $null}
        [Text.Encoding]::ASCII.GetString($bytes,0,$count)
    }finally{$f.Dispose()}
}
function Get-OwnerFingerprint($Context) {
    $sha=[Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    try{
        foreach($p in @($Context.Root,$Context.Java,$Context.Dll,$Context.Classes,$Context.Scripts)){Assert-OwnerPath $p}
        $sha.AppendData([Text.Encoding]::UTF8.GetBytes($Context.Root));$sha.AppendData([IO.File]::ReadAllBytes($Context.Java));$sha.AppendData([IO.File]::ReadAllBytes($Context.Dll))
        $javaRoot=[IO.Directory]::GetParent([IO.Path]::GetDirectoryName($Context.Java)).FullName
        foreach($name in @('bin/jli.dll','bin/server/jvm.dll','release')){
            $p=Join-Path $javaRoot $name;Assert-OwnerPath $p
            $sha.AppendData([Text.Encoding]::UTF8.GetBytes($name));$sha.AppendData([Text.Encoding]::ASCII.GetBytes((Get-FileHash -LiteralPath $p).Hash.ToLowerInvariant()))
        }
        $files=[IO.Directory]::GetFiles($Context.Classes,'*',[IO.SearchOption]::AllDirectories);[Array]::Sort($files,[StringComparer]::OrdinalIgnoreCase)
        if($files.Count -lt 1 -or $files.Count -gt 64){throw 'OWNER_CLASSES'}
        foreach($file in $files){
            Assert-OwnerPath $file;$name=[IO.Path]::GetRelativePath($Context.Classes,$file)
            if((Get-Item -LiteralPath $file).Length -gt 1048576){throw 'OWNER_CLASS_SIZE'}
            if($name -cnotmatch '^de\\mangalib\\ownerprobe\\(OwnerProbeFiles|OwnerProbeLifecycle|OwnerProvenanceMain|OwnerCompletionMain)(\$[A-Za-z0-9_]+)?\.class$'){throw 'OWNER_CLASS_SCOPE'}
            $b=[IO.File]::ReadAllBytes($file)
            if($b.Length -lt 8 -or $b[0] -ne 202 -or $b[1] -ne 254 -or $b[2] -ne 186 -or $b[3] -ne 190 -or $b[6]*256+$b[7] -ne 65){throw 'OWNER_CLASS_VERSION'}
            $sha.AppendData([Text.Encoding]::UTF8.GetBytes($name));$sha.AppendData($b)
        }
        foreach($name in @('OwnerProbe.ps1','OwnerNativeInspection.ps1','Start-OwnerProvenance.ps1','OwnerCompletion.ps1','Start-OwnerCompletion.ps1')){
            $p=Join-Path $Context.Scripts $name;Assert-OwnerPath $p;if((Get-Item -LiteralPath $p).Length -gt 100000){throw 'OWNER_SCRIPT_SIZE'}
            $sha.AppendData([Text.Encoding]::UTF8.GetBytes($name));$sha.AppendData([IO.File]::ReadAllBytes($p))
        }
        [Convert]::ToHexString($sha.GetHashAndReset()).ToLowerInvariant()
    }finally{$sha.Dispose()}
}
function Get-OwnerContext([string]$Root,[string]$AttemptId) {
    $root=[IO.Path]::GetFullPath($Root)
    if($PSVersionTable.PSVersion.Major -lt 7 -or [IntPtr]::Size -ne 8 -or
            [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() -cne 'X64' -or
            -not [OperatingSystem]::IsWindows() -or $root -match '[^\x20-\x7e]' -or $root -notmatch '^[A-Za-z]:\\'){throw 'OWNER_PLATFORM'}
    foreach($key in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){
        if(Test-Path ('Env:'+$key)){throw 'OWNER_JVM_OVERRIDE'}
    }
    $c=[pscustomobject]@{Root=$root;AttemptId=$AttemptId;Directory=(Get-OwnerDirectory $root $AttemptId);
        Java='C:\Program Files\Java\jdk-21\bin\java.exe';Dll='C:\Windows\System32\kernel32.dll';
        Classes=(Join-Path $root 'build/classes/java/ownerProbe');Scripts=(Join-Path $root 'src/ownerProbe/powershell');Artifact=$null;ManifestHash=$null;ObserverPid=$PID;HistoricalBoundary=$null}
    if((Get-FileHash -LiteralPath $c.Java).Hash -cne '7272CC277ABDAFC7FA9FDFAD2CA04639824A2D31338CD7010CBCB4006EE7FBA8'){throw 'OWNER_JAVA_PIN'}
    $c.Artifact=Get-OwnerFingerprint $c
    $dllHash=(Get-FileHash -LiteralPath $c.Dll).Hash.ToLowerInvariant()
    Assert-OwnerPath $c.Directory;$manifest=Join-Path $c.Directory 'manifest.txt';Assert-OwnerPath $manifest
    if((Get-Item -LiteralPath $manifest).Length -gt 1024){throw 'OWNER_MANIFEST_SIZE'}
    $text="OWNER_PROBE_V1`n"+$AttemptId+"`n"+$c.Artifact+"`n7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8`n"+$dllHash+"`n"+$root+"`n"
    if([IO.File]::ReadAllText($manifest) -cne $text){throw 'OWNER_MANIFEST'}
    $c.ManifestHash=(Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant()
    foreach($name in @('consumed','binding','ready','stop','finish','closed','result.json')){
        if(Test-Path -LiteralPath (Join-Path $c.Directory $name)){throw 'OWNER_ALREADY_USED'}
    }
    $c.HistoricalBoundary=Get-OwnerHistoricalBoundary $root $AttemptId
    $c
}
function Use-OwnerApproval($Context,[string]$ApprovalHash) {
    if($ApprovalHash -cnotmatch '^[a-f0-9]{64}$'){throw 'OWNER_APPROVAL_HASH'}
    if($Context.Directory -cne (Get-OwnerDirectory $Context.Root $Context.AttemptId) -or
            $Context.Artifact -cnotmatch '^[a-f0-9]{64}$' -or $Context.ManifestHash -cnotmatch '^[a-f0-9]{64}$'){throw 'OWNER_APPROVAL_SCOPE'}
    Assert-OwnerPath $Context.Directory
    $manifest=Join-Path $Context.Directory 'manifest.txt';Assert-OwnerPath $manifest
    if((Get-Item -LiteralPath $manifest).Length -gt 1024 -or (Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant() -cne $Context.ManifestHash){throw 'OWNER_MANIFEST_CHANGED'}
    $p=Join-Path $Context.Directory 'approval.txt';Assert-OwnerPath $p
    if((Get-Item -LiteralPath $p).Length -gt 256 -or (Get-FileHash -LiteralPath $p).Hash.ToLowerInvariant() -cne $ApprovalHash){throw 'OWNER_APPROVAL'}
    $expected="OWNER_APPROVED_V1`n"+$Context.AttemptId+"`n"+$Context.Artifact+"`n"+$Context.ManifestHash+"`n"
    if([IO.File]::ReadAllText($p) -cne $expected){throw 'OWNER_APPROVAL'}
    Write-OwnerNew (Join-Path $Context.Directory 'consumed') ("OWNER_CONSUMED_V1`n"+$Context.AttemptId+"`n"+$Context.Artifact+"`n"+$ApprovalHash+"`n")
    (Get-FileHash -LiteralPath (Join-Path $Context.Directory 'consumed')).Hash.ToLowerInvariant()
}
function Invoke-OwnerProbe($Context,$Ports,[string]$ApprovalHash,[switch]$ExecuteOnce) {
    if(-not $ExecuteOnce){throw 'OWNER_EXPLICIT_APPROVAL_REQUIRED'}
    if((& $Ports.Fingerprint $Context) -cne $Context.Artifact){throw 'OWNER_ARTIFACT'}
    $clock=& $Ports.Clock;$deadline=25000
    $consumed=Use-OwnerApproval $Context $ApprovalHash
    $owner=$null;$subscription=$null;$reads=$null;$identity=$null;$safe=$null;$key=$null
    $startRequested=$false;$startConfirmed=$false;$stop=$false;$stopSent=$false;$ready=$false;$finishSent=$false
    $rootTrace=$false;$observerFailed=$false;$ownerClosed=$false;$subscriptionClosed=$false
    $end=[pscustomobject]@{Ended=$false;ExitObserved=$false;ExitCode=$null}
    $stream=[pscustomobject]@{StdoutEof=$false;StderrEof=$false;StdoutEmpty=$false;DiagnosticsValid=$false;Diagnostics=@()}
    $findings=[Collections.Generic.List[object]]::new()
    $failures=[Collections.Generic.List[object]]::new()
    $observedTrace=@()
    $phase='PREFLIGHT';$failurePhase=$null
    try{
        if((& $Ports.Fingerprint $Context) -cne $Context.Artifact){throw 'OWNER_ARTIFACT_CHANGED'}
        if($clock.ElapsedMilliseconds -ge $deadline){throw 'OWNER_PREFLIGHT_DEADLINE'}
        $phase='NATIVE_DECLARATION';& $Ports.NativeInit
        if($clock.ElapsedMilliseconds -ge $deadline){throw 'OWNER_PREFLIGHT_DEADLINE'}
        $phase='SUBSCRIBE'
        $subscription=New-OwnerObservationReceipt $Context.AttemptId
        & $Ports.Subscribe $Context $subscription
        if($subscription.Acquisition -cne 'CONFIRMED' -or $null -eq $subscription.Subscriber -or $clock.ElapsedMilliseconds -ge $deadline){throw 'OWNER_SUBSCRIPTION'}
        if((& $Ports.Fingerprint $Context) -cne $Context.Artifact -or
                (Get-FileHash -LiteralPath (Join-Path $Context.Directory 'manifest.txt')).Hash.ToLowerInvariant() -cne $Context.ManifestHash -or
                (Get-FileHash -LiteralPath (Join-Path $Context.Directory 'approval.txt')).Hash.ToLowerInvariant() -cne $ApprovalHash -or
                $clock.ElapsedMilliseconds -ge $deadline){throw 'OWNER_PRESTART_CHANGED'}
        $phase='START';$startRequested=$true;$started=& $Ports.Start $Context;$owner=$started.Owner;$startConfirmed=$started.Confirmed
        if($null -ne $started.Failure -and $failures.Count -lt 8){$failures.Add($started.Failure)}
        if(-not $startConfirmed -or $null -eq $owner){throw 'OWNER_START_UNKNOWN'}
        $phase='IDENTITY';$safe=$owner.SafeHandle;$identity=& $Ports.Identity $owner $safe
        if($null -eq $identity -or -not $identity.Held -or $identity.Source -cne 'OWN_PROCESS_START_HANDLE_NATIVE' -or
                $identity.Pid -le 0 -or $identity.Image -ine $Context.Java -or
                $identity.Birth -cnotmatch '^[1-9][0-9]{0,18}$' -or [long]$identity.Birth -le 0){throw 'OWNER_IDENTITY'}
        $phase='READ_SETUP';$reads=& $Ports.BeginReads $owner
        $key='OP1|'+$Context.AttemptId+'|'+$Context.Artifact+'|'+$identity.Pid+'|'+$identity.Birth
        Write-OwnerNew (Join-Path $Context.Directory 'binding') ($key+"`n")
        while($clock.ElapsedMilliseconds -lt $deadline){
            $phase='OBSERVATION'
            $poll=& $Ports.Poll $subscription $identity $Context
            if($null -eq $poll -or -not $poll.Healthy){throw 'OWNER_OBSERVER'}
            $observedTrace=@($poll.ObservedTrace)
            $rootTrace=$rootTrace -or $poll.RootSeen
            foreach($finding in @($poll.Findings)){
                if($null -ne $finding){if($findings.Count -ge 256){throw 'OWNER_FINDINGS_LIMIT'};$findings.Add($finding);$stop=$true}
            }
            if(& $Ports.BadOutput $reads){$stop=$true}
            $message=Read-OwnerMessage (Join-Path $Context.Directory 'ready')
            if($null -ne $message){if($message -cne ("READY|"+$key+"`n")){throw 'OWNER_READY'};$ready=$true}
            if($stop){break}
            if($ready -and $rootTrace -and -not $finishSent){
                Write-OwnerNew (Join-Path $Context.Directory 'finish') ("FINISH|"+$key+"`n");$finishSent=$true
            }
            $phase='NATIVE_END';$end=& $Ports.End $owner $safe $identity
            if($null -eq $end -or ($end.Ended -and -not $end.ExitObserved) -or ($end.ExitObserved -and -not $end.Ended)){throw 'OWNER_END_UNKNOWN'}
            if($end.Ended){break}
            & $Ports.Pause
        }
        if($clock.ElapsedMilliseconds -ge $deadline -or -not $finishSent){$stop=$true}
    }catch{Add-OwnerFailure $failures $_ $phase $(if($phase -ceq 'SUBSCRIBE'){$subscription.Step}else{$phase});$failurePhase=$phase;$stop=$true;$observerFailed=$true}
    # Ein gebundener STOP führt nur zum eigenen geordneten JVM-Ausstieg, niemals zu PID-/Namenskills.
    if($stop -and $null -ne $key -and $clock.ElapsedMilliseconds -lt $deadline){
        try{Write-OwnerNew (Join-Path $Context.Directory 'stop') ("STOP|"+$key+"`n");$stopSent=$true}catch{Add-OwnerFailure $failures $_ 'STOP_WRITE' 'STOP_WRITE';$observerFailed=$true}
    }
    try{
        while($null -ne $owner -and $null -ne $identity -and -not $end.Ended -and $clock.ElapsedMilliseconds -lt $deadline){
            $end=& $Ports.End $owner $safe $identity
            if(-not $end.Ended){& $Ports.Pause}
        }
        if($null -ne $reads){$stream=& $Ports.Reads $reads $clock $deadline}
        if($null -ne $identity -and $null -ne $subscription -and $clock.ElapsedMilliseconds -lt $deadline){
            $last=& $Ports.Poll $subscription $identity $Context
            if(-not $last.Healthy){throw 'OWNER_OBSERVER'};$rootTrace=$rootTrace -or $last.RootSeen
            $observedTrace=@($last.ObservedTrace)
            foreach($finding in @($last.Findings)){if($null -ne $finding){if($findings.Count -ge 256){throw 'OWNER_FINDINGS_LIMIT'};$findings.Add($finding);$stop=$true}}
        }
    }catch{Add-OwnerFailure $failures $_ 'END_READ_OBSERVATION' 'END_READ_OBSERVATION';if($null -eq $failurePhase){$failurePhase='END_READ_OBSERVATION'};$observerFailed=$true;$stop=$true}
    if(-not $end.Ended -or -not $end.ExitObserved -or $end.ExitCode -ne 0){$stop=$true}
    # Selbst bei STOP erst native Endbeobachtung UND Read-/EOF-Abschluss, dann eigene Handles.
    if($null -ne $owner -and $end.Ended -and $end.ExitObserved -and $stream.StdoutEof -and $stream.StderrEof -and $clock.ElapsedMilliseconds -lt $deadline){
        try{$ownerClosed=& $Ports.CloseOwner $owner $safe $identity}catch{Add-OwnerFailure $failures $_ 'OWNER_CLOSE' 'OWNER_CLOSE';$observerFailed=$true}
    }
    if($null -ne $subscription -and $subscription.Acquisition -cne 'NOT_ATTEMPTED' -and $null -ne $subscription.Subscriber -and $clock.ElapsedMilliseconds -lt $deadline){
        $subscription.ReleaseAttempted=$true
        try{$subscriptionClosed=& $Ports.Unsubscribe $subscription;$subscription.ReleaseConfirmed=[bool]$subscriptionClosed}catch{Add-OwnerFailure $failures $_ 'SUBSCRIPTION_CLOSE' $subscription.Step;$observerFailed=$true}
    }
    $closed=$null
    try{$closed=Read-OwnerMessage (Join-Path $Context.Directory 'closed')}catch{Add-OwnerFailure $failures $_ 'CLOSED_READ' 'CLOSED_READ';$observerFailed=$true;$stop=$true}
    $normalClosed=$null -ne $key -and $closed -ceq ("CLOSED|"+$key+"|NORMAL`n")
    $stopClosed=$null -ne $key -and $closed -ceq ("CLOSED|"+$key+"|STOP`n")
    if((-not $normalClosed -and -not $stopClosed) -or -not $ownerClosed -or -not $subscriptionClosed -or -not $stream.DiagnosticsValid -or -not $stream.StdoutEmpty){$stop=$true}
    if($clock.ElapsedMilliseconds -ge $deadline){$stop=$true}
    $pass=-not $stop -and -not $observerFailed -and $clock.ElapsedMilliseconds -lt $deadline -and $startConfirmed -and $rootTrace
    $pass=$pass -and $ready -and $finishSent -and $normalClosed -and $end.Ended -and $end.ExitObserved -and $end.ExitCode -eq 0
    $pass=$pass -and $stream.StdoutEmpty -and $stream.StdoutEof -and $stream.StderrEof -and $stream.DiagnosticsValid
    $pass=$pass -and @($stream.Diagnostics).Count -eq 0 -and $findings.Count -eq 0 -and $ownerClosed -and $subscriptionClosed
    [pscustomobject]@{
        Report=[pscustomobject]@{Profile='OWNER_PROVENANCE_V1';AttemptId=$Context.AttemptId;ArtifactHash=$Context.Artifact;ManifestHash=$Context.ManifestHash;
            ApprovalHash=$ApprovalHash;ConsumedHash=$consumed;Outcome=$(if($pass){'PASS'}else{'PARTIAL/STOP'});
            HistoricalBoundary=$Context.HistoricalBoundary;
            StartRequested=$startRequested;StartConfirmed=$startConfirmed;Identity=$identity;HeldEnd=$end;
            ReadyObserved=$ready;FinishSent=$finishSent;StopRequested=$stop;StopSent=$stopSent;StopClosure=$stopClosed;NormalClosure=$normalClosed;
            RootTraceSeen=$rootTrace;Findings=$findings.ToArray();ObserverFailed=$observerFailed;Streams=$stream;
            SupplementalTrace=$observedTrace;SupplementalTraceGrantsOwnership=$false;
            FailurePhase=$failurePhase;
            Failures=$failures.ToArray();FailureLimitReached=($failures.Count -eq 8);
            OwnerCompletionConfirmed=($end.Ended -and $end.ExitObserved -and $stream.StdoutEof -and $stream.StderrEof -and $ownerClosed -and $clock.ElapsedMilliseconds -lt $deadline);
            OwnerExitAccepted=($end.Ended -and $end.ExitObserved -and $end.ExitCode -eq 0);
            OwnerHandleAcquired=($null -ne $safe);
            ObservationComplete=($rootTrace -and $subscriptionClosed -and -not $observerFailed -and $clock.ElapsedMilliseconds -lt $deadline);
            ProvenanceAccepted=($rootTrace -and $subscriptionClosed -and -not $observerFailed -and $findings.Count -eq 0 -and $clock.ElapsedMilliseconds -lt $deadline);
            ObservationAcquisition=$(if($null -ne $subscription){$subscription.Acquisition}else{'NOT_ATTEMPTED'});
            ObservationStep=$(if($null -ne $subscription){$subscription.Step}else{$null});
            ObservationReleaseAttempted=($null -ne $subscription -and $subscription.ReleaseAttempted);
            ResourcesUnconfirmed=($startRequested -and -not $ownerClosed -or $null -ne $subscription -and $subscription.Acquisition -cne 'NOT_ATTEMPTED' -and -not $subscriptionClosed);
            DeadlineExpired=($clock.ElapsedMilliseconds -ge $deadline);
            OwnerHandleClosed=$ownerClosed;SubscriptionClosed=$subscriptionClosed;WorkerStarts=0;JobCreates=0;
            RetryAllowed=$false;PidAbsenceIsEndProof=$false;GlobalProcessEnumerationProved=$false;NativeIdentityAuthority='HELD_START_HANDLE'}
        RetainedOwner=$(if(-not $ownerClosed){$owner}else{$null});RetainedSubscription=$(if($null -ne $subscription -and $subscription.Acquisition -cne 'NOT_ATTEMPTED' -and -not $subscriptionClosed){$subscription}else{$null})
    }
}
