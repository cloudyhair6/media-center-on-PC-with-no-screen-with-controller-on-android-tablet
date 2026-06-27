package com.minipc.kiosk;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

public class UnlockActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Toast.makeText(this, "Unlocking Tablet...", Toast.LENGTH_SHORT).show();
        
        // Disable kiosk lock in shared preferences so MainActivity finishes
        getApplicationContext().getSharedPreferences("MiniPC", MODE_PRIVATE)
            .edit().putBoolean("unlocked", true).commit();
        
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try { Thread.sleep(500); } catch (Exception e) {}
                return null;
            }
            @Override
            protected void onPostExecute(Void aVoid) {
                try {
                    // Clear the default launcher setting if MiniPC is currently the default
                    getPackageManager().clearPackagePreferredActivities(getPackageName());
                    
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                    intent.addCategory(android.content.Intent.CATEGORY_HOME);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {}
                finish();
            }
        }.execute();
    }
}
