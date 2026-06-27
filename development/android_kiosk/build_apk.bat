@echo off
set SDKROOT=C:\Users\Will\OneDrive\github\copilot cli\minipc\development\android-sdk
set BUILD_TOOLS=%SDKROOT%\build-tools\29.0.3
set PLATFORM=%SDKROOT%\platforms\android-10
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_481
set PATH=%JAVA_HOME%\bin;%PATH%

if not exist bin mkdir bin
echo [1] Generating R.java and Packaging APK resources...
"%BUILD_TOOLS%\aapt.exe" package -f -m -J src\main\java -M src\main\AndroidManifest.xml -S src\main\res -I "%PLATFORM%\android.jar" -F bin\app.unaligned.apk

echo [2] Compiling Java...
if not exist obj mkdir obj
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -d obj -cp "%PLATFORM%\android.jar" -source 1.7 -target 1.7 src\main\java\com\minipc\kiosk\*.java

echo [3] Converting to DEX...
call "%JAVA_HOME%\bin\jar.exe" cvf bin\classes.jar -C obj .
call "%JAVA_HOME%\bin\java.exe" -cp "%BUILD_TOOLS%\lib\d8.jar" com.android.tools.r8.D8 --min-api 10 --output bin\ bin\classes.jar

echo [4] Adding DEX to APK...
cd bin
"%BUILD_TOOLS%\aapt.exe" add app.unaligned.apk classes.dex
cd ..

echo [5] Generating Keystore...
if not exist my-release-key.keystore "%JAVA_HOME%\bin\keytool.exe" -genkey -v -keystore my-release-key.keystore -alias mykey -keyalg RSA -keysize 2048 -validity 10000 -storepass password -keypass password -dname "CN=MiniPC, O=MiniPC, C=US"

echo [6] Signing APK...
call "%JAVA_HOME%\bin\java.exe" -jar "%BUILD_TOOLS%\lib\apksigner.jar" sign --ks my-release-key.keystore --ks-pass pass:password --out app.apk bin\app.unaligned.apk

echo DONE!
