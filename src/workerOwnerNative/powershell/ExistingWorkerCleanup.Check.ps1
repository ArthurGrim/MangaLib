$ErrorActionPreference='Stop';$count=0
$file=Join-Path $PSScriptRoot 'Invoke-ExistingWorkerCleanup.ps1';$t=$null;$e=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$t,[ref]$e)
if($e.Count){throw 'CLEANUP_PARSE_TEST'}
function Check-Cleanup([bool]$ok){if(-not $ok){throw 'CLEANUP_TEST_ASSERTION'}}
function Cleanup-Function([string]$name){
    $n=$ast.Find({param($n);$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    $p=($n.Parameters|ForEach-Object {$_.Extent.Text}) -join ',';$b=$n.Body.Extent.Text
    [scriptblock]::Create('param('+$p+")`n"+$b.Substring(1,$b.Length-2))
}
${function:Assert-CleanupIdentity}=Cleanup-Function 'Assert-CleanupIdentity'
${function:Assert-WorkerCleanupIdentity}=Cleanup-Function 'Assert-WorkerCleanupIdentity'
${function:Assert-NoRecordedCleanup}=Cleanup-Function 'Assert-NoRecordedCleanup'
foreach($case in @('valid','pid','birth','image','missing')){
    $i=[pscustomobject]@{Pid=15044;Birth=134378485858303646L;Image='C:\Program Files\Java\jdk-21\bin\java.exe'}
    switch($case){pid{$i.Pid++};birth{$i.Birth++};image{$i.Image='C:\foreign.exe'};missing{$i=$null}}
    $ok=$true;try{Assert-CleanupIdentity $i 15044 134378485858303646}catch{$ok=$false}
    Check-Cleanup ($ok -eq ($case -ceq 'valid'));$count++
}
foreach($case in @('alive-valid','ended-image-known','ended-image-unavailable','alive-image-unavailable','pid','birth','wrong-image','unconfirmed-image','missing')){
    $i=[pscustomobject]@{Pid=42196;Birth=134378485914679271L;Image='C:\Program Files\Java\jdk-21\bin\java.exe';Ended=$true;ImageUnavailableOnEnded=$false}
    switch($case){'alive-valid'{$i.Ended=$false};'ended-image-unavailable'{$i.Image=$null;$i.ImageUnavailableOnEnded=$true};
        'alive-image-unavailable'{$i.Image=$null;$i.ImageUnavailableOnEnded=$true;$i.Ended=$false};pid{$i.Pid++};birth{$i.Birth++};
        'wrong-image'{$i.Image='C:\foreign.exe'};'unconfirmed-image'{$i.Image=$null};missing{$i=$null}}
    $ok=$true;try{Assert-WorkerCleanupIdentity $i}catch{$ok=$false}
    Check-Cleanup ($ok -eq ($case -cin @('alive-valid','ended-image-known','ended-image-unavailable')));$count++
}
foreach($case in @('empty','prior-inspection','other-attempt','already-forced')){
    $records=$(if($case -ceq 'empty'){@()}else{@([pscustomobject]@{AttemptId=$(if($case -ceq 'other-attempt'){'other'}else{'b6f62923-d20b-4873-ac12-154d2bd1d3ed'});ForcedOwnerTermination=($case -cne 'prior-inspection')})})
    $ok=$true;try{Assert-NoRecordedCleanup $records}catch{$ok=$false}
    Check-Cleanup ($ok -eq ($case -cne 'already-forced'));$count++
}
$caught=$false;try{& $file}catch{$caught=$_.Exception.Message -ceq 'CLEANUP_EXPLICIT_MODE_REQUIRED'}
Check-Cleanup $caught;$count++
Check-Cleanup ($null -eq ('MangaLib.ExistingWorkerCleanup.Native' -as [type]))
. (Join-Path $PSScriptRoot '../../ownerProbe/powershell/OwnerNativeInspection.ps1')
$n=$ast.Find({param($n);$n -is [Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -ceq '$cleanupNative'},$true)
Add-Type -TypeDefinition ($script:OwnerNativeDeclaration+"`n"+$n.Right.Expression.Value)
$type='MangaLib.ExistingWorkerCleanup.Native' -as [type]
Check-Cleanup ($null -ne $type -and $null -ne $type.GetMethod('StopExactOwner'));$count++
Check-Cleanup (-not ([IO.File]::ReadAllText($file) -match 'Stop-Process|taskkill|Process\]::Start|Start-Process|CreateJobObject|TerminateJobObject|Remove-Item'))
$count++
"Existing worker cleanup: $count fake/static/compile cases passed; no native method invoked."
