$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ScAttempt.ps1')
$script:cases=0
function Assert-ScCheck([bool] $Value) { if (-not $Value) { throw 'SC_FAKE_ASSERTION' } }
function Assert-ScThrows([scriptblock] $Action) {
    $thrown=$false
    try { & $Action | Out-Null } catch { $thrown=$true }
    Assert-ScCheck $thrown
}
function Test-ScCase([string] $Name,[scriptblock] $Action) {
    try { & $Action; $script:cases++ } catch { throw ('SC_FAKE_CASE_FAILED: ' + $Name) }
}
$script:fakeRoot=Join-Path ([IO.Path]::GetTempPath()) ('MangaLib-ScAttempt-Fakes-' + [guid]::NewGuid())
function New-ScFake {
    $id=[guid]::NewGuid().ToString()
    $root=Join-Path $script:fakeRoot $id
    $directory=Get-ScAttemptPath $root $id
    [void] [IO.Directory]::CreateDirectory($directory)
    $hash='a' * 64
    $approval=Join-Path $directory 'approval.txt'
    [IO.File]::WriteAllText($approval,($id + "`n" + $hash + "`n"),[Text.UTF8Encoding]::new($false))
    $context=[pscustomobject]@{Root=$root;Directory=$directory;AttemptId=$id;ArtifactHash=$hash;
        ApprovalHash=(Get-FileHash $approval).Hash.ToLowerInvariant();Java='C:\synthetic\java.exe';ObserverProcessId=41}
    $p=[pscustomobject]@{HasExited=$false;ExitCode=0;Waits=0;SafeHandle=[pscustomobject]@{IsInvalid=$false;IsClosed=$false}}
    $p | Add-Member ScriptMethod WaitForExit {param($Milliseconds);$this.Waits++;$this.HasExited=$true;$true}
    $p | Add-Member ScriptMethod Dispose {$this.SafeHandle.IsClosed=$true}
    $c=[pscustomobject]@{ProcessId=52;ParentProcessId=51;Birth='200';Image=$context.Java;CustodianCommandMatches=$true}
    $state=[pscustomobject]@{Starts=0;Fingerprints=0;ActualHash=$hash;ChangeHash=$false;Clock=[pscustomobject]@{ElapsedMilliseconds=0};
        Process=$p;Child=$c;Children=@($c);ChildFailure=$false;ReadFailure=$false;Diag='';StdoutEof=$true;StderrEof=$true;Evidence=$null}
    $state.Evidence="profile=SCN2`nsession=$id`nready=true`ncomplete=true`ngroupEnded=true`ncResources=true`nsResources=true`njournalTerminal=true`noperationOutstanding=false`ncEnd=true`ncHandlesClosed=true`ncExit=0`ncPid=52`ncBirth=200`n"
    $ports=@{
        Fingerprint={param($c);$state.Fingerprints++;if($state.ChangeHash -and $state.Fingerprints -gt 1){'b' * 64}else{$state.ActualHash}}.GetNewClosure()
        Clock={$state.Clock}.GetNewClosure()
        Start={param($c);$state.Starts++;$state.Process}.GetNewClosure()
        Identity={param($p);[pscustomobject]@{ProcessId=51;Birth='100';Image='C:\synthetic\java.exe';Held=$true}}.GetNewClosure()
        BeginReads={param($p);[pscustomobject]@{Fake=$true}}
        Children={param($p,$s,$c);if($state.ChildFailure){throw 'SECRET'};$state.Children}.GetNewClosure()
        Reads={param($r);if($state.ReadFailure){throw 'SECRET'};[pscustomobject]@{StdoutEof=$state.StdoutEof;StdoutEmpty=$state.StdoutEof;
            Stderr=$state.Diag;StderrEof=$state.StderrEof}}.GetNewClosure()
        Evidence={param($c);$state.Evidence}.GetNewClosure()
    }
    [pscustomobject]@{Context=$context;Ports=$ports;State=$state}
}
Test-ScCase 'explicit-switch-required' {
    $f=New-ScFake
    Assert-ScThrows {Invoke-ScAttemptCore $f.Context $f.Ports}
    Assert-ScCheck ($f.State.Starts -eq 0 -and -not (Test-Path (Join-Path $f.Context.Directory 'consumed')))
}
Test-ScCase 'full-end-pass-and-consumption-before-one-start' {
    $f=New-ScFake
    $old=$f.Ports.Start
    $f.Ports.Start={param($c);if(-not (Test-Path (Join-Path $c.Directory 'consumed'))){throw 'SC_MARKER_ORDER'};& $old $c}.GetNewClosure()
    $r=Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce
    Assert-ScCheck ($r.Report.Outcome -ceq 'PASS' -and $r.Report.OwnerHandleClosed -and $f.State.Starts -eq 1)
    Assert-ScCheck ($r.Report.Findings[0].Owned -and $r.Report.Findings[0].JobMembership -eq 'UNKNOWN')
    Assert-ScCheck ($r.Report.ConsumedHash -eq (Get-FileHash $r.Report.ConsumedPath).Hash.ToLowerInvariant())
}
Test-ScCase 'second-start-blocked-old-marker-byte-identical' {
    $f=New-ScFake;$r=Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce
    $before=(Get-FileHash $r.Report.ConsumedPath).Hash
    Assert-ScThrows {Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce}
    Assert-ScCheck ($f.State.Starts -eq 1 -and $before -eq (Get-FileHash $r.Report.ConsumedPath).Hash)
}
foreach($fault in @('missing-hash','wrong-hash','wrong-approval-hash','missing-approval','old-marker','native-exists','wrong-session')) {
    Test-ScCase $fault {
        $f=New-ScFake
        switch($fault) {
            'missing-hash' {$f.Context.ArtifactHash=''}
            'wrong-hash' {$f.State.ActualHash='c' * 64}
            'wrong-approval-hash' {$f.Context.ApprovalHash='c' * 64}
            'missing-approval' {$f.Context.Directory=Join-Path $f.Context.Root 'other'}
            'old-marker' {[IO.File]::WriteAllText((Join-Path $f.Context.Directory 'consumed'),'HISTORICAL_FAKE_CONSUMED')}
            'native-exists' {[void] [IO.Directory]::CreateDirectory((Join-Path $f.Context.Directory 'native'))}
            'wrong-session' {
                [IO.File]::WriteAllText((Join-Path $f.Context.Directory 'approval.txt'),([guid]::NewGuid().ToString() + "`n" + $f.Context.ArtifactHash))
                $f.Context.ApprovalHash=(Get-FileHash (Join-Path $f.Context.Directory 'approval.txt')).Hash.ToLowerInvariant()
            }
        }
        Assert-ScThrows {Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce}
        Assert-ScCheck ($f.State.Starts -eq 0)
    }
}
Test-ScCase 'changed-artifact-after-consumption-no-retry' {
    $f=New-ScFake;$f.State.ChangeHash=$true
    Assert-ScThrows {Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce}
    Assert-ScCheck ($f.State.Starts -eq 0 -and (Test-Path (Join-Path $f.Context.Directory 'consumed')))
    Assert-ScThrows {Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce}
    Assert-ScCheck ($f.State.Starts -eq 0)
}
Test-ScCase 'loader-before-C-K-confirmed-failure-not-pass' {
    $f=New-ScFake;$f.State.Process.ExitCode=78;$f.State.Children=@();$f.State.Evidence=$null
    $f.State.Diag='SC_STOP_V1 role=SUPERVISOR phase=NATIVE_LOAD category=LINKAGE exit=78 end=UNCONFIRMED resources=UNCONFIRMED'
    $r=Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce
    Assert-ScCheck ($r.Report.Outcome -ceq 'FAIL/STOP' -and $r.Report.HeldEnd.ExitCode -eq 78 -and $r.Report.NotReached.Count -eq 4)
    Assert-ScCheck (-not $r.Report.HeldEnd.ResourceEndProved -and -not $r.Report.NativeResultPresent)
}
foreach($fault in @('no-result','no-ack','group','c-resources','s-resources','journal','outstanding','c-end','c-handles','c-exit','stdout-eof','stderr-eof','wrong-result-session')) {
    Test-ScCase $fault {
        $f=New-ScFake
        switch($fault) {
            'no-result' {$f.State.Evidence=$null}
            'no-ack' {$f.State.Evidence=$f.State.Evidence.Replace('ready=true','ready=false')}
            'group' {$f.State.Evidence=$f.State.Evidence.Replace('groupEnded=true','groupEnded=false')}
            'c-resources' {$f.State.Evidence=$f.State.Evidence.Replace('cResources=true','cResources=false')}
            's-resources' {$f.State.Evidence=$f.State.Evidence.Replace('sResources=true','sResources=false')}
            'journal' {$f.State.Evidence=$f.State.Evidence.Replace('journalTerminal=true','journalTerminal=false')}
            'outstanding' {$f.State.Evidence=$f.State.Evidence.Replace('operationOutstanding=false','operationOutstanding=true')}
            'c-end' {$f.State.Evidence=$f.State.Evidence.Replace('cEnd=true','cEnd=false')}
            'c-handles' {$f.State.Evidence=$f.State.Evidence.Replace('cHandlesClosed=true','cHandlesClosed=false')}
            'c-exit' {$f.State.Evidence=$f.State.Evidence.Replace('cExit=0','cExit=unknown')}
            'stdout-eof' {$f.State.StdoutEof=$false}
            'stderr-eof' {$f.State.StderrEof=$false}
            'wrong-result-session' {$f.State.Evidence=$f.State.Evidence.Replace($f.Context.AttemptId,[guid]::NewGuid().ToString())}
        }
        $r=Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce
        Assert-ScCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and $f.State.Starts -eq 1)
    }
}
foreach($fault in @('observer','read','missing-exit','timeout','unknown-close','communication-live-S')) {
    Test-ScCase $fault {
        $f=New-ScFake
        switch($fault) {
            'observer' {$f.State.ChildFailure=$true}
            'read' {$f.State.ReadFailure=$true}
            'missing-exit' {$f.State.Process.ExitCode=$null}
            'timeout' {$f.State.Clock.ElapsedMilliseconds=25000}
            'unknown-close' {$f.State.Process | Add-Member ScriptMethod Dispose {throw 'SECRET'} -Force}
            'communication-live-S' {
                $f.State.ChildFailure=$true
                $f.State.Process | Add-Member ScriptMethod WaitForExit {param($Milliseconds);throw 'SECRET'} -Force
            }
        }
        $r=Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce
        Assert-ScCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and $f.State.Starts -eq 1)
        Assert-ScThrows {Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce}
        Assert-ScCheck ($f.State.Starts -eq 1 -and -not ($r.Report | ConvertTo-Json -Depth 8 -Compress).Contains('SECRET'))
    }
}
foreach($fault in @('conhost','similar-name','wrong-parent','old-birth','missing-birth','child-not-held','two-children')) {
    Test-ScCase $fault {
        $f=New-ScFake
        switch($fault) {
            'conhost' {$f.State.Child.Image='C:\Windows\System32\conhost.exe'}
            'similar-name' {$f.State.Child.Image='C:\untrusted\conhost.exe'}
            'wrong-parent' {$f.State.Child.ParentProcessId=999}
            'old-birth' {$f.State.Child.Birth='50'}
            'missing-birth' {$f.State.Child.Birth=$null}
            'child-not-held' {$f.State.Child.Birth='201'}
            'two-children' {$f.State.Children=@($f.State.Child,[pscustomobject]@{ProcessId=53;ParentProcessId=51;Birth='201';Image='C:\Windows\System32\conhost.exe'})}
        }
        $r=Invoke-ScAttemptCore $f.Context $f.Ports -ExecuteOnce
        Assert-ScCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP')
        if($fault -eq 'conhost') {
            Assert-ScCheck ($r.Report.Findings[0].Kind -ceq 'POSSIBLE_CONSOLE_COMPANION_UNPROVEN' -and -not $r.Report.Findings[0].Owned)
        }
    }
}
foreach($text in @('SECRET password 0x123456',('x' * 1025),"SC_STOP_V1 role=SUPERVISOR phase=HACK category=IO exit=78 end=UNCONFIRMED resources=UNCONFIRMED")) {
    Test-ScCase 'untrusted-diagnostics-redacted-and-rejected' {
        $d=ConvertFrom-ScDiagnostics $text $true
        Assert-ScCheck (-not $d.Valid -and $d.Lines.Count -eq 0 -and -not ($d | ConvertTo-Json).Contains('SECRET'))
    }
}
foreach($fault in @('eof','partial-then-eof','oversize','faulted','deadline','output-not-eof')) {
    Test-ScCase ('stream-' + $fault) {
        $buffer=[char[]]::new(1025)
        $task=[pscustomobject]@{IsFaulted=$false;IsCanceled=$false;IsCompletedSuccessfully=$true;Result=0}
        $reader=[pscustomobject]@{}
        $reader | Add-Member ScriptMethod ReadAsync {param($b,$o,$n);[pscustomobject]@{IsFaulted=$false;IsCanceled=$false;IsCompletedSuccessfully=$true;Result=0}}
        $out=[pscustomobject]@{IsCompletedSuccessfully=$true;Result=0}
        $clock=[pscustomobject]@{ElapsedMilliseconds=0}
        switch($fault) {
            'partial-then-eof' {$task.Result=3;$buffer[0]='a';$buffer[1]='b';$buffer[2]='c'}
            'oversize' {$task.Result=1025}
            'faulted' {$task.IsFaulted=$true}
            'deadline' {$clock.ElapsedMilliseconds=100}
            'output-not-eof' {$out.Result=1}
        }
        $reads=[pscustomobject]@{Err=$buffer;ErrTask=$task;OutTask=$out;ErrorReader=$reader}
        if($fault -eq 'faulted') {Assert-ScThrows {Read-ScOwnerStreams $reads $clock 100};return}
        $data=Read-ScOwnerStreams $reads $clock 100
        switch($fault) {
            'eof' {Assert-ScCheck ($data.StdoutEof -and $data.StderrEof -and $data.Stderr -eq '')}
            'partial-then-eof' {Assert-ScCheck ($data.StderrEof -and $data.Stderr -ceq 'abc')}
            'oversize' {Assert-ScCheck ($data.Stderr.Length -eq 1025 -and -not (ConvertFrom-ScDiagnostics $data.Stderr $false).Valid)}
            'deadline' {Assert-ScCheck (-not $data.StderrEof)}
            'output-not-eof' {Assert-ScCheck (-not $data.StdoutEof)}
        }
    }
}
Test-ScCase 'runtime-launcher-explicit-gate-and-single-start-static-only' {
    $file=Join-Path $PSScriptRoot 'Start-ScEmptyOnce.ps1'
    Assert-ScThrows { & $file }
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$tokens,[ref]$errors)
    Assert-ScCheck ($errors.Count -eq 0)
    $starts=@($ast.FindAll({param($node);$node -is [Management.Automation.Language.InvokeMemberExpressionAst] -and $node.Member.Value -ceq 'Start'},$true))
    Assert-ScCheck ($starts.Count -eq 1)
    $source=[IO.File]::ReadAllText($file)
    Assert-ScCheck (-not ($source -match 'Start-Process|Stop-Process|taskkill|GetProcessById|\.Kill\('))
    Assert-ScCheck ($source.Contains('CreateNoWindow=$true') -and $source.Contains('UseShellExecute=$false'))
}
"SC attempt: $script:cases fake cases passed; no process, Win32 or database calls."
