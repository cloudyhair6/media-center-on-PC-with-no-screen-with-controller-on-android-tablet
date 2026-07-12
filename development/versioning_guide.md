# Media Centre Versioning Guide

When you make new changes and want to release a new version of the Android app to GitHub, you should increment the version number so that Android recognizes it as an update.

The version number for the app is defined in a single file: the Android Manifest.

## How to Update the Version

1. Open the file `development/android_kiosk/src/main/AndroidManifest.xml` in your code editor.
2. Near the top of the file, you will see two properties:
   - `android:versionCode="2"`
   - `android:versionName="2.0"`
3. **`versionCode`**: This is an integer used internally by Android to compare versions. **You must increase this number by 1** every time you release a new APK (e.g., change `"2"` to `"3"`).
4. **`versionName`**: This is the human-readable string shown to the user (e.g., "2.0"). You can change this to anything you want, such as `"2.1"`, `"2.0.1"`, or `"3.0"`.

### Example Update
If you are releasing version 2.1, change it to look like this:

```diff
-    android:versionCode="2"
-    android:versionName="2.0">
+    android:versionCode="3"
+    android:versionName="2.1">
```

5. Save the file.
6. Run `build_apk.bat` in the `development/android_kiosk` folder. The resulting `app.apk` in `tablet_apk/` will now officially be your new version!
