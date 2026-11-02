# Nur Bibliothek für einen künftig separat freigegebenen Besitzerlauf. Kein Start,
# kein PID-Lookup und keine Termination. Der Aufrufer behält sein Process.Start-Objekt.
function Complete-ScOwnedObservation {
    param(
        [Parameter(Mandatory)] [object] $OwnedProcess,
        [Parameter(Mandatory)] [object] $Clock,
        [Parameter(Mandatory)] [long] $AbsoluteDeadlineMilliseconds,
        [bool] $StopRequested = $false
    )
    $ended = $false
    $exitCode = $null
    $observationFailed = $false
    try {
        if ($null -eq $OwnedProcess.SafeHandle -or $OwnedProcess.SafeHandle.IsInvalid -or $OwnedProcess.SafeHandle.IsClosed) {
            throw 'Owner handle unavailable'
        }
        # Nach STOP nur weiter beobachten, niemals normalen Test fortsetzen oder
        # die gemeinsame absolute Frist erneuern. Fremde PIDs sind keine Autorität.
        while ($true) {
            if ($OwnedProcess.HasExited) {
                $ended = $true
                $observedCode = $OwnedProcess.ExitCode
                # PowerShell kann einen fehlgeschlagenen ScriptProperty-Getter als
                # NULL liefern. NULL niemals durch einen Cast in Erfolgscode 0 wandeln.
                if ($null -eq $observedCode) { throw 'Exit code unavailable' }
                $exitCode = [int] $observedCode
                break
            }
            $remaining = $AbsoluteDeadlineMilliseconds - $Clock.ElapsedMilliseconds
            if ($remaining -le 0) { break }
            [void] $OwnedProcess.WaitForExit([int] [Math]::Min(10, $remaining))
        }
    } catch {
        # Keine Exceptiontexte, Prozessargumente, Pfade, Umgebung oder Handlewerte.
        $observationFailed = $true
    }
    [pscustomobject]@{
        Outcome = 'STOP' # Auch Exitcode 0 ist allein kein S/C/K-Abschlussbeleg.
        StopRequested = $StopRequested
        HeldEndObserved = $ended
        ExitCodeObserved = ($null -ne $exitCode)
        ExitCode = $exitCode
        ObservationFailed = $observationFailed
        ResourceEndProved = $false
        RetryAllowed = $false
        OwnerHandleDisposed = $false
    }
    # Absichtlich kein Dispose: der Besitzer muss zuerst berichten und bei UNKNOWN
    # seine gehaltene Beobachtungsautorität bewahren. Dieses Objekt nie per PID ersetzen.
}
