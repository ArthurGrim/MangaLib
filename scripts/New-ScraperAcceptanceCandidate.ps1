param([Parameter(Mandatory)][ValidateSet('LIVE_IMPORTS_V1','BROWSER_BOUNDARY_V1','BROWSER_TIMEOUT_V1')][string]$Profile,[Parameter(Mandatory)][guid]$AttemptId)
$ErrorActionPreference='Stop'
if($AttemptId -eq [guid]::Empty){throw 'CANDIDATE_ID'}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if([IO.File]::ReadAllText((Join-Path $root '.git/HEAD')).Trim() -cne 'ref: refs/heads/codex/t02-security-hardening'){throw 'CANDIDATE_BRANCH'}
$live=$Profile -ceq 'LIVE_IMPORTS_V1'
$timeout=$Profile -ceq 'BROWSER_TIMEOUT_V1'
$files=[ordered]@{
    owner=(Join-Path $root 'build/libs/mangalib-scraper-acceptance.jar')
    worker=(Join-Path $root ('build/libs/'+$(if($live){'mangalib-scraper-worker.jar'}else{'mangalib-scraper-boundary-worker.jar'})))
    productionWorker=(Join-Path $root 'build/libs/mangalib-scraper-worker.jar')
    java='C:\Program Files\Java\jdk-21\bin\java.exe'
    gecko='E:\Programme\Test\geckodriver.exe'
    firefox='C:\Program Files\Mozilla Firefox\firefox.exe'
    jna=(Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\net.java.dev.jna\jna\5.17.0\33d12735bef894440780fce64f9758d420c7bae2\jna-5.17.0.jar')
    jvm='C:\Program Files\Java\jdk-21\bin\server\jvm.dll'
    jli='C:\Program Files\Java\jdk-21\bin\jli.dll'
    release='C:\Program Files\Java\jdk-21\release'
    kernel='C:\Windows\System32\kernel32.dll'
    launcher=(Join-Path $root 'scripts/Start-ScraperAcceptance.ps1')
    inspector=(Join-Path $root 'src/ownerProbe/powershell/OwnerNativeInspection.ps1')
    manual=(Join-Path $root 'build/libs/mangalib-manual-test.jar')
    historicalManifest=(Join-Path $root '.gradle/t02-worker-job-proof/7af1c3c7-0eaa-4ff6-a218-33950de4af14/manifest.txt')
    historicalResult=(Join-Path $root '.gradle/t02-worker-job-proof/7af1c3c7-0eaa-4ff6-a218-33950de4af14/result.json')
    historicalConsumed=(Join-Path $root '.gradle/t02-worker-job-proof/7af1c3c7-0eaa-4ff6-a218-33950de4af14/consumed')
}
$lines=[Collections.Generic.List[string]]::new()
foreach($line in @('version=SCRAPER_ACCEPTANCE_V1',('attempt='+$AttemptId.ToString()),('profile='+$Profile),('root='+$root),
        ('slot='+(Join-Path ([Environment]::GetFolderPath('UserProfile')) '.mangalib/scraper/import-slot')),'series=2502','volume=15295','outerMs=480000',
        ('network='+$(if($live){'DNS_HTTPS_WWW_MEDIA_ONLY'}elseif($timeout){'ABOUT_BLANK_LOOPBACK_PROXY_ONLY'}else{'LOOPBACK_FIXTURES_ONLY_NO_DNS'})),
        ('starts='+$(if($live){'OWNER1_WORKERS2_JOBS2_BROWSERS2'}else{'OWNER1_WORKER1_JOB1_BROWSER1'})))){$lines.Add($line)}
if($timeout){foreach($budget in @('timeoutStartMs=30000','timeoutRunMs=45000','timeoutCleanupMs=30000','timeoutOuterMs=100000')){$lines.Add($budget)}}
foreach($role in $files.Keys){
    $path=$files[$role];$item=Get-Item -LiteralPath $path -Force
    if($item -isnot [IO.FileInfo] -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'CANDIDATE_FILE'}
    $hash=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
    if($role -ceq 'manual' -and $hash -cne '9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9'){throw 'CANDIDATE_MANUAL_CHANGED'}
    $lines.Add('file.'+$role+'='+$path+'|'+$hash)
}
$dir=Join-Path $root ('.gradle/t02-scraper-acceptance/'+$AttemptId.ToString())
if(Test-Path -LiteralPath $dir){throw 'CANDIDATE_EXISTS'}
New-Item -Path $dir -ItemType Directory | Out-Null
$manifest=Join-Path $dir 'manifest.txt';$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($lines -join "`n")+"`n")
$file=[IO.FileStream]::new($manifest,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
try{$file.Write($bytes);$file.Flush($true)}finally{$file.Dispose()}
$hash=(Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant()
$request="NOT_AN_APPROVAL`nProfile: $Profile`nAttempt: $AttemptId`nManifest-SHA256: $hash`nNo approval or consumption created. Native execution requires separate explicit owner approval.`n"
[IO.File]::WriteAllText((Join-Path $dir 'approval-request.txt'),$request,[Text.UTF8Encoding]::new($false))
$preflight=& (Join-Path $root 'scripts/Start-ScraperAcceptance.ps1') -AttemptId $AttemptId.ToString() -ManifestHash $hash -ValidateOnly
[IO.File]::WriteAllText((Join-Path $dir 'preflight.json'),($preflight|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$preflight
