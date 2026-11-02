$ErrorActionPreference='Stop'
$file=Join-Path $PSScriptRoot 'Release-ExistingWorkerQuarantine.ps1';$t=$null;$e=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$t,[ref]$e)
if($e.Count){throw 'RELEASE_PARSE_TEST'}
foreach($node in $ast.FindAll({param($n);$n -is [Management.Automation.Language.FunctionDefinitionAst]},$false)){
    Set-Item -LiteralPath ('Function:'+ $node.Name) -Value $node.Body.GetScriptBlock()
}
$script:cases=0
function Check-Release([bool]$ok){if(-not $ok){throw 'RELEASE_TEST_ASSERTION'}}
function Case-Release([string]$name,[scriptblock]$test){try{& $test;$script:cases++}catch{throw ('RELEASE_TEST_FAILED: '+$name+' / '+$_.Exception.GetType().Name)}}
function New-ReleaseFake {
    $workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
    $root=Join-Path $workspace ('.gradle/quarantine-release-fakes/'+[guid]::NewGuid())
    $ticket='cf728f97-7fbc-4ec6-8220-a27d9734f078';$slot=Join-Path $root '.gradle/t02-worker-job-proof/import-slot'
    [void][IO.Directory]::CreateDirectory($slot)
    Write-ReleaseNew (Join-Path $slot 'pending') $ticket;Write-ReleaseNew (Join-Path $slot 'quarantine') 'UNCONFIRMED'
    $release=Join-Path $root ('.gradle/t02-worker-job-release/'+$ticket)
    [pscustomobject]@{Root=$root;Ticket=$ticket;Slot=$slot;Release=$release;Archive=(Join-Path $release 'markers')}
}
function New-ReleaseFacts {
    $id='b6f62923-d20b-4873-ac12-154d2bd1d3ed'
    [pscustomobject]@{
        Attempt=[pscustomobject]@{AttemptId=$id;Outcome='PARTIAL/STOP';Job=[pscustomobject]@{Ticket='cf728f97-7fbc-4ec6-8220-a27d9734f078';State='QUARANTINED'}}
        Cleanup=[pscustomobject]@{AttemptId=$id;Outcome='OWN_PROCESS_ENDS_CONFIRMED';OwnerIdentity=[pscustomobject]@{Pid=15044;Birth=134378485858303646L;Image='C:\Program Files\Java\jdk-21\bin\java.exe'};
            WorkerIdentity=[pscustomobject]@{Pid=42196;Birth=134378485914679271L};OwnerAfter=[pscustomobject]@{Ended=$true;ExitObserved=$true;ExitCode=1296826369};
            WorkerAfter=[pscustomobject]@{Ended=$true;ExitObserved=$true;ExitCode=0};OwnerHandleClosed=$true;WorkerHandleClosed=$true;QuarantineReleased=$false;Failure=$null}
        Session=[pscustomobject]@{AttemptId=$id;ToolSession=86300;Method='CTRL_C_ORIGINAL_TOOL_SESSION';ToolReportedCompletion=$true;ToolReportedExitCode=1}
    }
}
foreach($case in @('valid','ticket','attempt','historical-pass','state','owner-birth','owner-pid','owner-image','worker-birth','worker-pid','owner-end','worker-end',
        'exit-unknown','owner-exit','worker-exit','owner-handle','worker-handle','session-id','session-ended','session-exit','released','failure','false-as-text')){
    Case-Release ('facts-'+$case) {
        $f=New-ReleaseFacts
        switch($case){ticket{$f.Attempt.Job.Ticket=[guid]::NewGuid().ToString()};attempt{$f.Attempt.AttemptId='other'};'historical-pass'{$f.Attempt.Outcome='PASS'};
            state{$f.Attempt.Job.State='IDLE'};'owner-birth'{$f.Cleanup.OwnerIdentity.Birth++};'owner-pid'{$f.Cleanup.OwnerIdentity.Pid++};
            'owner-image'{$f.Cleanup.OwnerIdentity.Image='C:\foreign.exe'};'worker-birth'{$f.Cleanup.WorkerIdentity.Birth++};'worker-pid'{$f.Cleanup.WorkerIdentity.Pid++};
            'owner-end'{$f.Cleanup.OwnerAfter.Ended=$false};'worker-end'{$f.Cleanup.WorkerAfter.Ended=$false};'exit-unknown'{$f.Cleanup.WorkerAfter.ExitObserved=$false};
            'owner-exit'{$f.Cleanup.OwnerAfter.ExitCode=0};'worker-exit'{$f.Cleanup.WorkerAfter.ExitCode=1};'owner-handle'{$f.Cleanup.OwnerHandleClosed=$false};
            'worker-handle'{$f.Cleanup.WorkerHandleClosed=$false};'session-id'{$f.Session.ToolSession++};'session-ended'{$f.Session.ToolReportedCompletion=$false};
            'session-exit'{$f.Session.ToolReportedExitCode=0};released{$f.Cleanup.QuarantineReleased=$true};failure{$f.Cleanup.Failure='SYNTHETIC'};
            'false-as-text'{$f.Cleanup.WorkerAfter.Ended='false'}
        }
        $ok=$true;try{Assert-ReleaseFacts $f.Attempt $f.Cleanup $f.Session}catch{$ok=$false}
        Check-Release ($ok -eq ($case -ceq 'valid'))
    }
}
Case-Release 'directory-rename-preserves-both-marker-bytes-and-permanent-consumption' {
    $c=New-ReleaseFake;$r=Invoke-TicketRelease $c
    Check-Release ($r.Outcome -ceq 'ADMINISTRATIVELY_RELEASED' -and -not $r.OriginalJobPass -and -not $r.NewNativeExecutionApproved)
    Check-Release (-not (Test-Path -LiteralPath $c.Slot));Assert-ReleaseMarkers $c.Archive
    $caught=$false;try{Invoke-TicketRelease $c|Out-Null}catch{$caught=$true};Check-Release $caught
    Check-Release (Test-Path (Join-Path $c.Release 'consumed'))
}
foreach($case in @('wrong-ticket','changed-pending','changed-quarantine','extra-file','extra-directory','wrong-slot','wrong-archive')){
    Case-Release ('preflight-'+$case) {
        $c=New-ReleaseFake
        switch($case){'wrong-ticket'{$c.Ticket='other'};'changed-pending'{[IO.File]::WriteAllText((Join-Path $c.Slot 'pending'),'other')};
            'changed-quarantine'{[IO.File]::WriteAllText((Join-Path $c.Slot 'quarantine'),'other')};
            'extra-file'{[IO.File]::WriteAllText((Join-Path $c.Slot 'other-ticket'),'SYNTHETIC')};
            'extra-directory'{[void][IO.Directory]::CreateDirectory((Join-Path $c.Slot 'other'))};
            'wrong-slot'{$c.Slot=Join-Path $c.Root 'other'};'wrong-archive'{$c.Archive=Join-Path $c.Root 'other'}
        }
        $caught=$false;try{Invoke-TicketRelease $c|Out-Null}catch{$caught=$true};Check-Release $caught
        Check-Release (-not (Test-Path -LiteralPath $c.Release))
    }
}
Case-Release 'failed-move-keeps-marker-pair-and-refuses-retry' {
    $c=New-ReleaseFake;$caught=$false;try{Invoke-TicketRelease $c -MoveDirectory {throw 'SYNTHETIC_MOVE_FAILURE'}|Out-Null}catch{$caught=$true}
    Check-Release $caught;Assert-ReleaseMarkers $c.Slot
    Check-Release (Test-Path (Join-Path $c.Release 'consumed'))
    $caught=$false;try{Invoke-TicketRelease $c|Out-Null}catch{$caught=$true};Check-Release $caught
    Assert-ReleaseMarkers $c.Slot
}
Case-Release 'consumed-exception-cannot-release-a-subsequent-ticket' {
    $c=New-ReleaseFake;Invoke-TicketRelease $c|Out-Null
    [void][IO.Directory]::CreateDirectory($c.Slot);$other=[guid]::NewGuid().ToString()
    Write-ReleaseNew (Join-Path $c.Slot 'pending') $other;Write-ReleaseNew (Join-Path $c.Slot 'quarantine') 'UNCONFIRMED'
    $caught=$false;try{Invoke-TicketRelease $c|Out-Null}catch{$caught=$true};Check-Release $caught
    Check-Release ([IO.File]::ReadAllText((Join-Path $c.Slot 'pending')) -ceq $other)
    Assert-ReleaseMarkers $c.Archive
}
Case-Release 'path-escape-is-rejected-before-moving-anything' {
    $c=New-ReleaseFake;$caught=$false;try{Assert-ReleasePath ([IO.Path]::GetTempPath()) $c.Root}catch{$caught=$true};Check-Release $caught
    Assert-ReleaseMarkers $c.Slot
}
Case-Release 'entry-is-cold-without-explicit-mode-and-has-no-process-api' {
    $caught=$false;try{& $file}catch{$caught=$_.Exception.Message -ceq 'RELEASE_EXPLICIT_MODE_REQUIRED'};Check-Release $caught
    Check-Release (-not ([IO.File]::ReadAllText($file) -match 'Add-Type|DllImport|OpenProcess|Stop-Process|Start-Process|taskkill|\.Kill\(|TerminateProcess'))
}
"Worker quarantine release: $script:cases synthetic filesystem/decision cases passed; no process, job or database operation."
