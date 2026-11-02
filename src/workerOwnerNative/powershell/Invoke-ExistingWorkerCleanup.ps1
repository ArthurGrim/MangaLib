param([switch]$ExecuteCleanup,[switch]$InspectOnly)
$ErrorActionPreference='Stop'
if($ExecuteCleanup -eq $InspectOnly){throw 'CLEANUP_EXPLICIT_MODE_REQUIRED'}
# Ausschließlich der bereits freigegebene b6f62923-Bestand, keine PID-Parameter,
# Startfunktion, Jobneuerzeugung oder automatische Quarantänefreigabe.
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$attempt=Join-Path $root '.gradle/t02-worker-job-proof/b6f62923-d20b-4873-ac12-154d2bd1d3ed'
$slot=Join-Path $root '.gradle/t02-worker-job-proof/import-slot'
if((Get-Content (Join-Path $root '.git/HEAD') -Raw).Trim() -cne 'ref: refs/heads/codex/t02-security-hardening'){throw 'CLEANUP_BRANCH'}
$fixed=@(@(Get-ChildItem (Join-Path $attempt 'artifact-snapshot') -File -Recurse)+@(Get-ChildItem -LiteralPath $attempt -File)|Sort-Object FullName)
$lines=@($fixed|ForEach-Object {$_.FullName+'|'+$_.Length+'|'+(Get-FileHash -LiteralPath $_.FullName).Hash})
if($fixed.Count -ne 163 -or [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))) -cne
    '727ABF21ABB3F218BFC7080C36190961521685A916F41F987CABDD7790B7D09B'){throw 'CLEANUP_SNAPSHOT_CHANGED'}
foreach($item in @(@('result.json','AED992547D3D2A03638AF79C62D4C10C4961BA2AA24138F3A36CA8F6866634FE'),
        @('job-result.txt','A2F2000B0582D14C57284DBB572586CDC6926999BFF818DE34E223A6B0AAB20C'),
        @('consumed','58B3480B9C832BE1AC9053B56113E1E8444366E4E44CAFB16FD50BD0EC485BFE'))){
    if((Get-FileHash -LiteralPath (Join-Path $attempt $item[0])).Hash -cne $item[1]){throw 'CLEANUP_EVIDENCE_CHANGED'}
}
foreach($item in @(@('pending','5C345DC37876AC72CA9B9A64564F6FFB1F604E9DE3E1F3D7A18D7617591E086F'),
        @('quarantine','5BA83DC787A930AA23431B6B5C0948A9536777F0C04D6E89C0F4E6FA05286FFF'))){
    if((Get-FileHash -LiteralPath (Join-Path $slot $item[0])).Hash -cne $item[1]){throw 'CLEANUP_SLOT_CHANGED'}
}
if((Get-FileHash -LiteralPath (Join-Path $root 'build/libs/mangalib-manual-test.jar')).Hash -cne
    '9AE1B14C46F874A7C32DDB5BB6BDDC3D46559F1F69852FC85AAD7E82B904FAA9'){throw 'CLEANUP_MANUAL_CHANGED'}
$inspection=Join-Path $root 'src/ownerProbe/powershell/OwnerNativeInspection.ps1'
if((Get-FileHash $inspection).Hash -cne (Get-FileHash (Join-Path $attempt 'artifact-snapshot/src/ownerProbe/powershell/OwnerNativeInspection.ps1')).Hash){throw 'CLEANUP_INSPECTION_CHANGED'}
function Assert-NoRecordedCleanup($Records){
    foreach($r in $Records){
        if($r.AttemptId -ceq 'b6f62923-d20b-4873-ac12-154d2bd1d3ed' -and $r.ForcedOwnerTermination -is [bool] -and $r.ForcedOwnerTermination){throw 'CLEANUP_ALREADY_RECORDED'}
    }
}
if($ExecuteCleanup){
    $records=@();$cleanupRoot=Join-Path $root '.gradle/t02-worker-job-cleanup'
    if(Test-Path -LiteralPath $cleanupRoot){foreach($file in Get-ChildItem -LiteralPath $cleanupRoot -Filter result.json -File -Recurse){
        if($file.Length -gt 16384){throw 'CLEANUP_RECORD_SIZE'};$records+=([IO.File]::ReadAllText($file.FullName)|ConvertFrom-Json)
    }}
    Assert-NoRecordedCleanup $records
}
function Write-CleanupNew([string]$Path,$Value){
    $b=[Text.UTF8Encoding]::new($false).GetBytes(($Value|ConvertTo-Json -Depth 9))
    $f=[IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{$f.Write($b,0,$b.Length);$f.Flush($true)}finally{$f.Dispose()}
}
function Assert-CleanupIdentity($Identity,[uint32]$ExpectedPid,[long]$ExpectedBirth){
    if($null -eq $Identity -or $Identity.Pid -ne $ExpectedPid -or $Identity.Birth -ne $ExpectedBirth -or
        $Identity.Image -ine 'C:\Program Files\Java\jdk-21\bin\java.exe'){throw 'CLEANUP_IDENTITY_MISMATCH'}
}
function Assert-WorkerCleanupIdentity($Identity){
    if($null -eq $Identity -or $Identity.Pid -ne 42196 -or $Identity.Birth -ne 134378485914679271 -or
        ($null -ne $Identity.Image -and $Identity.Image -ine 'C:\Program Files\Java\jdk-21\bin\java.exe') -or
        ($null -eq $Identity.Image -and -not ($Identity.Ended -is [bool] -and $Identity.Ended -and $Identity.ImageUnavailableOnEnded))){throw 'CLEANUP_IDENTITY_MISMATCH'}
}
function Get-CleanupEnd($Handle,$Identity){
    $e=[MangaLib.OwnerProbe.InspectionV1]::End($Handle,$Identity.Pid,$Identity.Birth)
    [pscustomobject]@{Ended=$e.Ended;ExitObserved=$e.ExitObserved;ExitCode=$(if($e.ExitObserved){$e.ExitCode}else{$null})}
}
$evidence=Join-Path $root ('.gradle/t02-worker-job-cleanup/'+[guid]::NewGuid().ToString())
[void][IO.Directory]::CreateDirectory($evidence)
$report=[pscustomobject]@{AttemptId='b6f62923-d20b-4873-ac12-154d2bd1d3ed';Outcome='PARTIAL/STOP';
    TimestampUtc=[DateTime]::UtcNow.ToString('O');EvidenceDirectory=$evidence;ScriptHash=(Get-FileHash -LiteralPath $PSCommandPath).Hash.ToLowerInvariant();
    ExplicitCleanupApproval=$true;InspectOnly=[bool]$InspectOnly;
    OwnerIdentity=$null;WorkerIdentity=$null;OwnerBefore=$null;WorkerBefore=$null;OwnerAfter=$null;WorkerAfter=$null;
    OwnerAbsent=$false;WorkerAbsent=$false;OwnerOpenError=$null;WorkerOpenError=$null;ForcedOwnerTermination=$false;TerminationCode=[uint32]0x4d4c0001;
    OwnerHandleClosed=$false;WorkerHandleClosed=$false;PrivateJobEmpty='NOT_DIRECTLY_MEASURED';
    OriginalSessionResources='NOT_MEASURED';QuarantineReleased=$false;NewProcessStarts=0;NewJobs=0;Failure=$null}
$owner=$null;$worker=$null;$phase='DECLARATIONS';$ownerId=$null;$workerId=$null
try{
    . $inspection
    $cleanupNative=@'
namespace MangaLib.ExistingWorkerCleanup {
  using System;
  using System.Runtime.InteropServices;
  using System.Text;
  using Microsoft.Win32.SafeHandles;
  public sealed class Opened { public SafeProcessHandle Handle; public int Error; }
  public sealed class WorkerIdentity {
    public uint Pid; public long Birth; public string Image; public bool Ended;
    public bool ImageUnavailableOnEnded; public int ImageError;
  }
  public static class Native {
    const string Dll=@"C:\Windows\System32\kernel32.dll";
    [DllImport(Dll,ExactSpelling=true,SetLastError=true)]
    static extern SafeProcessHandle OpenProcess(uint access,bool inherit,uint pid);
    [DllImport(Dll,ExactSpelling=true,SetLastError=true)]
    static extern bool TerminateProcess(SafeProcessHandle process,uint code);
    [DllImport(Dll,ExactSpelling=true,SetLastError=true)]
    static extern bool CloseHandle(SafeProcessHandle process);
    [DllImport(Dll,ExactSpelling=true,SetLastError=true)]
    static extern uint GetProcessId(SafeProcessHandle process);
    [DllImport(Dll,ExactSpelling=true,SetLastError=true)]
    static extern bool GetProcessTimes(SafeProcessHandle process,out long birth,out long exit,out long kernel,out long user);
    [DllImport(Dll,ExactSpelling=true,CharSet=CharSet.Unicode,SetLastError=true)]
    static extern bool QueryFullProcessImageNameW(SafeProcessHandle process,uint flags,StringBuilder path,ref uint size);
    public static Opened Open(uint access,uint pid) {
      var handle=OpenProcess(access,false,pid);int error=Marshal.GetLastWin32Error();
      return new Opened { Handle=handle, Error=handle.IsInvalid?error:0 };
    }
    public static WorkerIdentity InspectWorker(SafeProcessHandle handle) {
      uint pid=GetProcessId(handle);long birth,exit,kernel,user;
      if(!GetProcessTimes(handle,out birth,out exit,out kernel,out user))
        throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
      if(pid!=42196 || birth!=134378485914679271L) throw new InvalidOperationException("CLEANUP_IDENTITY_MISMATCH");
      var end=MangaLib.OwnerProbe.InspectionV1.End(handle,pid,birth);
      var path=new StringBuilder(512);uint length=512;
      bool imageKnown=QueryFullProcessImageNameW(handle,0,path,ref length);int imageError=imageKnown?0:Marshal.GetLastWin32Error();
      if(!imageKnown && !end.Ended) throw new System.ComponentModel.Win32Exception(imageError);
      return new WorkerIdentity { Pid=pid,Birth=birth,Image=imageKnown?path.ToString():null,
        Ended=end.Ended,ImageUnavailableOnEnded=!imageKnown && end.Ended,ImageError=imageError };
    }
    // Erwartete Identität erneut auf DEMSELBEN gehaltenen Handle prüfen.
    public static bool StopExactOwner(SafeProcessHandle handle,uint pid,long birth,string image,uint code) {
      var id=MangaLib.OwnerProbe.InspectionV1.Identity(handle);
      if(id.Pid!=pid || id.Birth!=birth || !String.Equals(id.Image,image,StringComparison.OrdinalIgnoreCase))
        throw new InvalidOperationException("CLEANUP_IDENTITY_MISMATCH");
      var end=MangaLib.OwnerProbe.InspectionV1.End(handle,pid,birth);
      if(end.Ended) return false;
      if(!TerminateProcess(handle,code)) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
      return true;
    }
    public static bool CloseOwn(SafeProcessHandle handle) {
      if(handle==null || handle.IsClosed) return true;
      if(handle.IsInvalid) { handle.Dispose(); return true; }
      if(!CloseHandle(handle)) return false;
      handle.SetHandleAsInvalid();handle.Dispose();return handle.IsClosed;
    }
  }
}
'@
    Add-Type -TypeDefinition ($script:OwnerNativeDeclaration+"`n"+$cleanupNative)
    $phase='OWNER_OPEN';$opened=[MangaLib.ExistingWorkerCleanup.Native]::Open($(if($InspectOnly){0x101000}else{0x101001}),15044)
    $owner=$opened.Handle;$report.OwnerOpenError=$opened.Error
    if($owner.IsInvalid){if($opened.Error -ne 87){throw 'CLEANUP_OWNER_OPEN'};$report.OwnerAbsent=$true}
    else{$ownerId=[MangaLib.OwnerProbe.InspectionV1]::Identity($owner);Assert-CleanupIdentity $ownerId 15044 134378485858303646
        $report.OwnerIdentity=$ownerId;$report.OwnerBefore=Get-CleanupEnd $owner $ownerId}
    $phase='WORKER_OPEN';$opened=[MangaLib.ExistingWorkerCleanup.Native]::Open(0x101000,42196)
    $worker=$opened.Handle;$report.WorkerOpenError=$opened.Error
    if($worker.IsInvalid){if($opened.Error -ne 87){throw 'CLEANUP_WORKER_OPEN'};$report.WorkerAbsent=$true}
    else{$workerId=[MangaLib.ExistingWorkerCleanup.Native]::InspectWorker($worker);Assert-WorkerCleanupIdentity $workerId
        $report.WorkerIdentity=$workerId;$report.WorkerBefore=Get-CleanupEnd $worker $workerId}
    Write-CleanupNew (Join-Path $evidence 'before.json') $report
    if($report.OwnerAbsent -or $report.WorkerAbsent){throw 'CLEANUP_TARGET_IDENTITY_UNAVAILABLE'}
    # Kein nachträglich erfundener kooperativer Kanal. Nur die eindeutig eigene
    # Ownerinstanz darf unter dieser expliziten Cleanupfreigabe beendet werden.
    $phase='OWNER_STOP'
    if(-not $InspectOnly -and $null -ne $ownerId -and -not $report.OwnerBefore.Ended){
        $report.ForcedOwnerTermination=[MangaLib.ExistingWorkerCleanup.Native]::StopExactOwner($owner,$ownerId.Pid,$ownerId.Birth,$ownerId.Image,$report.TerminationCode)
    }
    $phase='END_PROOF';$clock=[Diagnostics.Stopwatch]::StartNew()
    while($clock.ElapsedMilliseconds -lt $(if($InspectOnly){1}else{10000})){
        if($null -ne $ownerId){$report.OwnerAfter=Get-CleanupEnd $owner $ownerId}
        if($null -ne $workerId){$report.WorkerAfter=Get-CleanupEnd $worker $workerId}
        if(($null -eq $ownerId -or $report.OwnerAfter.Ended) -and ($null -eq $workerId -or $report.WorkerAfter.Ended)){break}
        [Threading.Thread]::Sleep(10)
    }
    if($null -ne $ownerId -and $null -ne $workerId -and $report.OwnerAfter.Ended -and $report.OwnerAfter.ExitObserved -and
        $report.WorkerAfter.Ended -and $report.WorkerAfter.ExitObserved){$report.Outcome='OWN_PROCESS_ENDS_CONFIRMED'}
}catch{
    $exception=$_.Exception;$chain=[Collections.Generic.List[object]]::new()
    while($null -ne $exception -and $chain.Count -lt 4){
        $chain.Add([pscustomobject]@{Type=$exception.GetType().FullName;HResult=$exception.HResult;
            NativeError=$(if($exception -is [ComponentModel.Win32Exception]){$exception.NativeErrorCode}else{$null});
            Code=$(if($exception.Message -cin @('CLEANUP_OWNER_OPEN','CLEANUP_WORKER_OPEN','CLEANUP_IDENTITY_MISMATCH','CLEANUP_TARGET_IDENTITY_UNAVAILABLE')){$exception.Message}else{$null})})
        $exception=$exception.InnerException
    }
    $report.Failure=[pscustomobject]@{Phase=$phase;Chain=$chain.ToArray();RawDetailsOmitted=$true}
}finally{
    if($null -ne ('MangaLib.ExistingWorkerCleanup.Native' -as [type])){
        if($null -ne $worker){$report.WorkerHandleClosed=[MangaLib.ExistingWorkerCleanup.Native]::CloseOwn($worker)}
        if($null -ne $owner){$report.OwnerHandleClosed=[MangaLib.ExistingWorkerCleanup.Native]::CloseOwn($owner)}
    }
    if(($null -ne $owner -and -not $report.OwnerHandleClosed) -or ($null -ne $worker -and -not $report.WorkerHandleClosed)){$report.Outcome='PARTIAL/STOP'}
    Write-CleanupNew (Join-Path $evidence 'result.json') $report
}
$report
