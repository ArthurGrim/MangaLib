$ErrorActionPreference='Stop'
$file=Join-Path $PSScriptRoot 'Start-WorkerJobProof.ps1';$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'JOB_PROOF_PARSE'}
$script:cases=0
function Check-Job([bool]$ok){if(-not $ok){throw 'JOB_PROOF_CHECK'}}
function Case-Job([string]$name,[scriptblock]$test){try{& $test;$script:cases++}catch{throw ('JOB_PROOF_FAILED: '+$name)}}
function Get-JobFunction([string]$name){
    $node=$ast.Find({param($n);$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    $parameters=($node.Parameters|ForEach-Object {$_.Extent.Text}) -join ','
    $body=$node.Body.Extent.Text
    [scriptblock]::Create('param('+$parameters+")`n"+$body.Substring(1,$body.Length-2))
}
${function:Assert-JobPath}=Get-JobFunction 'Assert-JobPath'
${function:Write-JobNew}=Get-JobFunction 'Write-JobNew'
${function:Get-JobFingerprint}=Get-JobFunction 'Get-JobFingerprint'
Case-Job 'entry-requires-explicit-mode-without-native-loading' {
    $caught=$false;try{& $file}catch{$caught=$_.Exception.Message -ceq 'JOB_EXPLICIT_MODE'};Check-Job $caught
    Check-Job ($null -eq ('MangaLib.OwnerProbe.InspectionV1' -as [type]))
    $starts=@($ast.FindAll({param($n);$n -is [Management.Automation.Language.InvokeMemberExpressionAst] -and $n.Member.Value -ceq 'Start'},$true))
    Check-Job ($starts.Count -eq 1)
    Check-Job (-not ([IO.File]::ReadAllText($file) -match 'Register-Cim|Get-CimInstance|Stop-Process|taskkill|GetProcessById|\.Kill\('))
}
Case-Job 'actual-new-file-port-never-overwrites-consumption' {
    $dir=Join-Path ([IO.Path]::GetTempPath()) ('MangaLib-Job-Proof-Fake-'+[guid]::NewGuid())
    [void][IO.Directory]::CreateDirectory($dir);$p=Join-Path $dir 'consumed'
    try{Write-JobNew $p "OWN`n";$caught=$false;try{Write-JobNew $p "OTHER`n"}catch{$caught=$true}
        Check-Job ($caught -and [IO.File]::ReadAllText($p) -ceq "OWN`n")
    }finally{[IO.File]::Delete($p);[IO.Directory]::Delete($dir)}
}
$assignment=$ast.Find({param($n);$n -is [Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -ceq '$pass'},$true)
$accept=[scriptblock]::Create($assignment.Right.Extent.Text)
foreach($fault in @('valid','multiple','job','outcome','state','pid','birth','exit','group','resources','channel','total','factories','journal','ticket','zero-ticket',
    'owner-end','owner-endedflag','owner-exit','stdout','stderr','streams','handle','deadline','diagnostic')){
    Case-Job ('actual-acceptance-'+$fault) {
        $diagnostic=$null;$job=[pscustomobject]@{Outcome='JOB_PROOF_PASS';State='IDLE';WorkerPid='51';WorkerBirth='134378472263984761';WorkerExit='0';
            GroupEnded='true';ResourcesClosed='true';ChannelEnded='true';AssignedTotal='1';Factories='1';JournalClear='true';Ticket=[guid]::NewGuid().ToString()}
        $ended=[pscustomobject]@{Ended=$true;ExitObserved=$true;ExitCode=0};$outEof=$true;$errEof=$true;$streamsClosed=$true;$handleClosed=$true
        $clock=[pscustomobject]@{ElapsedMilliseconds=10};$deadline=45000
        switch($fault){
            'multiple'{$job.AssignedTotal='2'}
            'job'{$job=$null};'outcome'{$job.Outcome='PARTIAL/STOP'};'state'{$job.State='QUARANTINED'};'pid'{$job.WorkerPid='0'};'birth'{$job.WorkerBirth='0'};
            'exit'{$job.WorkerExit='259'};'group'{$job.GroupEnded='false'};'resources'{$job.ResourcesClosed='false'};'channel'{$job.ChannelEnded='false'};
            'total'{$job.AssignedTotal='0'};'factories'{$job.Factories='2'};'journal'{$job.JournalClear='false'};'ticket'{$job.Ticket='bad'};
            'zero-ticket'{$job.Ticket='00000000-0000-0000-0000-000000000000'};'owner-end'{$ended.ExitObserved=$false};'owner-endedflag'{$ended.Ended=$false};'owner-exit'{$ended.ExitCode=78};
            'stdout'{$outEof=$false};'stderr'{$errEof=$false};'streams'{$streamsClosed=$false};'handle'{$handleClosed=$false};
            'deadline'{$clock.ElapsedMilliseconds=45000};'diagnostic'{$diagnostic=[pscustomobject]@{Phase='TEST'}}
        }
        Check-Job ((& $accept) -eq ($fault -cin @('valid','multiple')))
    }
}
Case-Job 'shared-persistent-slot-is-checked-without-reset' {
    $source=[IO.File]::ReadAllText($file)
    Check-Job ($source.Contains(".gradle/t02-worker-job-proof/import-slot") -and $source.Contains('JOB_PRIOR_IMPORT_UNCONFIRMED'))
    Check-Job (-not ($source -match 'Remove-Item|\.Delete\(|Clear-Content'))
}
foreach($endCase in @('unobserved','observed','absent')){
    Case-Job ('actual-report-exit-tristate-'+$endCase) {
        $assignment=$ast.Find({param($n);$n -is [Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -ceq '$report'},$true)
        $makeReport=[scriptblock]::Create($assignment.Right.Extent.Text)
        $ended=$(if($endCase -ceq 'absent'){$null}else{[pscustomobject]@{Ended=($endCase -ceq 'observed');ExitObserved=($endCase -ceq 'observed');ExitCode=0}})
        $clock=[pscustomobject]@{ElapsedMilliseconds=1};$pass=$false;$job=$null;$script:WorkerJobRetained=$null
        $r=& $makeReport
        if($endCase -ceq 'absent'){Check-Job ($null -eq $r.OwnerEnd)}
        elseif($endCase -ceq 'observed'){Check-Job ($r.OwnerEnd.ExitObserved -and $r.OwnerEnd.ExitCode -eq 0)}
        else{Check-Job (-not $r.OwnerEnd.ExitObserved -and $null -eq $r.OwnerEnd.ExitCode)}
        Check-Job ($r.Outcome -ceq 'PARTIAL/STOP' -and $r.ResourcesUnconfirmed)
    }
}
foreach($marker in @('clear','pending','quarantine')){
    Case-Job ('actual-slot-guard-'+$marker) {
        $node=$ast.Find({param($n);$n -is [Management.Automation.Language.IfStatementAst] -and $n.Extent.Text.StartsWith('if(Test-Path -LiteralPath $slot)')},$true)
        $guard=[scriptblock]::Create($node.Extent.Text)
        $slot=Join-Path ([IO.Path]::GetTempPath()) ('MangaLib-Job-Slot-Fake-'+[guid]::NewGuid());[void][IO.Directory]::CreateDirectory($slot)
        $p=Join-Path $slot $marker
        try{
            if($marker -cne 'clear'){Write-JobNew $p 'SYNTHETIC';$before=(Get-FileHash $p).Hash}
            $caught=$false;try{& $guard}catch{$caught=$_.Exception.Message -ceq 'JOB_PRIOR_IMPORT_UNCONFIRMED'}
            Check-Job ($caught -eq ($marker -cne 'clear'))
            if($marker -cne 'clear'){Check-Job ((Get-FileHash $p).Hash -ceq $before)}
        }finally{if($marker -cne 'clear'){[IO.File]::Delete($p)};[IO.Directory]::Delete($slot)}
    }
}
"Worker job proof: $script:cases fake/static cases passed; no native/process invocation."
