$ErrorActionPreference = "Stop"
$SdkRoot = "C:\Users\Will\OneDrive\github\copilot cli\minipc\android-sdk"
$Extracted = "$SdkRoot\commandlinetools-win-14742923_latest\cmdline-tools"

if (Test-Path $Extracted) {
    Rename-Item "$Extracted" "$SdkRoot\latest"
    New-Item -ItemType Directory -Force -Path "$SdkRoot\cmdline-tools" | Out-Null
    Move-Item "$SdkRoot\latest" "$SdkRoot\cmdline-tools\latest"
}

$SdkManager = "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $SdkManager) {
    Write-Host "Accepting licenses and downloading API 10..."
    cmd /c "echo y| `"$SdkManager`" --sdk_root=`"$SdkRoot`" `"platforms;android-10`" `"build-tools;29.0.3`""
} else {
    Write-Host "Could not find sdkmanager at $SdkManager"
}
