package com.minipc.kiosk;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private String currentIp = "";
    private ApiClient api;

    private LinearLayout screenConnection;
    private LinearLayout screenMain;
    private LinearLayout ipListContainer;
    private LinearLayout loadingOverlay;
    private TextView loadingText;
    private TextView statusToast;

    // Tabs
    private LinearLayout tabMusic;
    private ScrollView tabSettings;
    private LinearLayout tabPower;
    private Button[] tabButtons;

    // Music Sub-tabs
    private ScrollView musicNowPlaying;
    private LinearLayout musicSearch;
    private ScrollView musicLibrary;
    private ScrollView musicQueue;
    private String currentNpUri = "";
    private boolean isLiked = false;
    private Button[] musicSubTabButtons;

    // UI Elements - Now Playing
    private TextView npTitle, npArtist, npAlbum, volLabel, npTimeCurrent, npTimeTotal;
    private SeekBar npProgress;
    private int currentPositionS = 0;
    private int currentLengthS = 0;
    private boolean isNpPlaying = false;
    private ImageView npArtwork;
    // UI Elements - Movies
    private TextView currentPathText, movieTitleText, movieTimeText, movieInfoText;
    private String currentLibraryFolderUri = null;
    private JSONArray cachedLibraryItems = null;
    // UI Elements - Settings
    private ToggleButton toggleAlbumArt;
    private Button btnTheme;
    private TextView cpuText, ramText, diskText, gpuText, lastUpdatedText;
    private ProgressBar cpuBar, ramBar, diskBar, gpuBar;
    
    private SeekBar seekBarVolume;
    private boolean isTrackingVolume = false;

    private Handler handler = new Handler();
    private String currentMoviePath = "";
    private boolean allowSettings = false;
    private boolean shuffleOn = false;
    private String repeatState = "off";

    // Polling Runnables
    private Runnable pollSpotify = new Runnable() {
        @Override
        public void run() {
            if (screenMain.getVisibility() == View.VISIBLE && tabMusic.getVisibility() == View.VISIBLE && musicNowPlaying.getVisibility() == View.VISIBLE) {
                api.get("/api/spotify/now_playing", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            npTitle.setText(json.optString("title", "Not Playing"));
                            npArtist.setText(json.optString("artist", ""));
                            npAlbum.setText(json.optString("album", ""));
                            isNpPlaying = json.optBoolean("playing", false);
                            currentPositionS = json.optInt("position_s", 0);
                            currentLengthS = json.optInt("length_s", 0);
                            if (npTimeCurrent != null) npTimeCurrent.setText(formatTime(currentPositionS));
                            if (npTimeTotal != null) npTimeTotal.setText(formatTime(currentLengthS));
                            if (npProgress != null) {
                                npProgress.setMax(currentLengthS);
                                npProgress.setProgress(currentPositionS);
                            }
                            String uri = json.optString("uri", "");
                            if (!uri.equals(currentNpUri)) {
                                currentNpUri = uri;
                                if (!uri.isEmpty()) {
                                    if (prefs.getBoolean("show_album_art", true)) {
                                        loadAlbumArt(uri);
                                    }
                                    checkIfLiked(uri);
                                }
                            }
                        } catch (Exception e) {}
                    }
                    @Override
                    public void onError(String error) {}
                });
                
                api.get("/api/volume/current", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            int v = json.getInt("volume");
                            volLabel.setText(v + "%");
                            if (seekBarVolume != null && !isTrackingVolume) {
                                seekBarVolume.setProgress(v);
                            }
                        } catch (Exception e) {}
                    }
                    @Override
                    public void onError(String error) {}
                });
            }
            handler.postDelayed(this, 2000);
        }
    };

    private Runnable progressExtrapolator = new Runnable() {
        @Override
        public void run() {
            if (isNpPlaying && currentLengthS > 0 && currentPositionS < currentLengthS) {
                currentPositionS++;
                if (npTimeCurrent != null) npTimeCurrent.setText(formatTime(currentPositionS));
                if (npProgress != null) npProgress.setProgress(currentPositionS);
            }
            handler.postDelayed(this, 1000);
        }
    };


    private Runnable pollSystemStats = new Runnable() {
        @Override
        public void run() {
            if (screenMain.getVisibility() == View.VISIBLE && tabSettings.getVisibility() == View.VISIBLE) {
                api.get("/api/system/stats", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            int cpu = json.optInt("cpu", 0);
                            int ram = json.optInt("ram", 0);
                            int disk = json.optInt("disk", 0);
                            int gpu = json.optInt("gpu", 0);
                            String lastUpdated = json.optString("last_updated", "--");
                            
                            if (cpuText != null) cpuText.setText("CPU: " + cpu + "%");
                            if (cpuBar != null) cpuBar.setProgress(cpu);
                            if (ramText != null) ramText.setText("RAM: " + ram + "%");
                            if (ramBar != null) ramBar.setProgress(ram);
                            if (diskText != null) diskText.setText("Disk RW: " + disk + "%");
                            if (diskBar != null) diskBar.setProgress(disk);
                            if (gpuText != null) gpuText.setText("GPU: " + gpu + "%");
                            if (gpuBar != null) gpuBar.setProgress(gpu);
                            if (lastUpdatedText != null) lastUpdatedText.setText("Last Updated: " + lastUpdated);
                        } catch (Exception e) {}
                    }
                    @Override
                    public void onError(String error) {}
                });
            }
            handler.postDelayed(this, 3000);
        }
    };

    private Runnable pollUnlock = new Runnable() {
        @Override
        public void run() {
            if (api != null) {
                api.get("/api/unlock_status", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            if (json.optBoolean("unlock", false)) {
                                if (getApplicationContext().getSharedPreferences("MiniPC", MODE_PRIVATE).getBoolean("unlocked", false)) return;
                                getApplicationContext().getSharedPreferences("MiniPC", MODE_PRIVATE).edit().putBoolean("unlocked", true).commit();
                                
                                allowSettings = true;
                                
                                final String[] launchers = {"Kindle Launcher", "ADW Launcher", "Android Settings"};
                                final String[] packages = {"com.amazon.kindle.otter.launcher", "org.adw.launcher", "com.android.settings"};
                                
                                new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Unlocked - Choose App")
                                    .setItems(launchers, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            try {
                                                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packages[which]);
                                                if (launchIntent != null) {
                                                    startActivity(launchIntent);
                                                } else {
                                                    // Fallback for settings
                                                    Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    startActivity(intent);
                                                }
                                            } catch (Exception e) {}
                                            finish();
                                        }
                                    })
                                    .setCancelable(false)
                                    .show();
                            } else {
                                getApplicationContext().getSharedPreferences("MiniPC", MODE_PRIVATE).edit().putBoolean("unlocked", false).commit();
                                allowSettings = false;
                            }
                        } catch (Exception e) {}
                    }
                    @Override
                    public void onError(String error) {}
                });
            }
            handler.postDelayed(this, 3000);
        }
    };
    private Runnable blockSettings = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || allowSettings) {
                handler.postDelayed(this, 1000);
                return;
            }
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                try {
                    String topPackage = am.getRunningTasks(1).get(0).topActivity.getPackageName();
                    if (topPackage.equals("com.android.settings") || topPackage.equals("com.amazon.kindle.otter.settings")) {
                        Intent i = new Intent(MainActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(i);
                    }
                } catch (Exception e) {}
            }
            handler.postDelayed(this, 1000);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("MiniPC", MODE_PRIVATE);

        initViews();
        setupConnectionScreen();
        setupTabs();
        setupMusicControls();
        setupSettings();
        setupAudioControls();
        setupPowerControls();

        handler.post(pollSpotify);
        handler.post(progressExtrapolator);
        handler.post(pollSystemStats);
        handler.post(pollUnlock);
        handler.post(blockSettings);

        // Auto-connect
        String lastIp = prefs.getString("last_ip", "");
        if (!lastIp.isEmpty()) {
            connectToIp(lastIp);
        }
        
        // Apply theme on startup
        applyTheme(prefs.getString("theme", "dark"));
    }

    private void initViews() {
        try { screenConnection = (LinearLayout) findViewById(R.id.screen_connection); } catch (Exception e) { android.util.Log.e("DEBUG", "screen_connection failed", e); }
        try { screenMain = (LinearLayout) findViewById(R.id.screen_main); } catch (Exception e) { android.util.Log.e("DEBUG", "screen_main failed", e); }
        try { ipListContainer = (LinearLayout) findViewById(R.id.ip_list_container); } catch (Exception e) { android.util.Log.e("DEBUG", "ip_list_container failed", e); }
        try { loadingOverlay = (LinearLayout) findViewById(R.id.loading_overlay); } catch (Exception e) { android.util.Log.e("DEBUG", "loading_overlay failed", e); }
        try { loadingText = (TextView) findViewById(R.id.loading_text); } catch (Exception e) { android.util.Log.e("DEBUG", "loading_text failed", e); }
        try { statusToast = (TextView) findViewById(R.id.status_toast); } catch (Exception e) { android.util.Log.e("DEBUG", "status_toast failed", e); }

        try { tabMusic = (LinearLayout) findViewById(R.id.tab_music); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_music failed", e); }
        try { tabSettings = (ScrollView) findViewById(R.id.tab_settings); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_settings failed", e); }
        try { tabPower = (LinearLayout) findViewById(R.id.tab_power); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_power failed", e); }

        try { tabButtons = new Button[]{
            (Button) findViewById(R.id.tab_btn_music),
            (Button) findViewById(R.id.tab_btn_settings),
            (Button) findViewById(R.id.tab_btn_power)
        }; } catch (Exception e) { android.util.Log.e("DEBUG", "tabButtons failed", e); }

        try { musicNowPlaying = (ScrollView) findViewById(R.id.music_now_playing); } catch (Exception e) { android.util.Log.e("DEBUG", "musicNowPlaying failed", e); }
        try { musicSearch = (LinearLayout) findViewById(R.id.music_search); } catch (Exception e) { android.util.Log.e("DEBUG", "musicSearch failed", e); }
        try { musicLibrary = (ScrollView) findViewById(R.id.music_library); } catch (Exception e) { android.util.Log.e("DEBUG", "musicLibrary failed", e); }
        try { musicQueue = (ScrollView) findViewById(R.id.music_queue); } catch (Exception e) { android.util.Log.e("DEBUG", "musicQueue failed", e); }

        try { musicSubTabButtons = new Button[]{
            (Button) findViewById(R.id.btn_music_playing),
            (Button) findViewById(R.id.btn_music_search),
            (Button) findViewById(R.id.btn_music_library),
            (Button) findViewById(R.id.btn_music_queue)
        }; } catch (Exception e) { android.util.Log.e("DEBUG", "musicSubTabButtons failed", e); }

        try { npTitle = (TextView) findViewById(R.id.np_title); } catch (Exception e) { android.util.Log.e("DEBUG", "npTitle failed", e); }
        try { npArtist = (TextView) findViewById(R.id.np_artist); } catch (Exception e) { android.util.Log.e("DEBUG", "npArtist failed", e); }
        try { npAlbum = (TextView) findViewById(R.id.np_album); } catch (Exception e) { android.util.Log.e("DEBUG", "npAlbum failed", e); }
        try { volLabel = (TextView) findViewById(R.id.vol_label); } catch (Exception e) { android.util.Log.e("DEBUG", "volLabel failed", e); }
        try { npArtwork = (ImageView) findViewById(R.id.np_artwork); } catch (Exception e) { android.util.Log.e("DEBUG", "npArtwork failed", e); }
        try { npTimeCurrent = (TextView) findViewById(R.id.np_time_current); } catch (Exception e) {}
        try { npTimeTotal = (TextView) findViewById(R.id.np_time_total); } catch (Exception e) {}
        try { 
            npProgress = (SeekBar) findViewById(R.id.np_progress); 
            if (npProgress != null) {
                npProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {
                        int seekMs = seekBar.getProgress() * 1000;
                        sendCommand("/api/spotify/seek?ms=" + seekMs + "&type=absolute");
                    }
                });
            }
        } catch (Exception e) {}

        try { toggleAlbumArt = (ToggleButton) findViewById(R.id.toggle_album_art); } catch (Exception e) { android.util.Log.e("DEBUG", "toggleAlbumArt failed", e); }
        try { btnTheme = (Button) findViewById(R.id.btn_theme); } catch (Exception e) { android.util.Log.e("DEBUG", "btnTheme failed", e); }
        try { cpuText = (TextView) findViewById(R.id.cpu_text); } catch (Exception e) {}
        try { ramText = (TextView) findViewById(R.id.ram_text); } catch (Exception e) {}
        try { diskText = (TextView) findViewById(R.id.disk_text); } catch (Exception e) {}
        try { gpuText = (TextView) findViewById(R.id.gpu_text); } catch (Exception e) {}
        try { lastUpdatedText = (TextView) findViewById(R.id.last_updated_text); } catch (Exception e) {}
        try { cpuBar = (ProgressBar) findViewById(R.id.cpu_bar); } catch (Exception e) {}
        try { ramBar = (ProgressBar) findViewById(R.id.ram_bar); } catch (Exception e) {}
        try { diskBar = (ProgressBar) findViewById(R.id.disk_bar); } catch (Exception e) {}
        try { gpuBar = (ProgressBar) findViewById(R.id.gpu_bar); } catch (Exception e) {}

        try { seekBarVolume = (SeekBar) findViewById(R.id.seekBar_volume); } catch (Exception e) { android.util.Log.e("DEBUG", "seekBarVolume failed", e); }
        try { volLabel = (TextView) findViewById(R.id.vol_label); } catch (Exception e) {}    
        toggleAlbumArt.setChecked(prefs.getBoolean("show_album_art", true));
        
        ((Button) findViewById(R.id.btn_disconnect)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                screenMain.setVisibility(View.GONE);
                screenConnection.setVisibility(View.VISIBLE);
                api = null;
            }
        });

        ((Button) findViewById(R.id.btn_refresh)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                npTitle.setText("Refreshing...");
                npArtist.setText("");
                npAlbum.setText("");
                npArtwork.setImageBitmap(null);
                ProgressBar npArtProgress = (ProgressBar) findViewById(R.id.np_art_progress);
                if (npArtProgress != null) npArtProgress.setVisibility(View.GONE);
                LinearLayout npArtErrorLayout = (LinearLayout) findViewById(R.id.np_art_error_layout);
                if (npArtErrorLayout != null) npArtErrorLayout.setVisibility(View.GONE);
                
                currentNpUri = "";
                lastArtUri = "";
                
                if (npTimeCurrent != null) npTimeCurrent.setText("--:--");
                if (npTimeTotal != null) npTimeTotal.setText("--:--");
                if (npProgress != null) npProgress.setProgress(0);
                
                volLabel.setText("--%");
                // Immediately poll the server again for everything
                handler.removeCallbacks(pollSpotify);
                handler.removeCallbacks(pollSystemStats);
                
                handler.post(pollSpotify);
                handler.post(pollSystemStats);
            }
        });
    }

    private void setupConnectionScreen() {
        ipListContainer.removeAllViews();
        for (int i = 0; i < 5; i++) {
            final String ip = prefs.getString("ip_" + i, "");
            if (!ip.isEmpty()) {
                Button btn = new Button(this);
                btn.setText(ip);
                btn.setTextColor(0xFFFFFFFF);
                btn.setBackgroundColor(0xFF1a1f36);
                btn.setPadding(15, 15, 15, 15);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 10);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        connectToIp(ip);
                    }
                });
                ipListContainer.addView(btn);
            }
        }

        ((Button) findViewById(R.id.btn_add_pc)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(MainActivity.this);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Add New PC")
                    .setMessage("Enter IP address:")
                    .setView(input)
                    .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String newIp = input.getText().toString().trim();
                            if (!newIp.isEmpty()) {
                                saveIp(newIp);
                                connectToIp(newIp);
                            }
                        }
                    }).setNegativeButton("Cancel", null).show();
            }
        });
    }

    private void showPlaylistSelector(final String trackUri) {
        showLoading("Loading Playlists...");
        api.get("/api/spotify/library/hierarchy", new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                hideLoading();
                try {
                    JSONObject json = new JSONObject(response);
                    JSONArray items = json.optJSONArray("items");
                    if (items == null) return;
                    
                    final java.util.ArrayList<String> names = new java.util.ArrayList<String>();
                    final java.util.ArrayList<String> uris = new java.util.ArrayList<String>();
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        if ("playlist".equals(item.optString("type"))) {
                            names.add(item.optString("name"));
                            uris.add(item.optString("uri"));
                        }
                    }
                    
                    String[] nameArray = names.toArray(new String[0]);
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Add to Playlist")
                        .setItems(nameArray, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                String playlistUri = uris.get(which);
                                sendCommand("/api/spotify/playlist/add?playlist_uri=" + 
                                    java.net.URLEncoder.encode(playlistUri) + 
                                    "&track_uri=" + java.net.URLEncoder.encode(trackUri));
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } catch (Exception e) {}
            }
            @Override public void onError(String error) {
                hideLoading();
                showToast("Failed to load playlists", false);
            }
        });
    }

    private void saveIp(String ip) {
        for (int i = 0; i < 5; i++) {
            if (prefs.getString("ip_" + i, "").equals(ip)) return; // Already exists
        }
        for (int i = 4; i > 0; i--) {
            prefs.edit().putString("ip_" + i, prefs.getString("ip_" + (i - 1), "")).commit();
        }
        prefs.edit().putString("ip_0", ip).commit();
        setupConnectionScreen();
    }

    private void connectToIp(String ip) {
        currentIp = ip;
        prefs.edit().putString("last_ip", ip).commit();
        api = new ApiClient(ip);
        
        showLoading("Connecting to " + ip + "...");
        api.get("/api/status", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                hideLoading();
                showToast("Connected", true);
                screenConnection.setVisibility(View.GONE);
                screenMain.setVisibility(View.VISIBLE);
                switchTab(0);
            }
            @Override
            public void onError(String error) {
                hideLoading();
                showToast("Failed to connect", false);
            }
        });
    }

    private void setupTabs() {
        for (int i = 0; i < tabButtons.length; i++) {
            final int index = i;
            tabButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(index);
                }
            });
        }
        for (int i = 0; i < musicSubTabButtons.length; i++) {
            final int index = i;
            musicSubTabButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchMusicSubTab(index);
                }
            });
        }
    }

    private void switchTab(int index) {
        tabMusic.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabPower.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setTextColor(i == index ? 0xFF00d4ff : 0xFF8892b0);
            tabButtons[i].setBackgroundColor(i == index ? 0xFF1a1f36 : 0xFF111827);
        }

        TextView title = (TextView) findViewById(R.id.tab_title);
        if (title != null) {
            if (index == 0) title.setText("Music");
            if (index == 1) title.setText("Settings");
            if (index == 2) title.setText("Power");
        }
    }

    private void switchMusicSubTab(int index) {
        musicNowPlaying.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        musicSearch.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        musicLibrary.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (musicQueue != null) musicQueue.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < musicSubTabButtons.length; i++) {
            musicSubTabButtons[i].setTextColor(i == index ? 0xFF00d4ff : 0xFF8892b0);
            musicSubTabButtons[i].setBackgroundColor(i == index ? 0xFF1a1f36 : 0xFF111827);
        }
        
        if (index == 2) loadLibrary();
        if (index == 3) loadQueue();
    }

    private void setupMusicControls() {
        ((Button) findViewById(R.id.btn_play_pause)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/command/spotify_play_pause"); }
        });
        ((Button) findViewById(R.id.btn_next)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/command/spotify_next"); }
        });
        ((Button) findViewById(R.id.btn_prev)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/command/spotify_prev"); }
        });
        ((Button) findViewById(R.id.btn_seek_fwd)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/spotify/seek?ms=15000"); }
        });
        ((Button) findViewById(R.id.btn_seek_back)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/spotify/seek?ms=-15000"); }
        });
        final Button btnNpLike = (Button) findViewById(R.id.btn_np_like);
        if (btnNpLike != null) {
            btnNpLike.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!currentNpUri.isEmpty()) {
                        isLiked = !isLiked;
                        btnNpLike.setText("Like: " + (isLiked ? "YES" : "NO"));
                        btnNpLike.setTextColor(isLiked ? 0xFF00d4ff : 0xFFffffff);
                        if (isLiked) {
                            sendCommand("/api/spotify/library/add?uri=" + java.net.URLEncoder.encode(currentNpUri));
                        } else {
                            sendCommand("/api/spotify/library/remove?uri=" + java.net.URLEncoder.encode(currentNpUri));
                        }
                    }
                }
            });
        }
        ((Button) findViewById(R.id.btn_np_playlist)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!currentNpUri.isEmpty()) {
                    showPlaylistSelector(currentNpUri);
                }
            }
        });
        if (seekBarVolume != null) {
            seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && volLabel != null) {
                        volLabel.setText(progress + "%");
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isTrackingVolume = true;
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isTrackingVolume = false;
                    api.get("/api/volume/set?vol=" + seekBar.getProgress(), null);
                }
            });

            Button btnVolDown = (Button) findViewById(R.id.btn_vol_down);
            if (btnVolDown != null) {
                btnVolDown.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        int current = seekBarVolume.getProgress();
                        int newVol = Math.max(0, current - 5);
                        seekBarVolume.setProgress(newVol);
                        if (volLabel != null) volLabel.setText(newVol + "%");
                        api.get("/api/volume/set?vol=" + newVol, null);
                    }
                });
            }

            Button btnVolUp = (Button) findViewById(R.id.btn_vol_up);
            if (btnVolUp != null) {
                btnVolUp.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        int current = seekBarVolume.getProgress();
                        int newVol = Math.min(100, current + 5);
                        seekBarVolume.setProgress(newVol);
                        if (volLabel != null) volLabel.setText(newVol + "%");
                        api.get("/api/volume/set?vol=" + newVol, null);
                    }
                });
            }
        }
        
        // Shuffle button
        final Button btnShuffle = (Button) findViewById(R.id.btn_shuffle);
        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    shuffleOn = !shuffleOn;
                    btnShuffle.setText("Shuffle: " + (shuffleOn ? "ON" : "OFF"));
                    btnShuffle.setTextColor(shuffleOn ? 0xFF00d4ff : 0xFFffffff);
                    api.get("/api/spotify/shuffle?state=" + (shuffleOn ? "true" : "false"), new ApiClient.Callback() {
                        @Override public void onSuccess(String r) { showToast("Shuffle " + (shuffleOn ? "ON" : "OFF"), true); }
                        @Override public void onError(String error) { showToast("Shuffle failed", false); }
                    });
                }
            });
        }
        
        // Repeat button (cycles: off -> context -> track -> off)
        final Button btnRepeat = (Button) findViewById(R.id.btn_repeat);
        if (btnRepeat != null) {
            btnRepeat.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (repeatState.equals("off")) repeatState = "context";
                    else if (repeatState.equals("context")) repeatState = "track";
                    else repeatState = "off";
                    String label = "OFF";
                    if (repeatState.equals("context")) label = "ALL";
                    if (repeatState.equals("track")) label = "ONE";
                    btnRepeat.setText("Repeat: " + label);
                    btnRepeat.setTextColor(repeatState.equals("off") ? 0xFFffffff : 0xFF00d4ff);
                    final String st = repeatState;
                    api.get("/api/spotify/repeat?state=" + st, new ApiClient.Callback() {
                        @Override public void onSuccess(String r) { showToast("Repeat: " + st, true); }
                        @Override public void onError(String e) { showToast("Repeat failed", false); }
                    });
                }
            });
        }
        
        // Search setup
        Spinner typeSpinner = (Spinner) findViewById(R.id.search_type_spinner);
        String[] types = new String[]{"track", "album", "artist", "playlist"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
        
        ((Button) findViewById(R.id.btn_search)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                EditText input = (EditText) findViewById(R.id.search_input);
                EditText limitInput = (EditText) findViewById(R.id.search_limit);
                Spinner sp = (Spinner) findViewById(R.id.search_type_spinner);
                String q = input.getText().toString();
                String l = limitInput.getText().toString();
                if (l.isEmpty()) l = "10";
                String t = sp.getSelectedItem().toString();
                if (!q.isEmpty()) {
                    showLoading("Searching...");
                    api.get("/api/spotify/search?q=" + java.net.URLEncoder.encode(q) + "&type=" + t + "&limit=" + l, new ApiClient.Callback() {
                        @Override public void onSuccess(String response) {
                            hideLoading();
                            renderSearchResults(response);
                        }
                        @Override public void onError(String error) {
                            hideLoading();
                            showToast("Search failed", false);
                        }
                    });
                }
            }
        });
        
        // Movie controls setup
    }

    private void renderSearchResults(String jsonStr) {
        LinearLayout container = (LinearLayout) findViewById(R.id.search_results);
        container.removeAllViews();
        try {
            JSONObject json = new JSONObject(jsonStr);
            JSONArray items = json.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    final JSONObject item = items.getJSONObject(i);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(10, 20, 10, 20);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    
                    // Artwork (Left)
                    final RelativeLayout artContainer = new RelativeLayout(MainActivity.this);
                    artContainer.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                    artContainer.setPadding(0, 0, 15, 0);
                    row.addView(artContainer);
                    
                    final ImageView iv = new ImageView(MainActivity.this);
                    iv.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT));
                    artContainer.addView(iv);
                    
                    final ProgressBar pb = new ProgressBar(MainActivity.this);
                    RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    pbParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                    pb.setLayoutParams(pbParams);
                    pb.setVisibility(View.GONE);
                    artContainer.addView(pb);
                    
                    final LinearLayout errLayout = new LinearLayout(MainActivity.this);
                    errLayout.setOrientation(LinearLayout.VERTICAL);
                    errLayout.setGravity(android.view.Gravity.CENTER);
                    RelativeLayout.LayoutParams errParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    errParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                    errLayout.setLayoutParams(errParams);
                    errLayout.setVisibility(View.GONE);
                    
                    final TextView errText = new TextView(MainActivity.this);
                    errText.setTextColor(0xFFFFFFFF);
                    errText.setTextSize(10);
                    errText.setGravity(android.view.Gravity.CENTER);
                    errLayout.addView(errText);
                    
                    final Button retryBtn = new Button(MainActivity.this);
                    retryBtn.setText("Retry");
                    retryBtn.setTextSize(10);
                    retryBtn.setPadding(2, 2, 2, 2);
                    retryBtn.setTextColor(0xFFFFFFFF);
                    retryBtn.setBackgroundColor(0x44FFFFFF);
                    errLayout.addView(retryBtn);
                    
                    artContainer.addView(errLayout);

                    final String uri = item.optString("uri", "");
                    
                    final Runnable fetchImage = new Runnable() {
                        @Override
                        public void run() {
                            iv.setImageBitmap(null);
                            errLayout.setVisibility(View.GONE);
                            
                            if (uri.isEmpty()) {
                                pb.setVisibility(View.GONE);
                                iv.setImageResource(R.drawable.ic_error);
                                errText.setText("URL missing");
                                errLayout.setVisibility(View.VISIBLE);
                                return;
                            }
                            
                            pb.setVisibility(View.VISIBLE);
                            
                            api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {
                                    try {
                                        JSONObject j = new JSONObject(response);
                                        final String imgUrl = j.optString("thumbnail_url", "");
                                        final String highResUrl = j.optString("high_res_url", "");
                                        if (!imgUrl.isEmpty()) {
                                            new AsyncTask<Void, Void, Bitmap>() {
                                                @Override protected Bitmap doInBackground(Void... voids) {
                                                    try { return BitmapFactory.decodeStream(new URL(imgUrl).openStream()); } catch (Exception e) { return null; }
                                                }
                                                @Override protected void onPostExecute(Bitmap b) {
                                                    pb.setVisibility(View.GONE);
                                                    if (b != null) {
                                                        iv.setImageBitmap(b);
                                                        if (!highResUrl.isEmpty() && !highResUrl.equals(imgUrl)) {
                                                            new AsyncTask<Void, Void, Bitmap>() {
                                                                @Override protected Bitmap doInBackground(Void... voids) {
                                                                    try { return BitmapFactory.decodeStream(new URL(highResUrl).openStream()); } catch (Exception e) { return null; }
                                                                }
                                                                @Override protected void onPostExecute(Bitmap hb) {
                                                                    if (hb != null) iv.setImageBitmap(hb);
                                                                }
                                                            }.execute();
                                                        }
                                                    } else showError("Download failed");
                                                }
                                            }.execute();
                                        } else showError("Download failed");
                                    } catch (Exception e) { showError("Download failed"); }
                                }
                                @Override public void onError(String error) { showError("Download failed"); }
                                
                                private void showError(String msg) {
                                    pb.setVisibility(View.GONE);
                                    iv.setImageResource(R.drawable.ic_error);
                                    errText.setText(msg);
                                    errLayout.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    };
                    
                    retryBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { fetchImage.run(); }
                    });
                    
                    fetchImage.run();

                    // Text (Middle)
                    LinearLayout textCol = new LinearLayout(MainActivity.this);
                    textCol.setOrientation(LinearLayout.VERTICAL);
                    textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    
                    TextView tvName = new TextView(MainActivity.this);
                    tvName.setText(item.optString("name") + " (" + item.optString("type") + ")");
                    tvName.setTextSize(18);
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                    textCol.addView(tvName);

                    TextView tvArtist = new TextView(MainActivity.this);
                    tvArtist.setText(item.optString("artist", ""));
                    tvArtist.setTextSize(14);
                    tvArtist.setTextColor(0xFF8892b0);
                    textCol.addView(tvArtist);
                    
                    row.addView(textCol);
                    
                    // Buttons (Right)
                    LinearLayout btns = new LinearLayout(MainActivity.this);
                    btns.setOrientation(LinearLayout.HORIZONTAL);
                    
                    String type = item.optString("type", "");
                    
                    Button playBtn = new Button(MainActivity.this);
                    playBtn.setText("Play");
                    playBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri)); }
                    });
                    btns.addView(playBtn);
                    
                    Button queueBtn = new Button(MainActivity.this);
                    queueBtn.setText("Queue");
                    queueBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/queue/add?uri=" + java.net.URLEncoder.encode(uri)); }
                    });
                    btns.addView(queueBtn);

                    Button likeBtn = new Button(MainActivity.this);
                    if (type.equals("album") || type.equals("playlist")) {
                        likeBtn.setText("Add to Library");
                    } else {
                        likeBtn.setText("Like");
                    }
                    likeBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/library/add?uri=" + java.net.URLEncoder.encode(uri)); }
                    });
                    btns.addView(likeBtn);

                    if (type.equals("track")) {
                        Button playlistBtn = new Button(MainActivity.this);
                        playlistBtn.setText("Add to Playlist");
                        playlistBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { showPlaylistSelector(uri); }
                        });
                        btns.addView(playlistBtn);
                    }
                    
                    row.addView(btns);
                    container.addView(row);
                }
                applyTheme(prefs.getString("theme", "dark"));
            }
        } catch (Exception e) {}
    }

    private void loadLibrary() {
        showLoading("Loading Library...");
        api.get("/api/spotify/library/hierarchy", new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                hideLoading();
                try {
                    JSONObject json = new JSONObject(response);
                    cachedLibraryItems = json.optJSONArray("items");
                    currentLibraryFolderUri = null;
                    renderLibraryFolder();
                } catch (Exception e) {}
            }
            @Override public void onError(String error) {
                hideLoading();
                showToast("Failed to load library", false);
            }
        });
    }

    private java.util.Set<String> expandedFolders = new java.util.HashSet<>();

    private boolean isItemVisible(String parentUri) {
        if (parentUri == null || parentUri.isEmpty()) return true;
        if (!expandedFolders.contains(parentUri)) return false;
        // Check parent's parent
        try {
            for (int i = 0; i < cachedLibraryItems.length(); i++) {
                JSONObject p = cachedLibraryItems.getJSONObject(i);
                if (parentUri.equals(p.optString("uri", ""))) {
                    return isItemVisible(p.optString("parent_uri", null));
                }
            }
        } catch (Exception e) {}
        return true;
    }

    private String getFolderArtUri(String folderUri) {
        try {
            for (int i = 0; i < cachedLibraryItems.length(); i++) {
                JSONObject item = cachedLibraryItems.getJSONObject(i);
                if (folderUri.equals(item.optString("parent_uri", null))) {
                    if (item.optString("type", "playlist").equals("playlist")) {
                        return item.optString("uri", "");
                    } else {
                        String childArt = getFolderArtUri(item.optString("uri", ""));
                        if (childArt != null) return childArt;
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private void renderLibraryFolder() {
        if (cachedLibraryItems == null) return;
        LinearLayout container = (LinearLayout) findViewById(R.id.library_content);
        container.removeAllViews();
        
        Button likedBtn = new Button(MainActivity.this);
        likedBtn.setText("My Liked Songs");
        likedBtn.setTextColor(0xFF00d4ff);
        likedBtn.setTextSize(20);
        likedBtn.setPadding(20, 40, 20, 40);
        likedBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendCommand("/api/spotify/play?uri=spotify:collection:tracks");
            }
        });
        container.addView(likedBtn);

        try {
            for (int i = 0; i < cachedLibraryItems.length(); i++) {
                final JSONObject item = cachedLibraryItems.getJSONObject(i);
                final String uri = item.optString("uri", "");
                final String parentUri = item.optString("parent_uri", null);
                final String type = item.optString("type", "playlist");
                final int depth = item.optInt("depth", 1);
                
                if (!isItemVisible(parentUri)) continue;
                
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                int leftPadding = 10 + ((depth - 1) * 60);
                row.setPadding(leftPadding, 15, 10, 15);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                
                String artUriToLoad = uri;
                if (type.equals("folder")) {
                    artUriToLoad = getFolderArtUri(uri);
                    if (artUriToLoad == null) artUriToLoad = "";
                }
                
                if (prefs.getBoolean("show_album_art", true)) {
                    final RelativeLayout artContainer = new RelativeLayout(MainActivity.this);
                    artContainer.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                    artContainer.setPadding(0, 0, 15, 0);
                    row.addView(artContainer);
                    
                    final ImageView img = new ImageView(MainActivity.this);
                    img.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT));
                    artContainer.addView(img);
                    
                    final ProgressBar pb = new ProgressBar(MainActivity.this);
                    RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    pbParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                    pb.setLayoutParams(pbParams);
                    pb.setVisibility(View.GONE);
                    artContainer.addView(pb);
                    
                    final LinearLayout errLayout = new LinearLayout(MainActivity.this);
                    errLayout.setOrientation(LinearLayout.VERTICAL);
                    errLayout.setGravity(android.view.Gravity.CENTER);
                    RelativeLayout.LayoutParams errParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    errParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                    errLayout.setLayoutParams(errParams);
                    errLayout.setVisibility(View.GONE);
                    
                    final TextView errText = new TextView(MainActivity.this);
                    errText.setTextColor(0xFFFFFFFF);
                    errText.setTextSize(10);
                    errText.setGravity(android.view.Gravity.CENTER);
                    errLayout.addView(errText);
                    
                    final Button retryBtn = new Button(MainActivity.this);
                    retryBtn.setText("Retry");
                    retryBtn.setTextSize(10);
                    retryBtn.setPadding(2, 2, 2, 2);
                    retryBtn.setTextColor(0xFFFFFFFF);
                    retryBtn.setBackgroundColor(0x44FFFFFF);
                    errLayout.addView(retryBtn);
                    
                    artContainer.addView(errLayout);
                    
                    final String finalArtUriToLoad = artUriToLoad;
                    
                    final Runnable fetchImage = new Runnable() {
                        @Override
                        public void run() {
                            img.setImageBitmap(null);
                            errLayout.setVisibility(View.GONE);
                            
                            if (type.equals("folder") && finalArtUriToLoad.isEmpty()) {
                                pb.setVisibility(View.GONE);
                                img.setImageResource(R.drawable.ic_folder);
                                return;
                            }
                            
                            if (finalArtUriToLoad.isEmpty()) {
                                pb.setVisibility(View.GONE);
                                img.setImageResource(R.drawable.ic_error);
                                errText.setText("URL missing");
                                errLayout.setVisibility(View.VISIBLE);
                                return;
                            }
                            
                            pb.setVisibility(View.VISIBLE);
                            
                            api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(finalArtUriToLoad), new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {
                                    try {
                                        JSONObject j = new JSONObject(response);
                                        final String imgUrl = j.optString("thumbnail_url", "");
                                        final String highResUrl = j.optString("high_res_url", "");
                                        if (!imgUrl.isEmpty()) {
                                            new AsyncTask<Void, Void, Bitmap>() {
                                                @Override protected Bitmap doInBackground(Void... voids) {
                                                    try { return BitmapFactory.decodeStream(new URL(imgUrl).openStream()); } catch (Exception e) { return null; }
                                                }
                                                @Override protected void onPostExecute(Bitmap b) {
                                                    pb.setVisibility(View.GONE);
                                                    if (b != null) {
                                                        img.setImageBitmap(b);
                                                        if (!highResUrl.isEmpty() && !highResUrl.equals(imgUrl)) {
                                                            new AsyncTask<Void, Void, Bitmap>() {
                                                                @Override protected Bitmap doInBackground(Void... voids) {
                                                                    try { return BitmapFactory.decodeStream(new URL(highResUrl).openStream()); } catch (Exception e) { return null; }
                                                                }
                                                                @Override protected void onPostExecute(Bitmap hb) {
                                                                    if (hb != null) img.setImageBitmap(hb);
                                                                }
                                                            }.execute();
                                                        }
                                                    } else showError("Download failed");
                                                }
                                            }.execute();
                                        } else showError("Download failed");
                                    } catch (Exception e) { showError("Download failed"); }
                                }
                                @Override public void onError(String error) { showError("Download failed"); }
                                
                                private void showError(String msg) {
                                    pb.setVisibility(View.GONE);
                                    img.setImageResource(R.drawable.ic_error);
                                    errText.setText(msg);
                                    errLayout.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    };
                    
                    retryBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { fetchImage.run(); }
                    });
                    
                    fetchImage.run();
                }
                
                LinearLayout textCol = new LinearLayout(MainActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView tvName = new TextView(MainActivity.this);
                String namePrefix = "";
                if (type.equals("folder")) {
                    namePrefix = expandedFolders.contains(uri) ? "📂 " : "📁 ";
                }
                tvName.setText(namePrefix + item.optString("name", "Unknown"));
                tvName.setTextColor(type.equals("folder") ? 0xFF00d4ff : 0xFFFFFFFF);
                tvName.setTextSize(18);
                textCol.addView(tvName);
                
                TextView tvArtist = new TextView(MainActivity.this);
                tvArtist.setText(item.optString("artist", ""));
                tvArtist.setTextSize(14);
                tvArtist.setTextColor(0xFF8892b0);
                textCol.addView(tvArtist);
                
                row.addView(textCol);
                
                LinearLayout btns = new LinearLayout(MainActivity.this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                
                if (type.equals("folder")) {
                    Button openBtn = new Button(MainActivity.this);
                    openBtn.setText(expandedFolders.contains(uri) ? "Close folder" : "Open folder");
                    openBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            if (expandedFolders.contains(uri)) expandedFolders.remove(uri);
                            else expandedFolders.add(uri);
                            renderLibraryFolder();
                        }
                    });
                    btns.addView(openBtn);
                }
                
                if (!uri.isEmpty()) {
                    Button playBtn = new Button(MainActivity.this);
                    playBtn.setText("Play");
                    playBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri));
                        }
                    });
                    btns.addView(playBtn);
                    
                    Button removeBtn = new Button(MainActivity.this);
                    removeBtn.setText("Remove from library");
                    removeBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            api.get("/api/spotify/library/remove?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() { loadLibrary(); }
                                    });
                                }
                                @Override public void onError(String error) {}
                            });
                        }
                    });
                    btns.addView(removeBtn);
                }
                
                row.addView(btns);
                container.addView(row);
            }
            applyTheme(prefs.getString("theme", "dark"));
        } catch (Exception e) {}
    }

    private void loadQueue() {
        showLoading("Loading Queue...");
        api.get("/api/spotify/queue", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                hideLoading();
                LinearLayout container = (LinearLayout) findViewById(R.id.queue_content);
                container.removeAllViews();
                try {
                    JSONObject json = new JSONObject(response);
                    JSONArray items = json.optJSONArray("next_tracks");
                    if (items == null) items = json.optJSONArray("queue");
                    if (items == null) items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            final JSONObject item = items.getJSONObject(i);
                            final String uri = item.optString("uri", "");
                            LinearLayout row = new LinearLayout(MainActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setPadding(10, 15, 10, 15);
                            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            
                            if (prefs.getBoolean("show_album_art", true)) {
                                final RelativeLayout artContainer = new RelativeLayout(MainActivity.this);
                                artContainer.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                                artContainer.setPadding(0, 0, 15, 0);
                                row.addView(artContainer);
                                
                                final ImageView img = new ImageView(MainActivity.this);
                                img.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT));
                                artContainer.addView(img);
                                
                                final ProgressBar pb = new ProgressBar(MainActivity.this);
                                RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                pbParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                                pb.setLayoutParams(pbParams);
                                pb.setVisibility(View.GONE);
                                artContainer.addView(pb);
                                
                                final LinearLayout errLayout = new LinearLayout(MainActivity.this);
                                errLayout.setOrientation(LinearLayout.VERTICAL);
                                errLayout.setGravity(android.view.Gravity.CENTER);
                                RelativeLayout.LayoutParams errParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                errParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                                errLayout.setLayoutParams(errParams);
                                errLayout.setVisibility(View.GONE);
                                
                                final TextView errText = new TextView(MainActivity.this);
                                errText.setTextColor(0xFFFFFFFF);
                                errText.setTextSize(10);
                                errText.setGravity(android.view.Gravity.CENTER);
                                errLayout.addView(errText);
                                
                                final Button retryBtn = new Button(MainActivity.this);
                                retryBtn.setText("Retry");
                                retryBtn.setTextSize(10);
                                retryBtn.setPadding(2, 2, 2, 2);
                                retryBtn.setTextColor(0xFFFFFFFF);
                                retryBtn.setBackgroundColor(0x44FFFFFF);
                                errLayout.addView(retryBtn);
                                
                                artContainer.addView(errLayout);
                                
                                final Runnable fetchImage = new Runnable() {
                                    @Override
                                    public void run() {
                                        img.setImageBitmap(null);
                                        errLayout.setVisibility(View.GONE);
                                        
                                        if (uri.isEmpty()) {
                                            pb.setVisibility(View.GONE);
                                            img.setImageResource(R.drawable.ic_error);
                                            errText.setText("URL missing");
                                            errLayout.setVisibility(View.VISIBLE);
                                            return;
                                        }
                                        
                                        pb.setVisibility(View.VISIBLE);
                                        
                                        api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
                                            @Override public void onSuccess(String response) {
                                                try {
                                                    JSONObject j = new JSONObject(response);
                                                    final String imgUrl = j.optString("thumbnail_url", "");
                                                    final String highResUrl = j.optString("high_res_url", "");
                                                    if (!imgUrl.isEmpty()) {
                                                        new AsyncTask<Void, Void, Bitmap>() {
                                                            @Override protected Bitmap doInBackground(Void... voids) {
                                                                try { return BitmapFactory.decodeStream(new URL(imgUrl).openStream()); } catch (Exception e) { return null; }
                                                            }
                                                            @Override protected void onPostExecute(Bitmap b) {
                                                                pb.setVisibility(View.GONE);
                                                                if (b != null) {
                                                                    img.setImageBitmap(b);
                                                                    if (!highResUrl.isEmpty() && !highResUrl.equals(imgUrl)) {
                                                                        new AsyncTask<Void, Void, Bitmap>() {
                                                                            @Override protected Bitmap doInBackground(Void... voids) {
                                                                                try { return BitmapFactory.decodeStream(new URL(highResUrl).openStream()); } catch (Exception e) { return null; }
                                                                            }
                                                                            @Override protected void onPostExecute(Bitmap hb) {
                                                                                if (hb != null) img.setImageBitmap(hb);
                                                                            }
                                                                        }.execute();
                                                                    }
                                                                } else showError("Download failed");
                                                            }
                                                        }.execute();
                                                    } else showError("Download failed");
                                                } catch (Exception e) { showError("Download failed"); }
                                            }
                                            @Override public void onError(String error) { showError("Download failed"); }
                                            
                                            private void showError(String msg) {
                                                pb.setVisibility(View.GONE);
                                                img.setImageResource(R.drawable.ic_error);
                                                errText.setText(msg);
                                                errLayout.setVisibility(View.VISIBLE);
                                            }
                                        });
                                    }
                                };
                                
                                retryBtn.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) { fetchImage.run(); }
                                });
                                
                                fetchImage.run();
                            }
                            
                            TextView num = new TextView(MainActivity.this);
                            num.setText((i + 1) + ". ");
                            num.setTextColor(0xFF8892b0);
                            num.setTextSize(16);
                            row.addView(num);
                            
                            TextView tv = new TextView(MainActivity.this);
                            String name = item.optString("name", item.optString("title", "Unknown"));
                            String artist = item.optString("artist", "");
                            tv.setText(artist.isEmpty() ? name : name + " - " + artist);
                            tv.setTextColor(0xFFffffff);
                            tv.setTextSize(16);
                            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                            row.addView(tv);
                            
                            if (!uri.isEmpty()) {
                                Button btnPlay = new Button(MainActivity.this);
                                btnPlay.setText("Play");
                                btnPlay.setTextColor(0xFFffffff);
                                btnPlay.setPadding(20, 10, 20, 10);
                                btnPlay.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri));
                                    }
                                });
                                row.addView(btnPlay);
                            }
                            
                            container.addView(row);
                        }
                    }
                    if (container.getChildCount() == 0) {
                        TextView empty = new TextView(MainActivity.this);
                        empty.setText("Queue is empty");
                        empty.setTextColor(0xFF8892b0);
                        empty.setTextSize(18);
                        empty.setPadding(20, 40, 20, 40);
                        empty.setGravity(android.view.Gravity.CENTER);
                        container.addView(empty);
                    }
                } catch (Exception e) {
                    TextView err = new TextView(MainActivity.this);
                    err.setText("Could not load queue");
                    err.setTextColor(0xFF8892b0);
                    container.addView(err);
                }
                applyTheme(prefs.getString("theme", "dark"));
            }
            @Override public void onError(String error) { hideLoading(); showToast("Queue load failed", false); }
        });
    }


    private void setupSettings() {
        try {
            final android.widget.ToggleButton toggleAlbumArt = (android.widget.ToggleButton) findViewById(R.id.toggle_album_art);
            toggleAlbumArt.setChecked(prefs.getBoolean("show_album_art", true));
            toggleAlbumArt.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    prefs.edit().putBoolean("show_album_art", isChecked).apply();
                    api.get("/api/config/set?key=show_album_art&value=" + isChecked, new ApiClient.Callback() {
                        @Override public void onSuccess(String response) {}
                        @Override public void onError(String error) {}
                    });
                }
            });

            final Button btnTheme = (Button) findViewById(R.id.btn_theme);
            btnTheme.setText(prefs.getString("theme", "dark").substring(0, 1).toUpperCase() + prefs.getString("theme", "dark").substring(1));
            btnTheme.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String cur = prefs.getString("theme", "dark");
                    String next = cur.equals("dark") ? "light" : (cur.equals("light") ? "native" : "dark");
                    prefs.edit().putString("theme", next).apply();
                    btnTheme.setText(next.substring(0, 1).toUpperCase() + next.substring(1));
                    applyTheme(next);
                    api.get("/api/config/set?key=theme&value=" + next, new ApiClient.Callback() {
                        @Override public void onSuccess(String response) {}
                        @Override public void onError(String error) {}
                    });
                }
            });
        } catch (Exception e) {}
    }



    private void applyTheme(String theme) {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        
        int bgPrimary, bgSecondary, textPrimary, textSecondary;
        if (theme.equals("light")) {
            bgPrimary = android.graphics.Color.parseColor("#f0f2f5");
            bgSecondary = android.graphics.Color.parseColor("#ffffff");
            textPrimary = android.graphics.Color.parseColor("#1a1a2e");
            textSecondary = android.graphics.Color.parseColor("#5a6577");
        } else if (theme.equals("native")) {
            bgPrimary = android.graphics.Color.TRANSPARENT;
            bgSecondary = android.graphics.Color.TRANSPARENT;
            textPrimary = android.graphics.Color.BLACK;
            textSecondary = android.graphics.Color.DKGRAY;
        } else {
            // Dark
            bgPrimary = android.graphics.Color.parseColor("#0a0e1a");
            bgSecondary = android.graphics.Color.parseColor("#111827");
            textPrimary = android.graphics.Color.parseColor("#ffffff");
            textSecondary = android.graphics.Color.parseColor("#8892b0");
        }
        
        // Update root and main containers
        root.setBackgroundColor(bgPrimary);
        if (screenMain != null) screenMain.setBackgroundColor(bgPrimary);
        if (tabMusic != null) tabMusic.setBackgroundColor(bgPrimary);
        if (tabSettings != null) tabSettings.setBackgroundColor(bgPrimary);
        if (tabPower != null) tabPower.setBackgroundColor(bgPrimary);
        
        // Find all views and update them
        applyThemeToView(root, bgPrimary, bgSecondary, textPrimary, textSecondary, theme.equals("native"));
    }
    
    private void applyThemeToView(View v, int bgP, int bgS, int textP, int textS, boolean isNative) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyThemeToView(vg.getChildAt(i), bgP, bgS, textP, textS, isNative);
            }
        }
        
        if (v instanceof TextView && !(v instanceof Button)) {
            ((TextView) v).setTextColor(textP);
        }
        
        if (v instanceof Button) {
            Button b = (Button) v;
            if (isNative) {
                b.setBackgroundResource(android.R.drawable.btn_default);
                b.setTextColor(android.graphics.Color.BLACK);
            } else {
                int defaultColor = bgS;
                if (b.getId() == R.id.btn_play_pause) {
                    defaultColor = android.graphics.Color.parseColor("#00d4ff");
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_disconnect) {
                    defaultColor = android.graphics.Color.parseColor("#1a1f36");
                    b.setTextColor(android.graphics.Color.parseColor("#ff5252"));
                } else if (b.getId() == R.id.btn_shutdown) {
                    defaultColor = android.graphics.Color.parseColor("#ff5252");
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_restart) {
                    defaultColor = android.graphics.Color.parseColor("#ffa726");
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_close_app) {
                    defaultColor = android.graphics.Color.parseColor("#1a1f36");
                    b.setTextColor(android.graphics.Color.WHITE);
                } else {
                    b.setTextColor(textP);
                }
                
                android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
                android.graphics.drawable.ColorDrawable pressed = new android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY);
                android.graphics.drawable.ColorDrawable normal = new android.graphics.drawable.ColorDrawable(defaultColor);
                sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
                sld.addState(new int[]{}, normal);
                int pL = 20;
                int pT = 10;
                int pR = 20;
                int pB = 10;
                
                if (b.getId() == R.id.btn_seek_back || b.getId() == R.id.btn_seek_fwd || 
                    b.getId() == R.id.btn_vol_down || b.getId() == R.id.btn_vol_up) {
                    pL = 5; pR = 5;
                }
                
                android.util.Log.d("MINIPC_THEME", "Set padding " + pL + " for btn id: " + b.getId());
                b.setBackgroundDrawable(sld);
                b.setPadding(pL, pT, pR, pB);
            }
        } else if (v instanceof EditText) {
            EditText e = (EditText) v;
            if (isNative) {
                e.setBackgroundResource(android.R.drawable.edit_text);
                e.setTextColor(android.graphics.Color.BLACK);
            } else {
                e.setBackgroundColor(bgS);
                e.setTextColor(textP);
            }
        }
    }

    private void setupAudioControls() {
        final ToggleButton toggleFill = (ToggleButton) findViewById(R.id.toggle_speaker_fill);
        if (toggleFill != null) {
            toggleFill.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean on = toggleFill.isChecked();
                    sendCommand("/api/audio/speaker_fill?enabled=" + (on ? "true" : "false"));
                }
            });
        }
        
        Button btnConfig = (Button) findViewById(R.id.btn_speaker_config);
        if (btnConfig != null) {
            btnConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    sendCommand("/api/audio/open_config");
                }
            });
        }
        
        // Load audio info
        if (api != null) {
            api.get("/api/audio/speaker_fill_state", new ApiClient.Callback() {
                @Override public void onSuccess(String response) {
                    try {
                        JSONObject json = new JSONObject(response);
                        if (toggleFill != null) toggleFill.setChecked(json.optBoolean("enabled", false));
                    } catch (Exception e) {}
                }
                @Override public void onError(String error) {}
            });
            api.get("/api/audio/info", new ApiClient.Callback() {
                @Override public void onSuccess(String response) {
                    try {
                        JSONObject json = new JSONObject(response);
                        TextView ch = (TextView) findViewById(R.id.audio_channel_text);
                        if (ch != null) {
                            ch.setText("Channels: " + json.optInt("channels", 2) + " (" + json.optString("config", "Stereo") + ")");
                        }
                    } catch (Exception e) {}
                }
                @Override public void onError(String error) {}
            });
        }
    }

    private void setupPowerControls() {
        ((Button) findViewById(R.id.btn_shutdown)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Confirm").setMessage("Shutdown PC?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { sendCommand("/api/system/shutdown"); }
                }).setNegativeButton("No", null).show();
            }
        });
        ((Button) findViewById(R.id.btn_restart)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Confirm").setMessage("Restart PC?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { sendCommand("/api/system/restart"); }
                }).setNegativeButton("No", null).show();
            }
        });
        ((Button) findViewById(R.id.btn_close_app)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Confirm").setMessage("Close PC App?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { sendCommand("/api/system/close_app"); }
                }).setNegativeButton("No", null).show();
            }
        });
    }

    private void sendCommand(String endpoint) {
        showLoading("Waiting for PC...");
        api.get(endpoint, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                hideLoading();
                showToast("Successfully completed", true);
            }
            @Override
            public void onError(String error) {
                hideLoading();
                showToast("Failed: " + error, false);
            }
        });
    }

    private void showLoading(String text) {
        loadingText.setText(text);
        loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }

    private void showToast(String text, boolean success) {
        statusToast.setText(text);
        statusToast.setBackgroundColor(success ? 0xFF4CAF50 : 0xFFF44336);
        statusToast.setVisibility(View.VISIBLE);
        handler.postDelayed(new Runnable() {
            @Override public void run() { statusToast.setVisibility(View.GONE); }
        }, 2000);
    }

    private String lastArtUri = "";
    private void loadAlbumArt(final String uri) {
        if (uri.equals(lastArtUri)) return;
        lastArtUri = uri;
        
        final ProgressBar npArtProgress = (ProgressBar) findViewById(R.id.np_art_progress);
        final LinearLayout npArtErrorLayout = (LinearLayout) findViewById(R.id.np_art_error_layout);
        final TextView npArtErrorText = (TextView) findViewById(R.id.np_art_error_text);
        final Button npArtRetryBtn = (Button) findViewById(R.id.np_art_retry_btn);

        npArtwork.setImageBitmap(null);
        if (npArtProgress != null) npArtProgress.setVisibility(View.VISIBLE);
        if (npArtErrorLayout != null) npArtErrorLayout.setVisibility(View.GONE);

        if (uri.isEmpty()) {
            if (npArtProgress != null) npArtProgress.setVisibility(View.GONE);
            npArtwork.setImageResource(R.drawable.ic_error);
            if (npArtErrorLayout != null) npArtErrorLayout.setVisibility(View.VISIBLE);
            if (npArtErrorText != null) npArtErrorText.setText("URL not provided by PC");
            if (npArtRetryBtn != null) {
                npArtRetryBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        lastArtUri = "";
                        loadAlbumArt(currentNpUri);
                    }
                });
            }
            return;
        }
        
        api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    final String imgUrl = json.optString("thumbnail_url", "");
                    final String highResUrl = json.optString("high_res_url", "");
                    if (!imgUrl.isEmpty()) {
                        new AsyncTask<Void, Void, Bitmap>() {
                            @Override protected Bitmap doInBackground(Void... voids) {
                                try {
                                    InputStream in = new URL(imgUrl).openStream();
                                    return BitmapFactory.decodeStream(in);
                                } catch (Exception e) { return null; }
                            }
                            @Override protected void onPostExecute(Bitmap b) {
                                if (b != null) {
                                    if (npArtProgress != null) npArtProgress.setVisibility(View.GONE);
                                    npArtwork.setImageBitmap(b);
                                    if (!highResUrl.isEmpty() && !highResUrl.equals(imgUrl)) {
                                        new AsyncTask<Void, Void, Bitmap>() {
                                            @Override protected Bitmap doInBackground(Void... voids) {
                                                try { return BitmapFactory.decodeStream(new URL(highResUrl).openStream()); } catch (Exception e) { return null; }
                                            }
                                            @Override protected void onPostExecute(Bitmap hb) {
                                                if (hb != null) npArtwork.setImageBitmap(hb);
                                            }
                                        }.execute();
                                    }
                                } else {
                                    showErrorState("Failed to download");
                                }
                            }
                        }.execute();
                    } else {
                        showErrorState("Failed to download");
                    }
                } catch (Exception e) { showErrorState("Failed to download"); }
            }
            @Override public void onError(String error) { showErrorState("Failed to download"); }
            
            private void showErrorState(String errorMsg) {
                if (npArtProgress != null) npArtProgress.setVisibility(View.GONE);
                npArtwork.setImageResource(R.drawable.ic_error);
                if (npArtErrorLayout != null) npArtErrorLayout.setVisibility(View.VISIBLE);
                if (npArtErrorText != null) npArtErrorText.setText(errorMsg);
                if (npArtRetryBtn != null) {
                    npArtRetryBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            lastArtUri = "";
                            loadAlbumArt(currentNpUri);
                        }
                    });
                }
            }
        });
    }

    private void checkIfLiked(String uri) {
        api.get("/api/spotify/library/contains?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    isLiked = json.optBoolean("contains", false);
                    Button btnNpLike = (Button) findViewById(R.id.btn_np_like);
                    if (btnNpLike != null) {
                        btnNpLike.setText("Like: " + (isLiked ? "YES" : "NO"));
                        btnNpLike.setTextColor(isLiked ? 0xFF00d4ff : 0xFFffffff);
                    }
                } catch (Exception e) {}
            }
            @Override public void onError(String error) {}
        });
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    @Override
    public void onBackPressed() {
        if (screenMain.getVisibility() == View.VISIBLE) {
        } else {
            showToast("Device is locked", false);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && !isFinishing() && !allowSettings) {
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
