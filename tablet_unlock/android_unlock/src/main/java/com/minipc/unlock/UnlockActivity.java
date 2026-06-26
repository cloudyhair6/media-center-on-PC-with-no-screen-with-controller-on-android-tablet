package com.minipc.unlock;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UnlockActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);
        
        // We assume the PC IP is passed via intent or we can hit the default 192.168.x.x
        // For a more robust solution we'd share the SharedPreferences, but Android apps
        // are sandboxed.
        // For now, let's just use the shared preference if it exists via world-readable?
        // Actually, since API 10 doesn't have strict security on shared prefs, we could try
        // reading from the main app. But it's easier to just assume the same IP or hardcode.
        // Actually, the main app is polling /api/unlock_status from the PC. The PC is what triggers the unlock.
        // So the unlock activity just needs to hit the PC.
        // But how does it know the PC's IP?
        // Let's just finish() because this app itself isn't what unlocks it.
        // Wait, the plan says: The PC's "Unlock Tablet" button will run `adb shell am start` via subprocess.
        // Wait, if the PC runs `adb shell am start`, it can just stop the main app or bring up this app.
        // If this app comes up, it takes focus away from the Kiosk app, effectively breaking the kiosk lock because this app doesn't have a kiosk lock.
        // But the main app's blockSettings thread might kill this app if we aren't careful? No, blockSettings only kills "com.android.settings".
        // The main app polls /api/unlock_status on the PC.
        // If the main app sees /api/unlock_status returns true, it calls finish().
        // So the unlock activity isn't strictly necessary if the main app polls the PC.
        // The user asked for "create a separate application for unlocking the tablet" in the previous conversation.
        // Let's implement it so it just displays "Unlocked!" and finishes, taking over the screen.
        
        Toast.makeText(this, "Unlocking Tablet...", Toast.LENGTH_SHORT).show();
        
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try { Thread.sleep(1000); } catch (Exception e) {}
                return null;
            }
            @Override
            protected void onPostExecute(Void aVoid) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                    intent.addCategory(android.content.Intent.CATEGORY_HOME);
                    // Explicitly target ADW Launcher or Kindle Launcher if needed, but CATEGORY_HOME without package
                    // will bring up the default launcher chooser, or the next default launcher since Kiosk is finished.
                    // Let's just use generic HOME intent, the OS will handle it.
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {}
                finish();
            }
        }.execute();
    }
}
