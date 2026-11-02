param(
    [string] $AttemptId,
    [string] $ArtifactHash,
    [string] $ApprovalHash,
    [switch] $ExecuteOnce
)
$ErrorActionPreference = 'Stop'
if (-not $ExecuteOnce) { throw 'SC_EXPLICIT_APPROVAL_REQUIRED' }
# Nur nach neuer ausdrücklicher OS-Freigabe ausführen. Dieses Skript erzeugt selbst
# keine Freigabe und kennt keinen historischen Freigabe-/Versuchspfad.
. (Join-Path $PSScriptRoot 'ScAttempt.ps1')
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$directory = Get-ScAttemptPath $root $AttemptId
$jna = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\net.java.dev.jna\jna\5.17.0\33d12735bef894440780fce64f9758d420c7bae2\jna-5.17.0.jar'
$classpath = @('scNative','custodianExecutable','jobAdapter','communicationHost') | ForEach-Object { Join-Path $root ('build/classes/java/' + $_) }
$classpath = @($classpath) + @($jna)
$context = [pscustomobject]@{
    Root=$root; Directory=$directory; AttemptId=$AttemptId; ArtifactHash=$ArtifactHash; ApprovalHash=$ApprovalHash
    Java='C:\Program Files\Java\jdk-21\bin\java.exe'; Classpath=$classpath; ObserverProcessId=$PID
}
function Get-ScArtifactFingerprint($Context) {
    $hash = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        Assert-ScCanonical $Context.Java
        $hash.AppendData([IO.File]::ReadAllBytes($Context.Java))
        foreach ($dir in $Context.Classpath[0..3]) {
            Assert-ScCanonical $dir
            $files = [IO.Directory]::GetFiles($dir, '*', [IO.SearchOption]::AllDirectories)
            [Array]::Sort($files, [StringComparer]::OrdinalIgnoreCase)
            if ($files.Count -lt 1 -or $files.Count -gt 1000) { throw 'SC_CLASS_COUNT' }
            foreach ($file in $files) {
                Assert-ScCanonical $file
                $relative = [IO.Path]::GetRelativePath($dir, $file)
                if ($relative -match '[^\x20-\x7e]' -or $relative -match 'Test\$?[^\\]*\.class$') { throw 'SC_CLASS_ROOT' }
                $hash.AppendData([Text.Encoding]::UTF8.GetBytes($relative))
                $hash.AppendData([IO.File]::ReadAllBytes($file))
            }
        }
        $jar = $Context.Classpath[4]
        Assert-ScCanonical $jar
        if ((Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant() -cne 'b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0') { throw 'SC_JNA_PIN' }
        $hash.AppendData([IO.File]::ReadAllBytes($jar))
        foreach ($name in @('ScAttempt.ps1','ScOwnedObservation.ps1','Start-ScEmptyOnce.ps1','ScOuterControl.ps1')) {
            $file = Join-Path $PSScriptRoot $name
            Assert-ScCanonical $file
            if ((Get-Item -LiteralPath $file).Length -gt 100000) { throw 'SC_SCRIPT_SIZE' }
            $hash.AppendData([Text.Encoding]::UTF8.GetBytes($name)); $hash.AppendData([IO.File]::ReadAllBytes($file))
        }
        [Convert]::ToHexString($hash.GetHashAndReset()).ToLowerInvariant()
    } finally { $hash.Dispose() }
}
$ports = @{
    OpenControl = {param($c,$s,$clock,$deadline);Open-ScOuterControl $c $s $clock $deadline}
    StopControl = {param($c,$s,$cap,$clock,$deadline);Request-ScOuterStop $c $s $cap $clock $deadline}
    ControlEnd = {param($cap);Test-ScOuterControlEnd $cap}
    Fingerprint = { param($c) Get-ScArtifactFingerprint $c }
    Clock = { [Diagnostics.Stopwatch]::StartNew() }
    Start = {
        param($c)
        $psi = [Diagnostics.ProcessStartInfo]::new()
        $psi.FileName=$c.Java; $psi.WorkingDirectory=$c.Root
        $psi.UseShellExecute=$false; $psi.CreateNoWindow=$true
        $psi.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
        $psi.RedirectStandardOutput=$true; $psi.RedirectStandardError=$true
        $psi.Environment.Clear()
        $psi.Environment['SystemRoot']='C:\Windows'; $psi.Environment['TEMP']=$c.Directory; $psi.Environment['TMP']=$c.Directory
        foreach ($argument in @('-Djna.nosys=true',('-Dmangalib.sc.attempt=' + $c.AttemptId),'-cp',($c.Classpath -join ';'),
                'de.mangalib.windowsadapter.ScSupervisorMain','--sc-native-empty-v1')) { $psi.ArgumentList.Add($argument) }
        $p = [Diagnostics.Process]::new(); $p.StartInfo=$psi
        # Bei unbestätigtem Start bleibt auch das eigene Beobachterobjekt erhalten.
        $script:ScRetainedOwner=$p
        try { $script:ScStartConfirmed=$p.Start() } catch { $script:ScStartConfirmed=$false }
        $p
    }
    Identity = {
        param($p)
        if (-not $script:ScStartConfirmed) { throw 'SC_START_UNCONFIRMED' }
        $held=$p.SafeHandle
        [pscustomobject]@{ProcessId=$p.Id;Birth=$p.StartTime.ToUniversalTime().ToFileTimeUtc().ToString();
            Image=$p.MainModule.FileName;Held=(-not $held.IsInvalid -and -not $held.IsClosed);Source='OWN_PROCESS_START_HANDLE'}
    }
    BeginReads = {
        param($p)
        $outBuffer=[char[]]::new(1); $errBuffer=[char[]]::new(1025)
        [pscustomobject]@{Out=$outBuffer;Err=$errBuffer;ErrorReader=$p.StandardError;OutTask=$p.StandardOutput.ReadAsync($outBuffer,0,1);
            ErrTask=$p.StandardError.ReadAsync($errBuffer,0,1025)}
    }
    Children = {
        param($p,$s,$c)
        $args=@($c.Java,'-Djna.nosys=true',('-Dmangalib.sc.attempt=' + $c.AttemptId),'-cp',($c.Classpath -join ';'),
            'de.mangalib.windowsadapter.ScCustodianMain','--sc-native-empty-v1')
        # Die festen Argumente enthalten weder Quotes noch abschließende Backslashes;
        # entspricht deshalb exakt Win64LaunchSpec.quote, kein contains()-Provider.
        if (@($args | Where-Object { $_ -match '["\r\n]' -or $_.EndsWith('\') }).Count -ne 0) { throw 'SC_ARGUMENTS' }
        $expected=($args | ForEach-Object { '"' + $_ + '"' }) -join ' '
        Get-CimInstance Win32_Process -Filter ('ParentProcessId = ' + $p.Id) -Property ProcessId,ParentProcessId,CreationDate,ExecutablePath,CommandLine -OperationTimeoutSec 1 -ErrorAction Stop |
            ForEach-Object { [pscustomobject]@{ProcessId=$_.ProcessId;ParentProcessId=$_.ParentProcessId;
                Birth=$(if ($null -ne $_.CreationDate) { $_.CreationDate.ToUniversalTime().ToFileTimeUtc().ToString() } else { $null });
                Image=$_.ExecutablePath;CustodianCommandMatches=($_.CommandLine -ceq $expected)} }
    }
    Reads = {
        param($r,$clock,$deadline)
        Read-ScOwnerStreams $r $clock $deadline
    }
    Evidence = {
        param($c)
        $file=Join-Path $c.Directory 'native/result.txt'
        if (Test-Path -LiteralPath $file) {
            Assert-ScCanonical $file
            if ((Get-Item -LiteralPath $file).Length -gt 1024) { throw 'SC_RESULT_SIZE' }
            [IO.File]::ReadAllText($file)
        } else { $null }
    }
}
# Kein Start allein durch eine UUID: expliziter Schalter, vorab extern freigegebene
# approval.txt und beide erwarteten Hashes sind zusätzlich erforderlich.
$result=Invoke-ScAttemptCore $context $ports -ExecuteOnce:$ExecuteOnce -RequireOuterControl
$script:ScRetainedOwner=$result.RetainedOwner
Write-ScNewDurable (Join-Path $directory 'outer-result.json') ($result.Report | ConvertTo-Json -Depth 8)
$result.Report
