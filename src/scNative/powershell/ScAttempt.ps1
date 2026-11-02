# Isolierter Einmalharness. Dot-sourcing definiert nur Funktionen, startet nichts.
. (Join-Path $PSScriptRoot 'ScOwnedObservation.ps1')
. (Join-Path $PSScriptRoot 'ScOuterControl.ps1')

function Get-ScAttemptPath([string] $Root, [string] $AttemptId) {
    $id = [guid]::Empty
    if (-not [guid]::TryParseExact($AttemptId, 'D', [ref] $id) -or
        $id -eq [guid]::Empty -or $id.ToString() -cne $AttemptId) { throw 'SC_ATTEMPT_ID' }
    Join-Path ([IO.Path]::GetFullPath($Root)) ('.gradle/t02-sc-attempts/' + $AttemptId)
}
function Write-ScNewDurable([string] $Path, [string] $Text) {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    if ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) -cne
        [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($Path)))) { throw 'SC_DURABILITY' }
}
function Assert-ScCanonical([string] $Path) {
    # Auch Verzeichnis-Junctions/Ancestor-Reparsepoints ablehnen, kein freier Umlenkpfad.
    $current = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    while ($null -ne $current) {
        if (($current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'SC_REPARSE' }
        $current = $(if ($current -is [IO.FileInfo]) { $current.Directory } else { $current.Parent })
        if ($null -eq $current) { break }
    }
}
function Use-ScAttemptApproval($Context, [string] $ActualHash) {
    $directory = Get-ScAttemptPath $Context.Root $Context.AttemptId
    if ($directory -cne $Context.Directory -or $ActualHash -cnotmatch '^[a-f0-9]{64}$' -or
        $Context.ArtifactHash -cne $ActualHash -or $Context.ApprovalHash -cnotmatch '^[a-f0-9]{64}$') { throw 'SC_ARTIFACT' }
    Assert-ScCanonical $directory
    $approval = Join-Path $directory 'approval.txt'
    Assert-ScCanonical $approval
    $file = Get-Item -LiteralPath $approval
    if ($file.PSIsContainer -or $file.Length -gt 128 -or
        (Get-FileHash -LiteralPath $approval).Hash.ToLowerInvariant() -cne $Context.ApprovalHash) { throw 'SC_APPROVAL' }
    $lines = [IO.File]::ReadAllText($approval).Trim().Split([string[]]@("`r`n", "`n"), [StringSplitOptions]::None)
    if ($lines.Length -ne 2 -or $lines[0] -cne $Context.AttemptId -or $lines[1] -cne $ActualHash) { throw 'SC_APPROVAL' }
    if (Test-Path -LiteralPath (Join-Path $directory 'native')) { throw 'SC_NATIVE_ALREADY_PRESENT' }
    $marker = Join-Path $directory 'consumed'
    # CREATE_NEW ist die atomare Startsperre; auch Start-/Beobachterfehler verbrauchen sie.
    Write-ScNewDurable $marker ("SC_CONSUMED_V2`n" + $Context.AttemptId + "`n" + $ActualHash + "`n" + $Context.ApprovalHash + "`n")
    (Get-FileHash -LiteralPath $marker).Hash.ToLowerInvariant()
}
function Get-ScProcessFinding($Child, $Supervisor, $HeldChild = $null) {
    $kind = 'UNEXPECTED_PROCESS'
    $owned = $false
    $identityKnown = $null -ne $Child -and $null -ne $Supervisor -and $Supervisor.Held -eq $true -and
        $Child.ProcessId -gt 0 -and $Child.ProcessId -ne $Supervisor.ProcessId -and
        $Child.ParentProcessId -eq $Supervisor.ProcessId -and
        $null -ne $Child.Birth -and [long] $Child.Birth -ge [long] $Supervisor.Birth -and
        -not [string]::IsNullOrWhiteSpace($Child.Image)
    if ($identityKnown -and $Child.Image -ieq 'C:\Windows\System32\conhost.exe') {
        $kind = 'POSSIBLE_CONSOLE_COMPANION_UNPROVEN'
    } elseif ($identityKnown -and $Child.Image -ieq $Supervisor.Image -and $Child.CustodianCommandMatches -eq $true) {
        $kind = 'CUSTODIAN_CANDIDATE'
        if ($null -ne $HeldChild -and [string] $Child.ProcessId -ceq [string] $HeldChild.ProcessId -and
            [string] $Child.Birth -ceq [string] $HeldChild.Birth -and $Child.Image -ieq $HeldChild.Image) {
            $kind = 'HELD_CUSTODIAN_CORRELATED'; $owned = $true
        }
    }
    [pscustomobject]@{
        Kind = $kind; IdentityKnown = $identityKnown; Owned = $owned
        ProcessId = $Child.ProcessId; ParentProcessId = $Child.ParentProcessId
        Birth = [string] $Child.Birth; Image = [string] $Child.Image
        JobMembership = 'UNKNOWN'; ObservationOnly = $true
        RequiresStop = ($kind -notin @('CUSTODIAN_CANDIDATE', 'HELD_CUSTODIAN_CORRELATED'))
    }
}
function ConvertFrom-ScDiagnostics([string] $Text, [bool] $Eof) {
    # Keine Rohtexte zurückgeben. Auch nicht als Hex erkennbare Secrets verwerfen.
    $valid = $Text.Length -le 1024
    $lines = @()
    if ($valid -and $Text.Length -gt 0) {
        foreach ($line in $Text.TrimEnd("`r", "`n").Split("`n")) {
            $line = $line.TrimEnd("`r")
            if ($line.Length -gt 192 -or $line -cnotmatch '^SC_STOP_V1 role=SUPERVISOR phase=(ENTRY|MANIFEST|NATIVE_LOAD|INTENT|BOOTSTRAP|FINISH) category=(INCOMPLETE|LINKAGE|IO|BOOTSTRAP|CONFIGURATION|FAILURE) exit=78 end=UNCONFIRMED resources=UNCONFIRMED$') {
                $valid = $false; break
            }
            $lines += $line
        }
    }
    if (-not $valid) { $lines = @() }
    [pscustomobject]@{ Valid = $valid; Lines = $lines; Eof = $Eof; RawTextDiscarded = $true }
}
function Read-ScOwnerStreams($Reads, $Clock, [long] $Deadline) {
    if ($null -eq $Reads) { throw 'SC_READS_NOT_STARTED' }
    $text=''; $errEof=$false
    while ($Clock.ElapsedMilliseconds -lt $Deadline) {
        if ($Reads.ErrTask.IsFaulted -or $Reads.ErrTask.IsCanceled) { throw 'SC_DIAGNOSTICS_UNCONFIRMED' }
        if ($Reads.ErrTask.IsCompletedSuccessfully) {
            $count=[int] $Reads.ErrTask.Result
            if ($count -eq 0) { $errEof=$true; break }
            if ($count -lt 0 -or $count -gt $Reads.Err.Length) { throw 'SC_DIAGNOSTICS_COUNT' }
            if ($text.Length + $count -gt 1024) { $text='x' * 1025; break }
            $text += [string]::new($Reads.Err,0,$count)
            $Reads.ErrTask=$Reads.ErrorReader.ReadAsync($Reads.Err,0,$Reads.Err.Length)
        } else { [Threading.Thread]::Sleep(1) } # Kein neuer Prozess/Runspace.
    }
    $outEof=$Reads.OutTask.IsCompletedSuccessfully -and $Reads.OutTask.Result -eq 0
    [pscustomobject]@{StdoutEof=$outEof;StdoutEmpty=$outEof;StderrEof=$errEof;Stderr=$text}
}
function ConvertFrom-ScRunEvidence([string] $Text, [string] $AttemptId) {
    if ($Text.Length -gt 1024) { throw 'SC_EVIDENCE_SIZE' }
    $fields = @{}
    foreach ($line in $Text.TrimEnd("`n").Split("`n")) {
        $pair = $line.Split('=', 2)
        if ($pair.Length -ne 2 -or $fields.ContainsKey($pair[0])) { throw 'SC_EVIDENCE_FORMAT' }
        $fields[$pair[0]] = $pair[1]
    }
    $keys = @('profile','session','ready','complete','groupEnded','cResources','sResources','journalTerminal',
        'operationOutstanding','cEnd','cHandlesClosed','cExit','cPid','cBirth')
    if ($fields.Count -ne $keys.Count -or @($keys | Where-Object { -not $fields.ContainsKey($_) }).Count -ne 0 -or
        $fields.profile -cne 'SCN2' -or $fields.session -cne $AttemptId) { throw 'SC_EVIDENCE_BINDING' }
    foreach ($key in $keys[2..10]) { if ($fields[$key] -cnotin @('true','false')) { throw 'SC_EVIDENCE_BOOLEAN' } }
    if ($fields.cExit -cnotmatch '^(unknown|-?[0-9]{1,10})$' -or $fields.cPid -cnotmatch '^(unknown|[1-9][0-9]{0,9})$' -or
        $fields.cBirth -cnotmatch '^(unknown|[1-9][0-9]{0,18})$') { throw 'SC_EVIDENCE_IDENTITY' }
    $fields
}
function Get-ScAttemptOutcome($End, $Evidence, $Diagnostics, [bool] $StdoutEof, [bool] $Stop, [bool] $ObserverFailed, [bool] $ChildCorrelated, [bool] $OwnerClosed) {
    if (-not $End.HeldEndObserved -or -not $End.ExitCodeObserved -or $End.ObservationFailed -or
        $ObserverFailed -or $Stop -or -not $Diagnostics.Valid -or -not $OwnerClosed) { return 'PARTIAL/STOP' }
    if ($End.ExitCode -ne 0) { return 'FAIL/STOP' }
    $complete = $null -ne $Evidence -and $ChildCorrelated -and $StdoutEof -and $Diagnostics.Eof -and $Diagnostics.Lines.Count -eq 0
    foreach ($key in @('ready','complete','groupEnded','cResources','sResources','journalTerminal','cEnd','cHandlesClosed')) {
        $complete = $complete -and $null -ne $Evidence -and $Evidence[$key] -ceq 'true'
    }
    if ($complete -and $Evidence.operationOutstanding -ceq 'false' -and $Evidence.cExit -ceq '0') { return 'PASS' }
    'PARTIAL/STOP'
}
function Invoke-ScStopPort($Ports,$Context,$Identity,$Control,$Clock,[long]$Deadline) {
    try {
        $answer=& $Ports.StopControl $Context $Identity $Control $Clock $Deadline
        if($null -eq $answer -or -not $answer.Requested){throw 'SC_CONTROL_RESPONSE'}
        return $answer
    } catch {
        [pscustomobject]@{Requested=$true;Sent=$false;Acknowledged=$false;Effective=$false;Outstanding=$null;State='UNKNOWN/STOP'}
    }
}
function Invoke-ScAttemptCore($Context, $Ports, [switch] $ExecuteOnce, [switch] $RequireOuterControl) {
    if (-not $ExecuteOnce) { throw 'SC_EXPLICIT_APPROVAL_REQUIRED' }
    $markerHash = Use-ScAttemptApproval $Context (& $Ports.Fingerprint $Context)
    if ((& $Ports.Fingerprint $Context) -cne $Context.ArtifactHash) { throw 'SC_ARTIFACT_CHANGED_AFTER_CONSUMPTION' }
    $owner = $null; $identity = $null; $reads = $null; $end = $null
    $observerFailed = $false; $stop = $false; $closed = $false
    # Ohne Pflichtschalter nur historische injizierte Fakes; realer Launcher setzt ihn zwingend.
    $control=$null;$controlEnd=$false;$stopAttempted=$false
    $revocation=[pscustomobject]@{Requested=$false;Sent=$false;Acknowledged=$false;Effective=$false;Outstanding=$null;State='NOT_REQUESTED'}
    $seen = [Collections.Generic.Dictionary[string,object]]::new()
    $clock = & $Ports.Clock
    $deadline = 25000 # einmalige absolute Gesamtfrist, vor dem einzigen Start begonnen
    try {
        $owner = & $Ports.Start $Context # genau ein Aufruf, ausschließlich hinter CREATE_NEW
        if ($null -eq $owner) { throw 'SC_START_UNCONFIRMED' }
        $identity = & $Ports.Identity $owner
        if ($null -eq $identity -or $identity.ProcessId -le 0 -or $null -eq $identity.Birth -or
            $identity.Image -ine $Context.Java -or -not $identity.Held) { throw 'SC_OWNER_IDENTITY' }
        $reads = & $Ports.BeginReads $owner
        if($RequireOuterControl){$control=& $Ports.OpenControl $Context $identity $clock $deadline;if($null -eq $control -or -not $control.Bound){throw 'SC_CONTROL_UNCONFIRMED'}}
        while ($clock.ElapsedMilliseconds -lt $deadline) {
            foreach ($child in @(& $Ports.Children $owner $identity $Context)) {
                if ($null -eq $child) { continue }
                $finding = Get-ScProcessFinding $child $identity
                $key = [string] $finding.ProcessId + ':' + $finding.Birth
                if (-not $seen.ContainsKey($key)) { $seen.Add($key, $child) }
                if ($finding.RequiresStop -or $seen.Count -gt 1) { $stop = $true }
            }
            if ($stop -or $owner.HasExited) { break }
            [void] $owner.WaitForExit(10)
        }
        if ($clock.ElapsedMilliseconds -ge $deadline) { $stop = $true }
    } catch { $observerFailed = $true; $stop = $true }
    finally {
        if($RequireOuterControl -and $stop){
            $stopAttempted=$true;$revocation=Invoke-ScStopPort $Ports $Context $identity $control $clock $deadline
        }
        if ($null -ne $owner) {
            $end = Complete-ScOwnedObservation $owner $clock $deadline $stop
        } else {
            $end = [pscustomobject]@{ HeldEndObserved=$false; ExitCodeObserved=$false; ExitCode=$null; ObservationFailed=$true }
        }
    }
    if($RequireOuterControl -and ($end.ObservationFailed -or -not $end.HeldEndObserved -or -not $end.ExitCodeObserved)){
        $stop=$true
        if(-not $stopAttempted){
            $stopAttempted=$true;$revocation=Invoke-ScStopPort $Ports $Context $identity $control $clock $deadline
        }
    }
    $diagnostics = ConvertFrom-ScDiagnostics '' $false
    $stdoutEof = $false; $evidence = $null; $nativeResultPresent = $false
    try {
        $data = & $Ports.Reads $reads $clock $deadline
        $diagnostics = ConvertFrom-ScDiagnostics $data.Stderr $data.StderrEof
        $stdoutEof = $data.StdoutEof -and $data.StdoutEmpty
        $text = & $Ports.Evidence $Context
        $nativeResultPresent = $null -ne $text
        if ($nativeResultPresent) { $evidence = ConvertFrom-ScRunEvidence $text $Context.AttemptId }
        if($RequireOuterControl){$controlEnd=& $Ports.ControlEnd $control;if(-not $controlEnd){$observerFailed=$true}}
    } catch { $observerFailed = $true }
    if($RequireOuterControl -and $observerFailed -and -not $stopAttempted){
        $stop=$true
        $stopAttempted=$true;$revocation=Invoke-ScStopPort $Ports $Context $identity $control $clock $deadline
    }
    # Dispose schließt auch die Streams. Deshalb erst Endcode UND begrenzte Reads
    # abschließen, sonst Diagnose/EOF nicht durch eigenes Dispose zerstören.
    if ($null -ne $owner -and $end.HeldEndObserved -and $end.ExitCodeObserved -and $stdoutEof -and $diagnostics.Eof) {
        try { $held=$owner.SafeHandle; $owner.Dispose(); $closed=$held.IsClosed } catch { $observerFailed=$true }
    }
    $heldChild = $null
    if ($null -ne $evidence -and $evidence.cPid -ne 'unknown' -and $evidence.cBirth -ne 'unknown') {
        $heldChild = [pscustomobject]@{ProcessId=$evidence.cPid;Birth=$evidence.cBirth;Image=$Context.Java}
    }
    $findings = @($seen.Values | ForEach-Object { Get-ScProcessFinding $_ $identity $heldChild })
    $correlated = $findings.Count -eq 1 -and $findings[0].Owned
    $outcome = Get-ScAttemptOutcome $end $evidence $diagnostics $stdoutEof $stop $observerFailed $correlated $closed
    $notReached = @()
    if (@($diagnostics.Lines | Where-Object { $_ -match 'phase=(ENTRY|MANIFEST|NATIVE_LOAD) ' }).Count -gt 0) {
        $notReached = @('C_START','K_CREATE','ACK','READY')
    }
    [pscustomobject]@{
        Report = [pscustomobject]@{
            TrialId = 'SC_EMPTY_V2_' + $Context.AttemptId; AttemptId=$Context.AttemptId
            ObserverProcessId=$Context.ObserverProcessId; ObserverStartsSubprocesses=$false
            SStartRequested=$true; SStartConfirmed=($null -ne $identity); SStartUnconfirmed=($null -eq $identity)
            ArtifactHash=$Context.ArtifactHash; ApprovalHash=$Context.ApprovalHash; ConsumedHash=$markerHash
            ConsumedPath=(Join-Path $Context.Directory 'consumed'); Outcome=$outcome
            Supervisor=$identity; HeldEnd=$end; OwnerHandleClosed=$closed; ObserverFailed=$observerFailed
            StopRequested=$stop; Findings=$findings; Diagnostics=$diagnostics; StdoutEof=$stdoutEof
            OuterControlRequired=[bool]$RequireOuterControl;ControlBound=($null -ne $control -and $control.Bound)
            Revocation=$revocation;ControlEndConfirmed=$controlEnd
            NativeResultPresent=$nativeResultPresent; NativeEvidence=$evidence; NotReached=$notReached
            RetryAllowed=$false; PidAbsenceIsEndProof=$false; ExternalTerminationPerformed=$false
        }
        RetainedOwner = $(if (-not $closed) { $owner } else { $null })
    }
}
