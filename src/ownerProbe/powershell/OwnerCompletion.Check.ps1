$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'OwnerCompletion.ps1')
$script:cases=0
function Assert-Completion([bool]$Value){if(-not $Value){throw 'COMPLETION_ASSERTION'}}
function Assert-CompletionThrows([scriptblock]$Action){$caught=$false;try{& $Action|Out-Null}catch{$caught=$true};Assert-Completion $caught}
function Test-Completion([string]$Label,[scriptblock]$Action){try{& $Action;$script:cases++}catch{throw ('COMPLETION_FAILED: '+$Label)}}
function New-CompletionFake {
    $id=[guid]::NewGuid().ToString();$root=Join-Path ([IO.Path]::GetTempPath()) ('MangaLib-Completion-Fake-'+$id)
    $dir=Get-OwnerCompletionDirectory $root $id;[void][IO.Directory]::CreateDirectory($dir)
    $c=[pscustomobject]@{Profile='OWNER_COMPLETION_V1';Root=$root;AttemptId=$id;Directory=$dir;Artifact=('a'*64);ManifestHash=$null;Java='C:\synthetic\java.exe'}
    $m="OWNER_COMPLETION_V1`n"+$id+"`n"+$c.Artifact+"`n7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8`n"+('d'*64)+"`n"+$root+"`n"
    Write-OwnerNew (Join-Path $dir 'manifest.txt') $m;$c.ManifestHash=(Get-FileHash (Join-Path $dir 'manifest.txt')).Hash.ToLowerInvariant()
    Write-OwnerNew (Join-Path $dir 'approval.txt') ("OWNER_COMPLETION_APPROVED_V1`n"+$id+"`n"+$c.Artifact+"`n"+$c.ManifestHash+"`n")
    $approval=(Get-FileHash (Join-Path $dir 'approval.txt')).Hash.ToLowerInvariant()
    $clock=[pscustomobject]@{Value=0};$clock|Add-Member ScriptProperty ElapsedMilliseconds {$this.Value}
    $s=[pscustomobject]@{Starts=0;Inits=0;Ends=0;StreamCloses=0;OwnerCloses=0;Fault='';Clock=$clock;ConsumedBeforeStart=$false}
    $identity=[pscustomobject]@{Pid=51;Birth='134376986442231462';Image=$c.Java;Held=$true;Source='OWN_PROCESS_START_HANDLE_NATIVE'}
    $write=${function:Write-OwnerNew};$check=${function:Assert-Completion}
    $key='OP1|'+$id+'|'+$c.Artifact+'|51|'+$identity.Birth
    $ports=@{
        Fingerprint={param($ctx);$ctx.Artifact}
        Clock={$clock}.GetNewClosure()
        NativeInit={$s.Inits++;if($s.Fault -ceq 'init'){throw 'SECRET'};if($s.Fault -ceq 'prestart-timeout'){$clock.Value=25000}}.GetNewClosure()
        Start={param($ctx,$r);$s.Starts++;& $check (Test-Path (Join-Path $ctx.Directory 'consumed'));$s.ConsumedBeforeStart=$true
            $r.Owner=[pscustomobject]@{SafeHandle=[pscustomobject]@{IsClosed=$false}}
            if($s.Fault -ceq 'prepare-throws'){throw 'SECRET'}
            $r.StartMayHaveOccurred=$true;if($s.Fault -ceq 'start-throws'){throw 'SECRET'}
            $r.StartConfirmed=$s.Fault -cne 'start-unknown'
        }.GetNewClosure()
        Identity={param($r)
            switch($s.Fault){'image'{$identity.Image='C:\foreign.exe'};'time'{$identity.Birth='134376986442231460.0'};
                'unheld'{$identity.Held=$false};'source'{$identity.Source='CIM_ONLY'}};$identity
        }.GetNewClosure()
        BeginReads={param($r);$r.Reads=[pscustomobject]@{Fake=$true};if($s.Fault -ceq 'read-setup'){throw 'SECRET'}}.GetNewClosure()
        BadOutput={param($r)
            if($s.Fault -cne 'no-ready' -and -not (Test-Path (Join-Path $dir 'ready'))){& $write (Join-Path $dir 'ready') ('READY|'+$key+"`n")}
            $s.Fault -ceq 'bad-output'
        }.GetNewClosure()
        End={param($r);$s.Ends++
            if($s.Fault -ceq 'end-null'){return $null}
            if($s.Fault -ceq 'native-time-changed'){throw 'OWNER_INSTANCE'}
            $done=$s.Fault -cne 'no-end' -and ((Test-Path (Join-Path $dir 'finish')) -or (Test-Path (Join-Path $dir 'stop')))
            if($done -and $s.Fault -cne 'no-closed' -and -not (Test-Path (Join-Path $dir 'closed'))){
                $kind=$(if(Test-Path (Join-Path $dir 'stop')){'STOP'}else{'NORMAL'})
                & $write (Join-Path $dir 'closed') $(if($s.Fault -ceq 'closed'){"wrong`n"}else{'CLOSED|'+$key+'|'+$kind+"`n"})
            }
            $code=$(if(-not $done){$null}elseif($s.Fault -ceq 'exit-259'){259}elseif($s.Fault -ceq 'exit-nonzero'){78}else{0})
            [pscustomobject]@{Ended=$done;ExitObserved=($done -and $s.Fault -cne 'no-exit');ExitCode=$(if($s.Fault -ceq 'no-exit'){$null}else{$code})}
        }.GetNewClosure()
        Reads={param($r,$cl,$deadline)
            [pscustomobject]@{StdoutEof=($s.Fault -cnotin @('stdout-eof','stdout-data'));StderrEof=($s.Fault -cne 'stderr-eof');
                StdoutEmpty=($s.Fault -cne 'stdout-data');DiagnosticsValid=($s.Fault -cne 'stderr-data');Diagnostics=@()}
        }.GetNewClosure()
        CloseStreams={param($r);$s.StreamCloses++;$s.Fault -cne 'streams-close'}.GetNewClosure()
        CloseUnstarted={param($r);& $check (-not $r.StartMayHaveOccurred);$true}.GetNewClosure()
        CloseOwner={param($r);& $check ([object]::ReferenceEquals($r.Owner.SafeHandle,$r.SafeHandle));$s.OwnerCloses++
            if($s.Fault -ceq 'owner-throws'){throw 'SECRET'}
            if($s.Fault -ceq 'late-close'){$clock.Value=26000}
            $r.SafeHandle.IsClosed=$s.Fault -cne 'owner-close';$r.SafeHandle.IsClosed
        }.GetNewClosure()
        Pause={$clock.Value+=1000}.GetNewClosure()
    }
    [pscustomobject]@{Context=$c;Ports=$ports;ApprovalHash=$approval;State=$s}
}
Test-Completion 'complete-held-owner-does-not-claim-global-provenance' {
    $f=New-CompletionFake;$r=Invoke-OwnerCompletion $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-Completion ($r.Report.Outcome -ceq 'OWNER_COMPLETION_PASS' -and $r.Report.OwnerCompletionConfirmed)
    Assert-Completion ($f.State.Starts -eq 1 -and $f.State.ConsumedBeforeStart -and $null -eq $r.Retained)
    Assert-Completion ($r.Report.GlobalProcessProvenance -ceq 'NOT_MEASURED' -and $r.Report.ConsoleProvenance -ceq 'NOT_MEASURED' -and -not $r.Report.ProvenanceAccepted)
    Assert-Completion (-not $r.Report.ResourcesUnconfirmed -and $f.State.OwnerCloses -eq 1 -and $f.State.StreamCloses -eq 1)
    Assert-CompletionThrows {Invoke-OwnerCompletion $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce}
    Assert-Completion ($f.State.Starts -eq 1)
}
Test-Completion 'explicit-flag-required-before-consumption' {
    $f=New-CompletionFake;Assert-CompletionThrows {Invoke-OwnerCompletion $f.Context $f.Ports $f.ApprovalHash}
    Assert-Completion (-not (Test-Path (Join-Path $f.Context.Directory 'consumed')) -and $f.State.Inits -eq 0)
}
foreach($faultCase in @('init','prestart-timeout','start-throws','start-unknown','prepare-throws','read-setup','native-time-changed','image','time','unheld','source','no-ready','bad-output',
    'no-end','no-exit','end-null','stdout-eof','stderr-eof','stdout-data','stderr-data','streams-close','owner-close','owner-throws','late-close','closed','no-closed','exit-259','exit-nonzero')){
    Test-Completion ('failure-'+$faultCase) {
        $f=New-CompletionFake;$f.State.Fault=$faultCase
        $r=Invoke-OwnerCompletion $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
        Assert-Completion ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and -not $r.Report.ProvenanceAccepted)
        Assert-Completion (-not ($r.Report|ConvertTo-Json -Depth 12 -Compress).Contains('SECRET'))
        if($faultCase -cin @('init','prestart-timeout')){Assert-Completion ($f.State.Starts -eq 0)}
        else{Assert-Completion ($f.State.Starts -eq 1)}
        if($faultCase -cin @('image','time','unheld','source')){Assert-Completion ($f.State.Ends -eq 0 -and $f.State.OwnerCloses -eq 0 -and $null -ne $r.Retained)}
        if($faultCase -cin @('stdout-eof','stdout-data','stderr-eof','streams-close','owner-close','owner-throws','no-end','no-exit','end-null','start-throws','start-unknown')){
            Assert-Completion ($r.Report.ResourcesUnconfirmed -and $null -ne $r.Retained)
        }
        if($faultCase -ceq 'exit-259'){Assert-Completion ($r.Report.HeldEnd.Ended -and $r.Report.HeldEnd.ExitCode -eq 259)}
        if($faultCase -ceq 'prepare-throws'){Assert-Completion ($r.Report.PreparationClosed -and -not $r.Report.StartMayHaveOccurred -and -not $r.Report.ResourcesUnconfirmed -and $null -eq $r.Retained)}
        if($faultCase -ceq 'native-time-changed'){Assert-Completion ($f.State.OwnerCloses -eq 0 -and $r.Report.ResourcesUnconfirmed)}
    }
}
foreach($faultCase in @('manifest','approval','legacy-prefix','legacy-directory','manifest-profile','context-profile')){
    Test-Completion ('binding-'+$faultCase) {
        $f=New-CompletionFake
        switch($faultCase){
            'manifest'{[IO.File]::AppendAllText((Join-Path $f.Context.Directory 'manifest.txt'),'x')}
            'approval'{[IO.File]::AppendAllText((Join-Path $f.Context.Directory 'approval.txt'),'x')}
            'legacy-prefix'{$file=Join-Path $f.Context.Directory 'approval.txt';[IO.File]::WriteAllText($file,([IO.File]::ReadAllText($file).Replace('OWNER_COMPLETION_APPROVED_V1','OWNER_APPROVED_V1')));$f.ApprovalHash=(Get-FileHash $file).Hash.ToLowerInvariant()}
            'legacy-directory'{$f.Context.Directory=Get-OwnerDirectory $f.Context.Root $f.Context.AttemptId}
            'context-profile'{$f.Context.Profile='OWNER_PROBE_V1'}
            'manifest-profile'{
                $file=Join-Path $f.Context.Directory 'manifest.txt';[IO.File]::WriteAllText($file,([IO.File]::ReadAllText($file).Replace('OWNER_COMPLETION_V1','OWNER_PROBE_V1')))
                $f.Context.ManifestHash=(Get-FileHash $file).Hash.ToLowerInvariant();$file=Join-Path $f.Context.Directory 'approval.txt'
                [IO.File]::WriteAllText($file,("OWNER_COMPLETION_APPROVED_V1`n"+$f.Context.AttemptId+"`n"+$f.Context.Artifact+"`n"+$f.Context.ManifestHash+"`n"))
                $f.ApprovalHash=(Get-FileHash $file).Hash.ToLowerInvariant()
            }
        }
        Assert-CompletionThrows {Invoke-OwnerCompletion $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce}
        Assert-Completion ($f.State.Starts -eq 0 -and $f.State.Inits -eq 0)
    }
}
Test-Completion 'real-completion-entry-is-cold-single-start-and-has-no-global-observer' {
    $file=Join-Path $PSScriptRoot 'Start-OwnerCompletion.ps1';Assert-CompletionThrows {& $file}
    $t=$null;$e=$null;$a=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$t,[ref]$e)
    Assert-Completion ($e.Count -eq 0)
    $starts=@($a.FindAll({param($n);$n -is [Management.Automation.Language.InvokeMemberExpressionAst] -and $n.Member.Value -ceq 'Start'},$true))
    Assert-Completion ($starts.Count -eq 1)
    foreach($p in @($file,(Join-Path $PSScriptRoot 'OwnerCompletion.ps1'))){
        Assert-Completion (-not ([IO.File]::ReadAllText($p) -match 'Register-Cim|Get-CimInstance|Get-EventSubscriber|Unregister-Event|GetProcessById|Stop-Process|taskkill|\.Kill\('))
    }
}
function Get-CompletionPort([string]$Port){
    $t=$null;$e=$null;$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Start-OwnerCompletion.ps1'),[ref]$t,[ref]$e)
    $pair=@($ast.FindAll({param($n);$n -is [Management.Automation.Language.HashtableAst]},$true)|ForEach-Object {$_.KeyValuePairs}|Where-Object {$_.Item1.Value -ceq $Port})
    $body=$pair[0].Item2.PipelineElements[0].Expression.ScriptBlock.Extent.Text
    [scriptblock]::Create($body.Substring(1,$body.Length-2))
}
Test-Completion 'completion-errors-keep-bounded-diagnostic-codes' {
    foreach($code in @('OWNER_HANDLE_CHANGED','OWNER_PID_BINDING','OWNER_INSTANCE','OWNER_WAIT','OWNER_EXIT','OWNER_READ','OWNER_DIAGNOSTIC_LIMIT')){
        try{throw $code}catch{$diagnostic=Get-OwnerFailure $_ 'NATIVE_END' 'NATIVE_END'}
        Assert-Completion ($diagnostic.Chain[0].Code -ceq $code -and $diagnostic.RawDetailsOmitted)
    }
}
foreach($streamCase in @('empty','marker','stderr-secret','stdout-byte','too-long','invalid-utf8')){
    Test-Completion ('actual-managed-streams-'+$streamCase) {
        $outBytes=$(if($streamCase -ceq 'stdout-byte'){[Text.Encoding]::UTF8.GetBytes('X')}else{[byte[]]::new(0)})
        $errBytes=$(switch($streamCase){'marker'{[Text.Encoding]::UTF8.GetBytes("OWNER_STOP_V1`n")};'stderr-secret'{[Text.Encoding]::UTF8.GetBytes('SECRET')};
            'too-long'{[Text.Encoding]::UTF8.GetBytes(('x'*129))};'invalid-utf8'{[byte[]]@(255)};default{[byte[]]::new(0)}})
        # Explizite Bytearraycasts erhalten auch leere synthetische Payloads.
        $outMemory=[IO.MemoryStream]::new([byte[]]@($outBytes));$errMemory=[IO.MemoryStream]::new([byte[]]@($errBytes));$inputMemory=[IO.MemoryStream]::new()
        $p=[pscustomobject]@{StandardInput=[IO.StreamWriter]::new($inputMemory);
            StandardOutput=[IO.StreamReader]::new($outMemory,[Text.UTF8Encoding]::new($false,$true),$false);
            StandardError=[IO.StreamReader]::new($errMemory,[Text.UTF8Encoding]::new($false,$true),$false)}
        $r=[pscustomobject]@{Owner=$p;Reads=$null};$clock=[pscustomobject]@{Value=0};$clock|Add-Member ScriptProperty ElapsedMilliseconds {$this.Value++;$this.Value}
        try{
            & (Get-CompletionPort 'BeginReads') $r
            Assert-Completion (-not $inputMemory.CanWrite)
            if($streamCase -cin @('too-long','invalid-utf8')){
                Assert-CompletionThrows {& (Get-CompletionPort 'Reads') $r $clock 5000}
            }else{
                $io=& (Get-CompletionPort 'Reads') $r $clock 5000
                Assert-Completion ($io.StderrEof -and $io.StdoutEof -eq ($streamCase -cne 'stdout-byte'))
                if($streamCase -ceq 'empty'){Assert-Completion ($io.DiagnosticsValid -and @($io.Diagnostics).Count -eq 0)}
                if($streamCase -ceq 'marker'){Assert-Completion ($io.DiagnosticsValid -and $io.Diagnostics -ceq 'OWNER_STOP_V1')}
                if($streamCase -ceq 'stderr-secret'){Assert-Completion (-not $io.DiagnosticsValid -and -not ($io|ConvertTo-Json -Compress).Contains('SECRET'))}
                if($streamCase -ceq 'empty'){
                    Assert-Completion (& (Get-CompletionPort 'CloseStreams') $r)
                    Assert-Completion (-not $outMemory.CanRead -and -not $errMemory.CanRead)
                }
            }
        }finally{$p.StandardInput.Dispose();$p.StandardOutput.Dispose();$p.StandardError.Dispose()}
    }
}
"Owner completion: $script:cases fake/static cases passed; no process or Win32 invocation."
