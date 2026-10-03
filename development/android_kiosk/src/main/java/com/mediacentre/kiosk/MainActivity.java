package com.mediacentre.kiosk;

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
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
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

    // Connection Status Indicator
    public static final int STATUS_ONLINE = 0;
    public static final int STATUS_CONNECTING = 1;
    public static final int STATUS_OFFLINE = 2;
    private LinearLayout connectionStatusContainer;
    private View connectionStatusDot;
    private TextView connectionStatusText;
    private int currentConnectionStatus = STATUS_OFFLINE;
    private int consecutiveConnectionFailures = 0;

    // Tabs
    private LinearLayout tabMusic;
    private ScrollView tabSettings;
    private LinearLayout tabPower;
    private Button[] tabButtons;

    // Music Sub-tabs
    private FrameLayout musicNowPlayingContainer;
    private ScrollView musicNowPlaying;
    private LinearLayout npLoadingOverlay;
    private TextView npLoadingText;
    private LinearLayout musicSearch;
    private ScrollView musicLibrary;
    private ScrollView musicQueue;
    private ScrollView musicLyrics;
    private LinearLayout lyricsContent;
    private String currentLyricsTrack = "";
    private java.util.List<LyricLine> currentLyrics = new java.util.ArrayList<>();
    private int currentLyricLineIndex = -1;
    private String currentNpUri = "";
    private String lastNpTitle = "";
    private String lastNpArtist = "";
    private String currentContextUri = "";
    private TextView npContext;
    private boolean isLiked = false;
    private Button[] musicSubTabButtons;

    // UI Elements - Now Playing
    private TextView npTitle, npArtist, npAlbum, volLabel, npTimeCurrent, npTimeTotal;
    private SeekBar npProgress;
    private int currentPositionS = 0;
    private int currentLengthS = 0;
    private int currentPositionMs = 0;
    private long lastSyncTimeMs = 0;
    private boolean isNpPlaying = false;
    private boolean isTrackingNpProgress = false;
    private long lastSeekRequestTimeMs = 0;
    private static final long SEEK_IGNORE_POLL_MS = 2500;
    private ImageView npArtwork;
    private String currentLibraryFolderUri = null;
    private JSONArray cachedLibraryItems = null;
    // UI Elements - Settings
    private ToggleButton toggleAlbumArt;
    private ToggleButton togglePixelPerfectArt;
    private Button btnTheme;
    private TextView cpuText, ramText, diskText, gpuText, lastUpdatedText;
    private ProgressBar cpuBar, ramBar, diskBar, gpuBar;
    
    private SeekBar seekBarVolume;
    private boolean isTrackingVolume = false;

    private Handler handler = new Handler();
    private boolean allowSettings = false;
    private int testUnlockCounter = 0;
    private boolean shuffleOn = false;
    private String repeatState = "off";

    private void updateConnectionStatus(final int status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentConnectionStatus = status;
                if (connectionStatusDot == null || connectionStatusText == null) return;
                switch (status) {
                    case STATUS_ONLINE:
                        connectionStatusDot.setBackgroundResource(R.drawable.dot_online);
                        connectionStatusText.setText("Online");
                        connectionStatusText.setTextColor(0xFF00E676);
                        break;
                    case STATUS_CONNECTING:
                        connectionStatusDot.setBackgroundResource(R.drawable.dot_connecting);
                        connectionStatusText.setText("Connecting...");
                        connectionStatusText.setTextColor(0xFFFFA726);
                        break;
                    case STATUS_OFFLINE:
                    default:
                        connectionStatusDot.setBackgroundResource(R.drawable.dot_offline);
                        connectionStatusText.setText("Offline");
                        connectionStatusText.setTextColor(0xFFFF5252);
                        break;
                }
            }
        });
    }

    private void onConnectionSuccess() {
        consecutiveConnectionFailures = 0;
        if (currentConnectionStatus != STATUS_ONLINE) {
            updateConnectionStatus(STATUS_ONLINE);
        }
    }

    private void onConnectionFailure() {
        consecutiveConnectionFailures++;
        if (consecutiveConnectionFailures >= 2) {
            if (currentConnectionStatus != STATUS_OFFLINE) {
                updateConnectionStatus(STATUS_OFFLINE);
            }
        } else {
            if (currentConnectionStatus != STATUS_CONNECTING) {
                updateConnectionStatus(STATUS_CONNECTING);
            }
        }
    }

    private void showNpLoading(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (npLoadingOverlay != null) {
                    if (npLoadingText != null && message != null) {
                        npLoadingText.setText(message);
                    }
                    npLoadingOverlay.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void hideNpLoading() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (npLoadingOverlay != null) {
                    npLoadingOverlay.setVisibility(View.GONE);
                }
            }
        });
    }

    private void styleDynamicButton(Button b) {
        if (b == null) return;
        String currentTheme = prefs != null ? prefs.getString("theme", "dark") : "dark";
        if ("native".equals(currentTheme)) {
            b.setBackgroundResource(android.R.drawable.btn_default);
            b.setTextColor(android.graphics.Color.BLACK);
        } else if ("light".equals(currentTheme)) {
            b.setBackgroundResource(android.R.drawable.btn_default);
            b.setTextColor(android.graphics.Color.parseColor("#1a1a2e"));
        } else {
            b.setBackgroundResource(R.drawable.btn_dark);
            try {
                b.setTextColor(getResources().getColorStateList(R.color.btn_dark_text));
            } catch (Exception e) {
                b.setTextColor(0xFFffffff);
            }
        }
    }

    private void styleDynamicToggleButton(ToggleButton tb) {
        if (tb == null) return;
        String currentTheme = prefs != null ? prefs.getString("theme", "dark") : "dark";
        if ("native".equals(currentTheme)) {
            tb.setBackgroundResource(android.R.drawable.btn_default);
            tb.setTextColor(android.graphics.Color.BLACK);
        } else if ("light".equals(currentTheme)) {
            tb.setBackgroundResource(android.R.drawable.btn_default);
            tb.setTextColor(android.graphics.Color.parseColor("#1a1a2e"));
        } else {
            tb.setBackgroundResource(R.drawable.btn_toggle_dark);
            try {
                tb.setTextColor(getResources().getColorStateList(R.color.toggle_dark_text));
            } catch (Exception e) {
                tb.setTextColor(0xFFffffff);
            }
        }
    }

    // Polling Runnables
    private boolean isSpotifyPolling = false;
    private Runnable pollSpotify = new Runnable() {
        @Override
        public void run() {
            if (isSpotifyPolling) {
                handler.postDelayed(this, 1000);
                return;
            }
            if (screenMain.getVisibility() == View.VISIBLE && tabMusic.getVisibility() == View.VISIBLE) {
                isSpotifyPolling = true;
                api.get("/api/spotify/now_playing", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        isSpotifyPolling = false;
                        onConnectionSuccess();
                        try {
                            JSONObject json = new JSONObject(response);
                            updateNowPlayingFromJson(json);
                        } catch (Exception e) {}
                    }
                    @Override
                    public void onError(String error) {
                        isSpotifyPolling = false;
                        hideNpLoading();
                        onConnectionFailure();
                    }
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

    private long lastSpotifyReinstallPrompt = 0;
    
    private void showSpotifyReinstallDialog() {
        if (System.currentTimeMillis() - lastSpotifyReinstallPrompt < 60000) {
            return; // Don't spam the dialog
        }
        lastSpotifyReinstallPrompt = System.currentTimeMillis();
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Spotify Error")
                    .setMessage("Spotify connection failed. Do you want to reinstall Spotify automatically? This will uninstall and download the latest version.")
                    .setPositiveButton("Reinstall", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            sendCommand("/api/system/spotify_reinstall");
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });
    }

    private void updateNowPlayingFromJson(JSONObject json) {
        hideNpLoading();
        if (json == null) return;
        try {
            String title = json.optString("title", "Not Playing");
            String artist = json.optString("artist", "");
            String album = json.optString("album", "");
            isNpPlaying = json.optBoolean("playing", false);
            boolean isAd = json.optBoolean("is_ad", false);
            
            // Check for Spotify CLI crash / connection failure
            if (title.toLowerCase().contains("failed to connect") || title.toLowerCase().contains("client connection failed")) {
                if (npTitle != null) npTitle.setText("Spotify Connection Failed");
                if (npArtist != null) npArtist.setText("Tap here or use reinstall dialog");
                showSpotifyReinstallDialog();
                return;
            }

            if (npTitle != null) npTitle.setText(title);
            if (npArtist != null) npArtist.setText(isAd ? "Advertisement" : artist);
            if (npAlbum != null) npAlbum.setText(album);

            if (isAd) {
                if (npArtwork != null) {
                    npArtwork.setColorFilter(android.graphics.Color.argb(150, 255, 255, 0), android.graphics.PorterDuff.Mode.SRC_ATOP);
                }
            } else {
                if (npArtwork != null) {
                    npArtwork.clearColorFilter();
                }
            }

            int incomingPosS = json.optInt("position_s", 0);
            int incomingLenS = json.optInt("length_s", 0);
            int incomingPosMs = json.optInt("position_ms", incomingPosS * 1000);

            currentLengthS = incomingLenS;
            if (npTimeTotal != null) npTimeTotal.setText(formatTime(currentLengthS));
            if (npProgress != null) npProgress.setMax(currentLengthS);

            long timeSinceSeek = System.currentTimeMillis() - lastSeekRequestTimeMs;
            if (timeSinceSeek > SEEK_IGNORE_POLL_MS) {
                currentPositionS = incomingPosS;
                currentPositionMs = incomingPosMs;
                lastSyncTimeMs = System.currentTimeMillis();
                if (npTimeCurrent != null && !isTrackingNpProgress) {
                    npTimeCurrent.setText(formatTime(currentPositionS));
                }
                if (npProgress != null && !isTrackingNpProgress) {
                    npProgress.setProgress(currentPositionS);
                }
            }

            String uri = json.optString("uri", "");
            String ctxUri = json.optString("context_uri", "");
            String ctxDesc = json.optString("context", "");

            if (npContext != null) {
                if (ctxDesc != null && !ctxDesc.isEmpty()) {
                    npContext.setText("Playing from: " + ctxDesc);
                    npContext.setVisibility(View.VISIBLE);
                } else {
                    npContext.setVisibility(View.GONE);
                }
            }

            if (!ctxUri.equals(currentContextUri)) {
                currentContextUri = ctxUri;
                if (musicLibrary != null && musicLibrary.getVisibility() == View.VISIBLE) {
                    renderLibraryFolder();
                }
            }

            boolean trackChanged = false;
            if (!uri.isEmpty() && !uri.equals(currentNpUri)) {
                trackChanged = true;
                currentNpUri = uri;
            } else if (uri.isEmpty() && (!title.equals(lastNpTitle) || !artist.equals(lastNpArtist))) {
                trackChanged = true;
            }

            if (trackChanged) {
                lastNpTitle = title;
                lastNpArtist = artist;

                if (prefs.getBoolean("show_album_art", true)) {
                    loadAlbumArt(currentNpUri);
                }
                if (!currentNpUri.isEmpty()) {
                    checkIfLiked(currentNpUri);
                }

                if (musicLyrics != null && musicLyrics.getVisibility() == View.VISIBLE) {
                    if (!isAd && !title.isEmpty() && !title.equals("Not Playing") && !title.equals("Spotify") && !title.equals("Refreshing...")) {
                        fetchLyrics(title, artist);
                    }
                }
            } else {
                lastNpTitle = title;
                lastNpArtist = artist;
            }

            // Sync shuffle and repeat states
            if (json.has("shuffle")) {
                shuffleOn = json.optBoolean("shuffle", false);
                Button btnShuf = (Button) findViewById(R.id.btn_shuffle);
                if (btnShuf != null) {
                    btnShuf.setText("Shuffle: " + (shuffleOn ? "ON" : "OFF"));
                    btnShuf.setTextColor(shuffleOn ? 0xFF00d4ff : 0xFFffffff);
                }
            }
            if (json.has("repeat")) {
                int rep = json.optInt("repeat", 0);
                if (rep == 1) repeatState = "track";
                else if (rep == 2) repeatState = "context";
                else repeatState = "off";

                Button btnRep = (Button) findViewById(R.id.btn_repeat);
                if (btnRep != null) {
                    String label = "OFF";
                    if (repeatState.equals("context")) label = "ALL";
                    if (repeatState.equals("track")) label = "ONE";
                    btnRep.setText("Repeat: " + label);
                    btnRep.setTextColor(repeatState.equals("off") ? 0xFFffffff : 0xFF00d4ff);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("DEBUG", "updateNowPlayingFromJson failed", e);
        }
    }

    private void seekTo(int positionMs, boolean isAbsolute) {
        int targetMs;
        if (isAbsolute) {
            targetMs = Math.max(0, positionMs);
            if (currentLengthS > 0) {
                targetMs = Math.min(targetMs, currentLengthS * 1000);
            }
        } else {
            targetMs = Math.max(0, currentPositionMs + positionMs);
            if (currentLengthS > 0) {
                targetMs = Math.min(targetMs, currentLengthS * 1000);
            }
        }

        currentPositionMs = targetMs;
        currentPositionS = targetMs / 1000;
        lastSyncTimeMs = System.currentTimeMillis();
        lastSeekRequestTimeMs = System.currentTimeMillis();

        if (npTimeCurrent != null) {
            npTimeCurrent.setText(formatTime(currentPositionS));
        }
        if (npProgress != null && !isTrackingNpProgress) {
            npProgress.setProgress(currentPositionS);
        }
        updateLyricsSync(targetMs);

        String url = isAbsolute ?
            ("/api/spotify/seek?ms=" + targetMs + "&type=absolute") :
            ("/api/spotify/seek?ms=" + positionMs + "&type=relative");

        if (api != null) {
            api.get(url, new ApiClient.Callback() {
                @Override public void onSuccess(String response) {}
                @Override public void onError(String error) {}
            });
        }
    }

    private void refreshLyricsOnOpen() {
        if (api == null) {
            if (lyricsContent != null) {
                clearContainerViews(lyricsContent);
                TextView tv = new TextView(MainActivity.this);
                tv.setText("Not connected to PC");
                tv.setTextColor(0xFF8892b0);
                tv.setTextSize(18);
                tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                tv.setPadding(0, 40, 0, 0);
                lyricsContent.addView(tv);
            }
            return;
        }

        // Display non-blocking in-container loading indicator if lyrics not yet loaded
        if (currentLyrics.isEmpty() || (npTitle != null && !npTitle.getText().toString().equals(currentLyricsTrack))) {
            if (lyricsContent != null) {
                clearContainerViews(lyricsContent);
                TextView loadingTv = new TextView(MainActivity.this);
                loadingTv.setText("Checking current song...");
                loadingTv.setTextColor(0xFF8892b0);
                loadingTv.setTextSize(18);
                loadingTv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                loadingTv.setPadding(0, 40, 0, 0);
                lyricsContent.addView(loadingTv);
            }
        }

        // Actively query /api/spotify/now_playing with force=true to get fresh track state
        api.get("/api/spotify/now_playing?force=true", new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            updateNowPlayingFromJson(json);

                            String title = json.optString("title", "");
                            String artist = json.optString("artist", "");
                            boolean isAd = json.optBoolean("is_ad", false);

                            if (isAd) {
                                currentLyricsTrack = "";
                                currentLyrics.clear();
                                currentLyricLineIndex = -1;
                                if (lyricsContent != null) {
                                    clearContainerViews(lyricsContent);
                                    TextView tv = new TextView(MainActivity.this);
                                    tv.setText("Advertisement playing");
                                    tv.setTextColor(0xFF8892b0);
                                    tv.setTextSize(18);
                                    tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                                    tv.setPadding(0, 40, 0, 0);
                                    lyricsContent.addView(tv);
                                }
                            } else if (title.isEmpty() || title.equals("Not Playing") || title.equals("Spotify") || title.equals("Refreshing...")) {
                                currentLyricsTrack = "";
                                currentLyrics.clear();
                                currentLyricLineIndex = -1;
                                if (lyricsContent != null) {
                                    clearContainerViews(lyricsContent);
                                    TextView tv = new TextView(MainActivity.this);
                                    tv.setText("No song currently playing");
                                    tv.setTextColor(0xFF8892b0);
                                    tv.setTextSize(18);
                                    tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                                    tv.setPadding(0, 40, 0, 0);
                                    lyricsContent.addView(tv);
                                }
                            } else {
                                if (!title.equals(currentLyricsTrack) || currentLyrics.isEmpty()) {
                                    fetchLyrics(title, artist);
                                } else {
                                    // Track already loaded: update sync position immediately
                                    updateLyricsSync(currentPositionMs);
                                }
                            }
                        } catch (Exception e) {
                            String fbTitle = npTitle != null ? npTitle.getText().toString() : "";
                            String fbArtist = npArtist != null ? npArtist.getText().toString() : "";
                            fetchLyrics(fbTitle, fbArtist);
                        }
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String fbTitle = npTitle != null ? npTitle.getText().toString() : "";
                        String fbArtist = npArtist != null ? npArtist.getText().toString() : "";
                        if (!fbTitle.isEmpty() && !fbTitle.equals("Not Playing") && !fbTitle.equals("Refreshing...") && !fbTitle.equals("Spotify")) {
                            fetchLyrics(fbTitle, fbArtist);
                        } else {
                            if (lyricsContent != null) {
                                clearContainerViews(lyricsContent);
                                TextView tv = new TextView(MainActivity.this);
                                tv.setText("Unable to sync now playing with PC");
                                tv.setTextColor(0xFF8892b0);
                                tv.setTextSize(18);
                                tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                                tv.setPadding(0, 40, 0, 0);
                                lyricsContent.addView(tv);
                            }
                        }
                    }
                });
            }
        });
    }

    private Runnable progressExtrapolator = new Runnable() {
        @Override
        public void run() {
            if (isNpPlaying && currentLengthS > 0 && !isTrackingNpProgress) {
                long elapsed = System.currentTimeMillis() - lastSyncTimeMs;
                int estS = (currentPositionMs + (int) elapsed) / 1000;
                if (estS > currentLengthS) estS = currentLengthS;
                currentPositionS = estS;
                if (npTimeCurrent != null) npTimeCurrent.setText(formatTime(currentPositionS));
                if (npProgress != null) npProgress.setProgress(currentPositionS);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private Runnable lyricsSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (isNpPlaying && musicLyrics != null && musicLyrics.getVisibility() == View.VISIBLE) {
                long elapsed = System.currentTimeMillis() - lastSyncTimeMs;
                int estimatedMs = currentPositionMs + (int) elapsed;
                updateLyricsSync(estimatedMs);
            }
            handler.postDelayed(this, 100);
        }
    };


    private boolean isSysStatsPolling = false;
    private Runnable pollSystemStats = new Runnable() {
        @Override
        public void run() {
            if (isSysStatsPolling) {
                handler.postDelayed(this, 1000);
                return;
            }
            if (screenMain.getVisibility() == View.VISIBLE && tabSettings.getVisibility() == View.VISIBLE) {
                isSysStatsPolling = true;
                api.get("/api/system/stats", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        isSysStatsPolling = false;
                        onConnectionSuccess();
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
                    public void onError(String error) {
                        isSysStatsPolling = false;
                        onConnectionFailure();
                    }
                });
            }
            handler.postDelayed(this, 3000);
        }
    };

    // Bitmap Cache & OOM Optimization for Dalvik on legacy ARMv7 hardware (Kindle Fire)
    private static class BitmapCache {
        private static final int MAX_SIZE_BYTES = 4 * 1024 * 1024; // 4MB cache cap
        private final java.util.LinkedHashMap<String, Bitmap> cache;
        private int currentSizeBytes = 0;

        public BitmapCache() {
            this.cache = new java.util.LinkedHashMap<String, Bitmap>(16, 0.75f, true);
        }

        public synchronized Bitmap get(String key) {
            if (key == null) return null;
            return cache.get(key);
        }

        public synchronized void put(String key, Bitmap bitmap) {
            if (key == null || bitmap == null || bitmap.isRecycled()) return;
            int size = getBitmapSize(bitmap);
            if (size > MAX_SIZE_BYTES) return;

            Bitmap existing = cache.put(key, bitmap);
            if (existing != null) {
                currentSizeBytes -= getBitmapSize(existing);
            }
            currentSizeBytes += size;

            while (currentSizeBytes > MAX_SIZE_BYTES && !cache.isEmpty()) {
                java.util.Map.Entry<String, Bitmap> entry = cache.entrySet().iterator().next();
                Bitmap removed = entry.getValue();
                cache.remove(entry.getKey());
                if (removed != null) {
                    currentSizeBytes -= getBitmapSize(removed);
                }
            }
        }

        public synchronized void clear() {
            cache.clear();
            currentSizeBytes = 0;
        }

        private int getBitmapSize(Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled()) return 0;
            return bitmap.getRowBytes() * bitmap.getHeight();
        }
    }

    private BitmapCache bitmapCache = new BitmapCache();

    private static Bitmap decodeSampledBitmapFromBytes(byte[] data, int reqWidth, int reqHeight) {
        return decodeSampledBitmapFromBytes(data, reqWidth, reqHeight, false);
    }

    private static Bitmap decodeSampledBitmapFromBytes(byte[] data, int reqWidth, int reqHeight, boolean pixelPerfect) {
        if (data == null || data.length == 0) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inScaled = false;
            if (!pixelPerfect && reqWidth > 0 && reqHeight > 0) {
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
                options.inJustDecodeBounds = false;
            } else {
                options.inSampleSize = 1;
            }
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private static byte[] downloadUrlToBytes(String urlStr) {
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            is = conn.getInputStream();
            baos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (baos != null) try { baos.close(); } catch (Exception ignored) {}
            if (is != null) try { is.close(); } catch (Exception ignored) {}
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    private void clearContainerViews(ViewGroup container) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            releaseViewBitmaps(container.getChildAt(i));
        }
        container.removeAllViews();
    }

    private void releaseViewBitmaps(View v) {
        if (v == null) return;
        if (v instanceof ImageView) {
            ImageView iv = (ImageView) v;
            iv.setTag(null);
            iv.setImageBitmap(null);
            iv.setImageDrawable(null);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                releaseViewBitmaps(vg.getChildAt(i));
            }
        }
    }

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
                                allowSettings = true;
                                handler.removeCallbacks(blockSettings);
                                getApplicationContext().getSharedPreferences("MediaCentre", MODE_PRIVATE)
                                    .edit().putBoolean("unlocked", true).commit();
                                
                                try {
                                    getPackageManager().clearPackagePreferredActivities(getPackageName());
                                } catch (Exception ignored) {}

                                final String[] launchers = {"Kindle Launcher", "ADW Launcher", "Android Settings"};
                                final String[] packages = {"com.amazon.kindle.otter.launcher", "org.adw.launcher", "com.android.settings"};
                                
                                if (!isFinishing()) {
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
                                }
                            } else {
                                getApplicationContext().getSharedPreferences("MediaCentre", MODE_PRIVATE)
                                    .edit().putBoolean("unlocked", false).commit();
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
            if (isFinishing() || allowSettings || prefs.getBoolean("unlocked", false)) {
                return;
            }
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                try {
                    String topPackage = am.getRunningTasks(1).get(0).topActivity.getPackageName();
                    if (topPackage.equals("com.android.settings") || topPackage.equals("com.amazon.kindle.otter.settings")) {
                        Intent i = new Intent(MainActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                    }
                } catch (Exception e) {}
            }
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("MediaCentre", MODE_PRIVATE);
        allowSettings = prefs.getBoolean("unlocked", false);

        initViews();
        setupConnectionScreen();
        setupTabs();
        setupMusicControls();
        setupSettings();
        setupAudioControls();
        setupPowerControls();

        // Auto-connect
        String lastIp = prefs.getString("last_ip", "");
        if (!lastIp.isEmpty()) {
            connectToIp(lastIp);
        }
        
        // Apply theme on startup
        applyTheme(prefs.getString("theme", "dark"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        allowSettings = prefs.getBoolean("unlocked", false);

        handler.removeCallbacks(pollSpotify);
        handler.removeCallbacks(progressExtrapolator);
        handler.removeCallbacks(lyricsSyncRunnable);
        handler.removeCallbacks(pollSystemStats);
        handler.removeCallbacks(pollUnlock);
        handler.removeCallbacks(blockSettings);

        handler.post(pollSpotify);
        handler.post(progressExtrapolator);
        handler.post(lyricsSyncRunnable);
        handler.post(pollSystemStats);
        handler.post(pollUnlock);
        if (!allowSettings) {
            handler.post(blockSettings);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(pollSpotify);
        handler.removeCallbacks(progressExtrapolator);
        handler.removeCallbacks(lyricsSyncRunnable);
        handler.removeCallbacks(pollSystemStats);
        handler.removeCallbacks(pollUnlock);
        // deliberately leaving blockSettings running to ensure it constantly blocks escaping
    }

    private void initViews() {
        try { screenConnection = (LinearLayout) findViewById(R.id.screen_connection); } catch (Exception e) { android.util.Log.e("DEBUG", "screen_connection failed", e); }
        try { screenMain = (LinearLayout) findViewById(R.id.screen_main); } catch (Exception e) { android.util.Log.e("DEBUG", "screen_main failed", e); }
        try { ipListContainer = (LinearLayout) findViewById(R.id.ip_list_container); } catch (Exception e) { android.util.Log.e("DEBUG", "ip_list_container failed", e); }
        try { loadingOverlay = (LinearLayout) findViewById(R.id.loading_overlay); } catch (Exception e) { android.util.Log.e("DEBUG", "loading_overlay failed", e); }
        try { loadingText = (TextView) findViewById(R.id.loading_text); } catch (Exception e) { android.util.Log.e("DEBUG", "loading_text failed", e); }
        try { statusToast = (TextView) findViewById(R.id.status_toast); } catch (Exception e) { android.util.Log.e("DEBUG", "status_toast failed", e); }

        try { connectionStatusContainer = (LinearLayout) findViewById(R.id.connection_status_container); } catch (Exception e) { android.util.Log.e("DEBUG", "connectionStatusContainer failed", e); }
        try { connectionStatusDot = (View) findViewById(R.id.connection_status_dot); } catch (Exception e) { android.util.Log.e("DEBUG", "connectionStatusDot failed", e); }
        try { connectionStatusText = (TextView) findViewById(R.id.connection_status_text); } catch (Exception e) { android.util.Log.e("DEBUG", "connectionStatusText failed", e); }
        updateConnectionStatus(STATUS_OFFLINE);

        try { tabMusic = (LinearLayout) findViewById(R.id.tab_music); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_music failed", e); }
        try { tabSettings = (ScrollView) findViewById(R.id.tab_settings); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_settings failed", e); }
        try { tabPower = (LinearLayout) findViewById(R.id.tab_power); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_power failed", e); }

        try { tabButtons = new Button[]{
            (Button) findViewById(R.id.tab_btn_music),
            (Button) findViewById(R.id.tab_btn_settings),
            (Button) findViewById(R.id.tab_btn_power)
        }; } catch (Exception e) { android.util.Log.e("DEBUG", "tabButtons failed", e); }

        try { musicNowPlayingContainer = (FrameLayout) findViewById(R.id.music_now_playing_container); } catch (Exception e) { android.util.Log.e("DEBUG", "musicNowPlayingContainer failed", e); }
        try { musicNowPlaying = (ScrollView) findViewById(R.id.music_now_playing); } catch (Exception e) { android.util.Log.e("DEBUG", "musicNowPlaying failed", e); }
        try { npLoadingOverlay = (LinearLayout) findViewById(R.id.np_loading_overlay); } catch (Exception e) { android.util.Log.e("DEBUG", "npLoadingOverlay failed", e); }
        try { npLoadingText = (TextView) findViewById(R.id.np_loading_text); } catch (Exception e) { android.util.Log.e("DEBUG", "npLoadingText failed", e); }
        try { musicSearch = (LinearLayout) findViewById(R.id.music_search); } catch (Exception e) { android.util.Log.e("DEBUG", "musicSearch failed", e); }
        try { musicLibrary = (ScrollView) findViewById(R.id.music_library); } catch (Exception e) { android.util.Log.e("DEBUG", "musicLibrary failed", e); }
        try { musicQueue = (ScrollView) findViewById(R.id.music_queue); } catch (Exception e) { android.util.Log.e("DEBUG", "musicQueue failed", e); }
        try { musicLyrics = (ScrollView) findViewById(R.id.music_lyrics); } catch (Exception e) {}
        try { lyricsContent = (LinearLayout) findViewById(R.id.lyrics_content); } catch (Exception e) {}

        try { musicSubTabButtons = new Button[]{
            (Button) findViewById(R.id.btn_music_playing),
            (Button) findViewById(R.id.btn_music_search),
            (Button) findViewById(R.id.btn_music_library),
            (Button) findViewById(R.id.btn_music_queue),
            (Button) findViewById(R.id.btn_music_lyrics)
        }; } catch (Exception e) { android.util.Log.e("DEBUG", "musicSubTabButtons failed", e); }

        try { npTitle = (TextView) findViewById(R.id.np_title); } catch (Exception e) { android.util.Log.e("DEBUG", "npTitle failed", e); }
        try { npContext = (TextView) findViewById(R.id.np_context); } catch (Exception e) { android.util.Log.e("DEBUG", "npContext failed", e); }
        try { npArtist = (TextView) findViewById(R.id.np_artist); } catch (Exception e) { android.util.Log.e("DEBUG", "npArtist failed", e); }
        try { npAlbum = (TextView) findViewById(R.id.np_album); } catch (Exception e) { android.util.Log.e("DEBUG", "npAlbum failed", e); }
        try { volLabel = (TextView) findViewById(R.id.vol_label); } catch (Exception e) { android.util.Log.e("DEBUG", "volLabel failed", e); }
        try { 
            npArtwork = (ImageView) findViewById(R.id.np_artwork); 
            if (npArtwork != null) {
                boolean pixelPerfect = prefs.getBoolean("pixel_perfect_art", false);
                npArtwork.setScaleType(pixelPerfect ? ImageView.ScaleType.CENTER : ImageView.ScaleType.FIT_CENTER);
            }
        } catch (Exception e) { android.util.Log.e("DEBUG", "npArtwork failed", e); }
        try { npTimeCurrent = (TextView) findViewById(R.id.np_time_current); } catch (Exception e) {}
        try { npTimeTotal = (TextView) findViewById(R.id.np_time_total); } catch (Exception e) {}
        try { 
            npProgress = (SeekBar) findViewById(R.id.np_progress); 
            if (npProgress != null) {
                npProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            if (npTimeCurrent != null) {
                                npTimeCurrent.setText(formatTime(progress));
                            }
                            updateLyricsSync(progress * 1000);
                        }
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        isTrackingNpProgress = true;
                    }
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        isTrackingNpProgress = false;
                        int seekMs = seekBar.getProgress() * 1000;
                        seekTo(seekMs, true);
                    }
                });
            }
        } catch (Exception e) {}

        try { toggleAlbumArt = (ToggleButton) findViewById(R.id.toggle_album_art); } catch (Exception e) { android.util.Log.e("DEBUG", "toggleAlbumArt failed", e); }
        try { togglePixelPerfectArt = (ToggleButton) findViewById(R.id.toggle_pixel_perfect_art); } catch (Exception e) { android.util.Log.e("DEBUG", "togglePixelPerfectArt failed", e); }
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
        if (toggleAlbumArt != null) toggleAlbumArt.setChecked(prefs.getBoolean("show_album_art", true));
        if (togglePixelPerfectArt != null) togglePixelPerfectArt.setChecked(prefs.getBoolean("pixel_perfect_art", false));
        
        ((Button) findViewById(R.id.btn_disconnect)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                screenMain.setVisibility(View.GONE);
                screenConnection.setVisibility(View.VISIBLE);
                api = null;
                consecutiveConnectionFailures = 2;
                updateConnectionStatus(STATUS_OFFLINE);
            }
        });

        ((Button) findViewById(R.id.btn_refresh)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNpLoading("Refreshing...");
                updateConnectionStatus(STATUS_CONNECTING);
                npTitle.setText("Refreshing...");
                npArtist.setText("");
                npAlbum.setText("");
                npArtwork.setImageBitmap(null);
                ProgressBar npArtProgress = (ProgressBar) findViewById(R.id.np_art_progress);
                if (npArtProgress != null) {
                    npArtProgress.setVisibility(View.GONE);
                    npArtProgress.setIndeterminate(false);
                    npArtProgress.setIndeterminate(true);
                }
                LinearLayout npArtErrorLayout = (LinearLayout) findViewById(R.id.np_art_error_layout);
                if (npArtErrorLayout != null) npArtErrorLayout.setVisibility(View.GONE);
                
                currentNpUri = "";
                lastArtUri = "";
                
                if (npTimeCurrent != null) npTimeCurrent.setText("--:--");
                if (npTimeTotal != null) npTimeTotal.setText("--:--");
                if (npProgress != null) npProgress.setProgress(0);
                
                volLabel.setText("--%");
                
                Button btnShuf = (Button) findViewById(R.id.btn_shuffle);
                if (btnShuf != null) { btnShuf.setText("Shuffle"); btnShuf.setTextColor(0xFFffffff); }
                
                Button btnRep = (Button) findViewById(R.id.btn_repeat);
                if (btnRep != null) { btnRep.setText("Repeat"); btnRep.setTextColor(0xFFffffff); }
                
                Button btnLike = (Button) findViewById(R.id.btn_np_like);
                if (btnLike != null) { btnLike.setText("Like"); btnLike.setTextColor(0xFFffffff); }

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
                styleDynamicButton(btn);
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
                            if ("test".equalsIgnoreCase(newIp)) {
                                testUnlockCounter++;
                                if (testUnlockCounter >= 4) {
                                    allowSettings = !allowSettings;
                                    if (allowSettings) {
                                        handler.removeCallbacks(blockSettings);
                                        getApplicationContext().getSharedPreferences("MediaCentre", MODE_PRIVATE)
                                                .edit().putBoolean("unlocked", true).commit();
                                        android.widget.Toast.makeText(MainActivity.this, "Developer Mode Unlocked", android.widget.Toast.LENGTH_SHORT).show();
                                    } else {
                                        handler.post(blockSettings);
                                        getApplicationContext().getSharedPreferences("MediaCentre", MODE_PRIVATE)
                                                .edit().putBoolean("unlocked", false).commit();
                                        android.widget.Toast.makeText(MainActivity.this, "Developer Mode Locked", android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                    testUnlockCounter = 0;
                                }
                            } else {
                                testUnlockCounter = 0;
                                if (!newIp.isEmpty()) {
                                    saveIp(newIp);
                                    connectToIp(newIp);
                                }
                            }
                        }
                    }).setNegativeButton("Cancel", null).show();
            }
        });

        ((Button) findViewById(R.id.btn_remove_pc)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final java.util.ArrayList<String> savedIps = new java.util.ArrayList<String>();
                for (int i = 0; i < 5; i++) {
                    String ip = prefs.getString("ip_" + i, "");
                    if (!ip.isEmpty()) savedIps.add(ip);
                }
                
                if (savedIps.isEmpty()) {
                    showToast("No PCs saved", false);
                    return;
                }
                
                String[] ipArray = savedIps.toArray(new String[0]);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Remove PC")
                    .setItems(ipArray, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String ipToRemove = savedIps.get(which);
                            removeIp(ipToRemove);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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

    private void removeIp(String ip) {
        java.util.ArrayList<String> newIps = new java.util.ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            String savedIp = prefs.getString("ip_" + i, "");
            if (!savedIp.isEmpty() && !savedIp.equals(ip)) {
                newIps.add(savedIp);
            }
        }
        
        for (int i = 0; i < 5; i++) {
            if (i < newIps.size()) {
                prefs.edit().putString("ip_" + i, newIps.get(i)).commit();
            } else {
                prefs.edit().remove("ip_" + i).commit();
            }
        }
        setupConnectionScreen();
    }

    private void connectToIp(String ip) {
        currentIp = ip;
        prefs.edit().putString("last_ip", ip).commit();
        api = new ApiClient(ip);
        
        updateConnectionStatus(STATUS_CONNECTING);
        showLoading("Connecting to " + ip + "...");
        api.get("/api/status", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                hideLoading();
                onConnectionSuccess();
                try {
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    
                    String appVersion = "v1.8";
                    try {
                        appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (Exception e) {}

                    String serverVersion = json.optString("version", appVersion);
                    final String targetVersion = serverVersion;
                    if (!appVersion.equals(serverVersion)) {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Update Available")
                            .setMessage("An update is available (Version " + serverVersion + ").\nDo you want to update this tablet app now?\n\n(Current version: " + appVersion + ")")
                            .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    api.get("/api/system/tablet_update_install?version=" + targetVersion, new ApiClient.Callback() {
                                        @Override
                                        public void onSuccess(String response) {
                                            showToast("Update started! Please wait a moment...", true);
                                        }
                                        @Override
                                        public void onError(String error) {
                                            showToast("Failed to start update: " + error, false);
                                        }
                                    });
                                }
                            })
                            .setNegativeButton("Ignore", null)
                            .show();
                    } else {
                        showToast("Up-to-date", true);
                    }
                } catch (Exception e) {
                    showToast("Connected", true);
                }
                screenConnection.setVisibility(View.GONE);
                screenMain.setVisibility(View.VISIBLE);
                switchTab(0);
            }
            @Override
            public void onError(String error) {
                hideLoading();
                consecutiveConnectionFailures = 2;
                updateConnectionStatus(STATUS_OFFLINE);
                showToast("Failed to connect", false);
            }
        });
    }

    /**
     * Initializes the main navigation tabs (Music, System, Settings) and handles 
     * sub-tab navigation. Critically, this includes aggressive memory management: 
     * when navigating away from the 'Library' or 'Queue' tabs, all dynamically 
     * generated ImageViews are completely removed via `removeAllViews()` to 
     * prevent OutOfMemory crashes on older hardware (like the Amazon Fire HD 10).
     */
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
        if (musicNowPlayingContainer != null) {
            musicNowPlayingContainer.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        } else if (musicNowPlaying != null) {
            musicNowPlaying.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        }
        musicSearch.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        musicLibrary.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (musicQueue != null) musicQueue.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        if (musicLyrics != null) musicLyrics.setVisibility(index == 4 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < musicSubTabButtons.length; i++) {
            musicSubTabButtons[i].setTextColor(i == index ? 0xFF00d4ff : 0xFF8892b0);
            musicSubTabButtons[i].setBackgroundColor(i == index ? 0xFF1a1f36 : 0xFF111827);
        }
        
        if (index == 0) {
            showNpLoading("Loading Now Playing...");
            if (api != null) {
                api.get("/api/spotify/now_playing", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(final String response) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideNpLoading();
                                onConnectionSuccess();
                                try {
                                    JSONObject json = new JSONObject(response);
                                    updateNowPlayingFromJson(json);
                                } catch (Exception e) {}
                            }
                        });
                    }
                    @Override
                    public void onError(final String error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideNpLoading();
                                onConnectionFailure();
                            }
                        });
                    }
                });
            } else {
                hideNpLoading();
            }
        }
        
        if (index == 2) {
            loadLibrary();
        } else {
            LinearLayout libContainer = (LinearLayout) findViewById(R.id.library_content);
            if (libContainer != null) clearContainerViews(libContainer);
        }
        
        if (index == 3) {
            loadQueue();
        } else {
            LinearLayout queueContainer = (LinearLayout) findViewById(R.id.queue_content);
            if (queueContainer != null) clearContainerViews(queueContainer);
        }
        
        if (index != 1) {
            LinearLayout searchContainer = (LinearLayout) findViewById(R.id.search_results);
            if (searchContainer != null) clearContainerViews(searchContainer);
        }
        
        if (index == 4) {
            refreshLyricsOnOpen();
        }
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
            @Override public void onClick(View v) { seekTo(15000, false); }
        });
        ((Button) findViewById(R.id.btn_seek_back)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { seekTo(-15000, false); }
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
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.spinner_item, types);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
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
    }
    private void renderSearchResults(String jsonStr) {
        LinearLayout container = (LinearLayout) findViewById(R.id.search_results);
        clearContainerViews(container);
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
                    styleDynamicButton(retryBtn);
                    errLayout.addView(retryBtn);
                    
                    artContainer.addView(errLayout);

                    final String uri = item.optString("uri", "");
                    
                    final Runnable fetchImage = new Runnable() {
                        @Override
                        public void run() {
                            iv.setTag(null);
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
                                        if (!imgUrl.isEmpty()) {
                                            iv.setTag(imgUrl);
                                            Bitmap cached = bitmapCache.get(imgUrl);
                                            if (cached != null) {
                                                pb.setVisibility(View.GONE);
                                                iv.setImageBitmap(cached);
                                                return;
                                            }
                                            new AsyncTask<Void, Void, Bitmap>() {
                                                @Override protected Bitmap doInBackground(Void... voids) {
                                                    byte[] data = downloadUrlToBytes(imgUrl);
                                                    return decodeSampledBitmapFromBytes(data, 100, 100);
                                                }
                                                @Override protected void onPostExecute(Bitmap b) {
                                                    pb.setVisibility(View.GONE);
                                                    if (b != null) {
                                                        bitmapCache.put(imgUrl, b);
                                                        if (imgUrl.equals(iv.getTag())) {
                                                            iv.setImageBitmap(b);
                                                        }
                                                    } else {
                                                        if (imgUrl.equals(iv.getTag())) {
                                                            showError("Download failed");
                                                        }
                                                    }
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
                    tvName.setSingleLine(true);
                    tvName.setEllipsize(TextUtils.TruncateAt.END);
                    textCol.addView(tvName);

                    TextView tvArtist = new TextView(MainActivity.this);
                    tvArtist.setText(item.optString("artist", ""));
                    tvArtist.setTextSize(14);
                    tvArtist.setTextColor(0xFF8892b0);
                    tvArtist.setSingleLine(true);
                    tvArtist.setEllipsize(TextUtils.TruncateAt.END);
                    textCol.addView(tvArtist);
                    
                    row.addView(textCol);
                    
                    // Buttons (Right)
                    LinearLayout btns = new LinearLayout(MainActivity.this);
                    btns.setOrientation(LinearLayout.HORIZONTAL);
                    
                    String type = item.optString("type", "");
                    
                    Button playBtn = new Button(MainActivity.this);
                    playBtn.setText("Play");
                    styleDynamicButton(playBtn);
                    playBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri)); }
                    });
                    btns.addView(playBtn);
                    
                    Button queueBtn = new Button(MainActivity.this);
                    queueBtn.setText("Queue");
                    styleDynamicButton(queueBtn);
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
                    styleDynamicButton(likeBtn);
                    likeBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { sendCommand("/api/spotify/library/add?uri=" + java.net.URLEncoder.encode(uri)); }
                    });
                    btns.addView(likeBtn);

                    if (type.equals("track")) {
                        Button playlistBtn = new Button(MainActivity.this);
                        playlistBtn.setText("Add to Playlist");
                        styleDynamicButton(playlistBtn);
                        playlistBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { showPlaylistSelector(uri); }
                        });
                        btns.addView(playlistBtn);
                    }
                    
                    row.addView(btns);
                    container.addView(row);
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
        clearContainerViews(container);
        
        Button likedBtn = new Button(MainActivity.this);
        likedBtn.setText("My Liked Songs");
        if ("spotify:collection:tracks".equals(currentContextUri)) {
            likedBtn.setTextColor(0xFF00FFaa);
            likedBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            likedBtn.setTextColor(0xFF00d4ff);
            likedBtn.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        likedBtn.setTextSize(20);
        likedBtn.setPadding(20, 40, 20, 40);
        styleDynamicButton(likedBtn);
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
                    styleDynamicButton(retryBtn);
                    errLayout.addView(retryBtn);
                    
                    artContainer.addView(errLayout);
                    
                    final String finalArtUriToLoad = artUriToLoad;
                    
                    final Runnable fetchImage = new Runnable() {
                        @Override
                        public void run() {
                            img.setTag(null);
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
                                        if (!imgUrl.isEmpty()) {
                                            img.setTag(imgUrl);
                                            Bitmap cached = bitmapCache.get(imgUrl);
                                            if (cached != null) {
                                                pb.setVisibility(View.GONE);
                                                img.setImageBitmap(cached);
                                                return;
                                            }
                                            new AsyncTask<Void, Void, Bitmap>() {
                                                @Override protected Bitmap doInBackground(Void... voids) {
                                                    byte[] data = downloadUrlToBytes(imgUrl);
                                                    return decodeSampledBitmapFromBytes(data, 100, 100);
                                                }
                                                @Override protected void onPostExecute(Bitmap b) {
                                                    pb.setVisibility(View.GONE);
                                                    if (b != null) {
                                                        bitmapCache.put(imgUrl, b);
                                                        if (imgUrl.equals(img.getTag())) {
                                                            img.setImageBitmap(b);
                                                        }
                                                    } else {
                                                        if (imgUrl.equals(img.getTag())) {
                                                            showError("Download failed");
                                                        }
                                                    }
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
                if (uri.equals(currentContextUri) && !uri.isEmpty()) {
                    tvName.setTextColor(0xFF00FFaa); // Glowing green-blue
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    tvName.setTextColor(type.equals("folder") ? 0xFF00d4ff : 0xFFFFFFFF);
                    tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
                tvName.setTextSize(18);
                tvName.setSingleLine(true);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(tvName);
                
                TextView tvArtist = new TextView(MainActivity.this);
                tvArtist.setText(item.optString("artist", ""));
                tvArtist.setTextSize(14);
                tvArtist.setTextColor(0xFF8892b0);
                tvArtist.setSingleLine(true);
                tvArtist.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(tvArtist);
                
                row.addView(textCol);
                
                LinearLayout btns = new LinearLayout(MainActivity.this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                
                if (type.equals("folder")) {
                    Button openBtn = new Button(MainActivity.this);
                    openBtn.setText(expandedFolders.contains(uri) ? "Close folder" : "Open folder");
                    styleDynamicButton(openBtn);
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
                    styleDynamicButton(playBtn);
                    playBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri));
                        }
                    });
                    btns.addView(playBtn);
                    
                    Button removeBtn = new Button(MainActivity.this);
                    removeBtn.setText("Remove from library");
                    styleDynamicButton(removeBtn);
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
        } catch (Exception e) {}
    }

    private void loadQueue() {
        showLoading("Loading Queue...");
        api.get("/api/spotify/queue", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                hideLoading();
                LinearLayout container = (LinearLayout) findViewById(R.id.queue_content);
                clearContainerViews(container);
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
                                styleDynamicButton(retryBtn);
                                errLayout.addView(retryBtn);
                                
                                artContainer.addView(errLayout);
                                
                                final Runnable fetchImage = new Runnable() {
                                    @Override
                                    public void run() {
                                        img.setTag(null);
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
                                                    if (!imgUrl.isEmpty()) {
                                                        img.setTag(imgUrl);
                                                        Bitmap cached = bitmapCache.get(imgUrl);
                                                        if (cached != null) {
                                                            pb.setVisibility(View.GONE);
                                                            img.setImageBitmap(cached);
                                                            return;
                                                        }
                                                        new AsyncTask<Void, Void, Bitmap>() {
                                                            @Override protected Bitmap doInBackground(Void... voids) {
                                                                byte[] data = downloadUrlToBytes(imgUrl);
                                                                return decodeSampledBitmapFromBytes(data, 100, 100);
                                                            }
                                                            @Override protected void onPostExecute(Bitmap b) {
                                                                pb.setVisibility(View.GONE);
                                                                if (b != null) {
                                                                    bitmapCache.put(imgUrl, b);
                                                                    if (imgUrl.equals(img.getTag())) {
                                                                        img.setImageBitmap(b);
                                                                    }
                                                                } else {
                                                                    if (imgUrl.equals(img.getTag())) {
                                                                        showError("Download failed");
                                                                    }
                                                                }
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
                            tv.setSingleLine(true);
                            tv.setEllipsize(TextUtils.TruncateAt.END);
                            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                            row.addView(tv);
                            
                            if (!uri.isEmpty()) {
                                Button btnPlay = new Button(MainActivity.this);
                                btnPlay.setText("Play");
                                btnPlay.setPadding(20, 10, 20, 10);
                                styleDynamicButton(btnPlay);
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


    private void setupSettings() {
        try {
            final android.widget.ToggleButton toggleAlbumArt = (android.widget.ToggleButton) findViewById(R.id.toggle_album_art);
            if (toggleAlbumArt != null) {
                toggleAlbumArt.setChecked(prefs.getBoolean("show_album_art", true));
                toggleAlbumArt.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                        prefs.edit().putBoolean("show_album_art", isChecked).apply();
                        if (api != null) {
                            api.get("/api/config/set?key=show_album_art&value=" + isChecked, new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {}
                                @Override public void onError(String error) {}
                            });
                        }
                    }
                });
            }

            final android.widget.ToggleButton togglePixelPerfectArt = (android.widget.ToggleButton) findViewById(R.id.toggle_pixel_perfect_art);
            if (togglePixelPerfectArt != null) {
                togglePixelPerfectArt.setChecked(prefs.getBoolean("pixel_perfect_art", false));
                togglePixelPerfectArt.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                        prefs.edit().putBoolean("pixel_perfect_art", isChecked).apply();
                        if (npArtwork != null) {
                            npArtwork.setScaleType(isChecked ? ImageView.ScaleType.CENTER : ImageView.ScaleType.FIT_CENTER);
                            npArtwork.invalidate();
                        }
                        if (api != null) {
                            api.get("/api/config/set?key=pixel_perfect_art&value=" + isChecked, new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {}
                                @Override public void onError(String error) {}
                            });
                        }
                    }
                });
            }

            final Button btnTheme = (Button) findViewById(R.id.btn_theme);
            if (btnTheme != null) {
                btnTheme.setText(prefs.getString("theme", "dark").substring(0, 1).toUpperCase() + prefs.getString("theme", "dark").substring(1));
                btnTheme.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String cur = prefs.getString("theme", "dark");
                        String next = cur.equals("dark") ? "light" : (cur.equals("light") ? "native" : "dark");
                        prefs.edit().putString("theme", next).apply();
                        btnTheme.setText(next.substring(0, 1).toUpperCase() + next.substring(1));
                        applyTheme(next);
                        if (api != null) {
                            api.get("/api/config/set?key=theme&value=" + next, new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {}
                                @Override public void onError(String error) {}
                            });
                        }
                    }
                });
            }
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
        applyThemeToView(root, bgPrimary, bgSecondary, textPrimary, textSecondary, theme);
        
        // Restore tab highlights manually to avoid triggering loadLibrary() loops
        int currentMainTab = (tabMusic != null && tabMusic.getVisibility() == View.VISIBLE) ? 0 : 
                             (tabSettings != null && tabSettings.getVisibility() == View.VISIBLE) ? 1 : 2;
        if (tabButtons != null) {
            for (int i = 0; i < tabButtons.length; i++) {
                if (tabButtons[i] != null) {
                    tabButtons[i].setTextColor(i == currentMainTab ? 0xFF00d4ff : 0xFF8892b0);
                    tabButtons[i].setBackgroundColor(i == currentMainTab ? 0xFF1a1f36 : 0xFF111827);
                }
            }
        }
        
        int currentSubTab = 0;
        if (musicSearch != null && musicSearch.getVisibility() == View.VISIBLE) currentSubTab = 1;
        if (musicLibrary != null && musicLibrary.getVisibility() == View.VISIBLE) currentSubTab = 2;
        if (musicQueue != null && musicQueue.getVisibility() == View.VISIBLE) currentSubTab = 3;
        if (musicLyrics != null && musicLyrics.getVisibility() == View.VISIBLE) currentSubTab = 4;
        
        if (musicSubTabButtons != null) {
            for (int i = 0; i < musicSubTabButtons.length; i++) {
                if (musicSubTabButtons[i] != null) {
                    musicSubTabButtons[i].setTextColor(i == currentSubTab ? 0xFF00d4ff : 0xFF8892b0);
                    musicSubTabButtons[i].setBackgroundColor(i == currentSubTab ? 0xFF1a1f36 : 0xFF111827);
                }
            }
        }
    }
    
    private void applyThemeToView(View v, int bgP, int bgS, int textP, int textS, String theme) {
        if (v == null) return;
        
        int id = v.getId();
        if (id == R.id.lyrics_content || id == R.id.library_content || id == R.id.search_results || id == R.id.queue_content ||
            id == R.id.connection_status_container || id == R.id.connection_status_dot || id == R.id.connection_status_text ||
            id == R.id.np_loading_overlay || id == R.id.np_loading_text) {
            return;
        }

        if (v instanceof ToggleButton) {
            ToggleButton tb = (ToggleButton) v;
            if ("native".equals(theme)) {
                tb.setBackgroundResource(android.R.drawable.btn_default);
                tb.setTextColor(android.graphics.Color.BLACK);
            } else if ("light".equals(theme)) {
                tb.setBackgroundResource(android.R.drawable.btn_default);
                tb.setTextColor(android.graphics.Color.parseColor("#1a1a2e"));
            } else {
                tb.setBackgroundResource(R.drawable.btn_toggle_dark);
                try {
                    tb.setTextColor(getResources().getColorStateList(R.color.toggle_dark_text));
                } catch (Exception e) {
                    tb.setTextColor(android.graphics.Color.WHITE);
                }
            }
            return;
        }
        
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyThemeToView(vg.getChildAt(i), bgP, bgS, textP, textS, theme);
            }
        }
        
        if (v instanceof TextView && !(v instanceof Button)) {
            ((TextView) v).setTextColor(textP);
        }
        
        if (v instanceof Button) {
            Button b = (Button) v;
            if ("native".equals(theme)) {
                b.setBackgroundResource(android.R.drawable.btn_default);
                b.setTextColor(android.graphics.Color.BLACK);
            } else if ("light".equals(theme)) {
                if (b.getId() == R.id.btn_play_pause) {
                    b.setBackgroundColor(android.graphics.Color.parseColor("#00d4ff"));
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_shutdown) {
                    b.setBackgroundColor(android.graphics.Color.parseColor("#ff5252"));
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_restart) {
                    b.setBackgroundColor(android.graphics.Color.parseColor("#ffa726"));
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_disconnect) {
                    b.setBackgroundResource(android.R.drawable.btn_default);
                    b.setTextColor(android.graphics.Color.parseColor("#ff5252"));
                } else {
                    b.setBackgroundResource(android.R.drawable.btn_default);
                    b.setTextColor(android.graphics.Color.parseColor("#1a1a2e"));
                }
            } else {
                // Dark theme
                if (b.getId() == R.id.btn_play_pause) {
                    b.setBackgroundColor(android.graphics.Color.parseColor("#00d4ff"));
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_shutdown) {
                    b.setBackgroundColor(android.graphics.Color.parseColor("#ff5252"));
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_restart) {
                    b.setBackgroundColor(android.graphics.Color.parseColor("#ffa726"));
                    b.setTextColor(android.graphics.Color.WHITE);
                } else if (b.getId() == R.id.btn_disconnect) {
                    b.setBackgroundResource(R.drawable.btn_dark);
                    b.setTextColor(android.graphics.Color.parseColor("#ff5252"));
                } else if (b.getId() == R.id.btn_add_pc) {
                    b.setBackgroundResource(R.drawable.btn_dark);
                    b.setTextColor(android.graphics.Color.parseColor("#00d4ff"));
                } else if (b.getId() == R.id.btn_remove_pc) {
                    b.setBackgroundResource(R.drawable.btn_dark);
                    b.setTextColor(android.graphics.Color.parseColor("#ff3333"));
                } else {
                    b.setBackgroundResource(R.drawable.btn_dark);
                    try {
                        b.setTextColor(getResources().getColorStateList(R.color.btn_dark_text));
                    } catch (Exception e) {
                        b.setTextColor(android.graphics.Color.WHITE);
                    }
                }
            }
        } else if (v instanceof EditText) {
            EditText e = (EditText) v;
            if ("native".equals(theme)) {
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
                    public void onClick(DialogInterface d, int w) { 
                        sendCommand("/api/system/shutdown"); 
                        screenMain.setVisibility(View.GONE);
                        screenConnection.setVisibility(View.VISIBLE);
                        api = null;
                    }
                }).setNegativeButton("No", null).show();
            }
        });
        ((Button) findViewById(R.id.btn_restart)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Confirm").setMessage("Restart PC?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { 
                        sendCommand("/api/system/restart"); 
                        screenMain.setVisibility(View.GONE);
                        screenConnection.setVisibility(View.VISIBLE);
                        api = null;
                    }
                }).setNegativeButton("No", null).show();
            }
        });
        ((Button) findViewById(R.id.btn_close_app)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Confirm").setMessage("Close PC App?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { 
                        sendCommand("/api/system/close_app"); 
                        screenMain.setVisibility(View.GONE);
                        screenConnection.setVisibility(View.VISIBLE);
                        api = null;
                    }
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
        
        final boolean pixelPerfect = prefs.getBoolean("pixel_perfect_art", false);
        if (npArtwork != null) {
            npArtwork.setScaleType(pixelPerfect ? ImageView.ScaleType.CENTER : ImageView.ScaleType.FIT_CENTER);
        }

        final ProgressBar npArtProgress = (ProgressBar) findViewById(R.id.np_art_progress);
        final LinearLayout npArtErrorLayout = (LinearLayout) findViewById(R.id.np_art_error_layout);
        final TextView npArtErrorText = (TextView) findViewById(R.id.np_art_error_text);
        final Button npArtRetryBtn = (Button) findViewById(R.id.np_art_retry_btn);

        npArtwork.setTag(null);
        npArtwork.setImageBitmap(null);
        if (npArtProgress != null) {
            npArtProgress.setVisibility(View.VISIBLE);
            npArtProgress.setIndeterminate(false);
            npArtProgress.setIndeterminate(true);
        }
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
                        npArtwork.setTag(imgUrl);
                        Bitmap cached = bitmapCache.get(imgUrl);
                        if (cached != null) {
                            if (npArtProgress != null) npArtProgress.setVisibility(View.GONE);
                            npArtwork.setImageBitmap(cached);
                            loadHighResIfNeeded(highResUrl, imgUrl);
                            return;
                        }
                        new AsyncTask<Void, Void, Bitmap>() {
                            @Override protected Bitmap doInBackground(Void... voids) {
                                byte[] data = downloadUrlToBytes(imgUrl);
                                return decodeSampledBitmapFromBytes(data, 500, 500, pixelPerfect);
                            }
                            @Override protected void onPostExecute(Bitmap b) {
                                if (b != null) {
                                    bitmapCache.put(imgUrl, b);
                                    if (imgUrl.equals(npArtwork.getTag())) {
                                        if (npArtProgress != null) npArtProgress.setVisibility(View.GONE);
                                        npArtwork.setImageBitmap(b);
                                        loadHighResIfNeeded(highResUrl, imgUrl);
                                    }
                                } else {
                                    if (imgUrl.equals(npArtwork.getTag())) {
                                        showErrorState("Failed to download");
                                    }
                                }
                            }
                        }.execute();
                    } else {
                        showErrorState("Failed to download");
                    }
                } catch (Exception e) { showErrorState("Failed to download"); }
            }
            @Override public void onError(String error) { showErrorState("Failed to download"); }
            
            private void loadHighResIfNeeded(final String highResUrl, final String currentImgUrl) {
                if (highResUrl != null && !highResUrl.isEmpty() && !highResUrl.equals(currentImgUrl)) {
                    npArtwork.setTag(highResUrl);
                    Bitmap highResCached = bitmapCache.get(highResUrl);
                    if (highResCached != null) {
                        npArtwork.setImageBitmap(highResCached);
                        return;
                    }
                    new AsyncTask<Void, Void, Bitmap>() {
                        @Override protected Bitmap doInBackground(Void... voids) {
                            byte[] data = downloadUrlToBytes(highResUrl);
                            return decodeSampledBitmapFromBytes(data, 500, 500, pixelPerfect);
                        }
                        @Override protected void onPostExecute(Bitmap hb) {
                            if (hb != null) {
                                bitmapCache.put(highResUrl, hb);
                                if (highResUrl.equals(npArtwork.getTag())) {
                                    npArtwork.setImageBitmap(hb);
                                }
                            }
                        }
                    }.execute();
                }
            }

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

    private static class LyricLine {
        public int timeMs;
        public String text;
        public TextView view;
        public LyricLine(int timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private void fetchLyrics(final String track, final String artist) {
        if (track == null || track.isEmpty() || artist == null || artist.isEmpty() || 
            track.equals("Advertisement") || track.equals("Not Playing") || 
            track.equals("Refreshing...") || track.equals("Spotify")) {
            currentLyricsTrack = "";
            currentLyrics.clear();
            currentLyricLineIndex = -1;
            if (lyricsContent != null) {
                clearContainerViews(lyricsContent);
            }
            return;
        }
        if (track.equals(currentLyricsTrack) && currentLyrics.size() > 0) {
            return;
        }
        currentLyricsTrack = track;
        currentLyrics.clear();
        currentLyricLineIndex = -1;
        if (lyricsContent != null) {
            clearContainerViews(lyricsContent);
            TextView loadingTv = new TextView(MainActivity.this);
            loadingTv.setText("Loading lyrics for \"" + track + "\"...");
            loadingTv.setTextColor(0xFF8892b0);
            loadingTv.setTextSize(18);
            loadingTv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            loadingTv.setPadding(0, 40, 0, 0);
            lyricsContent.addView(loadingTv);
        }
        if (api == null) {
            return;
        }
        showLoading("Loading Lyrics...");

        String url = "/api/spotify/lyrics?track=" + java.net.URLEncoder.encode(track) + "&artist=" + java.net.URLEncoder.encode(artist);
        api.get(url, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                hideLoading();
                try {
                    JSONObject json = new JSONObject(response);
                    if (json.has("status") && json.optString("status").equals("error")) {
                        final String errMsg = json.has("error") ? json.optJSONObject("error").optString("message", "Error") : json.optString("message", "Error");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (lyricsContent != null) {
                                    clearContainerViews(lyricsContent);
                                    TextView error = new TextView(MainActivity.this);
                                    error.setText(errMsg);
                                    error.setTextColor(0xFF8892b0);
                                    error.setTextSize(18);
                                    error.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                                    error.setPadding(0, 40, 0, 0);
                                    lyricsContent.addView(error);
                                }
                            }
                        });
                        return;
                    }
                    
                    JSONObject data = json.optJSONObject("data");
                    if (data == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (lyricsContent != null) {
                                    clearContainerViews(lyricsContent);
                                    TextView error = new TextView(MainActivity.this);
                                    error.setText("No lyrics found.");
                                    error.setTextColor(0xFF8892b0);
                                    error.setTextSize(18);
                                    error.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                                    error.setPadding(0, 40, 0, 0);
                                    lyricsContent.addView(error);
                                }
                            }
                        });
                        return;
                    }
                    JSONArray timedLyrics = data.optJSONArray("timed_lyrics");
                    if (timedLyrics != null && timedLyrics.length() > 0) {
                        currentLyrics.clear();
                        for (int i = 0; i < timedLyrics.length(); i++) {
                            JSONObject obj = timedLyrics.optJSONObject(i);
                            if (obj != null) {
                                int timeMs = obj.optInt("start_time", -1);
                                String text = obj.optString("text", "");
                                currentLyrics.add(new LyricLine(timeMs, text));
                            }
                        }
                        renderLyrics();
                    } else {
                        String synced = data.optString("lyrics", "");
                        boolean hasTimestamps = data.optBoolean("hasTimestamps", true);
                        if (synced.isEmpty()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (lyricsContent != null) {
                                        clearContainerViews(lyricsContent);
                                        TextView error = new TextView(MainActivity.this);
                                        error.setText("No lyrics found.");
                                        error.setTextColor(0xFF8892b0);
                                        error.setTextSize(18);
                                        error.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                                        error.setPadding(0, 40, 0, 0);
                                        lyricsContent.addView(error);
                                    }
                                }
                            });
                            return;
                        }
                        parseLrc(synced, hasTimestamps);
                    }
                } catch (Exception e) {
                    android.util.Log.e("DEBUG", "Failed to parse lyrics JSON", e);
                }
            }
            @Override
            public void onError(final String error) {
                hideLoading();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (lyricsContent != null) {
                            clearContainerViews(lyricsContent);
                            TextView errView = new TextView(MainActivity.this);
                            String errMsg = "Error loading lyrics";
                            if (error != null && !error.isEmpty()) {
                                try {
                                    JSONObject json = new JSONObject(error);
                                    if (json.has("error")) {
                                        errMsg = json.optJSONObject("error").optString("message", errMsg);
                                    } else {
                                        errMsg = json.optString("message", errMsg);
                                    }
                                } catch (Exception e) {
                                    errMsg = error;
                                }
                            }
                            errView.setText(errMsg);
                            errView.setTextColor(0xFF8892b0);
                            errView.setTextSize(18);
                            errView.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                            errView.setPadding(0, 40, 0, 0);
                            lyricsContent.addView(errView);
                        }
                    }
                });
            }
        });
    }

    private void parseLrc(String lrc, boolean hasTimestamps) {
        currentLyrics.clear();
        String[] lines = lrc.split("\n");
        for (String line : lines) {
            if (!hasTimestamps) {
                currentLyrics.add(new LyricLine(-1, line.trim()));
            } else if (line.startsWith("[")) {
                int endBracket = line.indexOf("]");
                if (endBracket > 0) {
                    String timeStr = line.substring(1, endBracket);
                    String text = line.substring(endBracket + 1).trim();
                    String[] parts = timeStr.split(":");
                    if (parts.length >= 2) {
                        try {
                            int min = Integer.parseInt(parts[0]);
                            float sec = Float.parseFloat(parts[1]);
                            int timeMs = (int) ((min * 60 + sec) * 1000);
                            currentLyrics.add(new LyricLine(timeMs, text));
                        } catch (Exception e) { }
                    }
                }
            }
        }
        renderLyrics();
    }

    private void renderLyrics() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lyricsContent != null) {
                    clearContainerViews(lyricsContent);
                    for (int i = 0; i < currentLyrics.size(); i++) {
                        LyricLine ll = currentLyrics.get(i);
                        TextView tv = new TextView(MainActivity.this);
                        tv.setText(ll.text.isEmpty() ? "♪" : ll.text);
                        tv.setTextColor(0xFF8892b0); // Dim
                        tv.setTextSize(22);
                        tv.setTypeface(null, Typeface.NORMAL);
                        tv.setPadding(0, 20, 0, 20);
                        tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                        tv.setLayoutParams(new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
                        ll.view = tv;
                        final int index = i;
                        tv.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int ms = currentLyrics.get(index).timeMs;
                                if (ms >= 0) {
                                    seekTo(ms, true);
                                }
                            }
                        });
                        lyricsContent.addView(tv);
                    }
                    // Force updateLyricsSync to apply colors immediately on the next tick
                    currentLyricLineIndex = -2;
                }
            }
        });
    }

    private void updateLyricsSync(int positionMs) {
        if (currentLyrics.isEmpty() || lyricsContent == null || musicLyrics == null || musicLyrics.getVisibility() != View.VISIBLE) {
            return;
        }
        if (currentLyrics.get(0).timeMs < 0) {
            return;
        }
        int newIndex = -1;
        for (int i = 0; i < currentLyrics.size(); i++) {
            int lineTime = currentLyrics.get(i).timeMs;
            if (lineTime >= 0 && positionMs >= lineTime) {
                newIndex = i;
            } else if (lineTime >= 0 && positionMs < lineTime) {
                break;
            }
        }
        if (newIndex != currentLyricLineIndex) {
            // Un-highlight previous active line (if valid)
            if (currentLyricLineIndex >= 0 && currentLyricLineIndex < currentLyrics.size()) {
                LyricLine oldLine = currentLyrics.get(currentLyricLineIndex);
                if (oldLine != null && oldLine.view != null) {
                    oldLine.view.setTextColor(0xFF8892b0);
                    oldLine.view.setTypeface(null, Typeface.NORMAL);
                }
            }
            
            // Highlight new active line (if valid)
            if (newIndex >= 0 && newIndex < currentLyrics.size()) {
                LyricLine newLine = currentLyrics.get(newIndex);
                if (newLine != null && newLine.view != null) {
                    newLine.view.setTextColor(0xFF00d4ff);
                    newLine.view.setTypeface(null, Typeface.BOLD);
                    if (musicLyrics.getHeight() > 0) {
                        int scrollY = newLine.view.getTop() - (musicLyrics.getHeight() / 2) + (newLine.view.getHeight() / 2);
                        musicLyrics.smoothScrollTo(0, Math.max(0, scrollY));
                    }
                }
            }
            
            currentLyricLineIndex = newIndex;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (bitmapCache != null) {
            bitmapCache.clear();
        }
    }
}
