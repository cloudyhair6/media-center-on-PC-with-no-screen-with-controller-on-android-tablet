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
    private LinearLayout tabMovies;
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
    private LinearLayout movieBrowserView, moviePlayerView;
    private TextView currentPathText, movieTitleText, movieTimeText, movieInfoText;
    private LinearLayout filesList;
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
    private boolean isPlayingMovie = false;
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

    private Runnable pollMovieInfo = new Runnable() {
        @Override
        public void run() {
            if (isPlayingMovie) {
                api.get("/api/movies/info", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            int time = json.optInt("time", 0);
                            int length = json.optInt("length", 0);
                            String state = json.optString("state", "");
                            movieTimeText.setText(formatTime(time) + " / " + formatTime(length));
                            String res = json.optString("resolution", "");
                            String codec = json.optString("codec", "");
                            movieInfoText.setText(res + " | " + codec);
                            
                            if (state.equals("stopped") && time == 0 && length == 0) {
                                // Movie might have ended
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
        handler.post(pollMovieInfo);
        handler.post(pollSystemStats);
        handler.post(pollUnlock);
        handler.post(blockSettings);

        // Auto-connect
        String lastIp = prefs.getString("last_ip", "");
        if (!lastIp.isEmpty()) {
            connectToIp(lastIp);
        }
    }

    private void initViews() {
        try { screenConnection = (LinearLayout) findViewById(R.id.screen_connection); } catch (Exception e) { android.util.Log.e("DEBUG", "screen_connection failed", e); }
        try { screenMain = (LinearLayout) findViewById(R.id.screen_main); } catch (Exception e) { android.util.Log.e("DEBUG", "screen_main failed", e); }
        try { ipListContainer = (LinearLayout) findViewById(R.id.ip_list_container); } catch (Exception e) { android.util.Log.e("DEBUG", "ip_list_container failed", e); }
        try { loadingOverlay = (LinearLayout) findViewById(R.id.loading_overlay); } catch (Exception e) { android.util.Log.e("DEBUG", "loading_overlay failed", e); }
        try { loadingText = (TextView) findViewById(R.id.loading_text); } catch (Exception e) { android.util.Log.e("DEBUG", "loading_text failed", e); }
        try { statusToast = (TextView) findViewById(R.id.status_toast); } catch (Exception e) { android.util.Log.e("DEBUG", "status_toast failed", e); }

        try { tabMusic = (LinearLayout) findViewById(R.id.tab_music); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_music failed", e); }
        try { tabMovies = (LinearLayout) findViewById(R.id.tab_movies); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_movies failed", e); }
        try { tabSettings = (ScrollView) findViewById(R.id.tab_settings); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_settings failed", e); }
        try { tabPower = (LinearLayout) findViewById(R.id.tab_power); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_power failed", e); }

        try { tabButtons = new Button[]{
            (Button) findViewById(R.id.tab_btn_music),
            (Button) findViewById(R.id.tab_btn_movies),
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

        try { movieBrowserView = (LinearLayout) findViewById(R.id.movie_browser_view); } catch (Exception e) { android.util.Log.e("DEBUG", "movieBrowserView failed", e); }
        try { moviePlayerView = (LinearLayout) findViewById(R.id.movie_player_view); } catch (Exception e) { android.util.Log.e("DEBUG", "moviePlayerView failed", e); }
        try { currentPathText = (TextView) findViewById(R.id.current_path); } catch (Exception e) { android.util.Log.e("DEBUG", "currentPathText failed", e); }
        try { filesList = (LinearLayout) findViewById(R.id.files_list); } catch (Exception e) { android.util.Log.e("DEBUG", "filesList failed", e); }
        try { movieTitleText = (TextView) findViewById(R.id.movie_title); } catch (Exception e) { android.util.Log.e("DEBUG", "movieTitleText failed", e); }
        try { movieTimeText = (TextView) findViewById(R.id.movie_time); } catch (Exception e) { android.util.Log.e("DEBUG", "movieTimeText failed", e); }
        try { movieInfoText = (TextView) findViewById(R.id.movie_info); } catch (Exception e) { android.util.Log.e("DEBUG", "movieInfoText failed", e); }

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
                volLabel.setText("--%");
                
                // Immediately poll the server again for everything
                handler.removeCallbacks(pollSpotify);
                handler.removeCallbacks(pollMovieInfo);
                handler.removeCallbacks(pollSystemStats);
                
                handler.post(pollSpotify);
                handler.post(pollMovieInfo);
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
        tabMovies.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        tabPower.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setTextColor(i == index ? 0xFF00d4ff : 0xFF8892b0);
            tabButtons[i].setBackgroundColor(i == index ? 0xFF1a1f36 : 0xFF111827);
        }

        TextView title = (TextView) findViewById(R.id.tab_title);
        if (index == 0) title.setText("Music");
        if (index == 1) {
            title.setText("Movies");
            loadMovies("C:\\");
        }
        if (index == 2) title.setText("Settings");
        if (index == 3) title.setText("Power");
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
        ((Button) findViewById(R.id.btn_go_up)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String cur = currentPathText.getText().toString();
                int idx = cur.lastIndexOf("\\");
                if (idx > 0) {
                    loadMovies(cur.substring(0, idx));
                } else if (idx == 0) {
                    loadMovies("C:\\");
                }
            }
        });
        
        ((Button) findViewById(R.id.btn_movie_play_pause)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/movies/control?action=play_pause"); }
        });
        ((Button) findViewById(R.id.btn_movie_seek_fwd)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/movies/control?action=seek_fwd"); }
        });
        ((Button) findViewById(R.id.btn_movie_seek_back)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/movies/control?action=seek_back"); }
        });
        ((Button) findViewById(R.id.btn_movie_stop)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { 
                sendCommand("/api/movies/control?action=stop"); 
                isPlayingMovie = false;
                moviePlayerView.setVisibility(View.GONE);
                movieBrowserView.setVisibility(View.VISIBLE);
            }
        });
        ((Button) findViewById(R.id.btn_movie_vol_down)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/movies/control?action=volume_down"); }
        });
        ((Button) findViewById(R.id.btn_movie_vol_up)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("/api/movies/control?action=volume_up"); }
        });
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
                    ImageView iv = new ImageView(MainActivity.this);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                    iv.setPadding(0, 0, 15, 0);
                    row.addView(iv);
                    final String imgUrl = item.optString("image", "");
                    if (!imgUrl.isEmpty()) {
                        final ImageView fIv = iv;
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    java.io.InputStream in = new java.net.URL(imgUrl).openStream();
                                    final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                                    runOnUiThread(new Runnable() { public void run() { fIv.setImageBitmap(bmp); } });
                                } catch(Exception e) {}
                            }
                        }).start();
                    }

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
                    
                    Button playBtn = new Button(MainActivity.this);
                    playBtn.setText("Play");
                    playBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(item.optString("uri"))); }
                    });
                    btns.addView(playBtn);
                    
                    Button queueBtn = new Button(MainActivity.this);
                    queueBtn.setText("Queue");
                    queueBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/queue/add?uri=" + java.net.URLEncoder.encode(item.optString("uri"))); }
                    });
                    btns.addView(queueBtn);

                    Button likeBtn = new Button(MainActivity.this);
                    likeBtn.setText("Like");
                    likeBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/library/add?uri=" + java.net.URLEncoder.encode(item.optString("uri"))); }
                    });
                    btns.addView(likeBtn);

                    Button playlistBtn = new Button(MainActivity.this);
                    playlistBtn.setText("Add to Playlist");
                    playlistBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { showPlaylistSelector(item.optString("uri")); }
                    });
                    btns.addView(playlistBtn);
                    
                    row.addView(btns);
                    container.addView(row);
                    
                    String t = prefs.getString("theme", "dark");
                    int bgS = t.equals("light") ? android.graphics.Color.parseColor("#ffffff") : android.graphics.Color.parseColor("#111827");
                    int txtP = t.equals("light") ? android.graphics.Color.parseColor("#1a1a2e") : android.graphics.Color.parseColor("#ffffff");
                    applyThemeToView(row, android.graphics.Color.TRANSPARENT, bgS, txtP, txtP, t.equals("native"));
                }
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
                    final ImageView img = new ImageView(MainActivity.this);
                    img.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                    img.setBackgroundColor(0xFF111827);
                    img.setPadding(0, 0, 15, 0);
                    row.addView(img);
                    
                    if (!artUriToLoad.isEmpty()) {
                        api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(artUriToLoad), new ApiClient.Callback() {
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
                                                }
                                            }
                                        }.execute();
                                    }
                                } catch (Exception e) {}
                            }
                            @Override public void onError(String error) {}
                        });
                    }
                }
                
                Button btn = new Button(MainActivity.this);
                String namePrefix = "";
                if (type.equals("folder")) {
                    namePrefix = expandedFolders.contains(uri) ? "📂 " : "📁 ";
                }
                btn.setText(namePrefix + item.optString("name", "Unknown"));
                btn.setTextColor(type.equals("folder") ? 0xFF00d4ff : 0xFFFFFFFF);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                btn.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
                btn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (type.equals("folder")) {
                            if (expandedFolders.contains(uri)) expandedFolders.remove(uri);
                            else expandedFolders.add(uri);
                            renderLibraryFolder();
                        } else if (!uri.isEmpty()) {
                            sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri));
                        }
                    }
                });
                row.addView(btn);
                container.addView(row);
            }
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
                            
                            if (prefs.getBoolean("show_album_art", true) && !uri.isEmpty()) {
                                final ImageView img = new ImageView(MainActivity.this);
                                img.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                                img.setBackgroundColor(0xFF111827);
                                img.setPadding(0, 0, 15, 0);
                                row.addView(img);
                                
                                api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
                                    @Override public void onSuccess(String response) {
                                        try {
                                            JSONObject j = new JSONObject(response);
                                            final String imgUrl = j.optString("thumbnail_url", "");
                                            final String highResUrl = j.optString("high_res_url", "");
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
                                                        }
                                                    }
                                                }.execute();
                                            }
                                        } catch (Exception e) {}
                                    }
                                    @Override public void onError(String error) {}
                                });
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
            }
            @Override public void onError(String error) { hideLoading(); showToast("Queue load failed", false); }
        });
    }

    private void loadMovies(final String path) {
        showLoading("Loading...");
        String url = "/api/movies/browse";
        if (path != null && !path.isEmpty()) {
            url += "?path=" + java.net.URLEncoder.encode(path);
        }
        api.get(url, new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                hideLoading();
                filesList.removeAllViews();
                try {
                    JSONObject json = new JSONObject(response);
                    currentPathText.setText(json.optString("current_path", ""));
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            final JSONObject item = items.getJSONObject(i);
                            Button btn = new Button(MainActivity.this);
                            final String name = item.optString("name");
                            final String type = item.optString("type");
                            final String filePath = item.optString("path");
                            
                            if (type.equals("folder")) {
                                btn.setText("[DIR] " + name);
                                btn.setTextColor(0xFF00d4ff);
                                btn.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) { loadMovies(filePath); }
                                });
                            } else {
                                btn.setText(name + " (" + item.optString("size") + ")");
                                btn.setTextColor(0xFFFFFFFF);
                                btn.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) { playMovie(filePath, name); }
                                });
                            }
                            filesList.addView(btn);
                        }
                    }
                } catch (Exception e) {}
            }
            @Override public void onError(String error) {
                hideLoading();
                showToast("Failed to load folder", false);
            }
        });
    }

    private void playMovie(String path, final String name) {
        showLoading("Starting VLC...");
        api.get("/api/movies/play?path=" + java.net.URLEncoder.encode(path), new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                hideLoading();
                showToast("Playing " + name, true);
                isPlayingMovie = true;
                movieTitleText.setText(name);
                movieBrowserView.setVisibility(View.GONE);
                moviePlayerView.setVisibility(View.VISIBLE);
            }
            @Override public void onError(String error) {
                hideLoading();
                showToast("Failed to start VLC", false);
            }
        });
    }

    private void setupSettings() {
        toggleAlbumArt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean val = toggleAlbumArt.isChecked();
                prefs.edit().putBoolean("show_album_art", val).commit();
                sendCommand("/api/config/set?key=show_album_art&value=" + val);
                if (!val) npArtwork.setImageBitmap(null);
            }
        });
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String current = btnTheme.getText().toString();
                String next = "Dark";
                if (current.equals("Dark")) next = "Light";
                else if (current.equals("Light")) next = "Native";
                else next = "Dark";
                
                btnTheme.setText(next);
                String val = next.toLowerCase();
                prefs.edit().putString("theme", val).commit();
                sendCommand("/api/config/set?key=theme&value=" + val);
                applyTheme(val);
            }
        });
        
        String savedTheme = prefs.getString("theme", "dark");
        btnTheme.setText(savedTheme.substring(0, 1).toUpperCase() + savedTheme.substring(1));
        applyTheme(savedTheme);
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
        if (tabMovies != null) tabMovies.setBackgroundColor(bgPrimary);
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
                } else {
                    b.setTextColor(textP);
                }
                
                android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
                android.graphics.drawable.ColorDrawable pressed = new android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY);
                android.graphics.drawable.ColorDrawable normal = new android.graphics.drawable.ColorDrawable(defaultColor);
                sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
                sld.addState(new int[]{}, normal);
                b.setBackgroundDrawable(sld);
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
        final TextView artError = (TextView) findViewById(R.id.np_art_error);
        if (artError != null) artError.setVisibility(View.GONE);
        
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
                                    npArtwork.setImageBitmap(b);
                                    if (artError != null) artError.setVisibility(View.GONE);
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
                                }
                                else if (artError != null) artError.setVisibility(View.VISIBLE);
                            }
                        }.execute();
                    } else if (artError != null) artError.setVisibility(View.VISIBLE);
                } catch (Exception e) { if (artError != null) artError.setVisibility(View.VISIBLE); }
            }
            @Override public void onError(String error) { if (artError != null) artError.setVisibility(View.VISIBLE); }
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
            if (isPlayingMovie) {
                sendCommand("/api/movies/control?action=stop"); 
                isPlayingMovie = false;
                moviePlayerView.setVisibility(View.GONE);
                movieBrowserView.setVisibility(View.VISIBLE);
                return;
            }
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
