param(
    [ValidateSet("app", "session")]
    [string]$Source = "app",
    [string]$Serial = "127.0.0.1:16384",
    [string]$Adb = "D:\MuMuPlayer\nx_device\15.0\shell\adb.exe"
)

$ErrorActionPreference = "Stop"
$package = "io.github.shinarin.azurpilotandroid"

if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) {
    throw "adb not found: $Adb"
}

& $Adb connect $Serial | Out-Host
if ($LASTEXITCODE -ne 0) { throw "adb connect failed: $Serial" }

if ($Source -eq "session") {
    $path = "/sdcard/Android/data/$package/files/log/proot/session.log"
    Write-Host "Watching $path (Ctrl+C to stop)"
    & $Adb -s $Serial shell "tail -n 100 -F $path"
    exit $LASTEXITCODE
}

$pidText = (& $Adb -s $Serial shell "pidof $package").Trim()
if (-not $pidText) { throw "$package is not running" }
$pidValue = ($pidText -split "\s+")[0]
Write-Host "Watching logcat for $package pid=$pidValue (Ctrl+C to stop)"
& $Adb -s $Serial logcat -v time --pid=$pidValue
exit $LASTEXITCODE
