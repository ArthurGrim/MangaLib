param([string]$AttemptId,[string]$ApprovalHash,[switch]$ValidateOnly,[switch]$ExecuteOnce)
$ErrorActionPreference='Stop'
if(($ValidateOnly -and $ExecuteOnce) -or (-not $ValidateOnly -and -not $ExecuteOnce)){throw 'JOB_EXPLICIT_MODE'}
# Dieser Einstieg ist ausschließlich ein neuer synthetischer Jobversuch. Keine alte
# Owner-Freigabe, kein Spring-/Seleniumklassenpfad und kein globaler Beobachter.
function Assert-JobPath([string]$Path){
    $f=Get-Item -LiteralPath $Path -Force
    while($null -ne $f){if(($f.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'JOB_PATH'};$f=$(if($f -is [IO.FileInfo]){$f.Directory}else{$f.Parent})}
}
function Write-JobNew([string]$Path,[string]$Value){
    $bytes=[Text.UTF8Encoding]::new($false).GetBytes($Value)
    $f=[IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{$f.Write($bytes);$f.Flush($true)}finally{$f.Dispose()}
    if((Get-FileHash -LiteralPath $Path).Hash -cne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))){throw 'JOB_WRITE'}
}
function Get-JobFingerprint($c){
    $lines=[Collections.Generic.List[string]]::new();$lines.Add('WORKER_JOB_PROOF_V1');$lines.Add($c.Root)
    foreach($name in @($c.Java,'C:\Windows\System32\kernel32.dll',$c.Jna,'C:\Program Files\Java\jdk-21\bin\jli.dll',
            'C:\Program Files\Java\jdk-21\bin\server\jvm.dll','C:\Program Files\Java\jdk-21\release')){
        Assert-JobPath $name;$lines.Add($name+'|'+(Get-FileHash -LiteralPath $name).Hash.ToLowerInvariant())
    }
    foreach($name in @('workerOwner','workerOwnerNative','jobAdapter')){
        $base=Join-Path $c.Root ('build/classes/java/'+$name);Assert-JobPath $base
        $files=[IO.Directory]::GetFiles($base,'*',[IO.SearchOption]::AllDirectories);[Array]::Sort($files,[StringComparer]::OrdinalIgnoreCase)
        if($files.Count -lt 1 -or $files.Count -gt 512){throw 'JOB_CLASSES'}
        foreach($file in $files){
            Assert-JobPath $file;$relative=[IO.Path]::GetRelativePath($base,$file)
            if($relative -cnotmatch '^de\\mangalib\\windowsadapter\\[A-Za-z0-9_$]+\.class$' -or $relative.Contains('Test') -or (Get-Item $file).Length -gt 1048576){throw 'JOB_CLASS'}
            $bytes=[IO.File]::ReadAllBytes($file)
            if($bytes.Length -lt 8 -or $bytes[0] -ne 202 -or $bytes[1] -ne 254 -or $bytes[2] -ne 186 -or $bytes[3] -ne 190 -or $bytes[6] -ne 0 -or $bytes[7] -ne 65){throw 'JOB_CLASS_VERSION'}
            $lines.Add($name+'/'+$relative+'|'+(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant())
        }
    }
    foreach($name in @('src/workerOwnerNative/powershell/Start-WorkerJobProof.ps1','src/ownerProbe/powershell/OwnerNativeInspection.ps1')){
        $file=Join-Path $c.Root $name;Assert-JobPath $file;if((Get-Item $file).Length -gt 100000){throw 'JOB_SCRIPT'}
        $lines.Add($name+'|'+(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant())
    }
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")+"`n"))).ToLowerInvariant()
}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'));$id=[guid]::Empty
if(-not [guid]::TryParseExact($AttemptId,'D',[ref]$id) -or $id -eq [guid]::Empty -or $id.ToString() -cne $AttemptId){throw 'JOB_ID'}
if($PSVersionTable.PSVersion.Major -lt 7 -or -not [OperatingSystem]::IsWindows() -or [IntPtr]::Size -ne 8 -or
    [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() -cne 'X64' -or $root -match '[^\x20-\x7e]'){throw 'JOB_PLATFORM'}
foreach($key in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){if(Test-Path ('Env:'+$key)){throw 'JOB_OVERRIDE'}}
$c=[pscustomobject]@{Root=$root;Directory=(Join-Path $root ('.gradle/t02-worker-job-proof/'+$AttemptId));
    Java='C:\Program Files\Java\jdk-21\bin\java.exe';Jna=(Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\net.java.dev.jna\jna\5.17.0\33d12735bef894440780fce64f9758d420c7bae2\jna-5.17.0.jar')}
Assert-JobPath $c.Directory
if((Get-FileHash $c.Java).Hash -cne '7272CC277ABDAFC7FA9FDFAD2CA04639824A2D31338CD7010CBCB4006EE7FBA8' -or
    (Get-FileHash $c.Jna).Hash -cne 'B3A9408E7C51E08EF0E3BFCC08F443F6EC0F6191BA8CD7C18D53D2B22E5BDBC0'){throw 'JOB_RUNTIME_PIN'}
$artifact=Get-JobFingerprint $c;$manifest=Join-Path $c.Directory 'manifest.txt';Assert-JobPath $manifest
if((Get-Item $manifest).Length -gt 1024 -or [IO.File]::ReadAllText($manifest) -cne ("WORKER_JOB_PROOF_V1`n"+$AttemptId+"`n"+$artifact+"`n"+$root+"`n")){throw 'JOB_MANIFEST'}
$manifestHash=(Get-FileHash $manifest).Hash.ToLowerInvariant()
foreach($file in @('consumed','binding','stop','job-result.txt','job-failure.txt','job-native-failure.txt','job-diagnostic.txt','result.json','journal')){if(Test-Path (Join-Path $c.Directory $file)){throw 'JOB_USED'}}
$slot=Join-Path $root '.gradle/t02-worker-job-proof/import-slot'
if(Test-Path -LiteralPath $slot){
    Assert-JobPath $slot
    if((Test-Path (Join-Path $slot 'pending')) -or (Test-Path (Join-Path $slot 'quarantine'))){throw 'JOB_PRIOR_IMPORT_UNCONFIRMED'}
}
if($ValidateOnly){[pscustomobject]@{Profile='WORKER_JOB_PROOF_V1';AttemptId=$AttemptId;ArtifactHash=$artifact;ManifestHash=$manifestHash;
    Approved=$false;Consumed=$false;NativeCalls=$false;OwnerStarts=0;WorkerStarts=0;JobCreates=0};return}
if($null -ne ('MangaLib.OwnerProbe.InspectionV1' -as [type]) -or $null -ne $script:WorkerJobRetained){throw 'JOB_FRESH_SESSION'}
$approval=Join-Path $c.Directory 'approval.txt';Assert-JobPath $approval
if($ApprovalHash -cnotmatch '^[a-f0-9]{64}$' -or (Get-Item $approval).Length -gt 256 -or
    (Get-FileHash $approval).Hash.ToLowerInvariant() -cne $ApprovalHash -or
    [IO.File]::ReadAllText($approval) -cne ("WORKER_JOB_APPROVED_V1`n"+$AttemptId+"`n"+$artifact+"`n"+$manifestHash+"`n")){throw 'JOB_APPROVAL'}
Write-JobNew (Join-Path $c.Directory 'consumed') ("WORKER_JOB_CONSUMED_V1`n"+$AttemptId+"`n"+$artifact+"`n"+$ApprovalHash+"`n")
$clock=[Diagnostics.Stopwatch]::StartNew();$deadline=45000;$identity=$null;$safe=$null;$p=$null;$ended=$null
$streamsClosed=$false;$handleClosed=$false;$outEof=$false;$errEof=$false;$requested=$false;$phase='NATIVE_DECLARATION';$diagnostic=$null;$job=$null
try{
    . (Join-Path $root 'src/ownerProbe/powershell/OwnerNativeInspection.ps1');Initialize-OwnerNativeInspection
    if((Get-JobFingerprint $c) -cne $artifact -or (Get-FileHash $manifest).Hash.ToLowerInvariant() -cne $manifestHash -or
        (Get-FileHash $approval).Hash.ToLowerInvariant() -cne $ApprovalHash -or $clock.ElapsedMilliseconds -ge $deadline){throw 'JOB_CHANGED'}
    $phase='START';$p=[Diagnostics.Process]::new();$script:WorkerJobRetained=$p
    $s=[Diagnostics.ProcessStartInfo]::new();$s.FileName=$c.Java;$s.WorkingDirectory=$root;$s.UseShellExecute=$false;$s.CreateNoWindow=$true
    $s.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden;$s.RedirectStandardInput=$true;$s.RedirectStandardOutput=$true;$s.RedirectStandardError=$true
    $s.StandardOutputEncoding=[Text.UTF8Encoding]::new($false,$true);$s.StandardErrorEncoding=[Text.UTF8Encoding]::new($false,$true)
    $s.Environment.Clear();$s.Environment['SystemRoot']='C:\Windows';$s.Environment['TEMP']=$c.Directory;$s.Environment['TMP']=$c.Directory
    $cp=(@('workerOwner','workerOwnerNative','jobAdapter')|ForEach-Object {Join-Path $root ('build/classes/java/'+$_)}) -join ';';$cp+=';'+$c.Jna
    foreach($arg in @('-Xmx256m','-Djna.nosys=true','-cp',$cp,'de.mangalib.windowsadapter.WorkerJobProofMain','--worker-job-proof-v1',$AttemptId,$artifact)){$s.ArgumentList.Add($arg)}
    $p.StartInfo=$s;$requested=$true;if(-not $p.Start()){throw 'JOB_START'};$safe=$p.SafeHandle
    if($clock.ElapsedMilliseconds -ge $deadline){throw 'JOB_START_EXPIRED'}
    $phase='IDENTITY';$identity=[MangaLib.OwnerProbe.InspectionV1]::Identity($safe)
    if($identity.Pid -ne $p.Id -or $identity.Image -ine $c.Java -or $identity.Birth -le 0){throw 'JOB_IDENTITY'}
    $phase='IO';$p.StandardInput.Dispose();$out=[char[]]::new(1);$err=[char[]]::new(1)
    $outTask=$p.StandardOutput.ReadAsync($out,0,1);$errTask=$p.StandardError.ReadAsync($err,0,1)
    Write-JobNew (Join-Path $c.Directory 'binding') ('JOB1|'+$AttemptId+'|'+$artifact+'|'+$identity.Pid+'|'+$identity.Birth+"`n")
    $phase='END'
    while($clock.ElapsedMilliseconds -lt $deadline){
        if(-not [object]::ReferenceEquals($p.SafeHandle,$safe)){throw 'JOB_HANDLE_CHANGED'}
        $ended=[MangaLib.OwnerProbe.InspectionV1]::End($safe,$identity.Pid,$identity.Birth)
        if($ended.Ended -and $outTask.IsCompleted -and $errTask.IsCompleted){break};[Threading.Thread]::Sleep(1)
    }
    $outEof=$outTask.IsCompletedSuccessfully -and $outTask.Result -eq 0;$errEof=$errTask.IsCompletedSuccessfully -and $errTask.Result -eq 0
    if($ended.Ended -and $ended.ExitObserved -and $outEof -and $errEof -and $clock.ElapsedMilliseconds -lt $deadline){
        $phase='CLOSE';$p.StandardOutput.Dispose();$p.StandardError.Dispose();$streamsClosed=$true
        $handleClosed=[MangaLib.OwnerProbe.InspectionV1]::CloseEndedOwner($safe,$identity.Pid,$identity.Birth)
        if($handleClosed){$p.Dispose();$handleClosed=$safe.IsClosed;if($handleClosed){$script:WorkerJobRetained=$null}}
    }
    $phase='REPORT';$file=Join-Path $c.Directory 'job-result.txt';Assert-JobPath $file
    if((Get-Item $file).Length -gt 4096){throw 'JOB_REPORT_LIMIT'};$parts=[IO.File]::ReadAllText($file).Split([char]10)
    if($parts.Count -ne 16 -or $parts[0] -cne 'WORKER_JOB_RESULT_V1' -or $parts[1] -cne $AttemptId -or $parts[2] -cne $artifact -or $parts[15] -cne ''){throw 'JOB_REPORT_BINDING'}
    $job=[pscustomobject]@{Outcome=$parts[3];State=$parts[4];WorkerPid=$parts[5];WorkerBirth=$parts[6];WorkerExit=$parts[7];
        GroupEnded=$parts[8];ResourcesClosed=$parts[9];ChannelEnded=$parts[10];AssignedTotal=$parts[11];Factories=$parts[12];JournalClear=$parts[13];Ticket=$parts[14]}
}catch{
    # Nur begrenzte Kategorie/Typ/HResult; keine Message, Commandline oder Stacktrace.
    $diagnostic=[pscustomobject]@{Phase=$phase;Category=[string]$_.CategoryInfo.Category;Type=$_.Exception.GetType().FullName;HResult=$_.Exception.HResult;RawDetailsOmitted=$true}
}
$pass=$null -eq $diagnostic -and $null -ne $job -and $job.Outcome -ceq 'JOB_PROOF_PASS' -and $job.State -ceq 'IDLE' -and
    $job.WorkerPid -cmatch '^[1-9][0-9]*$' -and $job.WorkerBirth -cmatch '^[1-9][0-9]*$' -and $job.WorkerExit -ceq '0' -and
    $job.GroupEnded -ceq 'true' -and $job.ResourcesClosed -ceq 'true' -and $job.ChannelEnded -ceq 'true' -and $job.AssignedTotal -cmatch '^[1-9][0-9]{0,9}$' -and
    $job.Factories -ceq '1' -and $job.JournalClear -ceq 'true' -and $null -ne $ended -and $ended.Ended -and $ended.ExitObserved -and $ended.ExitCode -eq 0 -and
    $job.Ticket -cmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$' -and
    $job.Ticket -cne '00000000-0000-0000-0000-000000000000' -and
    $outEof -and $errEof -and $streamsClosed -and $handleClosed -and $clock.ElapsedMilliseconds -lt $deadline
$report=[pscustomobject]@{Profile='WORKER_JOB_PROOF_V1';AttemptId=$AttemptId;ArtifactHash=$artifact;ManifestHash=$manifestHash;
    Outcome=$(if($pass){'WORKER_JOB_PROOF_PASS'}else{'PARTIAL/STOP'});OwnerStartRequested=$requested;OwnerIdentity=$identity;
    OwnerEnd=$(if($null -eq $ended){$null}else{[pscustomobject]@{Ended=$ended.Ended;ExitObserved=$ended.ExitObserved;ExitCode=$(if($ended.ExitObserved){$ended.ExitCode}else{$null})}});
    OwnerStdoutEof=$outEof;OwnerStderrEof=$errEof;OwnerStreamsClosed=$streamsClosed;OwnerHandleClosed=$handleClosed;Job=$job;
    OwnerResourcesUnconfirmed=($null -ne $script:WorkerJobRetained);
    ResourcesUnconfirmed=($null -ne $script:WorkerJobRetained -or $null -eq $job -or $job.ResourcesClosed -cne 'true' -or $job.JournalClear -cne 'true');
    Diagnostic=$diagnostic;ElapsedMs=$clock.ElapsedMilliseconds;
    RetryAllowed=$false;GlobalProcessProvenance='NOT_MEASURED';ConsoleProvenance='NOT_MEASURED'}
try{Write-JobNew (Join-Path $c.Directory 'result.json') ($report|ConvertTo-Json -Depth 8)}
catch{$report.Outcome='PARTIAL/STOP';$report.Diagnostic=[pscustomobject]@{Phase='REPORT_WRITE';RawDetailsOmitted=$true}}
$report
if($null -ne $script:WorkerJobRetained){Write-Output 'WORKER_JOB_RETAINED_SESSION_OPEN';while($true){[Threading.Thread]::Sleep(1000)}}
