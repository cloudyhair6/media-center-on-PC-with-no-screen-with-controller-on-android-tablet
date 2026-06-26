$ErrorActionPreference = "Stop"
$SdkRoot = "C:\Users\Will\OneDrive\github\copilot cli\minipc\android-sdk"
$ZipFile = "$SdkRoot\cmdline-tools.zip"

if (-not (Test-Path $SdkRoot)) {
    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
}

if (-not (Test-Path "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat")) {
    Write-Host "Downloading Android SDK Command-line Tools..."
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11479070_latest.zip" -OutFile $ZipFile
    Write-Host "Extracting..."
    Expand-Archive -Path $ZipFile -DestinationPath "$SdkRoot" -Force
    Remove-Item $ZipFile
    
    # cmdline-tools needs to be inside a 'latest' folder for sdkmanager to work
    if (Test-Path "$SdkRoot\cmdline-tools") {
        Rename-Item "$SdkRoot\cmdline-tools" "$SdkRoot\latest"
        New-Item -ItemType Directory -Force -Path "$SdkRoot\cmdline-tools" | Out-Null
        Move-Item "$SdkRoot\latest" "$SdkRoot\cmdline-tools\latest"
    }
}

Write-Host "Accepting licenses and downloading platforms..."
$SdkManager = "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"
# Auto-accept licenses
cmd /c "echo y| `"$SdkManager`" --sdk_root=`"$SdkRoot`" `"platforms;android-10`" `"build-tools;29.0.3`""

Write-Host "Done!"
