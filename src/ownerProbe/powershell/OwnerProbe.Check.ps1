$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'OwnerProbe.ps1')
$script:checks=0
$script:fakeRoot=Join-Path ([IO.Path]::GetTempPath()) ('MangaLib-OwnerProvenance-Fakes-'+[guid]::NewGuid())
function Assert-OwnerCheck([bool]$Value){if(-not $Value){throw 'OWNER_FAKE_ASSERTION'}}
function Assert-OwnerThrows([scriptblock]$Action){$thrown=$false;try{& $Action | Out-Null}catch{$thrown=$true};Assert-OwnerCheck $thrown}
function Test-OwnerCase([string]$Name,[scriptblock]$Action){try{& $Action;$script:checks++}catch{throw ('OWNER_FAKE_FAILED: '+$Name)}}
function New-OwnerFake {
    $writeNew=${function:Write-OwnerNew};$check=${function:Assert-OwnerCheck}
    $id=[guid]::NewGuid().ToString();$root=Join-Path $script:fakeRoot $id;$dir=Get-OwnerDirectory $root $id
    [void][IO.Directory]::CreateDirectory($dir)
    [IO.File]::WriteAllText((Join-Path $dir 'manifest.txt'),'synthetic-manifest')
    $c=[pscustomobject]@{Root=$root;Directory=$dir;AttemptId=$id;Artifact=('a'*64);Java='C:\synthetic\java.exe';
        ManifestHash=(Get-FileHash (Join-Path $dir 'manifest.txt')).Hash.ToLowerInvariant();ObserverPid=41}
    $approval="OWNER_APPROVED_V1`n"+$id+"`n"+$c.Artifact+"`n"+$c.ManifestHash+"`n"
    Write-OwnerNew (Join-Path $dir 'approval.txt') $approval
    $approvalHash=(Get-FileHash (Join-Path $dir 'approval.txt')).Hash.ToLowerInvariant()
    $clock=[pscustomobject]@{Value=0};$clock|Add-Member ScriptProperty ElapsedMilliseconds {$this.Value}
    $process=[pscustomobject]@{SafeHandle=[pscustomobject]@{IsClosed=$false;IsInvalid=$false}}
    $state=[pscustomobject]@{Starts=0;NativeInits=0;Polls=0;Fingerprints=0;ChangeArtifact=$false;StartConfirmed=$true;Closed=$false;
        SubscribeError=$false;PollError=$false;CloseError=$false;UnsubscribeError=$false;NoReady=$false;NoRoot=$false;NoEnd=$false;NoExit=$false;
        IdentityFault='';BadOutput=$false;StreamFault='';EarlyExit=$false;Exit=0;Extra=@();Clock=$clock;Process=$process;Subscriber=[pscustomobject]@{Own=$true}}
    $identity=[pscustomobject]@{Pid=51;Birth='134376986442231462';Image=$c.Java;Held=$true;Source='OWN_PROCESS_START_HANDLE_NATIVE'}
    $ports=@{
        Fingerprint={param($ctx);$state.Fingerprints++;if($state.ChangeArtifact -and $state.Fingerprints -gt 1){'b'*64}else{$ctx.Artifact}}.GetNewClosure()
        Clock={$clock}.GetNewClosure()
        NativeInit={$state.NativeInits++}.GetNewClosure()
        Subscribe={param($ctx,$receipt);if($state.SubscribeError){throw 'PRIVATE'};$receipt.Acquisition='CONFIRMED';$receipt.Step='BOUND';$receipt.Subscriber=$state.Subscriber}.GetNewClosure()
        Start={param($ctx);& $check (Test-Path (Join-Path $ctx.Directory 'consumed'));$state.Starts++;[pscustomobject]@{Owner=$process;Confirmed=$state.StartConfirmed}}.GetNewClosure()
        Identity={param($p,$safe);switch($state.IdentityFault){'image' {$identity.Image='C:\foreign\java.exe'};'birth' {$identity.Birth='1.343e17'};
            'unheld' {$identity.Held=$false};'source' {$identity.Source='CIM_ONLY'};'error' {throw 'PRIVATE'}};$identity}.GetNewClosure()
        BeginReads={param($p);[pscustomobject]@{Fake=$true}}
        BadOutput={param($r);$state.BadOutput}.GetNewClosure()
        Poll={param($s,$i,$ctx);$state.Polls++;if($state.PollError){throw 'PRIVATE'}
            if(-not $state.NoReady -and -not (Test-Path (Join-Path $ctx.Directory 'ready'))){
                & $writeNew (Join-Path $ctx.Directory 'ready') ('READY|OP1|'+$ctx.AttemptId+'|'+$ctx.Artifact+'|'+$i.Pid+'|'+$i.Birth+"`n")}
            [pscustomobject]@{Healthy=$true;RootSeen=(-not $state.NoRoot);Findings=$(if($state.Polls -eq 1){$state.Extra}else{@()})}
        }.GetNewClosure()
        End={param($p,$safe,$i);
            $done=-not $state.NoEnd -and ($state.EarlyExit -or (Test-Path (Join-Path $c.Directory 'finish')) -or (Test-Path (Join-Path $c.Directory 'stop')))
            if($done -and -not (Test-Path (Join-Path $c.Directory 'closed'))){
                $kind=$(if(Test-Path (Join-Path $c.Directory 'stop')){'STOP'}else{'NORMAL'})
                & $writeNew (Join-Path $c.Directory 'closed') ('CLOSED|OP1|'+$c.AttemptId+'|'+$c.Artifact+'|'+$i.Pid+'|'+$i.Birth+'|'+$kind+"`n")}
            [pscustomobject]@{Ended=$done;ExitObserved=($done -and -not $state.NoExit);ExitCode=$(if($done -and -not $state.NoExit){$state.Exit}else{$null})}
        }.GetNewClosure()
        Reads={param($r,$clock,$deadline);if($state.StreamFault -eq 'throw'){throw 'PRIVATE'}
            [pscustomobject]@{StdoutEof=($state.StreamFault -ne 'stdout');StdoutEmpty=($state.StreamFault -ne 'output');
                StderrEof=($state.StreamFault -ne 'stderr');DiagnosticsValid=($state.StreamFault -ne 'diagnostic');Diagnostics=@();RawTextDiscarded=$true}
        }.GetNewClosure()
        CloseOwner={param($p,$safe,$i);& $check ([object]::ReferenceEquals($safe,$process.SafeHandle) -and $i.Birth -ceq '134376986442231462');if($state.CloseError){throw 'PRIVATE'};$safe.IsClosed=$true;$state.Closed=$true;$true}.GetNewClosure()
        Unsubscribe={param($s);if($state.UnsubscribeError){throw 'PRIVATE'};$true}.GetNewClosure()
        Pause={$clock.Value+=1000}.GetNewClosure()
    }
    [pscustomobject]@{Context=$c;ApprovalHash=$approvalHash;State=$state;Ports=$ports}
}
Test-OwnerCase 'no-explicit-execution-no-consumption' {
    $f=New-OwnerFake;Assert-OwnerThrows {Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash}
    Assert-OwnerCheck ($f.State.Starts -eq 0 -and -not (Test-Path (Join-Path $f.Context.Directory 'consumed')))
}
Test-OwnerCase 'normal-bound-native-identity-end-EOF-and-owned-resource-completion' {
    $f=New-OwnerFake;$r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.Outcome -ceq 'PASS' -and $f.State.Starts -eq 1 -and $r.Report.RootTraceSeen)
    Assert-OwnerCheck ($r.Report.OwnerHandleClosed -and $r.Report.SubscriptionClosed -and $null -eq $r.RetainedOwner)
    Assert-OwnerCheck ($r.Report.OwnerCompletionConfirmed -and $r.Report.ProvenanceAccepted -and -not $r.Report.ResourcesUnconfirmed)
    Assert-OwnerCheck ($r.Report.Identity.Birth -ceq '134376986442231462' -and $r.Report.WorkerStarts -eq 0 -and $r.Report.JobCreates -eq 0)
    Assert-OwnerThrows {Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce};Assert-OwnerCheck ($f.State.Starts -eq 1)
}
foreach($fault in @('hash','approval','manifest','namespace','already-consumed')){
    Test-OwnerCase ('prestart-'+$fault) {
        $f=New-OwnerFake
        switch($fault){'hash'{$f.ApprovalHash='b'*64};'approval'{[IO.File]::AppendAllText((Join-Path $f.Context.Directory 'approval.txt'),'x')};
            'manifest'{[IO.File]::AppendAllText((Join-Path $f.Context.Directory 'manifest.txt'),'x')};
            'namespace'{$f.Context.Directory=$f.Context.Root};default{Write-OwnerNew (Join-Path $f.Context.Directory 'consumed') 'used'}}
        Assert-OwnerThrows {Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce}
        Assert-OwnerCheck ($f.State.Starts -eq 0 -and $f.State.NativeInits -eq 0)
    }
}
foreach($fault in @('artifact-after-consumption','subscription','native-init')){
    Test-OwnerCase ('consumed-prestart-'+$fault) {
        $f=New-OwnerFake
        switch($fault){'artifact-after-consumption'{$f.State.ChangeArtifact=$true};'subscription'{$f.State.SubscribeError=$true};default{$f.Ports.NativeInit={throw 'PRIVATE'}}}
        $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
        Assert-OwnerCheck ($r.Report.Outcome -cne 'PASS' -and $f.State.Starts -eq 0 -and (Test-Path (Join-Path $f.Context.Directory 'consumed')))
    }
}
foreach($fault in @('image','birth','unheld','source','error')){
    Test-OwnerCase ('native-identity-'+$fault) {
        $f=New-OwnerFake;$f.State.IdentityFault=$fault;$r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
        Assert-OwnerCheck ($r.Report.Outcome -cne 'PASS' -and $f.State.Starts -eq 1 -and $null -ne $r.RetainedOwner)
        Assert-OwnerCheck (-not (Test-Path (Join-Path $f.Context.Directory 'binding')))
    }
}
foreach($name in @('conhost.exe','java.exe','unknown.exe')){
    Test-OwnerCase ('extra-process-never-authorized-by-name-'+$name) {
        $f=New-OwnerFake;$f.State.Extra=@([pscustomobject]@{Pid=71;Parent=51;Name=$name;Owned=$false;RequiresStop=$true})
        $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
        Assert-OwnerCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and $r.Report.StopSent -and $r.Report.StopClosure)
        Assert-OwnerCheck (-not $r.Report.Findings[0].Owned -and $f.State.Starts -eq 1)
        Assert-OwnerCheck (-not $r.Report.ProvenanceAccepted)
    }
}
foreach($fault in @('no-end','no-exit','stdout','stderr','output','diagnostic','throw','close','unsubscribe','poll','no-root','start-unknown','bad-output','nonzero-exit')){
    Test-OwnerCase ('end-or-observer-fault-'+$fault) {
        $f=New-OwnerFake
        switch($fault){'no-end'{$f.State.NoEnd=$true};'no-exit'{$f.State.NoExit=$true};'close'{$f.State.CloseError=$true};
            'unsubscribe'{$f.State.UnsubscribeError=$true};'poll'{$f.State.PollError=$true};'no-root'{$f.State.NoRoot=$true};
            'start-unknown'{$f.State.StartConfirmed=$false};'bad-output'{$f.State.BadOutput=$true};'nonzero-exit'{$f.State.Exit=78};default{$f.State.StreamFault=$fault}}
        $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
        Assert-OwnerCheck ($r.Report.Outcome -cne 'PASS' -and $f.State.Starts -eq 1)
        if($fault -in @('no-end','no-exit','stdout','stderr','throw','close','start-unknown')){Assert-OwnerCheck ($null -ne $r.RetainedOwner)}
        if($fault -eq 'unsubscribe'){Assert-OwnerCheck ($null -ne $r.RetainedSubscription)}
        Assert-OwnerCheck (-not ($r.Report|ConvertTo-Json -Depth 8 -Compress).Contains('PRIVATE'))
    }
}
Test-OwnerCase 'native-declarations-compile-and-reflect-without-invocation' {
    $types=Add-Type -TypeDefinition $script:OwnerNativeDeclaration -PassThru
    $type=$types|Where-Object FullName -eq 'MangaLib.OwnerProbe.InspectionV1'
    $imports=@($type.GetMethods([Reflection.BindingFlags]'NonPublic,Static')|Where-Object {$_.GetCustomAttributes([Runtime.InteropServices.DllImportAttribute],$false).Count -gt 0})
    Assert-OwnerCheck ($imports.Count -eq 6)
    foreach($method in $imports){
        $attr=$method.GetCustomAttributes([Runtime.InteropServices.DllImportAttribute],$false)[0]
        Assert-OwnerCheck ($attr.Value -ceq 'C:\Windows\System32\kernel32.dll' -and $method.GetParameters()[0].ParameterType.FullName -ceq 'Microsoft.Win32.SafeHandles.SafeProcessHandle')
    }
    Assert-OwnerCheck (@($imports|Where-Object Name -match 'OpenProcess|CreateProcess|Terminate|Job').Count -eq 0)
    Assert-OwnerCheck (@($imports|Where-Object Name -ceq 'CloseHandle').Count -eq 1)
    # Keine reflektierte Methode wird aufgerufen; kein DLL-Export aufgelöst.
}
Test-OwnerCase 'real-launcher-remains-explicit-cold-and-single-start' {
    $file=Join-Path $PSScriptRoot 'Start-OwnerProvenance.ps1';Assert-OwnerThrows {& $file}
    $tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$tokens,[ref]$errors)
    Assert-OwnerCheck ($errors.Count -eq 0)
    $starts=@($ast.FindAll({param($n);$n -is [Management.Automation.Language.InvokeMemberExpressionAst] -and $n.Member.Value -ceq 'Start'},$true))
    Assert-OwnerCheck ($starts.Count -eq 1)
    $text=[IO.File]::ReadAllText($file)
    Assert-OwnerCheck (-not ($text -match 'GetProcessById|\.Kill\(|Stop-Process|taskkill|Start-Process'))
    Assert-OwnerCheck ($text.Contains('CreateNoWindow=$true') -and $text.Contains('UseShellExecute=$false') -and $text.Contains('InspectionV1]::Identity($safe)'))
}
Test-OwnerCase 'elapsed-prestart-budget-cannot-be-renewed-into-a-start' {
    $f=New-OwnerFake;$c=$f.State.Clock;$f.Ports.NativeInit={$c.Value=25000}.GetNewClosure()
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($f.State.Starts -eq 0 -and $r.Report.Outcome -ceq 'PARTIAL/STOP')
}
Test-OwnerCase 'manifest-change-during-subscribe-prevents-the-only-start' {
    $f=New-OwnerFake;$path=Join-Path $f.Context.Directory 'manifest.txt';$sub=$f.State.Subscriber
    $f.Ports.Subscribe={param($ctx,$receipt);$receipt.Acquisition='CONFIRMED';$receipt.Subscriber=$sub;[IO.File]::AppendAllText($path,'x')}.GetNewClosure()
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($f.State.Starts -eq 0 -and $r.Report.Outcome -ceq 'PARTIAL/STOP')
}
Test-OwnerCase 'even-a-claimed-owned-extra-process-is-not-a-name-allowlist' {
    $f=New-OwnerFake;$f.State.Extra=@([pscustomobject]@{Pid=71;Parent=51;Name='conhost.exe';Owned=$true;RequiresStop=$false})
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.StopRequested -and $r.Report.Outcome -ceq 'PARTIAL/STOP')
}
Test-OwnerCase 'missing-ready-never-sends-finish-or-passes' {
    $f=New-OwnerFake;$f.State.NoReady=$true
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and -not $r.Report.FinishSent -and -not $r.Report.ReadyObserved)
}
Test-OwnerCase 'malformed-close-message-cannot-pass-even-after-native-end-and-EOF' {
    $f=New-OwnerFake;Write-OwnerNew (Join-Path $f.Context.Directory 'closed') 'wrong'
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and $r.Report.HeldEnd.Ended -and -not $r.Report.NormalClosure)
}
Test-OwnerCase 'unacknowledged-native-close-retains-the-original-owner' {
    $f=New-OwnerFake;$f.Ports.CloseOwner={param($p,$safe,$id);$false}
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and -not $r.Report.OwnerHandleClosed -and $null -ne $r.RetainedOwner)
}
foreach($probeName in @('conhost.exe','OpenConsole.exe','other-observer-child.exe','unrelated.exe')){
    Test-OwnerCase ('actual-poll-script-with-only-fake-CIM-commands-'+$probeName) {
        # Tatsächlichen Pollblock aus dem Launcher auswerten, alle OS-Cmdlets lokal ersetzen.
        # Weder Launcher noch Start-/Native-/CIM-Kommandos werden dabei ausgeführt.
        $tokens=$null;$errors=$null
        $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Start-OwnerProvenance.ps1'),[ref]$tokens,[ref]$errors)
        $pair=@($ast.FindAll({param($n);$n -is [Management.Automation.Language.HashtableAst]},$true)|ForEach-Object {$_.KeyValuePairs}|Where-Object {$_.Item1.Value -ceq 'Poll'})
        $body=$pair[0].Item2.PipelineElements[0].Expression.ScriptBlock.Extent.Text
        $poll=[scriptblock]::Create($body.Substring(1,$body.Length-2))
        $parent=$(if($probeName -ceq 'other-observer-child.exe'){41}else{1})
        $events=@([pscustomobject]@{SourceIdentifier='FAKE_ONLY';EventIdentifier=1;SourceEventArgs=[pscustomobject]@{NewEvent=[pscustomobject]@{ProcessID=51;ParentProcessID=41;ProcessName='java.exe';TIME_CREATED='100'}}},
            [pscustomobject]@{SourceIdentifier='FAKE_ONLY';EventIdentifier=2;SourceEventArgs=[pscustomobject]@{NewEvent=[pscustomobject]@{ProcessID=71;ParentProcessID=$parent;ProcessName=$probeName;TIME_CREATED='101'}}})
        $s=[pscustomobject]@{Source='FAKE_ONLY';SourceIdentifier='FAKE_ONLY';RootSeen=$false;Trace=[Collections.Generic.List[object]]::new();Seen=[Collections.Generic.HashSet[string]]::new()}
        $s|Add-Member -NotePropertyName Subscriber -NotePropertyValue $s
        $r=& {
            param($poll,$events,$s)
            function Get-Event {param($SourceIdentifier,$ErrorAction);$events}
            function Remove-Event {param($EventIdentifier,$ErrorAction)}
            function Get-CimInstance {param($ClassName,$Filter,$Property,$OperationTimeoutSec,$ErrorAction)}
            function Get-EventSubscriber {param($SourceIdentifier,$ErrorAction);$s}
            & $poll $s ([pscustomobject]@{Pid=51}) ([pscustomobject]@{ObserverPid=41})
        } $poll $events $s
        Assert-OwnerCheck ($r.Healthy -and $r.RootSeen -and $r.ObservedTrace.Count -eq 2)
        if($probeName -ceq 'unrelated.exe'){Assert-OwnerCheck ($r.Findings.Count -eq 0)}
        else{Assert-OwnerCheck ($r.Findings.Count -eq 1 -and -not $r.Findings[0].Owned -and $r.Findings[0].RequiresStop)}
    }
}
foreach($case in @('before-register','partial-unknown','partial-known','close-missing','close-late','close-throws')){
    Test-OwnerCase ('receipt-and-diagnostics-'+$case) {
        $f=New-OwnerFake;$state=$f.State;$mode=$case
        $f.Ports.Subscribe={param($ctx,$receipt)
            $receipt.Step='CHECK_EXISTING'
            if($mode -ceq 'before-register'){throw [ComponentModel.Win32Exception]::new(5,'SECRET_PASSWORD')}
            $receipt.Step='REGISTER_CIM';$receipt.Acquisition='UNCONFIRMED'
            if($mode -ceq 'partial-unknown'){throw [ComponentModel.Win32Exception]::new(5,'SECRET_PASSWORD')}
            $receipt.Subscriber=$state.Subscriber;$receipt.Acquisition='CONFIRMED';$receipt.Step='VERIFY_SUBSCRIBER'
            throw [Reflection.TargetInvocationException]::new('SECRET_OUTER',[ComponentModel.Win32Exception]::new(5,'SECRET_PASSWORD'))
        }.GetNewClosure()
        if($case -ceq 'close-missing'){$f.Ports.Unsubscribe={param($s);$false}}
        if($case -ceq 'close-late'){$clock=$f.State.Clock;$f.Ports.Unsubscribe={param($s);$clock.Value=26000;$true}.GetNewClosure()}
        if($case -ceq 'close-throws'){$f.State.UnsubscribeError=$true}
        $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
        Assert-OwnerCheck ($f.State.Starts -eq 0 -and $r.Report.Outcome -ceq 'PARTIAL/STOP' -and $r.Report.StopRequested)
        Assert-OwnerCheck ($r.Report.Failures[0].Phase -ceq 'SUBSCRIBE' -and @($r.Report.Failures[0].Chain|Where-Object NativeErrorCode -eq 5).Count -eq 1)
        Assert-OwnerCheck (-not ($r.Report|ConvertTo-Json -Depth 12 -Compress).Contains('SECRET'))
        if($case -ceq 'before-register'){
            Assert-OwnerCheck ($r.Report.ObservationAcquisition -ceq 'NOT_ATTEMPTED' -and -not $r.Report.ResourcesUnconfirmed -and $null -eq $r.RetainedSubscription -and -not $r.Report.ObservationReleaseAttempted)
        }elseif($case -ceq 'partial-unknown'){
            Assert-OwnerCheck ($r.Report.ResourcesUnconfirmed -and $null -ne $r.RetainedSubscription -and -not $r.Report.ObservationReleaseAttempted)
        }elseif($case -ceq 'partial-known'){
            Assert-OwnerCheck ($r.Report.SubscriptionClosed -and $r.Report.ObservationReleaseAttempted -and $null -eq $r.RetainedSubscription)
        }elseif($case -ceq 'close-late'){
            Assert-OwnerCheck ($r.Report.DeadlineExpired -and -not $r.Report.ObservationComplete)
        }else{
            Assert-OwnerCheck ($r.Report.ResourcesUnconfirmed -and $null -ne $r.RetainedSubscription)
            if($case -ceq 'close-throws'){Assert-OwnerCheck ($r.Report.Failures.Count -eq 2 -and $r.Report.Failures[1].Phase -ceq 'SUBSCRIPTION_CLOSE')}
        }
    }
}
Test-OwnerCase 'owner-completion-is-independent-of-provenance-success' {
    $f=New-OwnerFake;$f.State.PollError=$true
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.OwnerCompletionConfirmed -and -not $r.Report.ObservationComplete -and $r.Report.Outcome -ceq 'PARTIAL/STOP')
}
Test-OwnerCase 'diagnostic-bounds-keep-codes-chain-and-category-without-raw-secrets' {
    $e=[ComponentModel.Win32Exception]::new(5,'SECRET_PASSWORD')
    $er=[Management.Automation.ErrorRecord]::new($e,('SECRET_ID'+'x'*5000),[Management.Automation.ErrorCategory]::PermissionDenied,$null)
    $d=Get-OwnerFailure $er 'SUBSCRIBE' 'REGISTER_CIM'
    Assert-OwnerCheck ($d.Chain[0].NativeErrorCode -eq 5 -and $d.Chain[0].HResult -match '^0x[0-9A-F]{8}$' -and $d.Category -ceq 'PermissionDenied' -and $d.ErrorIdTruncated)
    $json=$d|ConvertTo-Json -Depth 8 -Compress
    Assert-OwnerCheck ($json.Length -lt 2048 -and -not $json.Contains('SECRET') -and $d.RawDetailsOmitted)
    $nested=[Exception]::new('SECRET');for($i=0;$i -lt 6;$i++){$nested=[InvalidOperationException]::new('SECRET',$nested)}
    $d=Get-OwnerFailure ([Management.Automation.ErrorRecord]::new($nested,'ID',[Management.Automation.ErrorCategory]::InvalidOperation,$null)) 'SUBSCRIBE' 'VERIFY_SUBSCRIBER'
    Assert-OwnerCheck ($d.Chain.Count -eq 4 -and $d.ChainTruncated)
}
function Get-ActualOwnerPort([string]$PortName){
    $tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Start-OwnerProvenance.ps1'),[ref]$tokens,[ref]$errors)
    $pair=@($ast.FindAll({param($n);$n -is [Management.Automation.Language.HashtableAst]},$true)|ForEach-Object {$_.KeyValuePairs}|Where-Object {$_.Item1.Value -ceq $PortName})
    $body=$pair[0].Item2.PipelineElements[0].Expression.ScriptBlock.Extent.Text
    [scriptblock]::Create($body.Substring(1,$body.Length-2))
}
Test-OwnerCase 'cold-preflight-error-is-bounded-and-cannot-start' {
    $received=[Collections.Generic.List[object]]::new();$caught=$false
    try{& (Join-Path $PSScriptRoot 'Start-OwnerProvenance.ps1') -AttemptId '../SECRET' -ValidateOnly|ForEach-Object {$received.Add($_)}}catch{$caught=$true}
    Assert-OwnerCheck ($caught -and $received.Count -eq 1 -and $received[0].Outcome -ceq 'BLOCKED' -and -not $received[0].StartRequested)
    Assert-OwnerCheck ($received[0].Failure.Phase -ceq 'PREFLIGHT' -and -not ($received[0]|ConvertTo-Json -Depth 10 -Compress).Contains('SECRET'))
    Assert-OwnerCheck (@($received[0].Failure.Chain|Where-Object Code -CEQ 'OWNER_ID').Count -eq 1)
}
Test-OwnerCase 'late-owner-close-cannot-produce-overall-success' {
    $f=New-OwnerFake;$clock=$f.State.Clock;$f.Ports.CloseOwner={param($p,$safe,$id);$clock.Value=26000;$true}.GetNewClosure()
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and $r.Report.StopRequested -and $r.Report.DeadlineExpired -and -not $r.Report.OwnerCompletionConfirmed)
}
foreach($probeFault in @('check-fails','existing','register','verify','success')){
    Test-OwnerCase ('actual-subscribe-port-only-mocked-commands-'+$probeFault) {
        $subscribe=Get-ActualOwnerPort 'Subscribe';$receipt=New-OwnerObservationReceipt ([guid]::NewGuid().ToString())
        $r=& {
            param($port,$receipt,$fault)
            $counter=[pscustomobject]@{Queries=0;Registers=0};$original=[pscustomobject]@{SubscriptionId=7;SourceIdentifier=$receipt.Source}
            function Get-EventSubscriber {param($SourceIdentifier,$ErrorAction)
                $counter.Queries++;if($counter.Queries -eq 1){if($fault -ceq 'check-fails'){throw [ComponentModel.Win32Exception]::new(5,'SECRET')};if($fault -ceq 'existing'){$original}}
                elseif($fault -ceq 'verify'){throw [ComponentModel.Win32Exception]::new(5,'SECRET')}else{$original}
            }
            function Register-CimIndicationEvent {param($Query,$SourceIdentifier,$ErrorAction)
                $counter.Registers++;if($fault -ceq 'register'){throw [ComponentModel.Win32Exception]::new(5,'SECRET')}
            }
            $failure=$null;try{& $port ([pscustomobject]@{}) $receipt}catch{$failure=Get-OwnerFailure $_ 'SUBSCRIBE' $receipt.Step}
            [pscustomobject]@{Receipt=$receipt;Failure=$failure;Registers=$counter.Registers;Original=$original}
        } $subscribe $receipt $probeFault
        if($probeFault -cin @('existing','check-fails')){Assert-OwnerCheck ($r.Registers -eq 0 -and $receipt.Acquisition -ceq 'NOT_ATTEMPTED' -and $r.Failure.Step -ceq 'CHECK_EXISTING')}
        elseif($probeFault -ceq 'register'){Assert-OwnerCheck ($r.Registers -eq 1 -and $receipt.Acquisition -ceq 'UNCONFIRMED' -and $r.Failure.Step -ceq 'REGISTER_CIM')}
        elseif($probeFault -ceq 'verify'){Assert-OwnerCheck ($r.Registers -eq 1 -and $receipt.Acquisition -ceq 'UNCONFIRMED' -and $r.Failure.Step -ceq 'VERIFY_SUBSCRIBER')}
        else{Assert-OwnerCheck ($receipt.Acquisition -ceq 'CONFIRMED' -and [object]::ReferenceEquals($receipt.Subscriber,$r.Original) -and $null -eq $r.Failure)}
    }
}
foreach($probeFault in @('changed-reference','verify-fails','queue-remains','success')){
    Test-OwnerCase ('actual-release-port-only-mocked-commands-'+$probeFault) {
        $port=Get-ActualOwnerPort 'Unsubscribe';$receipt=New-OwnerObservationReceipt ([guid]::NewGuid().ToString())
        $receipt.Subscriber=[pscustomobject]@{SubscriptionId=7;SourceIdentifier=$receipt.Source};$receipt.Acquisition='CONFIRMED'
        $r=& {
            param($port,$receipt,$fault)
            $counter=[pscustomobject]@{Queries=0;Releases=0;EventReads=0}
            function Get-EventSubscriber {param($SourceIdentifier,$ErrorAction)
                $counter.Queries++
                if($counter.Queries -eq 1){if($fault -ceq 'changed-reference'){[pscustomobject]@{SubscriptionId=7;SourceIdentifier=$receipt.Source}}else{$receipt.Subscriber}}
                elseif($fault -ceq 'verify-fails'){throw [ComponentModel.Win32Exception]::new(6,'SECRET')}
            }
            function Unregister-Event {param($SubscriptionId,$ErrorAction)
                if($SubscriptionId -ne 7){throw 'FOREIGN_SUBSCRIBER'};$counter.Releases++
            }
            function Get-Event {param($SourceIdentifier,$ErrorAction)
                $counter.EventReads++
                if($fault -ceq 'queue-remains' -and $counter.EventReads -gt 1){[pscustomobject]@{SourceIdentifier=$receipt.Source}}
            }
            function Remove-Event {
                [CmdletBinding()]param([Parameter(ValueFromPipeline)]$InputObject)
                process{if($null -ne $InputObject){throw 'UNEXPECTED_REMOVE'}}
            }
            $failure=$null;$answer=$false
            try{$answer=& $port $receipt}catch{$failure=Get-OwnerFailure $_ 'SUBSCRIPTION_CLOSE' $receipt.Step}
            [pscustomobject]@{Failure=$failure;Closed=$answer;Releases=$counter.Releases}
        } $port $receipt $probeFault
        if($probeFault -ceq 'changed-reference'){Assert-OwnerCheck ($r.Releases -eq 0 -and $r.Failure.Step -ceq 'CHECK_ORIGINAL' -and -not $r.Closed)}
        elseif($probeFault -ceq 'verify-fails'){Assert-OwnerCheck ($r.Releases -eq 1 -and $r.Failure.Step -ceq 'VERIFY_RELEASE' -and -not $r.Closed)}
        elseif($probeFault -ceq 'queue-remains'){Assert-OwnerCheck ($r.Releases -eq 1 -and -not $r.Closed -and $null -eq $r.Failure)}
        else{Assert-OwnerCheck ($r.Releases -eq 1 -and $r.Closed -and $null -eq $r.Failure)}
    }
}
function New-HistoricalBoundaryFixture {
    $root=Join-Path $script:fakeRoot ([guid]::NewGuid().ToString())
    $old='07574fb1-9fc5-41de-8a9b-d77c8841edf2';$dir=Get-OwnerDirectory $root $old
    [void][IO.Directory]::CreateDirectory($dir)
    $record=[pscustomobject]@{AttemptId=$old;Profile='OWNER_PROVENANCE_V1';Outcome='PARTIAL/STOP';FailurePhase='SUBSCRIBE';
        StartRequested=$false;StartConfirmed=$false;Identity=$null;WorkerStarts=0;JobCreates=0}
    $file=Join-Path $dir 'result.json';[IO.File]::WriteAllText($file,($record|ConvertTo-Json))
    [pscustomobject]@{Root=$root;File=$file;Old=$old;New=[guid]::NewGuid().ToString();Record=$record}
}
Test-OwnerCase 'unconfirmed-prestart-observer-is-not-an-owned-import-job-or-execution-approval' {
    $f=New-HistoricalBoundaryFixture;$b=Get-OwnerHistoryClassification $f.Record $f.New
    Assert-OwnerCheck ($b.HistoricalSessionState -ceq 'UNKNOWN' -and -not $b.HistoricalCleanupConfirmed -and -not $b.HistoricalImportStarted)
    Assert-OwnerCheck ($b.IndependentProbeNamespace -and -not $b.RequiresHistoricalCleanup -and -not $b.GrantsExecution)
    Assert-OwnerCheck (-not $b.MayClearImportQuarantine -and -not $b.MayTerminateHistoricalProcess)
    Assert-OwnerCheck (-not $b.EvidenceValidated)
}
foreach($probeFault in @('owner-start','worker-start','job-start','identity','wrong-attempt','unknown-phase','string-boolean','string-count')){
    Test-OwnerCase ('tampered-or-non-prestart-history-cannot-enable-independent-probe-'+$probeFault) {
        $f=New-HistoricalBoundaryFixture;$e=$f.Record
        switch($probeFault){'owner-start'{$e.StartRequested=$true};'worker-start'{$e.WorkerStarts=1};'job-start'{$e.JobCreates=1};
            'identity'{$e.Identity=[pscustomobject]@{Pid=17608}};'wrong-attempt'{$e.AttemptId=$f.New};
            'string-boolean'{$e.StartRequested='false'};'string-count'{$e.WorkerStarts='0'};default{$e.FailurePhase='START'}}
        Assert-OwnerThrows {Get-OwnerHistoryClassification $e $f.New}
    }
}
Test-OwnerCase 'historical-attempt-id-cannot-be-reused-in-the-new-namespace' {
    $f=New-HistoricalBoundaryFixture;Assert-OwnerThrows {Get-OwnerHistoryClassification $f.Record $f.Old}
}
Test-OwnerCase 'missing-history-fails-closed-without-a-process-lookup-fallback' {
    $root=Join-Path $script:fakeRoot ([guid]::NewGuid().ToString())
    Assert-OwnerThrows {Get-OwnerHistoricalBoundary $root ([guid]::NewGuid().ToString())}
}
Test-OwnerCase 'history-classification-never-resets-an-import-journal' {
    $f=New-HistoricalBoundaryFixture;$pending=Join-Path $f.Root 'pending';$quarantine=Join-Path $f.Root 'quarantine'
    Write-OwnerNew $pending 'OWNED_IMPORT_NOT_ENDED';Write-OwnerNew $quarantine 'UNCONFIRMED'
    $before=@((Get-FileHash $pending).Hash,(Get-FileHash $quarantine).Hash)
    $b=Get-OwnerHistoryClassification $f.Record $f.New
    Assert-OwnerCheck (-not $b.MayClearImportQuarantine -and (Get-FileHash $pending).Hash -ceq $before[0] -and (Get-FileHash $quarantine).Hash -ceq $before[1])
}
Test-OwnerCase 'new-scope-completion-does-not-claim-historical-observer-cleanup' {
    $f=New-OwnerFake;$history=New-HistoricalBoundaryFixture
    $f.Context|Add-Member -NotePropertyName HistoricalBoundary -NotePropertyValue (Get-OwnerHistoryClassification $history.Record $f.Context.AttemptId)
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.Outcome -ceq 'PASS' -and $r.Report.OwnerCompletionConfirmed -and $r.Report.ProvenanceAccepted)
    Assert-OwnerCheck (-not $r.Report.HistoricalBoundary.HistoricalCleanupConfirmed -and $r.Report.HistoricalBoundary.HistoricalSessionState -ceq 'UNKNOWN')
    Assert-OwnerCheck (-not $r.Report.ResourcesUnconfirmed -and -not $r.Report.HistoricalBoundary.MayClearImportQuarantine)
}
Test-OwnerCase 'plausible-synthetic-history-never-substitutes-for-pinned-original-bytes' {
    $f=New-HistoricalBoundaryFixture
    Assert-OwnerThrows {Get-OwnerHistoricalBoundary $f.Root $f.New}
    Assert-OwnerCheck (-not (Get-OwnerHistoryClassification $f.Record $f.New).EvidenceValidated)
}
Test-OwnerCase 'registration-permission-denial-consumes-once-without-owner-or-blind-cleanup' {
    $f=New-OwnerFake;$calls=[pscustomobject]@{Registers=0;Releases=0}
    $denial=[Management.Automation.ErrorRecord]::new([ComponentModel.Win32Exception]::new(5,'SECRET_DENIAL'),
        'SECRET_ERROR_ID',[Management.Automation.ErrorCategory]::PermissionDenied,$null)
    $f.Ports.Subscribe={param($ctx,$receipt)
        $receipt.Step='REGISTER_CIM';$receipt.Acquisition='UNCONFIRMED';$calls.Registers++;throw $denial
    }.GetNewClosure()
    $f.Ports.Unsubscribe={param($receipt);$calls.Releases++;$true}.GetNewClosure()
    $r=Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce
    Assert-OwnerCheck ($r.Report.Outcome -ceq 'PARTIAL/STOP' -and $r.Report.FailurePhase -ceq 'SUBSCRIBE')
    Assert-OwnerCheck ($r.Report.Failures[0].Step -ceq 'REGISTER_CIM' -and $r.Report.Failures[0].Category -ceq 'PermissionDenied')
    Assert-OwnerCheck ($calls.Registers -eq 1 -and $calls.Releases -eq 0 -and $f.State.Starts -eq 0 -and -not $r.Report.StartRequested)
    Assert-OwnerCheck ($r.Report.ResourcesUnconfirmed -and $null -ne $r.RetainedSubscription -and -not $r.Report.ObservationReleaseAttempted)
    Assert-OwnerCheck ($null -eq $r.Report.Identity -and $null -eq $r.Report.HeldEnd.ExitCode -and -not $r.Report.OwnerCompletionConfirmed)
    Assert-OwnerCheck (Test-Path (Join-Path $f.Context.Directory 'consumed'))
    Assert-OwnerCheck (-not ($r.Report|ConvertTo-Json -Depth 12 -Compress).Contains('SECRET'))
    Assert-OwnerThrows {Invoke-OwnerProbe $f.Context $f.Ports $f.ApprovalHash -ExecuteOnce}
    Assert-OwnerCheck ($calls.Registers -eq 1 -and $f.State.NativeInits -eq 1 -and $f.State.Starts -eq 0)
}
"Owner provenance: $script:checks fake/metadata cases passed; no process or Win32 invocation."
