param([Parameter(Mandatory)][string]$AttemptId,[Parameter(Mandatory)][string]$ManifestHash,[string]$ApprovalHash,[switch]$ValidateOnly,[switch]$ExecuteOnce)
$ErrorActionPreference='Stop'
if(($ValidateOnly -and $ExecuteOnce) -or (-not $ValidateOnly -and -not $ExecuteOnce)){throw 'ACCEPTANCE_MODE'}
if($PSVersionTable.PSVersion.Major -lt 7 -or -not [OperatingSystem]::IsWindows() -or [IntPtr]::Size -ne 8){throw 'ACCEPTANCE_PLATFORM'}
function Assert-AcceptancePath([string]$Path){
    $f=Get-Item -LiteralPath $Path -Force
    while($null -ne $f){if(($f.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'ACCEPTANCE_PATH'};$f=$(if($f -is [IO.FileInfo]){$f.Directory}else{$f.Parent})}
}
function Write-AcceptanceNew([string]$Path,[string]$Value){
    $bytes=[Text.UTF8Encoding]::new($false).GetBytes($Value)
    $f=[IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{$f.Write($bytes);$f.Flush($true)}finally{$f.Dispose()}
}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'));$id=[guid]::Empty
if([IO.File]::ReadAllText((Join-Path $root '.git/HEAD')).Trim() -cne 'ref: refs/heads/codex/t02-security-hardening'){throw 'ACCEPTANCE_BRANCH'}
if(-not [guid]::TryParseExact($AttemptId,'D',[ref]$id) -or $id -eq [guid]::Empty -or $id.ToString() -cne $AttemptId -or $ManifestHash -cnotmatch '^[a-f0-9]{64}$'){throw 'ACCEPTANCE_ID'}
foreach($key in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){if(Test-Path ('Env:'+$key)){throw 'ACCEPTANCE_OVERRIDE'}}
$dir=Join-Path $root ('.gradle/t02-scraper-acceptance/'+$AttemptId);$manifest=Join-Path $dir 'manifest.txt'
Assert-AcceptancePath $manifest
if((Get-Item -LiteralPath $manifest).Length -gt 16384 -or (Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant() -cne $ManifestHash){throw 'ACCEPTANCE_MANIFEST_HASH'}
$m=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($line in ([IO.File]::ReadAllText($manifest).Split([char]10))){
    if($line -ceq ''){continue};$eq=$line.IndexOf('=')
    if($eq -lt 1 -or $line.Length -gt 2048 -or $line.Contains("`r") -or -not $m.TryAdd($line.Substring(0,$eq),$line.Substring($eq+1))){throw 'ACCEPTANCE_MANIFEST_FORMAT'}
}
$roles=@('owner','worker','productionWorker','java','gecko','firefox','jna','jvm','jli','release','kernel','launcher','inspector','manual','historicalManifest','historicalResult','historicalConsumed')
$keys=@('version','attempt','profile','root','slot','series','volume','outerMs','network','starts')+@($roles|ForEach-Object {'file.'+$_})
if($m['profile'] -ceq 'BROWSER_TIMEOUT_V1'){$keys+=@('timeoutStartMs','timeoutRunMs','timeoutCleanupMs','timeoutOuterMs')}
if($m.Count -ne $keys.Count -or @($keys|Where-Object {-not $m.ContainsKey($_)}).Count -ne 0 -or
    $m['version'] -cne 'SCRAPER_ACCEPTANCE_V1' -or $m['attempt'] -cne $AttemptId -or $m['root'] -cne $root -or
    $m['profile'] -cnotin @('LIVE_IMPORTS_V1','BROWSER_BOUNDARY_V1','BROWSER_TIMEOUT_V1') -or $m['series'] -cne '2502' -or $m['volume'] -cne '15295' -or $m['outerMs'] -cne '480000') {throw 'ACCEPTANCE_CONTRACT'}
$slot=Join-Path ([Environment]::GetFolderPath('UserProfile')) '.mangalib/scraper/import-slot'
if($m['slot'] -cne $slot){throw 'ACCEPTANCE_SLOT'}
$paths=@{}
function Test-AcceptanceFiles {
    foreach($role in $roles){
        $pair=$m['file.'+$role].Split('|')
        if($pair.Count -ne 2 -or -not [IO.Path]::IsPathFullyQualified($pair[0]) -or $pair[1] -cnotmatch '^[a-f0-9]{64}$'){throw 'ACCEPTANCE_PIN_FORMAT'}
        Assert-AcceptancePath $pair[0]
        if((Get-FileHash -LiteralPath $pair[0]).Hash.ToLowerInvariant() -cne $pair[1]){throw 'ACCEPTANCE_PIN_CHANGED'}
        $paths[$role]=$pair[0]
    }
    if($paths.java -cne 'C:\Program Files\Java\jdk-21\bin\java.exe' -or $paths.launcher -cne $PSCommandPath -or
        $paths.owner -cne (Join-Path $root 'build/libs/mangalib-scraper-acceptance.jar')){throw 'ACCEPTANCE_LAUNCHER'}
}
Test-AcceptanceFiles
$live=$m['profile'] -ceq 'LIVE_IMPORTS_V1'
$timeout=$m['profile'] -ceq 'BROWSER_TIMEOUT_V1'
if($timeout -and ($m['timeoutStartMs'] -cne '30000' -or $m['timeoutRunMs'] -cne '45000' -or $m['timeoutCleanupMs'] -cne '30000' -or $m['timeoutOuterMs'] -cne '100000')){throw 'ACCEPTANCE_TIMEOUT_BUDGETS'}
$workerName=$(if($live){'mangalib-scraper-worker.jar'}else{'mangalib-scraper-boundary-worker.jar'})
if($paths.worker -cne (Join-Path $root ('build/libs/'+$workerName)) -or $paths.productionWorker -cne (Join-Path $root 'build/libs/mangalib-scraper-worker.jar') -or
    $m['starts'] -cne $(if($live){'OWNER1_WORKERS2_JOBS2_BROWSERS2'}else{'OWNER1_WORKER1_JOB1_BROWSER1'}) -or
    $m['network'] -cne $(if($live){'DNS_HTTPS_WWW_MEDIA_ONLY'}elseif($timeout){'ABOUT_BLANK_LOOPBACK_PROXY_ONLY'}else{'LOOPBACK_FIXTURES_ONLY_NO_DNS'})){throw 'ACCEPTANCE_SCOPE'}
if(Test-Path -LiteralPath $slot){Assert-AcceptancePath $slot;if((Test-Path (Join-Path $slot 'pending')) -or (Test-Path (Join-Path $slot 'quarantine'))){throw 'ACCEPTANCE_PRIOR_IMPORT'}}
foreach($name in @('consumed','binding','stop','acceptance-result.txt','failure.txt','result.json')){if(Test-Path (Join-Path $dir $name)){throw 'ACCEPTANCE_USED'}}
if($ValidateOnly){[pscustomobject]@{Profile=$m['profile'];AttemptId=$AttemptId;ManifestHash=$ManifestHash;Approved=$false;Consumed=$false;NativeCalls=$false;OwnerStarts=0;WorkerStarts=0;NetworkCalls=0};return}
if($null -ne ('MangaLib.OwnerProbe.InspectionV1' -as [type]) -or $null -ne $script:ScraperAcceptanceRetained){throw 'ACCEPTANCE_FRESH_SESSION_REQUIRED'}
$approval=Join-Path $dir 'approval.txt';Assert-AcceptancePath $approval
$expected="SCRAPER_ACCEPTANCE_APPROVED_V1`n"+$AttemptId+"`n"+$ManifestHash+"`n"
if($ApprovalHash -cnotmatch '^[a-f0-9]{64}$' -or (Get-Item -LiteralPath $approval).Length -gt 256 -or
    [IO.File]::ReadAllText($approval) -cne $expected -or (Get-FileHash -LiteralPath $approval).Hash.ToLowerInvariant() -cne $ApprovalHash){throw 'ACCEPTANCE_APPROVAL'}
Write-AcceptanceNew (Join-Path $dir 'consumed') ("SCRAPER_ACCEPTANCE_CONSUMED_V1`n"+$AttemptId+"`n"+$ManifestHash+"`n"+$ApprovalHash+"`n")
$phase='PREFLIGHT';$identity=$null;$end=$null;$p=$null;$safe=$null;$outEof=$false;$errEof=$false;$streams=$false;$handle=$false;$diagnostic=$null;$pass=$false;$report=$null
$clock=[Diagnostics.Stopwatch]::StartNew()
try{
    Test-AcceptanceFiles
    if((Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant() -cne $ManifestHash){throw 'ACCEPTANCE_CHANGED'}
    . $paths.inspector;Initialize-OwnerNativeInspection
    $phase='START';$p=[Diagnostics.Process]::new();$script:ScraperAcceptanceRetained=$p
    $s=[Diagnostics.ProcessStartInfo]::new();$s.FileName=$paths.java;$s.UseShellExecute=$false;$s.CreateNoWindow=$true;$s.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
    $s.WorkingDirectory=$root;$s.RedirectStandardInput=$true;$s.RedirectStandardOutput=$true;$s.RedirectStandardError=$true
    $s.Environment.Clear();$s.Environment['SystemRoot']='C:\Windows';$s.Environment['TEMP']=$dir;$s.Environment['TMP']=$dir
    foreach($arg in @('-Xmx256m','-XX:-UsePerfData','-Djna.nosys=true','-jar',$paths.owner,'--scraper-acceptance-v1',$manifest,$ManifestHash)){$s.ArgumentList.Add($arg)}
    $p.StartInfo=$s;if(-not $p.Start()){throw 'ACCEPTANCE_START'};$safe=$p.SafeHandle
    $phase='IDENTITY';$identity=[MangaLib.OwnerProbe.InspectionV1]::Identity($safe)
    if($identity.Pid -ne $p.Id -or $identity.Image -ine $paths.java -or $identity.Birth -le 0){throw 'ACCEPTANCE_IDENTITY'}
    Write-AcceptanceNew (Join-Path $dir 'binding') ('ACCEPTANCE1|'+$ManifestHash+'|'+$identity.Pid+'|'+$identity.Birth+"`n")
    $p.StandardInput.Dispose();$a=[char[]]::new(1);$b=[char[]]::new(1);$out=$p.StandardOutput.ReadAsync($a,0,1);$err=$p.StandardError.ReadAsync($b,0,1)
    $phase='END'
    while($clock.ElapsedMilliseconds -lt 480000){
        if(-not [object]::ReferenceEquals($safe,$p.SafeHandle)){throw 'ACCEPTANCE_HANDLE_CHANGED'}
        $end=[MangaLib.OwnerProbe.InspectionV1]::End($safe,$identity.Pid,$identity.Birth)
        if($end.Ended -and $out.IsCompleted -and $err.IsCompleted){break};[Threading.Thread]::Sleep(10)
    }
    $outEof=$out.IsCompletedSuccessfully -and $out.Result -eq 0;$errEof=$err.IsCompletedSuccessfully -and $err.Result -eq 0
    if($end.Ended -and $end.ExitObserved -and $outEof -and $errEof){
        $p.StandardOutput.Dispose();$p.StandardError.Dispose();$streams=$true
        $handle=[MangaLib.OwnerProbe.InspectionV1]::CloseEndedOwner($safe,$identity.Pid,$identity.Birth)
        if($handle){$p.Dispose();$handle=$safe.IsClosed;if($handle){$script:ScraperAcceptanceRetained=$null}}
    }
    $phase='POST_INTEGRITY';Test-AcceptanceFiles
    $phase='REPORT';$reportFile=Join-Path $dir 'acceptance-result.txt';Assert-AcceptancePath $reportFile
    if((Get-Item -LiteralPath $reportFile).Length -gt 65536){throw 'ACCEPTANCE_REPORT_SIZE'}
    $report=[IO.File]::ReadAllText($reportFile).Split([char]10)
    $pass=$report[0] -ceq 'SCRAPER_ACCEPTANCE_RESULT_V1' -and $report[1] -ceq $ManifestHash -and $report[2] -ceq 'PASS' -and
        $end.Ended -and $end.ExitObserved -and $end.ExitCode -eq 0 -and $outEof -and $errEof -and $streams -and $handle -and $clock.ElapsedMilliseconds -lt 480000
}catch{$diagnostic=[pscustomobject]@{Phase=$phase;Type=$_.Exception.GetType().FullName;HResult=$_.Exception.HResult;RawDetailsOmitted=$true}}
$result=[pscustomobject]@{AttemptId=$AttemptId;ManifestHash=$ManifestHash;Profile=$m['profile'];Outcome=$(if($pass){'SCOPED_PASS'}else{'PARTIAL/STOP'});
    OwnerIdentity=$identity;OwnerEnd=$end;StdoutEOF=$outEof;StderrEOF=$errEof;StreamsClosed=$streams;HandleClosed=$handle;Diagnostic=$diagnostic;
    OwnerResourcesUnconfirmed=($null -ne $script:ScraperAcceptanceRetained);
    JobResourcesUnconfirmed=($null -eq $report -or $report -cnotcontains 'CLEANUP|true|CLOSED|true');
    GlobalNetworkProvenance='NOT_MEASURED';GlobalConsoleProvenance='NOT_MEASURED';Coverage='Enumerated_cases_only';RetryAllowed=$false;ElapsedMs=$clock.ElapsedMilliseconds}
Write-AcceptanceNew (Join-Path $dir 'result.json') ($result|ConvertTo-Json -Depth 6)
$result
if($null -ne $script:ScraperAcceptanceRetained){Write-Output 'ACCEPTANCE_RETAINED_STOP';while($true){[Threading.Thread]::Sleep(1000)}}
