param([switch]$ValidateOnly,[switch]$ApplyRelease)
$ErrorActionPreference='Stop'
if($ValidateOnly -eq $ApplyRelease){throw 'RELEASE_EXPLICIT_MODE_REQUIRED'}

# Einmalige administrative Ausnahme für das ausdrücklich genehmigte Ticket.
# Keine Änderung an WorkerJournal, keine Runtime-Recovery und keine Prozess-API.
function Assert-ReleasePath {
    param([string]$Path,[string]$Root)
    $rootPath=[IO.Path]::GetFullPath($Root);$full=[IO.Path]::GetFullPath($Path)
    if(-not $full.StartsWith($rootPath+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'RELEASE_PATH_SCOPE'}
    $p=$full
    while(-not (Test-Path -LiteralPath $p)){$p=[IO.Path]::GetDirectoryName($p);if([string]::IsNullOrEmpty($p)){throw 'RELEASE_PATH'}}
    $f=Get-Item -LiteralPath $p -Force
    while($null -ne $f){if(($f.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'RELEASE_REPARSE'};$f=$(if($f -is [IO.FileInfo]){$f.Directory}else{$f.Parent})}
}
function Assert-ReleaseFacts {
    param($Attempt,$Cleanup,$Session)
    $ticket='cf728f97-7fbc-4ec6-8220-a27d9734f078';$id='b6f62923-d20b-4873-ac12-154d2bd1d3ed'
    if($Attempt.AttemptId -cne $id -or $Attempt.Outcome -cne 'PARTIAL/STOP' -or $Attempt.Job.Ticket -cne $ticket -or
        $Attempt.Job.State -cne 'QUARANTINED' -or $Cleanup.AttemptId -cne $id -or $Cleanup.Outcome -cne 'OWN_PROCESS_ENDS_CONFIRMED' -or
        $Cleanup.OwnerIdentity.Pid -ne 15044 -or $Cleanup.OwnerIdentity.Birth -ne 134378485858303646 -or
        $Cleanup.OwnerIdentity.Image -ine 'C:\Program Files\Java\jdk-21\bin\java.exe' -or
        $Cleanup.WorkerIdentity.Pid -ne 42196 -or $Cleanup.WorkerIdentity.Birth -ne 134378485914679271){throw 'RELEASE_TICKET_BINDING'}
    foreach($flag in @($Cleanup.OwnerAfter.Ended,$Cleanup.OwnerAfter.ExitObserved,$Cleanup.WorkerAfter.Ended,$Cleanup.WorkerAfter.ExitObserved,
            $Cleanup.OwnerHandleClosed,$Cleanup.WorkerHandleClosed,$Session.ToolReportedCompletion)){
        if($flag -isnot [bool] -or -not $flag){throw 'RELEASE_END_UNCONFIRMED'}
    }
    if($Cleanup.OwnerAfter.ExitCode -ne 1296826369 -or $Cleanup.WorkerAfter.ExitCode -ne 0 -or $Cleanup.QuarantineReleased -isnot [bool] -or
        $Cleanup.QuarantineReleased -or $null -ne $Cleanup.Failure -or $Session.AttemptId -cne $id -or $Session.ToolSession -ne 86300 -or
        $Session.Method -cne 'CTRL_C_ORIGINAL_TOOL_SESSION' -or $Session.ToolReportedExitCode -ne 1){throw 'RELEASE_CLEANUP_UNCONFIRMED'}
}
function Assert-ReleaseMarkers {
    param([string]$Slot)
    $files=@(Get-ChildItem -LiteralPath $Slot -Force)
    if($files.Count -ne 2 -or @($files|Where-Object {$_.PSIsContainer -or $_.Name -cnotin @('pending','quarantine')}).Count){throw 'RELEASE_UNKNOWN_SLOT_ENTRY'}
    foreach($entry in @(@('pending','5C345DC37876AC72CA9B9A64564F6FFB1F604E9DE3E1F3D7A18D7617591E086F'),
            @('quarantine','5BA83DC787A930AA23431B6B5C0948A9536777F0C04D6E89C0F4E6FA05286FFF'))){
        $p=Join-Path $Slot $entry[0]
        if((Get-FileHash -LiteralPath $p).Hash -cne $entry[1]){throw 'RELEASE_MARKER_BINDING'}
    }
}
function Write-ReleaseNew {
    param([string]$Path,[string]$Text)
    $b=[Text.UTF8Encoding]::new($false).GetBytes($Text)
    $f=[IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{$f.Write($b,0,$b.Length);$f.Flush($true)}finally{$f.Dispose()}
    if((Get-FileHash -LiteralPath $Path).Hash -cne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($b))){throw 'RELEASE_WRITE'}
}
function Get-ReleaseContext {
    param([string]$Root)
    $rootPath=[IO.Path]::GetFullPath($Root);$ticket='cf728f97-7fbc-4ec6-8220-a27d9734f078'
    $release=Join-Path $rootPath ('.gradle/t02-worker-job-release/'+$ticket)
    Assert-ReleasePath $release $rootPath
    if(Test-Path -LiteralPath $release){throw 'RELEASE_ALREADY_USED'}
    if((Get-Content (Join-Path $rootPath '.git/HEAD') -Raw).Trim() -cne 'ref: refs/heads/codex/t02-security-hardening'){throw 'RELEASE_BRANCH'}
    $base=Join-Path $rootPath '.gradle/t02-worker-job-proof';Assert-ReleasePath $base $rootPath
    $entries=@(Get-ChildItem -LiteralPath $base -Force)
    if($entries.Count -ne 2 -or @($entries|Where-Object {-not $_.PSIsContainer -or $_.Name -cnotin @('import-slot','b6f62923-d20b-4873-ac12-154d2bd1d3ed')}).Count){throw 'RELEASE_OTHER_ATTEMPT'}
    $attempt=Join-Path $base 'b6f62923-d20b-4873-ac12-154d2bd1d3ed'
    $fixed=@(@(Get-ChildItem (Join-Path $attempt 'artifact-snapshot') -File -Recurse)+@(Get-ChildItem -LiteralPath $attempt -File)|Sort-Object FullName)
    foreach($f in $fixed){Assert-ReleasePath $f.FullName $rootPath}
    $lines=@($fixed|ForEach-Object {$_.FullName+'|'+$_.Length+'|'+(Get-FileHash -LiteralPath $_.FullName).Hash})
    if($fixed.Count -ne 163 -or [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))) -cne
        '727ABF21ABB3F218BFC7080C36190961521685A916F41F987CABDD7790B7D09B'){throw 'RELEASE_ATTEMPT_CHANGED'}
    $cleanup=Join-Path $rootPath '.gradle/t02-worker-job-cleanup/7ac770d5-b743-4d1c-b7cc-bdd957bed8f6'
    foreach($item in @(@((Join-Path $cleanup 'result.json'),'9C1069F361D51A6AEC9C096E369D5FA0BE4CA4238A4424EE686E1B898DD07ED3'),
            @((Join-Path $cleanup 'original-session-close.json'),'58B1341071D2FCAA6A8D2E0BE1C8BAF3469BFB2C3969C64E0E262284E922EA8D'),
            @((Join-Path $rootPath 'build/libs/mangalib-manual-test.jar'),'9AE1B14C46F874A7C32DDB5BB6BDDC3D46559F1F69852FC85AAD7E82B904FAA9'))){
        Assert-ReleasePath $item[0] $rootPath;if((Get-FileHash -LiteralPath $item[0]).Hash -cne $item[1]){throw 'RELEASE_CLEANUP_CHANGED'}
    }
    $attemptFacts=[IO.File]::ReadAllText((Join-Path $attempt 'result.json'))|ConvertFrom-Json
    $cleanupFacts=[IO.File]::ReadAllText((Join-Path $cleanup 'result.json'))|ConvertFrom-Json
    $sessionFacts=[IO.File]::ReadAllText((Join-Path $cleanup 'original-session-close.json'))|ConvertFrom-Json
    Assert-ReleaseFacts $attemptFacts $cleanupFacts $sessionFacts
    $slot=Join-Path $base 'import-slot';Assert-ReleasePath $slot $rootPath;Assert-ReleaseMarkers $slot
    [pscustomobject]@{Root=$rootPath;Ticket=$ticket;Slot=$slot;Release=$release;Archive=(Join-Path $release 'markers');
        ScriptHash=(Get-FileHash -LiteralPath $PSCommandPath).Hash.ToLowerInvariant()}
}
function Invoke-TicketRelease {
    param($Context,[scriptblock]$MoveDirectory={param($from,$to);[IO.Directory]::Move($from,$to)})
    $c=$Context;$ticket='cf728f97-7fbc-4ec6-8220-a27d9734f078'
    if($c.Ticket -cne $ticket -or $c.Slot -cne (Join-Path $c.Root '.gradle/t02-worker-job-proof/import-slot') -or
        $c.Release -cne (Join-Path $c.Root ('.gradle/t02-worker-job-release/'+$ticket)) -or $c.Archive -cne (Join-Path $c.Release 'markers')){throw 'RELEASE_SCOPE'}
    Assert-ReleasePath $c.Slot $c.Root;Assert-ReleasePath $c.Release $c.Root
    if(Test-Path -LiteralPath $c.Release){throw 'RELEASE_ALREADY_USED'}
    if([IO.Path]::GetPathRoot($c.Slot) -ine [IO.Path]::GetPathRoot($c.Archive)){throw 'RELEASE_CROSS_VOLUME'}
    Assert-ReleaseMarkers $c.Slot
    $leases=[Collections.Generic.List[object]]::new();$claimed=$false;$moved=$false
    try{
        # Während der Belegvorbereitung Byteänderungen sperren. Windows verweigert
        # das Verzeichnis-Rename auch bei SHARE_DELETE, solange Kinder offen sind.
        # Vor dem EINEN Rename schließen und beide Marker nochmals prüfen. Andere
        # reguläre Admission bleibt bis dahin durch die Quarantäne ausgeschlossen.
        foreach($name in @('pending','quarantine')){$leases.Add([IO.FileStream]::new((Join-Path $c.Slot $name),[IO.FileMode]::Open,[IO.FileAccess]::Read,
            ([IO.FileShare]::Read -bor [IO.FileShare]::Delete)))}
        Assert-ReleaseMarkers $c.Slot
        [void][IO.Directory]::CreateDirectory($c.Release)
        Write-ReleaseNew (Join-Path $c.Release 'consumed') ("ADMIN_TICKET_RELEASE_V1`n"+$ticket+"`nb6f62923-d20b-4873-ac12-154d2bd1d3ed`n")
        $claimed=$true
        $decision=[pscustomobject]@{Ticket=$ticket;AttemptId='b6f62923-d20b-4873-ac12-154d2bd1d3ed';ExplicitProjectOwnerDecision=$true;
            HistoricalOutcome='PARTIAL/STOP';OriginalJobEmpty='NOT_RECONSTRUCTABLE';OriginalEofDisposes='NOT_RECONSTRUCTABLE';
            Basis='Confirmed native Owner/Worker ends and original tool-session completion';AutomaticRecovery=$false;
            CleanupReportHash='9c1069f361d51a6aec9c096e369d5fa0be4ca4238a4424ee686e1b898dd07ed3';
            SessionReceiptHash='58b1341071d2fcaa6a8d2e0be1c8baf3469bfb2c3969c64e0e262284e922ea8d';
            PendingHash='5c345dc37876ac72ca9b9a64564f6ffb1f604e9de3e1f3d7a18d7617591e086f';
            QuarantineHash='5ba83dc787a930aa23431b6b5c0948a9536777f0c04d6e89c0f4e6fa05286fff';ScriptHash=$c.ScriptHash;
            TimestampUtc=[DateTime]::UtcNow.ToString('O');SourceSlot=$c.Slot;Archive=$c.Archive}
        Write-ReleaseNew (Join-Path $c.Release 'decision.json') ($decision|ConvertTo-Json -Depth 5)
        foreach($lease in $leases){$lease.Dispose()};$leases.Clear()
        Assert-ReleaseMarkers $c.Slot
        & $MoveDirectory $c.Slot $c.Archive;$moved=$true
        Assert-ReleaseMarkers $c.Archive
        $sourceAbsent=-not (Test-Path -LiteralPath $c.Slot)
        $result=[pscustomobject]@{Outcome='ADMINISTRATIVELY_RELEASED';Ticket=$ticket;HistoricalOutcome='PARTIAL/STOP';
            MarkerPairArchived=$true;SourceSlotAbsent=$sourceAbsent;Archive=$c.Archive;OriginalJobPass=$false;
            NewNativeExecutionApproved=$false;UnknownHistoricalEvidenceAcceptedByOwner=$true;TimestampUtc=[DateTime]::UtcNow.ToString('O')}
        if(-not $sourceAbsent){$result.Outcome='ADMIN_RELEASED_SOURCE_CHANGED_STOP'}
        Write-ReleaseNew (Join-Path $c.Release 'result.json') ($result|ConvertTo-Json -Depth 5)
        $result
    }catch{
        if($claimed){
            $failure=[pscustomobject]@{Outcome='BLOCKED';Ticket=$ticket;DirectoryMoved=$moved;HistoricalOutcome='PARTIAL/STOP';AutomaticRetry=$false;
                Type=$_.Exception.GetType().FullName;HResult=$_.Exception.HResult;RawDetailsOmitted=$true}
            Write-ReleaseNew (Join-Path $c.Release 'failure.json') ($failure|ConvertTo-Json)
        }
        throw 'RELEASE_STOP_NO_RETRY'
    }finally{foreach($lease in $leases){$lease.Dispose()}}
}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$context=Get-ReleaseContext $root
if($ValidateOnly){[pscustomobject]@{Outcome='READY_FOR_ADMIN_RELEASE';Ticket=$context.Ticket;HistoricalOutcome='PARTIAL/STOP';FilesChanged=0;NativeCalls=$false};return}
Invoke-TicketRelease $context
