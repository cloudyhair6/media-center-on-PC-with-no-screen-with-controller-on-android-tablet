package com.mediacentre.kiosk;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
    public static final int TAB_SPOTIFY = 0;
    public static final int TAB_LOCAL_MUSIC = 1;
    public static final int TAB_SETTINGS = 2;
    public static final int TAB_POWER = 3;

    private LinearLayout tabMusic;
    private FrameLayout tabLocalMusic;
    private ScrollView tabSettings;
    private LinearLayout tabPower;
    private Button[] tabButtons;

    // Local Music Sub-screens & Controls
    private LinearLayout localScreenFolders;
    private LinearLayout localScreenIndexing;
    private LinearLayout localScreenFingerprint;
    private LinearLayout localScreenLibrary;

    private TextView localFolderCurrentPath;
    private Button btnLocalFolderUp;
    private Button btnLocalFolderSet;
    private LinearLayout localBrowserItemsContainer;
    private LinearLayout localSelectedFoldersContainer;
    private Button btnLocalFoldersDone;
    private String currentBrowsedPath = "";

    private ProgressBar localIdxBar;
    private TextView localIdxPercent;
    private TextView localIdxFile;
    private TextView localIdxCount;
    private Button btnLocalIdxBackground;
    private boolean isLocalIndexingPolling = false;

    private TextView localFpTitle;
    private TextView localFpSubtitle;
    private Button btnLocalReviewIncomplete;
    private Button btnLocalReviewUnindexed;
    private LinearLayout localFpStatsContainer;
    private TextView localFpStatsText;
    private TextView localFpStatsSub;
    private LinearLayout localFpActionsContainer;
    private LinearLayout localFpFilterContainer;
    private Button btnLocalFilterAll;
    private Button btnLocalFilterWorked;
    private Button btnLocalFilterFailed;
    private int currentReviewFilter = 0;
    private JSONArray cachedReviewWorked = null;
    private JSONArray cachedReviewFailed = null;
    private int currentReviewTab = 0;
    private Button btnLocalFpVlc;
    private Button btnLocalFpArt;
    private Button btnLocalFpWrite;
    private ProgressBar localFpProgressBar;
    private TextView localFpStatus;
    private LinearLayout localFpItemsContainer;
    private Button btnLocalFpDone;

    private EditText localSearchInput;
    private Button btnLocalSearchClear;
    private Button btnLocalRescan;
    private Button btnLocalFoldersManage;
    private Button btnLocalReviewManage;
    private Button btnLocalSubSongs;
    private Button btnLocalSubArtists;
    private Button btnLocalSubAlbums;
    private Button btnLocalSubUnindexed;
    private LinearLayout localLibraryItemsContainer;
    private int currentLocalSubTab = 0;
    private String currentLocalFilterArtist = null;
    private String currentLocalFilterAlbum = null;

    private ImageView localNpThumb;
    private TextView localNpTitle;
    private TextView localNpArtist;
    private Button btnLocalNpPlayPause;
    private Button btnLocalNpStop;
    private boolean isLocalPlaying = false;

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
    private String currentLyricsArtist = "";
    private String currentlyFetchingLyricsTrack = "";
    private String currentlyFetchingLyricsArtist = "";
    private int lyricsSearchGeneration = 0;
    private int currentLyricsLoadId = -1;
    private java.util.List<LyricLine> currentLyrics = new java.util.ArrayList<>();
    private int currentLyricLineIndex = -1;
    private String currentNpUri = "";
    private String lastNpTitle = "";
    private String lastNpArtist = "";
    private boolean lastIsAd = false;
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

    // Loading Stack & Background Poll Coordination
    private static class LoadingEntry {
        final int id;
        String text;
        LoadingEntry(int id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    private final java.util.ArrayList<LoadingEntry> activeLoadings = new java.util.ArrayList<LoadingEntry>();
    private int nextLoadingId = 1;
    private int activeTaskCount = 0;
    private int spotifyPollGeneration = 0;
    private int currentPollLoadingId = -1;
    private Runnable currentPollFallback = null;

    private boolean isBusyWithOtherTasks() {
        if (activeTaskCount > 0) return true;
        for (int i = 0; i < activeLoadings.size(); i++) {
            if (activeLoadings.get(i).id != currentPollLoadingId) {
                return true;
            }
        }
        return false;
    }

    private void pauseSpotifyPolling(final boolean cancelInFlight) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(pollSpotify);
                if (cancelInFlight) {
                    spotifyPollGeneration++;
                    isSpotifyPolling = false;
                    if (currentPollFallback != null) {
                        handler.removeCallbacks(currentPollFallback);
                        currentPollFallback = null;
                    }
                    if (currentPollLoadingId != -1) {
                        int oldId = currentPollLoadingId;
                        currentPollLoadingId = -1;
                        stopLoading(oldId);
                    }
                }
            }
        });
    }

    private void resumeSpotifyPollingIfIdle(final int delayMs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isBusyWithOtherTasks() && !isSpotifyPolling) {
                    handler.removeCallbacks(pollSpotify);
                    handler.postDelayed(pollSpotify, delayMs);
                }
            }
        });
    }

    private void beginTask(final boolean cancelInFlightPoll) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activeTaskCount++;
                pauseSpotifyPolling(cancelInFlightPoll);
            }
        });
    }

    private void endTask(final int resumeDelayMs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activeTaskCount = Math.max(0, activeTaskCount - 1);
                resumeSpotifyPollingIfIdle(resumeDelayMs);
            }
        });
    }

    private int startLoading(final String text) {
        return startLoadingInternal(text, false, false);
    }

    private int startLoading(final String text, final boolean cancelInFlightPoll) {
        return startLoadingInternal(text, false, cancelInFlightPoll);
    }

    private int startBackgroundLoading(final String text) {
        return startLoadingInternal(text, true, false);
    }

    private int startLoadingInternal(final String text, final boolean insertAtBottom, final boolean cancelInFlightPoll) {
        final int id = nextLoadingId++;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!insertAtBottom) {
                    pauseSpotifyPolling(cancelInFlightPoll);
                    activeLoadings.add(new LoadingEntry(id, text));
                } else {
                    activeLoadings.add(0, new LoadingEntry(id, text));
                }
                if (!activeLoadings.isEmpty()) {
                    LoadingEntry top = activeLoadings.get(activeLoadings.size() - 1);
                    if (loadingText != null) loadingText.setText(top.text);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
                }
            }
        });
        return id;
    }

    private void updateLoadingText(final int id, final String newText) {
        if (id <= 0) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = activeLoadings.size() - 1; i >= 0; i--) {
                    if (activeLoadings.get(i).id == id) {
                        activeLoadings.get(i).text = newText;
                        break;
                    }
                }
                if (!activeLoadings.isEmpty()) {
                    LoadingEntry top = activeLoadings.get(activeLoadings.size() - 1);
                    if (loadingText != null) loadingText.setText(top.text);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void stopLoading(final int id) {
        if (id <= 0) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = activeLoadings.size() - 1; i >= 0; i--) {
                    if (activeLoadings.get(i).id == id) {
                        activeLoadings.remove(i);
                        break;
                    }
                }
                if (activeLoadings.isEmpty()) {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    resumeSpotifyPollingIfIdle(2000);
                } else {
                    LoadingEntry top = activeLoadings.get(activeLoadings.size() - 1);
                    if (loadingText != null) loadingText.setText(top.text);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void fetchSongInfoNow() {
        if (api == null) return;
        final int loadId = startLoading("Loading song info...", true);
        api.get("/api/spotify/now_playing?force=true", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                stopLoading(loadId);
                onConnectionSuccess();
                try {
                    JSONObject json = new JSONObject(response);
                    updateNowPlayingFromJson(json);
                } catch (Exception e) {}
            }
            @Override
            public void onError(String error) {
                stopLoading(loadId);
                onConnectionFailure();
            }
        });
    }

    // Polling Runnables
    private boolean isSpotifyPolling = false;
    private Runnable pollSpotify = new Runnable() {
        @Override
        public void run() {
            if (isBusyWithOtherTasks()) {
                return;
            }
            if (isSpotifyPolling) {
                handler.postDelayed(this, 1000);
                return;
            }
            if (screenMain.getVisibility() == View.VISIBLE && tabMusic.getVisibility() == View.VISIBLE && api != null) {
                isSpotifyPolling = true;
                final int myPollGen = ++spotifyPollGeneration;
                
                // Show connecting status quietly
                updateConnectionStatus(STATUS_CONNECTING);
                
                // Only show the "Loading song info..." popup during background polling if:
                // 1) We are on Now Playing AND within a few seconds of the start or end of the song, OR
                // 2) We are on the Lyrics page and the song is near the start/end (changing song).
                final boolean onNowPlaying = (musicNowPlayingContainer != null && musicNowPlayingContainer.getVisibility() == View.VISIBLE) ||
                                             (musicNowPlaying != null && musicNowPlaying.getVisibility() == View.VISIBLE);
                final boolean onLyrics = (musicLyrics != null && musicLyrics.getVisibility() == View.VISIBLE);
                final boolean nearStartOrEnd = (currentLengthS <= 0) || (currentPositionS <= 5) || ((currentLengthS - currentPositionS) <= 5);
                final boolean allowPollPopup = (onNowPlaying || onLyrics) && nearStartOrEnd;

                final Runnable loadingFallback = new Runnable() {
                    @Override
                    public void run() {
                        if (allowPollPopup && isSpotifyPolling && myPollGen == spotifyPollGeneration && currentPollLoadingId == -1) {
                            currentPollLoadingId = startBackgroundLoading("Loading song info...");
                        }
                    }
                };
                currentPollFallback = loadingFallback;
                if (allowPollPopup) {
                    handler.postDelayed(loadingFallback, 2000);
                }

                api.get("/api/spotify/now_playing", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        if (myPollGen != spotifyPollGeneration) return;
                        handler.removeCallbacks(loadingFallback);
                        if (currentPollFallback == loadingFallback) currentPollFallback = null;
                        if (currentPollLoadingId != -1) {
                            int oldId = currentPollLoadingId;
                            currentPollLoadingId = -1;
                            stopLoading(oldId);
                        }
                        
                        isSpotifyPolling = false;
                        onConnectionSuccess(); // This resets the status to connected
                        try {
                            JSONObject json = new JSONObject(response);
                            updateNowPlayingFromJson(json);
                        } catch (Exception e) {}
                        resumeSpotifyPollingIfIdle(2000);
                    }
                    @Override
                    public void onError(String error) {
                        if (myPollGen != spotifyPollGeneration) return;
                        handler.removeCallbacks(loadingFallback);
                        if (currentPollFallback == loadingFallback) currentPollFallback = null;
                        if (currentPollLoadingId != -1) {
                            int oldId = currentPollLoadingId;
                            currentPollLoadingId = -1;
                            stopLoading(oldId);
                        }
                        
                        isSpotifyPolling = false;
                        hideNpLoading();
                        onConnectionFailure(); // Sets status to offline
                        resumeSpotifyPollingIfIdle(2000);
                    }
                });
                
                api.get("/api/volume/current", new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        if (myPollGen != spotifyPollGeneration) return;
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
            } else {
                handler.postDelayed(this, 2000);
            }
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
            boolean isAd = json.optBoolean("is_ad", false) ||
                           "Advertisement".equalsIgnoreCase(title) ||
                           "Advertisement".equalsIgnoreCase(artist);
            
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
            }
            if (!title.equals(lastNpTitle) || !artist.equals(lastNpArtist) || (isAd != lastIsAd)) {
                trackChanged = true;
            }
            currentNpUri = uri;
            lastNpTitle = title;
            lastNpArtist = artist;
            lastIsAd = isAd;

            if (trackChanged) {
                if (prefs.getBoolean("show_album_art", true)) {
                    loadAlbumArt(currentNpUri);
                }
                if (!currentNpUri.isEmpty() && !isAd) {
                    checkIfLiked(currentNpUri);
                }
            }

            if (musicLyrics != null && musicLyrics.getVisibility() == View.VISIBLE) {
                if (isAd) {
                    if (!"__AD__".equals(currentLyricsTrack)) {
                        cancelLyricsSearch();
                        currentLyricsTrack = "__AD__";
                        currentLyricsArtist = "";
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
                    }
                } else if (title.isEmpty() || title.equals("Not Playing") || title.equals("Spotify") || title.equals("Refreshing...")) {
                    if (!"__NONE__".equals(currentLyricsTrack)) {
                        cancelLyricsSearch();
                        currentLyricsTrack = "__NONE__";
                        currentLyricsArtist = "";
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
                    }
                } else if (trackChanged || !title.equals(currentLyricsTrack) || !artist.equals(currentLyricsArtist)) {
                    if (!title.equals(currentlyFetchingLyricsTrack) || !artist.equals(currentlyFetchingLyricsArtist)) {
                        fetchLyrics(title, artist);
                    }
                }
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
            beginTask(true);
            api.get(url, new ApiClient.Callback() {
                @Override public void onSuccess(String response) { endTask(2000); }
                @Override public void onError(String error) { endTask(2000); }
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

        // If previous search found no lyrics, allow manual tab open to retry
        if (currentLyrics.isEmpty() && !"__AD__".equals(currentLyricsTrack)) {
            currentLyricsTrack = "";
            currentLyricsArtist = "";
        }

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

        // First connect to PC and update current song info, then fetchLyrics will step through 1/8..8/8
        final int checkLoadId = startLoading("Loading song info...");
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
                            boolean isAd = json.optBoolean("is_ad", false) ||
                                           "Advertisement".equalsIgnoreCase(title) ||
                                           "Advertisement".equalsIgnoreCase(artist);

                            if (!isAd && !title.isEmpty() && !title.equals("Not Playing") && !title.equals("Spotify") && !title.equals("Refreshing...")) {
                                if ((!title.equals(currentLyricsTrack) || !artist.equals(currentLyricsArtist) || currentLyrics.isEmpty()) &&
                                    (!title.equals(currentlyFetchingLyricsTrack) || !artist.equals(currentlyFetchingLyricsArtist))) {
                                    fetchLyrics(title, artist);
                                } else if (!currentLyrics.isEmpty()) {
                                    updateLyricsSync(currentPositionMs);
                                }
                            }
                        } catch (Exception e) {
                            String fbTitle = npTitle != null ? npTitle.getText().toString() : "";
                            String fbArtist = npArtist != null ? npArtist.getText().toString() : "";
                            fetchLyrics(fbTitle, fbArtist);
                        } finally {
                            stopLoading(checkLoadId);
                        }
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
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
                        } finally {
                            stopLoading(checkLoadId);
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

    private static javax.net.ssl.SSLSocketFactory trustAllSocketFactory = null;
    private static javax.net.ssl.HostnameVerifier trustAllHostnameVerifier = null;

    private static synchronized void ensureTrustAllSsl() {
        if (trustAllSocketFactory != null && trustAllHostnameVerifier != null) {
            return;
        }
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            trustAllSocketFactory = sc.getSocketFactory();
            trustAllHostnameVerifier = new javax.net.ssl.HostnameVerifier() {
                @Override
                public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                    return true;
                }
            };
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(trustAllSocketFactory);
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnameVerifier);
        } catch (Exception ignored) {}
    }

    private static byte[] downloadUrlToBytesRaw(String urlStr) {
        ensureTrustAllSsl();
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        HttpURLConnection conn = null;
        try {
            String currentUrl = urlStr;
            for (int redirectCount = 0; redirectCount < 5; redirectCount++) {
                URL url = new URL(currentUrl);
                conn = (HttpURLConnection) url.openConnection();
                if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                    javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) conn;
                    if (trustAllSocketFactory != null) {
                        httpsConn.setSSLSocketFactory(trustAllSocketFactory);
                    }
                    if (trustAllHostnameVerifier != null) {
                        httpsConn.setHostnameVerifier(trustAllHostnameVerifier);
                    }
                }
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Accept", "image/jpeg,image/png,image/*,*/*;q=0.8");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = null;
                    if (location == null || location.isEmpty()) {
                        break;
                    }
                    currentUrl = new URL(url, location).toString();
                    continue;
                }
                break;
            }
            if (conn == null) return null;
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

    private byte[] downloadUrlToBytes(String urlStr) {
        byte[] data = downloadUrlToBytesRaw(urlStr);
        if (data != null && data.length > 0) {
            return data;
        }
        // Fallback: proxy image download through the PC server if direct download failed
        if (currentIp != null && !currentIp.isEmpty() && urlStr != null && !urlStr.contains("/api/image_download")) {
            try {
                String proxyUrl = "http://" + currentIp + ":8080/api/image_download?url=" + java.net.URLEncoder.encode(urlStr, "UTF-8");
                return downloadUrlToBytesRaw(proxyUrl);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Bitmap downloadAndDecodeBitmap(String urlStr, int reqWidth, int reqHeight, boolean pixelPerfect) {
        byte[] data = downloadUrlToBytesRaw(urlStr);
        Bitmap b = decodeSampledBitmapFromBytes(data, reqWidth, reqHeight, pixelPerfect);
        if (b != null) {
            return b;
        }
        if (currentIp != null && !currentIp.isEmpty() && urlStr != null && !urlStr.contains("/api/image_download")) {
            try {
                String proxyUrl = "http://" + currentIp + ":8080/api/image_download?url=" + java.net.URLEncoder.encode(urlStr, "UTF-8");
                byte[] proxyData = downloadUrlToBytesRaw(proxyUrl);
                return decodeSampledBitmapFromBytes(proxyData, reqWidth, reqHeight, pixelPerfect);
            } catch (Exception ignored) {}
        }
        return null;
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
        try { tabLocalMusic = (FrameLayout) findViewById(R.id.tab_local_music); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_local_music failed", e); }
        try { tabSettings = (ScrollView) findViewById(R.id.tab_settings); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_settings failed", e); }
        try { tabPower = (LinearLayout) findViewById(R.id.tab_power); } catch (Exception e) { android.util.Log.e("DEBUG", "tab_power failed", e); }

        try { tabButtons = new Button[]{
            (Button) findViewById(R.id.tab_btn_music),
            (Button) findViewById(R.id.tab_btn_local_music),
            (Button) findViewById(R.id.tab_btn_settings),
            (Button) findViewById(R.id.tab_btn_power)
        }; } catch (Exception e) { android.util.Log.e("DEBUG", "tabButtons failed", e); }

        // Setup Local Music Views
        try {
            localScreenFolders = (LinearLayout) findViewById(R.id.local_screen_folders);
            localScreenIndexing = (LinearLayout) findViewById(R.id.local_screen_indexing);
            localScreenFingerprint = (LinearLayout) findViewById(R.id.local_screen_fingerprint);
            localScreenLibrary = (LinearLayout) findViewById(R.id.local_screen_library);

            localFolderCurrentPath = (TextView) findViewById(R.id.local_folder_current_path);
            btnLocalFolderUp = (Button) findViewById(R.id.btn_local_folder_up);
            btnLocalFolderSet = (Button) findViewById(R.id.btn_local_folder_set);
            localBrowserItemsContainer = (LinearLayout) findViewById(R.id.local_browser_items_container);
            localSelectedFoldersContainer = (LinearLayout) findViewById(R.id.local_selected_folders_container);
            btnLocalFoldersDone = (Button) findViewById(R.id.btn_local_folders_done);

            if (btnLocalFolderSet != null) {
                btnLocalFolderSet.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (currentBrowsedPath != null && !currentBrowsedPath.isEmpty()) {
                            addConfiguredFolder(currentBrowsedPath);
                        } else {
                            showToast("Select a valid folder first", false);
                        }
                    }
                });
            }

            if (btnLocalFoldersDone != null) {
                btnLocalFoldersDone.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startLocalScanAndShowProgress();
                    }
                });
            }

            localIdxBar = (ProgressBar) findViewById(R.id.local_idx_bar);
            localIdxPercent = (TextView) findViewById(R.id.local_idx_percent);
            localIdxFile = (TextView) findViewById(R.id.local_idx_file);
            localIdxCount = (TextView) findViewById(R.id.local_idx_count);
            btnLocalIdxBackground = (Button) findViewById(R.id.btn_local_idx_background);
            if (btnLocalIdxBackground != null) {
                btnLocalIdxBackground.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchTab(TAB_SPOTIFY);
                    }
                });
            }

            localFpTitle = (TextView) findViewById(R.id.local_fp_title);
            localFpSubtitle = (TextView) findViewById(R.id.local_fp_subtitle);
            btnLocalReviewIncomplete = (Button) findViewById(R.id.btn_local_review_incomplete);
            btnLocalReviewUnindexed = (Button) findViewById(R.id.btn_local_review_unindexed);
            localFpStatsContainer = (LinearLayout) findViewById(R.id.local_fp_stats_container);
            localFpStatsText = (TextView) findViewById(R.id.local_fp_stats_text);
            localFpStatsSub = (TextView) findViewById(R.id.local_fp_stats_sub);
            localFpActionsContainer = (LinearLayout) findViewById(R.id.local_fp_actions_container);
            localFpFilterContainer = (LinearLayout) findViewById(R.id.local_fp_filter_container);
            btnLocalFilterAll = (Button) findViewById(R.id.btn_local_filter_all);
            btnLocalFilterWorked = (Button) findViewById(R.id.btn_local_filter_worked);
            btnLocalFilterFailed = (Button) findViewById(R.id.btn_local_filter_failed);

            if (btnLocalFilterAll != null) {
                btnLocalFilterAll.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchReviewFilter(0); }
                });
            }
            if (btnLocalFilterWorked != null) {
                btnLocalFilterWorked.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchReviewFilter(1); }
                });
            }
            if (btnLocalFilterFailed != null) {
                btnLocalFilterFailed.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchReviewFilter(2); }
                });
            }

            if (btnLocalReviewIncomplete != null) {
                btnLocalReviewIncomplete.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchReviewTab(0); }
                });
            }
            if (btnLocalReviewUnindexed != null) {
                btnLocalReviewUnindexed.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchReviewTab(1); }
                });
            }

            btnLocalFpVlc = (Button) findViewById(R.id.btn_local_fp_vlc);
            btnLocalFpArt = (Button) findViewById(R.id.btn_local_fp_art);
            btnLocalFpWrite = (Button) findViewById(R.id.btn_local_fp_write);
            localFpProgressBar = (ProgressBar) findViewById(R.id.local_fp_progress_bar);
            localFpStatus = (TextView) findViewById(R.id.local_fp_status);
            localFpItemsContainer = (LinearLayout) findViewById(R.id.local_fp_items_container);
            btnLocalFpDone = (Button) findViewById(R.id.btn_local_fp_done);

            if (btnLocalFpVlc != null) {
                btnLocalFpVlc.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { runFingerprintAction("/api/indexer/fingerprint", "VLC Fingerprint"); }
                });
            }
            if (btnLocalFpArt != null) {
                btnLocalFpArt.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { runFingerprintAction("/api/indexer/fetch_art", "Album Art Fetcher"); }
                });
            }
            if (btnLocalFpWrite != null) {
                btnLocalFpWrite.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { runFingerprintAction("/api/indexer/write_tags", "Write Tags to Files"); }
                });
            }
            if (btnLocalFpDone != null) {
                btnLocalFpDone.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLocalScreen(4);
                        switchLocalLibrarySubTab(currentLocalSubTab);
                    }
                });
            }

            localSearchInput = (EditText) findViewById(R.id.local_search_input);
            btnLocalSearchClear = (Button) findViewById(R.id.btn_local_search_clear);
            btnLocalRescan = (Button) findViewById(R.id.btn_local_rescan);
            btnLocalFoldersManage = (Button) findViewById(R.id.btn_local_folders_manage);
            btnLocalReviewManage = (Button) findViewById(R.id.btn_local_review_manage);
            btnLocalSubSongs = (Button) findViewById(R.id.btn_local_sub_songs);
            btnLocalSubArtists = (Button) findViewById(R.id.btn_local_sub_artists);
            btnLocalSubAlbums = (Button) findViewById(R.id.btn_local_sub_albums);
            btnLocalSubUnindexed = (Button) findViewById(R.id.btn_local_sub_unindexed);
            localLibraryItemsContainer = (LinearLayout) findViewById(R.id.local_library_items_container);

            if (btnLocalReviewManage != null) {
                btnLocalReviewManage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLocalScreen(3);
                        switchReviewTab(0);
                    }
                });
            }

            if (btnLocalSubSongs != null) {
                btnLocalSubSongs.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchLocalLibrarySubTab(0); }
                });
            }
            if (btnLocalSubArtists != null) {
                btnLocalSubArtists.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchLocalLibrarySubTab(1); }
                });
            }
            if (btnLocalSubAlbums != null) {
                btnLocalSubAlbums.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchLocalLibrarySubTab(2); }
                });
            }
            if (btnLocalSubUnindexed != null) {
                btnLocalSubUnindexed.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { switchLocalLibrarySubTab(3); }
                });
            }

            if (btnLocalSearchClear != null) {
                btnLocalSearchClear.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (localSearchInput != null) localSearchInput.setText("");
                        currentLocalFilterArtist = null;
                        currentLocalFilterAlbum = null;
                        if (currentLocalSubTab == 3) {
                            loadLocalUnindexed("");
                        } else {
                            loadLocalSongs("");
                        }
                    }
                });
            }

            if (btnLocalRescan != null) {
                btnLocalRescan.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startLocalScanAndShowProgress();
                    }
                });
            }

            if (btnLocalFoldersManage != null) {
                btnLocalFoldersManage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLocalScreen(1);
                        loadLocalFolderBrowser("");
                        loadConfiguredFolders();
                    }
                });
            }

            if (localSearchInput != null) {
                localSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        if (currentLocalSubTab == 0) {
                            loadLocalSongs(s.toString().trim());
                        } else if (currentLocalSubTab == 3) {
                            loadLocalUnindexed(s.toString().trim());
                        }
                    }
                });
            }

            localNpThumb = (ImageView) findViewById(R.id.local_np_thumb);
            localNpTitle = (TextView) findViewById(R.id.local_np_title);
            localNpArtist = (TextView) findViewById(R.id.local_np_artist);
            btnLocalNpPlayPause = (Button) findViewById(R.id.btn_local_np_play_pause);
            btnLocalNpStop = (Button) findViewById(R.id.btn_local_np_stop);

            if (btnLocalNpPlayPause != null) {
                btnLocalNpPlayPause.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isLocalPlaying) {
                            if (api != null) api.get("/api/indexer/pause", null);
                            isLocalPlaying = false;
                            btnLocalNpPlayPause.setText("▶");
                        } else {
                            if (isNpPlaying) {
                                sendCommand("/api/command/spotify_play_pause");
                                isNpPlaying = false;
                            }
                            if (api != null) api.get("/api/indexer/resume", null);
                            isLocalPlaying = true;
                            btnLocalNpPlayPause.setText("❚❚");
                        }
                    }
                });
            }

            if (btnLocalNpStop != null) {
                btnLocalNpStop.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (api != null) api.get("/api/indexer/stop", null);
                        isLocalPlaying = false;
                        if (btnLocalNpPlayPause != null) btnLocalNpPlayPause.setText("▶");
                        if (localNpTitle != null) localNpTitle.setText("Not Playing");
                        if (localNpArtist != null) localNpArtist.setText("");
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("DEBUG", "Local music setup failed", e);
        }

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
        final int loadId = startLoading("Loading Playlists...");
        api.get("/api/spotify/library/hierarchy", new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                stopLoading(loadId);
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
                stopLoading(loadId);
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
    private ProgressDialog updateDialog = null;

    private void pollUpdateStatus() {
        api.get("/api/system/tablet_update_status", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    final String status = json.optString("status", "idle");
                    final String message = json.optString("message", "");
                    final String error = json.optString("error", "");
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ("error".equals(status)) {
                                if (updateDialog != null) { updateDialog.dismiss(); updateDialog = null; }
                                new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Update Failed")
                                    .setMessage(error)
                                    .setPositiveButton("OK", null)
                                    .show();
                            } else if ("manual_install".equals(status)) {
                                if (updateDialog != null) { updateDialog.setMessage("Downloading APK directly to tablet..."); }
                                downloadAndInstallApkManually();
                            } else if ("success".equals(status)) {
                                if (updateDialog != null) {
                                    updateDialog.setMessage(message);
                                }
                            } else if ("running".equals(status)) {
                                if (updateDialog != null) {
                                    updateDialog.setMessage(message);
                                }
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        pollUpdateStatus();
                                    }
                                }, 1500);
                            }
                        }
                    });
                } catch (Exception e) {}
            }
            @Override
            public void onError(String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                pollUpdateStatus();
                            }
                        }, 1500);
                    }
                });
            }
        });
    }

    private void downloadAndInstallApkManually() {
        new android.os.AsyncTask<Void, Void, java.io.File>() {
            @Override
            protected java.io.File doInBackground(Void... voids) {
                try {
                    java.net.URL url = new java.net.URL("http://" + currentIp + ":8080/api/system/tablet_apk");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.connect();
                    if (conn.getResponseCode() != 200) return null;
                    java.io.File sdcard = android.os.Environment.getExternalStorageDirectory();
                    java.io.File apkFile = new java.io.File(sdcard, "mediacentre_update.apk");
                    java.io.InputStream in = conn.getInputStream();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(apkFile);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    out.close();
                    in.close();
                    return apkFile;
                } catch (Exception e) {
                    return null;
                }
            }
            @Override
            protected void onPostExecute(java.io.File apkFile) {
                if (updateDialog != null) { updateDialog.dismiss(); updateDialog = null; }
                if (apkFile != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(android.net.Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else {
                    showToast("Failed to download APK directly.", false);
                }
            }
        }.execute();
    }

    private String getCurrentTabletVersion() {
        String appVersion = "v1.8";
        try {
            appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {}
        return appVersion != null ? appVersion.trim() : "v1.8";
    }

    private int estimateVersionsBehind(String currentVer, String latestVer) {
        try {
            String c = currentVer != null ? currentVer.trim().replaceAll("^[vV]", "") : "";
            String l = latestVer != null ? latestVer.trim().replaceAll("^[vV]", "") : "";
            if (c.length() == 0 || l.length() == 0 || c.equalsIgnoreCase(l)) return 0;
            String[] cParts = c.split("\\.");
            String[] lParts = l.split("\\.");
            if (cParts.length >= 2 && lParts.length >= 2) {
                int cMaj = Integer.parseInt(cParts[0].replaceAll("[^0-9]", ""));
                int lMaj = Integer.parseInt(lParts[0].replaceAll("[^0-9]", ""));
                int cMin = Integer.parseInt(cParts[1].replaceAll("[^0-9]", ""));
                int lMin = Integer.parseInt(lParts[1].replaceAll("[^0-9]", ""));
                if (cMaj == lMaj && lMin > cMin) {
                    return lMin - cMin;
                }
            }
        } catch (Exception e) {}
        return 1;
    }

    private void showTabletUpdateDialog(final String currentVer, final String latestVer, final int releasesBehind, final String commitsSummary) {
        int count = releasesBehind > 0 ? releasesBehind : estimateVersionsBehind(currentVer, latestVer);
        StringBuilder msg = new StringBuilder();
        msg.append("An update is available for this tablet.\n");
        msg.append("You are ").append(count).append(count == 1 ? " version" : " versions").append(" out of date.\n\n");
        msg.append("Current Version on Tablet: ").append(currentVer).append("\n");
        msg.append("Latest Version: ").append(latestVer).append("\n");
        if (commitsSummary != null && commitsSummary.trim().length() > 0) {
            msg.append("\nChanges between ").append(currentVer).append(" and ").append(latestVer).append(":\n");
            msg.append("----------------------------------------\n");
            msg.append(commitsSummary.trim()).append("\n");
            msg.append("----------------------------------------\n");
        }
        msg.append("\nDo you want to update this tablet app now?");

        new AlertDialog.Builder(MainActivity.this)
            .setTitle("Update Available (" + currentVer + " -> " + latestVer + ")")
            .setMessage(msg.toString())
            .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    updateDialog = new ProgressDialog(MainActivity.this);
                    updateDialog.setTitle("Updating Tablet");
                    updateDialog.setMessage("Starting update to " + latestVer + "...");
                    updateDialog.setCancelable(false);
                    updateDialog.show();

                    api.get("/api/system/tablet_update_install?version=" + android.net.Uri.encode(latestVer), new ApiClient.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            pollUpdateStatus();
                        }
                        @Override
                        public void onError(final String error) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if (updateDialog != null) { updateDialog.dismiss(); updateDialog = null; }
                                    showToast("Failed to start update: " + error, false);
                                }
                            });
                        }
                    });
                }
            })
            .setNegativeButton("Ignore", null)
            .show();
    }

    private void checkForTabletUpdate(final String appVersion, final String fallbackServerVersion) {
        api.get("/api/system/tablet_update_check?version=" + android.net.Uri.encode(appVersion), new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    org.json.JSONObject upd = new org.json.JSONObject(response);
                    boolean updateAvailable = upd.optBoolean("update_available", false);
                    final String currentVer = upd.optString("current_version", appVersion);
                    final String latestVer = upd.optString("latest_version", fallbackServerVersion);
                    final int releasesBehind = upd.optInt("releases_behind", 0);

                    StringBuilder commitsBuilder = new StringBuilder();
                    String changelog = upd.optString("changelog", "").trim();
                    if (changelog.length() > 0) {
                        commitsBuilder.append(changelog);
                    } else {
                        org.json.JSONArray commitsArr = upd.optJSONArray("commits");
                        if (commitsArr != null && commitsArr.length() > 0) {
                            for (int i = 0; i < commitsArr.length(); i++) {
                                String c = commitsArr.optString(i, "").trim();
                                if (c.length() > 0) {
                                    if (commitsBuilder.length() > 0) commitsBuilder.append("\n\n");
                                    commitsBuilder.append("- ").append(c);
                                }
                            }
                        }
                    }
                    final String commitsSummary = commitsBuilder.toString();

                    if (updateAvailable || (!appVersion.equalsIgnoreCase(latestVer) && latestVer.length() > 0)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showTabletUpdateDialog(currentVer, latestVer, releasesBehind, commitsSummary);
                            }
                        });
                    } else {
                        showToast("Up-to-date (" + appVersion + ")", true);
                    }
                } catch (Exception e) {
                    if (!appVersion.equalsIgnoreCase(fallbackServerVersion)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showTabletUpdateDialog(appVersion, fallbackServerVersion, 0, "");
                            }
                        });
                    } else {
                        showToast("Up-to-date", true);
                    }
                }
            }
            @Override
            public void onError(String error) {
                if (fallbackServerVersion != null && fallbackServerVersion.length() > 0 && !appVersion.equalsIgnoreCase(fallbackServerVersion)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showTabletUpdateDialog(appVersion, fallbackServerVersion, 0, "");
                        }
                    });
                } else {
                    showToast("Connected", true);
                }
            }
        });
    }

    private void connectToIp(String ip) {
        currentIp = ip;
        prefs.edit().putString("last_ip", ip).commit();
        api = new ApiClient(ip);
        
        updateConnectionStatus(STATUS_CONNECTING);
        final int loadId = startLoading("Connecting to " + ip + "...");
        final String appVersion = getCurrentTabletVersion();
        api.get("/api/status", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                stopLoading(loadId);
                onConnectionSuccess();
                try {
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    String serverVersion = json.optString("version", appVersion);
                    checkForTabletUpdate(appVersion, serverVersion);
                } catch (Exception e) {
                    showToast("Connected", true);
                }
                screenConnection.setVisibility(View.GONE);
                screenMain.setVisibility(View.VISIBLE);
                switchTab(0);
            }
            @Override
            public void onError(String error) {
                stopLoading(loadId);
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
        if (tabMusic != null) tabMusic.setVisibility(index == TAB_SPOTIFY ? View.VISIBLE : View.GONE);
        if (tabLocalMusic != null) tabLocalMusic.setVisibility(index == TAB_LOCAL_MUSIC ? View.VISIBLE : View.GONE);
        if (tabSettings != null) tabSettings.setVisibility(index == TAB_SETTINGS ? View.VISIBLE : View.GONE);
        if (tabPower != null) tabPower.setVisibility(index == TAB_POWER ? View.VISIBLE : View.GONE);

        for (int i = 0; i < tabButtons.length; i++) {
            if (tabButtons[i] != null) {
                tabButtons[i].setTextColor(i == index ? 0xFF00d4ff : 0xFF8892b0);
                tabButtons[i].setBackgroundColor(i == index ? 0xFF1a1f36 : 0xFF111827);
            }
        }

        TextView title = (TextView) findViewById(R.id.tab_title);
        if (title != null) {
            if (index == TAB_SPOTIFY) title.setText("Spotify");
            else if (index == TAB_LOCAL_MUSIC) title.setText("Local Music");
            else if (index == TAB_SETTINGS) title.setText("Settings");
            else if (index == TAB_POWER) title.setText("Power");
        }

        if (index == TAB_LOCAL_MUSIC) {
            refreshLocalMusicState();
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
                beginTask(false);
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
                                endTask(2000);
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
                                endTask(2000);
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
                    beginTask(true);
                    api.get("/api/spotify/shuffle?state=" + (shuffleOn ? "true" : "false"), new ApiClient.Callback() {
                        @Override public void onSuccess(String r) { endTask(2000); showToast("Shuffle " + (shuffleOn ? "ON" : "OFF"), true); }
                        @Override public void onError(String error) { endTask(2000); showToast("Shuffle failed", false); }
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
                    beginTask(true);
                    api.get("/api/spotify/repeat?state=" + st, new ApiClient.Callback() {
                        @Override public void onSuccess(String r) { endTask(2000); showToast("Repeat: " + st, true); }
                        @Override public void onError(String e) { endTask(2000); showToast("Repeat failed", false); }
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
                    final int loadId = startLoading("Searching...");
                    api.get("/api/spotify/search?q=" + java.net.URLEncoder.encode(q) + "&type=" + t + "&limit=" + l, new ApiClient.Callback() {
                        @Override public void onSuccess(String response) {
                            stopLoading(loadId);
                            renderSearchResults(response);
                        }
                        @Override public void onError(String error) {
                            stopLoading(loadId);
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
                            beginTask(false);
                            api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {
                                    endTask(2000);
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
                                                    return downloadAndDecodeBitmap(imgUrl, 100, 100, false);
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
                                        } else showError("URL missing");
                                    } catch (Exception e) { showError("URL missing"); }
                                }
                                @Override public void onError(String error) { endTask(2000); showError("URL missing"); }
                                
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
        final int loadId = startLoading("Loading Library...");
        api.get("/api/spotify/library/hierarchy", new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                stopLoading(loadId);
                try {
                    JSONObject json = new JSONObject(response);
                    cachedLibraryItems = json.optJSONArray("items");
                    currentLibraryFolderUri = null;
                    renderLibraryFolder();
                } catch (Exception e) {}
            }
            @Override public void onError(String error) {
                stopLoading(loadId);
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

    private void showCreatePlaylistDialog() {
        LinearLayout layout = new LinearLayout(MainActivity.this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        final EditText nameInput = new EditText(MainActivity.this);
        nameInput.setHint("Playlist Name");
        nameInput.setSingleLine(true);
        layout.addView(nameInput);

        final EditText descInput = new EditText(MainActivity.this);
        descInput.setHint("Description (optional)");
        descInput.setSingleLine(true);
        layout.addView(descInput);

        new AlertDialog.Builder(MainActivity.this)
            .setTitle("Create New Playlist")
            .setView(layout)
            .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = nameInput.getText().toString().trim();
                    String desc = descInput.getText().toString().trim();
                    if (name.length() == 0) {
                        showToast("Please enter a playlist name", false);
                        return;
                    }
                    final int loadId = startLoading("Creating playlist...");
                    String url = "/api/spotify/playlist/create?name=" + java.net.URLEncoder.encode(name)
                        + "&description=" + java.net.URLEncoder.encode(desc);
                    api.get(url, new ApiClient.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            stopLoading(loadId);
                            showToast("Playlist created!", true);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    loadLibrary();
                                }
                            });
                        }
                        @Override
                        public void onError(String error) {
                            stopLoading(loadId);
                            showToast("Failed to create playlist", false);
                        }
                    });
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openPlaylistTracks(final String playlistUri, final String fallbackName, final int offset) {
        final int limit = 50;
        final int loadId = startLoading("Loading playlist tracks...");
        String url = "/api/spotify/playlist/get?uri=" + java.net.URLEncoder.encode(playlistUri)
            + "&limit=" + limit + "&offset=" + offset;
        api.get(url, new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                stopLoading(loadId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderPlaylistTracksView(playlistUri, fallbackName, offset, limit, response);
                    }
                });
            }
            @Override
            public void onError(String error) {
                stopLoading(loadId);
                showToast("Failed to load playlist songs", false);
            }
        });
    }

    private void renderPlaylistTracksView(final String playlistUri, final String fallbackName, final int offset, final int limit, String jsonStr) {
        LinearLayout container = (LinearLayout) findViewById(R.id.library_content);
        if (container == null) return;
        clearContainerViews(container);

        try {
            JSONObject data = new JSONObject(jsonStr);
            if (data.has("error") && !data.has("tracks")) {
                showToast("Error: " + data.optString("error"), false);
                renderLibraryFolder();
                return;
            }
            final String plName = data.optString("name", fallbackName);
            final int totalTracks = data.optInt("track_count", 0);
            JSONArray tracks = data.optJSONArray("tracks");
            if (tracks == null) tracks = new JSONArray();

            // Header Row: Back Button + Title + Play Entire Playlist Button
            LinearLayout headerRow = new LinearLayout(MainActivity.this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setPadding(10, 14, 10, 14);
            headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            Button backBtn = new Button(MainActivity.this);
            backBtn.setText("< Back to Library");
            styleDynamicButton(backBtn);
            backBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    renderLibraryFolder();
                }
            });
            headerRow.addView(backBtn);

            TextView titleTv = new TextView(MainActivity.this);
            titleTv.setText("  " + plName + " (" + totalTracks + " songs)");
            titleTv.setTextSize(18);
            titleTv.setTextColor(0xFF00d4ff);
            titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
            titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            headerRow.addView(titleTv);

            Button playAllBtn = new Button(MainActivity.this);
            playAllBtn.setText("Play Playlist");
            styleDynamicButton(playAllBtn);
            playAllBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(playlistUri));
                }
            });
            headerRow.addView(playAllBtn);
            container.addView(headerRow);

            // Pagination Bar if totalTracks > limit or offset > 0
            if (totalTracks > limit || offset > 0) {
                LinearLayout pageRow = new LinearLayout(MainActivity.this);
                pageRow.setOrientation(LinearLayout.HORIZONTAL);
                pageRow.setPadding(10, 6, 10, 12);
                pageRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

                Button prevBtn = new Button(MainActivity.this);
                prevBtn.setText("< Prev " + limit);
                styleDynamicButton(prevBtn);
                prevBtn.setEnabled(offset > 0);
                prevBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openPlaylistTracks(playlistUri, plName, Math.max(0, offset - limit));
                    }
                });
                pageRow.addView(prevBtn);

                int startNum = totalTracks > 0 ? (offset + 1) : 0;
                int endNum = Math.min(offset + tracks.length(), totalTracks > 0 ? totalTracks : offset + tracks.length());
                TextView pageInfo = new TextView(MainActivity.this);
                pageInfo.setText("Showing " + startNum + " - " + endNum + " of " + totalTracks);
                pageInfo.setTextColor(0xFF8892b0);
                pageInfo.setTextSize(14);
                pageInfo.setGravity(android.view.Gravity.CENTER);
                pageInfo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                pageRow.addView(pageInfo);

                Button nextBtn = new Button(MainActivity.this);
                nextBtn.setText("Next " + limit + " >");
                styleDynamicButton(nextBtn);
                nextBtn.setEnabled(offset + limit < totalTracks);
                nextBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openPlaylistTracks(playlistUri, plName, offset + limit);
                    }
                });
                pageRow.addView(nextBtn);
                container.addView(pageRow);
            }

            final boolean isLikedCollection = "spotify:collection:tracks".equals(playlistUri) || playlistUri.endsWith(":collection");

            for (int i = 0; i < tracks.length(); i++) {
                final JSONObject tr = tracks.getJSONObject(i);
                final String trackUri = tr.optString("uri", "");
                final String trackName = tr.optString("name", "Unknown");
                final String trackArtist = tr.optString("artist", "");
                final String trackAlbum = tr.optString("album", "");
                final int pos = tr.optInt("position", offset + i);

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

                            if (trackUri.isEmpty()) {
                                pb.setVisibility(View.GONE);
                                img.setImageResource(R.drawable.ic_error);
                                errText.setText("URL missing");
                                errLayout.setVisibility(View.VISIBLE);
                                return;
                            }

                            pb.setVisibility(View.VISIBLE);
                            beginTask(false);
                            api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(trackUri), new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {
                                    endTask(2000);
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
                                                    return downloadAndDecodeBitmap(imgUrl, 100, 100, false);
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
                                        } else showError("URL missing");
                                    } catch (Exception e) { showError("URL missing"); }
                                }
                                @Override public void onError(String error) { endTask(2000); showError("URL missing"); }

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
                num.setText((pos + 1) + ". ");
                num.setTextColor(0xFF8892b0);
                num.setTextSize(16);
                row.addView(num);

                LinearLayout textCol = new LinearLayout(MainActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView tvTitle = new TextView(MainActivity.this);
                tvTitle.setText(trackName);
                tvTitle.setTextSize(16);
                if (!trackUri.isEmpty() && trackUri.equals(currentNpUri)) {
                    tvTitle.setTextColor(0xFF00FFaa);
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    tvTitle.setTextColor(0xFFFFFFFF);
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                tvTitle.setSingleLine(true);
                tvTitle.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(tvTitle);

                TextView tvSub = new TextView(MainActivity.this);
                String subText = trackArtist;
                if (trackAlbum != null && trackAlbum.length() > 0) {
                    subText = subText.length() > 0 ? (subText + "  •  " + trackAlbum) : trackAlbum;
                }
                tvSub.setText(subText);
                tvSub.setTextSize(13);
                tvSub.setTextColor(0xFF8892b0);
                tvSub.setSingleLine(true);
                tvSub.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(tvSub);

                row.addView(textCol);

                LinearLayout btns = new LinearLayout(MainActivity.this);
                btns.setOrientation(LinearLayout.HORIZONTAL);

                // Play Song button (plays song directly without claiming playlist context)
                Button playSongBtn = new Button(MainActivity.this);
                playSongBtn.setText("Play");
                styleDynamicButton(playSongBtn);
                playSongBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        currentContextUri = "";
                        if (npContext != null) npContext.setVisibility(View.GONE);
                        sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(trackUri));
                    }
                });
                btns.addView(playSongBtn);

                // Queue Song button
                Button queueSongBtn = new Button(MainActivity.this);
                queueSongBtn.setText("Queue");
                styleDynamicButton(queueSongBtn);
                queueSongBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendCommand("/api/spotify/queue/add?uri=" + java.net.URLEncoder.encode(trackUri));
                    }
                });
                btns.addView(queueSongBtn);

                // Remove from Playlist / Unlike button
                Button remSongBtn = new Button(MainActivity.this);
                remSongBtn.setText(isLikedCollection ? "Unlike" : "Remove");
                styleDynamicButton(remSongBtn);
                remSongBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String remUrl;
                        if (isLikedCollection) {
                            remUrl = "/api/spotify/library/remove?uri=" + java.net.URLEncoder.encode(trackUri);
                        } else {
                            remUrl = "/api/spotify/playlist/remove?playlist_uri=" + java.net.URLEncoder.encode(playlistUri)
                                + "&positions=" + pos;
                        }
                        final int remLoad = startLoading("Removing track...");
                        api.get(remUrl, new ApiClient.Callback() {
                            @Override
                            public void onSuccess(String r) {
                                stopLoading(remLoad);
                                showToast("Removed " + trackName, true);
                                openPlaylistTracks(playlistUri, plName, offset);
                            }
                            @Override
                            public void onError(String e) {
                                stopLoading(remLoad);
                                showToast("Failed to remove track", false);
                            }
                        });
                    }
                });
                btns.addView(remSongBtn);

                row.addView(btns);
                container.addView(row);
            }
        } catch (Exception e) {
            showToast("Failed to render playlist tracks", false);
        }
    }

    private void renderLibraryFolder() {
        if (cachedLibraryItems == null) return;
        LinearLayout container = (LinearLayout) findViewById(R.id.library_content);
        clearContainerViews(container);
        
        LinearLayout topActionsRow = new LinearLayout(MainActivity.this);
        topActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        topActionsRow.setPadding(6, 6, 6, 12);
        topActionsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Button createPlBtn = new Button(MainActivity.this);
        createPlBtn.setText("+ New Playlist");
        createPlBtn.setTextSize(17);
        createPlBtn.setPadding(16, 20, 16, 20);
        createPlBtn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        styleDynamicButton(createPlBtn);
        createPlBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showCreatePlaylistDialog();
            }
        });
        topActionsRow.addView(createPlBtn);

        container.addView(topActionsRow);

        try {
            JSONArray itemsToRender = new JSONArray();
            boolean hasLiked = false;
            for (int k = 0; k < cachedLibraryItems.length(); k++) {
                JSONObject it = cachedLibraryItems.optJSONObject(k);
                if (it != null && ("spotify:collection:tracks".equals(it.optString("uri")) || it.optBoolean("is_liked", false))) {
                    hasLiked = true;
                    break;
                }
            }
            if (!hasLiked) {
                JSONObject likedObj = new JSONObject();
                likedObj.put("uri", "spotify:collection:tracks");
                likedObj.put("name", "Liked Songs");
                likedObj.put("type", "playlist");
                likedObj.put("depth", 1);
                likedObj.put("is_liked", true);
                itemsToRender.put(likedObj);
            }
            for (int k = 0; k < cachedLibraryItems.length(); k++) {
                itemsToRender.put(cachedLibraryItems.getJSONObject(k));
            }

            for (int i = 0; i < itemsToRender.length(); i++) {
                final JSONObject item = itemsToRender.getJSONObject(i);
                final String uri = item.optString("uri", "");
                final String parentUri = item.optString("parent_uri", null);
                final String type = item.optString("type", "playlist");
                final int depth = item.optInt("depth", 1);
                final boolean isLikedItem = item.optBoolean("is_liked", false) || "spotify:collection:tracks".equals(uri);
                
                if (!isItemVisible(parentUri)) continue;
                
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                int leftPadding = 10 + ((depth - 1) * 60);
                row.setPadding(leftPadding, 15, 10, 15);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                
                String artUriToLoad = item.optString("art_uri", uri);
                if (artUriToLoad == null || artUriToLoad.isEmpty()) artUriToLoad = uri;
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
                            beginTask(false);
                            api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(finalArtUriToLoad), new ApiClient.Callback() {
                                @Override public void onSuccess(String response) {
                                    endTask(2000);
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
                                                    return downloadAndDecodeBitmap(imgUrl, 100, 100, false);
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
                                        } else showError("URL missing");
                                    } catch (Exception e) { showError("URL missing"); }
                                }
                                @Override public void onError(String error) { endTask(2000); showError("URL missing"); }
                                
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

                final String itemName = item.optString("name", "Unknown");
                final String artUriForCheck = item.optString("art_uri", "");
                TextView tvName = new TextView(MainActivity.this);
                String namePrefix = "";
                if (type.equals("folder")) {
                    namePrefix = expandedFolders.contains(uri) ? "[Open] " : "[Folder] ";
                }
                tvName.setText(namePrefix + itemName);
                boolean isCurrentContext = (!uri.isEmpty() && uri.equals(currentContextUri))
                    || (!artUriForCheck.isEmpty() && artUriForCheck.equals(currentContextUri));
                if (isCurrentContext) {
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
                String rawType = (type == null || type.isEmpty()) ? "playlist" : type;
                String displayType = rawType.substring(0, 1).toUpperCase() + rawType.substring(1);
                String owner = item.optString("owner", "");
                if (owner == null || owner.isEmpty()) {
                    owner = item.optString("artist", "");
                }
                String subLabel = displayType;
                if (owner != null && !owner.isEmpty()) {
                    subLabel = displayType + "  •  Owner: " + owner;
                }
                tvArtist.setText(subLabel);
                tvArtist.setTextSize(14);
                tvArtist.setTextColor(0xFF8892b0);
                tvArtist.setSingleLine(true);
                tvArtist.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(tvArtist);

                if (!type.equals("folder") && !uri.isEmpty()) {
                    textCol.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            openPlaylistTracks(uri, itemName, 0);
                        }
                    });
                }
                
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
                    if (!type.equals("folder")) {
                        Button songsBtn = new Button(MainActivity.this);
                        songsBtn.setText("Songs");
                        styleDynamicButton(songsBtn);
                        songsBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                openPlaylistTracks(uri, itemName, 0);
                            }
                        });
                        btns.addView(songsBtn);
                    }

                    Button playBtn = new Button(MainActivity.this);
                    playBtn.setText("Play");
                    styleDynamicButton(playBtn);
                    playBtn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri));
                        }
                    });
                    btns.addView(playBtn);
                    
                    if (!isLikedItem && !type.equals("folder")) {
                        Button removeBtn = new Button(MainActivity.this);
                        removeBtn.setText("Remove from library");
                        styleDynamicButton(removeBtn);
                        removeBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                final int remLoadId = startLoading("Removing from Library...");
                                api.get("/api/spotify/library/remove?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
                                    @Override public void onSuccess(String response) {
                                        stopLoading(remLoadId);
                                        runOnUiThread(new Runnable() {
                                            @Override public void run() { loadLibrary(); }
                                        });
                                    }
                                    @Override public void onError(String error) {
                                        stopLoading(remLoadId);
                                    }
                                });
                            }
                        });
                        btns.addView(removeBtn);
                    }
                }
                
                row.addView(btns);
                container.addView(row);
            }
        } catch (Exception e) {}
    }

    private void loadQueue() {
        final int loadId = startLoading("Loading Queue...");
        api.get("/api/spotify/queue", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                stopLoading(loadId);
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
                                        beginTask(false);
                                        api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
                                            @Override public void onSuccess(String response) {
                                                endTask(2000);
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
                                                                return downloadAndDecodeBitmap(imgUrl, 100, 100, false);
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
                                                    } else showError("URL missing");
                                                } catch (Exception e) { showError("URL missing"); }
                                            }
                                            @Override public void onError(String error) { endTask(2000); showError("URL missing"); }
                                            
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
                            
                            final int queuePos = i;
                            final int totalQueueItems = items.length();
                            final String trackDisplayName = name;

                            LinearLayout btns = new LinearLayout(MainActivity.this);
                            btns.setOrientation(LinearLayout.HORIZONTAL);

                            if (!uri.isEmpty()) {
                                Button btnPlay = new Button(MainActivity.this);
                                btnPlay.setText("Play");
                                btnPlay.setPadding(16, 10, 16, 10);
                                styleDynamicButton(btnPlay);
                                btnPlay.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        currentContextUri = "";
                                        if (npContext != null) npContext.setVisibility(View.GONE);
                                        sendCommand("/api/spotify/play?uri=" + java.net.URLEncoder.encode(uri));
                                    }
                                });
                                btns.addView(btnPlay);
                            }

                            if (queuePos > 0) {
                                Button btnUp = new Button(MainActivity.this);
                                btnUp.setText("Up");
                                btnUp.setPadding(14, 10, 14, 10);
                                styleDynamicButton(btnUp);
                                btnUp.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        moveQueueItem(queuePos, queuePos - 1);
                                    }
                                });
                                btns.addView(btnUp);
                            }

                            if (queuePos < totalQueueItems - 1) {
                                Button btnDown = new Button(MainActivity.this);
                                btnDown.setText("Down");
                                btnDown.setPadding(14, 10, 14, 10);
                                styleDynamicButton(btnDown);
                                btnDown.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        moveQueueItem(queuePos, queuePos + 1);
                                    }
                                });
                                btns.addView(btnDown);
                            }

                            if (totalQueueItems > 1) {
                                Button btnMove = new Button(MainActivity.this);
                                btnMove.setText("Move");
                                btnMove.setPadding(14, 10, 14, 10);
                                styleDynamicButton(btnMove);
                                btnMove.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        showMoveQueueItemDialog(queuePos, totalQueueItems, trackDisplayName);
                                    }
                                });
                                btns.addView(btnMove);
                            }

                            Button btnRemove = new Button(MainActivity.this);
                            btnRemove.setText("Remove");
                            btnRemove.setPadding(14, 10, 14, 10);
                            styleDynamicButton(btnRemove);
                            btnRemove.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    removeQueueItem(queuePos, trackDisplayName);
                                }
                            });
                            btns.addView(btnRemove);

                            row.addView(btns);
                            
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
            @Override public void onError(String error) { stopLoading(loadId); showToast("Queue load failed", false); }
        });
    }

    private void removeQueueItem(final int position, final String trackName) {
        final int loadId = startLoading("Removing from Queue...");
        api.get("/api/spotify/queue/remove?position=" + position, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                stopLoading(loadId);
                showToast("Removed " + trackName, true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadQueue();
                    }
                });
            }
            @Override
            public void onError(String error) {
                stopLoading(loadId);
                showToast("Failed to remove from queue", false);
            }
        });
    }

    private void moveQueueItem(final int fromPos, final int toPos) {
        final int loadId = startLoading("Reordering Queue...");
        api.get("/api/spotify/queue/move?from=" + fromPos + "&to=" + toPos, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                stopLoading(loadId);
                showToast("Moved to #" + (toPos + 1), true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadQueue();
                    }
                });
            }
            @Override
            public void onError(String error) {
                stopLoading(loadId);
                showToast("Failed to move track in queue", false);
            }
        });
    }

    private void showMoveQueueItemDialog(final int fromPos, final int totalItems, final String trackName) {
        LinearLayout layout = new LinearLayout(MainActivity.this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        TextView label = new TextView(MainActivity.this);
        label.setText("Move \"" + trackName + "\" (currently #" + (fromPos + 1) + ") to position (1 - " + totalItems + "):");
        label.setTextSize(15);
        label.setPadding(0, 0, 0, 10);
        layout.addView(label);

        final EditText posInput = new EditText(MainActivity.this);
        posInput.setHint("1 - " + totalItems);
        posInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        posInput.setSingleLine(true);
        layout.addView(posInput);

        new AlertDialog.Builder(MainActivity.this)
            .setTitle("Move in Queue")
            .setView(layout)
            .setPositiveButton("Move", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String val = posInput.getText().toString().trim();
                    try {
                        int targetOneBased = Integer.parseInt(val);
                        int targetZeroBased = targetOneBased - 1;
                        if (targetZeroBased < 0 || targetZeroBased >= totalItems) {
                            showToast("Enter a number between 1 and " + totalItems, false);
                            return;
                        }
                        if (targetZeroBased == fromPos) return;
                        moveQueueItem(fromPos, targetZeroBased);
                    } catch (Exception e) {
                        showToast("Invalid position number", false);
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    private void sendCommand(final String endpoint) {
        final boolean isPlaybackChange = endpoint.contains("spotify_next") ||
            endpoint.contains("spotify_prev") ||
            endpoint.contains("spotify_play_pause") ||
            endpoint.contains("/api/spotify/play");
        if (isPlaybackChange && api != null) {
            // Mutual Pause: Pause local playback when Spotify is triggered
            api.get("/api/indexer/pause", null);
            isLocalPlaying = false;
            if (btnLocalNpPlayPause != null) btnLocalNpPlayPause.setText("Play");
        }
        final boolean isTrackChange = endpoint.contains("spotify_next") ||
            endpoint.contains("spotify_prev") ||
            endpoint.contains("/api/spotify/play");
        final int loadId = startLoading("Waiting for PC...", isPlaybackChange);
        api.get(endpoint, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                if (isTrackChange && api != null) {
                    fetchSongInfoNow();
                }
                stopLoading(loadId);
                showToast("Successfully completed", true);
            }
            @Override
            public void onError(String error) {
                stopLoading(loadId);
                showToast("Failed: " + error, false);
            }
        });
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
            if (npArtErrorText != null) npArtErrorText.setText("cannot get url");
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
        
        beginTask(false);
        api.get("/api/proxy_art?uri=" + java.net.URLEncoder.encode(uri), new ApiClient.Callback() {
            @Override public void onSuccess(String response) {
                endTask(2000);
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
                                return downloadAndDecodeBitmap(imgUrl, 500, 500, pixelPerfect);
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
                                        showErrorState("Download failed");
                                    }
                                }
                            }
                        }.execute();
                    } else {
                        showErrorState("cannot get url");
                    }
                } catch (Exception e) { showErrorState("cannot get url"); }
            }
            @Override public void onError(String error) { endTask(2000); showErrorState("cannot get url"); }
            
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
                            return downloadAndDecodeBitmap(highResUrl, 500, 500, pixelPerfect);
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

    private void cancelLyricsSearch() {
        lyricsSearchGeneration++;
        currentlyFetchingLyricsTrack = "";
        currentlyFetchingLyricsArtist = "";
        if (currentLyricsLoadId != -1) {
            int oldId = currentLyricsLoadId;
            currentLyricsLoadId = -1;
            stopLoading(oldId);
        }
    }

    private void showLyricsNotFound(final String track, final String artist) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lyricsContent != null) {
                    clearContainerViews(lyricsContent);
                    TextView error = new TextView(MainActivity.this);
                    error.setText("Lyrics not found");
                    error.setTextColor(0xFF8892b0);
                    error.setTextSize(18);
                    error.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                    error.setPadding(0, 40, 0, 20);
                    lyricsContent.addView(error);

                    Button retryBtn = new Button(MainActivity.this);
                    retryBtn.setText("Retry");
                    styleDynamicButton(retryBtn);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                    retryBtn.setLayoutParams(lp);
                    retryBtn.setPadding(35, 15, 35, 15);
                    retryBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            currentLyricsTrack = "";
                            currentLyricsArtist = "";
                            String curTitle = npTitle != null ? npTitle.getText().toString() : track;
                            String curArtist = npArtist != null ? npArtist.getText().toString() : artist;
                            fetchLyrics(curTitle, curArtist);
                        }
                    });
                    lyricsContent.addView(retryBtn);
                }
            }
        });
    }

    private void fetchLyrics(final String track, final String artist) {
        if (track == null || track.isEmpty() || artist == null || artist.isEmpty() || 
            track.equalsIgnoreCase("Advertisement") || artist.equalsIgnoreCase("Advertisement") ||
            track.equals("Not Playing") || track.equals("Refreshing...") || track.equals("Spotify")) {
            cancelLyricsSearch();
            currentLyricsTrack = "";
            currentLyricsArtist = "";
            currentLyrics.clear();
            currentLyricLineIndex = -1;
            if (lyricsContent != null) {
                clearContainerViews(lyricsContent);
            }
            return;
        }
        if (track.equals(currentLyricsTrack) && artist.equals(currentLyricsArtist) && currentLyrics.size() > 0) {
            return;
        }
        if (track.equals(currentlyFetchingLyricsTrack) && artist.equals(currentlyFetchingLyricsArtist)) {
            return;
        }

        cancelLyricsSearch();
        final int myGen = ++lyricsSearchGeneration;
        currentlyFetchingLyricsTrack = track;
        currentlyFetchingLyricsArtist = artist;
        currentLyrics.clear();
        currentLyricLineIndex = -1;

        final int[] sourceIds = new int[]{2, 3, 4, 5, 6, 7, 1, 8};
        final String[] sourceNames = new String[]{"LRCLIB", "YouTube", "NetEase", "Megalobiz", "Musixmatch", "SimpMusic", "Genius", "Lyrics.ovh"};

        final TextView loadingTv = new TextView(MainActivity.this);
        loadingTv.setText("Searching for lyrics on " + sourceNames[0] + " 1/" + sourceNames.length + "...");
        loadingTv.setTextColor(0xFF8892b0);
        loadingTv.setTextSize(18);
        loadingTv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        loadingTv.setPadding(0, 40, 0, 0);

        if (lyricsContent != null) {
            clearContainerViews(lyricsContent);
            lyricsContent.addView(loadingTv);
        }
        if (api == null) {
            currentlyFetchingLyricsTrack = "";
            currentlyFetchingLyricsArtist = "";
            return;
        }

        final int loadId = startLoading("Searching for lyrics on " + sourceNames[0] + " 1/" + sourceNames.length + "...");
        currentLyricsLoadId = loadId;

        fetchLyricsFromSource(track, artist, 0, sourceIds, sourceNames, loadId, myGen, loadingTv);
    }

    private void fetchLyricsFromSource(
        final String track,
        final String artist,
        final int stepIndex,
        final int[] sourceIds,
        final String[] sourceNames,
        final int loadId,
        final int myGen,
        final TextView loadingTv
    ) {
        if (myGen != lyricsSearchGeneration || api == null) {
            return;
        }
        final String progressText = "Searching for lyrics on " + sourceNames[stepIndex] + " " + (stepIndex + 1) + "/" + sourceNames.length + "...";
        updateLoadingText(loadId, progressText);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (loadingTv != null) {
                    loadingTv.setText(progressText);
                }
            }
        });

        String url = "/api/spotify/lyrics?track=" + java.net.URLEncoder.encode(track) +
                     "&artist=" + java.net.URLEncoder.encode(artist) +
                     "&source=" + sourceIds[stepIndex];

        api.get(url, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                if (myGen != lyricsSearchGeneration) return;
                boolean found = false;
                try {
                    JSONObject json = new JSONObject(response);
                    if ("success".equals(json.optString("status"))) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
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
                                if (!currentLyrics.isEmpty()) {
                                    found = true;
                                    currentlyFetchingLyricsTrack = "";
                                    currentlyFetchingLyricsArtist = "";
                                    currentLyricsTrack = track;
                                    currentLyricsArtist = artist;
                                    if (currentLyricsLoadId == loadId) currentLyricsLoadId = -1;
                                    stopLoading(loadId);
                                    renderLyrics();
                                    return;
                                }
                            } else {
                                String synced = data.optString("lyrics", "");
                                boolean hasTimestamps = data.optBoolean("hasTimestamps", true);
                                if (!synced.trim().isEmpty()) {
                                    found = true;
                                    currentlyFetchingLyricsTrack = "";
                                    currentlyFetchingLyricsArtist = "";
                                    currentLyricsTrack = track;
                                    currentLyricsArtist = artist;
                                    if (currentLyricsLoadId == loadId) currentLyricsLoadId = -1;
                                    stopLoading(loadId);
                                    parseLrc(synced, hasTimestamps);
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("DEBUG", "Failed to parse lyrics step JSON", e);
                }

                if (!found) {
                    advanceOrFinish();
                }
            }

            @Override
            public void onError(String error) {
                if (myGen != lyricsSearchGeneration) return;
                advanceOrFinish();
            }

            private void advanceOrFinish() {
                if (myGen != lyricsSearchGeneration) return;
                if (stepIndex + 1 < sourceIds.length) {
                    fetchLyricsFromSource(track, artist, stepIndex + 1, sourceIds, sourceNames, loadId, myGen, loadingTv);
                } else {
                    currentlyFetchingLyricsTrack = "";
                    currentlyFetchingLyricsArtist = "";
                    currentLyricsTrack = track;
                    currentLyricsArtist = artist;
                    if (currentLyricsLoadId == loadId) currentLyricsLoadId = -1;
                    stopLoading(loadId);
                    showLyricsNotFound(track, artist);
                }
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
                        tv.setText(ll.text.isEmpty() ? "..." : ll.text);
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
        isLocalIndexingPolling = false;
        isLocalFpPolling = false;
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(pollLocalIndexingRunnable);
        handler.removeCallbacks(pollLocalFpRunnable);
        if (bitmapCache != null) {
            bitmapCache.clear();
        }
    }

    // =========================================================================
    // LOCAL MUSIC MANAGEMENT & PYMUSICINDEXER BRIDGE
    // =========================================================================

    private void showLocalScreen(int screenId) {
        if (localScreenFolders != null) localScreenFolders.setVisibility(screenId == 1 ? View.VISIBLE : View.GONE);
        if (localScreenIndexing != null) localScreenIndexing.setVisibility(screenId == 2 ? View.VISIBLE : View.GONE);
        if (localScreenFingerprint != null) localScreenFingerprint.setVisibility(screenId == 3 ? View.VISIBLE : View.GONE);
        if (localScreenLibrary != null) localScreenLibrary.setVisibility(screenId == 4 ? View.VISIBLE : View.GONE);
    }

    private void refreshLocalMusicState() {
        if (api == null) return;
        api.get("/api/indexer/status", new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            boolean isScanning = json.optBoolean("is_scanning", false);
                            boolean isFingerprinting = json.optBoolean("is_fingerprinting", false);
                            boolean hasFolders = json.optBoolean("has_configured_folders", false);
                            int incCount = json.optInt("incomplete_count", 0);
                            JSONObject player = json.optJSONObject("player_status");
                            updateLocalPlayerUi(player);

                            if (isScanning) {
                                showLocalScreen(2);
                                startLocalIndexingPoll();
                            } else if (isFingerprinting) {
                                showLocalScreen(3);
                                switchReviewTab(currentReviewTab);
                                startFingerprintActionPoll("VLC Fingerprint");
                            } else if (!hasFolders) {
                                showLocalScreen(1);
                                loadLocalFolderBrowser("");
                                loadConfiguredFolders();
                            } else {
                                showLocalScreen(4);
                                switchLocalLibrarySubTab(currentLocalSubTab);
                            }
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {
                showToast("Could not reach local music service", false);
            }
        });
    }

    private void loadLocalFolderBrowser(final String path) {
        if (api == null) return;
        currentBrowsedPath = path;
        String encoded = "";
        try { encoded = java.net.URLEncoder.encode(path, "UTF-8"); } catch (Exception e) {}
        api.get("/api/indexer/browse?path=" + encoded, new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            final boolean isDrives = json.optBoolean("is_drives", false);
                            final String cur = json.optString("current_path", "");
                            final String parent = json.optString("parent_path", "");
                            currentBrowsedPath = cur;

                            if (localFolderCurrentPath != null) {
                                localFolderCurrentPath.setText(cur.isEmpty() ? "Browsing: Root Drives" : cur);
                            }

                            if (btnLocalFolderUp != null) {
                                btnLocalFolderUp.setEnabled(!isDrives);
                                btnLocalFolderUp.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        loadLocalFolderBrowser(parent != null ? parent : "");
                                    }
                                });
                            }

                            if (localBrowserItemsContainer != null) {
                                localBrowserItemsContainer.removeAllViews();
                                JSONArray folders = json.optJSONArray("folders");
                                if (folders != null && folders.length() > 0) {
                                    for (int i = 0; i < folders.length(); i++) {
                                        final String name = folders.getString(i);
                                        Button itemBtn = new Button(MainActivity.this);
                                        itemBtn.setText(isDrives ? ("[Drive] " + name) : ("[Folder] " + name));
                                        styleDynamicButton(itemBtn);
                                        itemBtn.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
                                        itemBtn.setPadding(20, 15, 20, 15);
                                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                        lp.setMargins(0, 0, 0, 6);
                                        itemBtn.setLayoutParams(lp);

                                        itemBtn.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                String nextPath = isDrives ? name : (cur.endsWith("\\") ? cur + name : cur + "\\" + name);
                                                loadLocalFolderBrowser(nextPath);
                                            }
                                        });
                                        localBrowserItemsContainer.addView(itemBtn);
                                    }
                                } else {
                                    TextView empty = new TextView(MainActivity.this);
                                    empty.setText("No subdirectories found.");
                                    empty.setTextColor(0xFF8892b0);
                                    empty.setPadding(20, 20, 20, 20);
                                    localBrowserItemsContainer.addView(empty);
                                }
                            }
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {
                showToast("Failed to browse: " + error, false);
            }
        });
    }

    private void loadConfiguredFolders() {
        if (api == null) return;
        api.get("/api/indexer/folders", new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONArray folders = json.optJSONArray("folders");
                            renderSelectedFolders(folders);
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {}
        });
    }

    private void renderSelectedFolders(JSONArray folders) {
        if (localSelectedFoldersContainer == null) return;
        localSelectedFoldersContainer.removeAllViews();
        if (folders == null || folders.length() == 0) {
            TextView none = new TextView(this);
            none.setText("No folders configured yet. Navigate to your music directory and tap '+ Set Folder'.");
            none.setTextColor(0xFF8892b0);
            none.setTextSize(12);
            none.setPadding(10, 10, 10, 10);
            localSelectedFoldersContainer.addView(none);
            return;
        }

        for (int i = 0; i < folders.length(); i++) {
            final String folderPath = folders.optString(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(6, 6, 6, 6);

            TextView tv = new TextView(this);
            tv.setText("✓ " + folderPath);
            tv.setTextColor(0xFFffffff);
            tv.setTextSize(13);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tv.setLayoutParams(tvLp);
            row.addView(tv);

            Button btnRemove = new Button(this);
            btnRemove.setText("✕ Remove");
            btnRemove.setTextColor(0xFFff5252);
            btnRemove.setTextSize(11);
            styleDynamicButton(btnRemove);
            btnRemove.setPadding(12, 6, 12, 6);
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeConfiguredFolder(folderPath);
                }
            });
            row.addView(btnRemove);

            localSelectedFoldersContainer.addView(row);
        }
    }

    private void addConfiguredFolder(final String folder) {
        if (api == null || folder == null || folder.isEmpty()) return;
        String encoded = "";
        try { encoded = java.net.URLEncoder.encode(folder, "UTF-8"); } catch (Exception e) {}
        api.get("/api/indexer/folders/add?folder=" + encoded, new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            renderSelectedFolders(json.optJSONArray("folders"));
                            showToast("Added folder: " + folder, true);
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {
                showToast("Failed to add: " + error, false);
            }
        });
    }

    private void removeConfiguredFolder(String folder) {
        if (api == null || folder == null) return;
        String encoded = "";
        try { encoded = java.net.URLEncoder.encode(folder, "UTF-8"); } catch (Exception e) {}
        api.get("/api/indexer/folders/remove?folder=" + encoded, new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            renderSelectedFolders(json.optJSONArray("folders"));
                            showToast("Folder removed", true);
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {
                showToast("Failed to remove: " + error, false);
            }
        });
    }

    private void startLocalScanAndShowProgress() {
        if (api == null) return;
        showLocalScreen(2);
        if (localIdxBar != null) localIdxBar.setProgress(0);
        if (localIdxPercent != null) localIdxPercent.setText("0%");
        if (localIdxFile != null) localIdxFile.setText("Starting scan...");
        if (localIdxCount != null) localIdxCount.setText("Songs indexed: 0");

        api.get("/api/indexer/scan", new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                startLocalIndexingPoll();
            }
            @Override
            public void onError(String error) {
                showToast("Failed to start scan: " + error, false);
            }
        });
    }

    private final Runnable pollLocalIndexingRunnable = new Runnable() {
        @Override
        public void run() {
            if (api == null) return;
            api.get("/api/indexer/status", new ApiClient.Callback() {
                @Override
                public void onSuccess(final String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject json = new JSONObject(response);
                                boolean isScanning = json.optBoolean("is_scanning", false);
                                JSONObject prog = json.optJSONObject("scan_progress");
                                int pct = prog != null ? prog.optInt("progress_percent", 0) : 0;
                                String curr = prog != null ? prog.optString("current_file", "") : "";
                                int total = json.optInt("total_songs", 0);
                                if (prog != null && prog.has("songs_indexed")) {
                                    total = prog.optInt("songs_indexed", total);
                                }
                                int inc = json.optInt("incomplete_count", 0);
                                int unindexed = json.optInt("unindexed_count", 0);

                                if (localIdxBar != null) localIdxBar.setProgress(pct);
                                if (localIdxPercent != null) localIdxPercent.setText(pct + "%");
                                if (localIdxFile != null) localIdxFile.setText(curr.isEmpty() ? "Indexing..." : "Scanning: " + curr);
                                if (localIdxCount != null) {
                                    localIdxCount.setText("Songs indexed: " + total + " | Needing FP: " + inc + " | Unindexed: " + unindexed);
                                }

                                if (isScanning) {
                                    handler.postDelayed(pollLocalIndexingRunnable, 1200);
                                } else {
                                    isLocalIndexingPolling = false;
                                    showToast("Indexing complete! " + total + " songs indexed.", true);
                                    if (inc > 0 || unindexed > 0) {
                                        showLocalScreen(3);
                                        if (inc > 0) {
                                            switchReviewTab(0);
                                        } else {
                                            switchReviewTab(1);
                                        }
                                    } else {
                                        showLocalScreen(4);
                                        switchLocalLibrarySubTab(currentLocalSubTab);
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    });
                }
                @Override
                public void onError(String error) {
                    if (isLocalIndexingPolling) {
                        handler.postDelayed(pollLocalIndexingRunnable, 2500);
                    }
                }
            });
        }
    };

    private void startLocalIndexingPoll() {
        isLocalIndexingPolling = true;
        handler.removeCallbacks(pollLocalIndexingRunnable);
        handler.post(pollLocalIndexingRunnable);
    }

    private void switchReviewTab(int tab) {
        currentReviewTab = tab;
        if (btnLocalReviewIncomplete != null) {
            btnLocalReviewIncomplete.setTextColor(tab == 0 ? 0xFF00d4ff : 0xFF8892b0);
            btnLocalReviewIncomplete.setBackgroundColor(tab == 0 ? 0xFF1a1f36 : 0xFF111827);
        }
        if (btnLocalReviewUnindexed != null) {
            btnLocalReviewUnindexed.setTextColor(tab == 1 ? 0xFF00d4ff : 0xFF8892b0);
            btnLocalReviewUnindexed.setBackgroundColor(tab == 1 ? 0xFF1a1f36 : 0xFF111827);
        }
        if (localFpTitle != null) {
            localFpTitle.setText(tab == 0 ? "Songs Needing Metadata / Fingerprinting" : "Unindexed & Failed Files");
        }
        if (localFpSubtitle != null) {
            localFpSubtitle.setText(tab == 0
                ? "Review songs split into worked (enriched) and needing attention."
                : "Files that could not be indexed (non-audio, unsupported format, or corrupt).");
        }
        if (localFpStatsContainer != null) {
            localFpStatsContainer.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        }
        if (localFpActionsContainer != null) {
            localFpActionsContainer.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        }
        if (localFpFilterContainer != null) {
            localFpFilterContainer.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        }
        if (tab == 0) {
            loadIncompleteSongs();
        } else {
            loadUnindexedFiles(localFpItemsContainer, "");
        }
    }

    private void switchReviewFilter(int filter) {
        currentReviewFilter = filter;
        if (btnLocalFilterAll != null) {
            btnLocalFilterAll.setTextColor(filter == 0 ? 0xFF00d4ff : 0xFF8892b0);
            btnLocalFilterAll.setBackgroundColor(filter == 0 ? 0xFF1a1f36 : 0xFF111827);
        }
        if (btnLocalFilterWorked != null) {
            btnLocalFilterWorked.setTextColor(filter == 1 ? 0xFF00ffaa : 0xFF8892b0);
            btnLocalFilterWorked.setBackgroundColor(filter == 1 ? 0xFF102d20 : 0xFF111827);
        }
        if (btnLocalFilterFailed != null) {
            btnLocalFilterFailed.setTextColor(filter == 2 ? 0xFFffa726 : 0xFF8892b0);
            btnLocalFilterFailed.setBackgroundColor(filter == 2 ? 0xFF3a2810 : 0xFF111827);
        }
        renderReviewSplitList();
    }

    private void loadLocalUnindexed(String query) {
        loadUnindexedFiles(localLibraryItemsContainer, query);
    }

    private void loadUnindexedFiles(final LinearLayout targetContainer, final String filterQuery) {
        if (api == null || targetContainer == null) return;
        targetContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Loading unindexed files...");
        loading.setTextColor(0xFF8892b0);
        loading.setPadding(15, 20, 15, 20);
        targetContainer.addView(loading);

        api.get("/api/indexer/unindexed", new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONArray files = json.optJSONArray("unindexed");
                            renderUnindexedList(files, targetContainer, filterQuery);
                        } catch (Exception e) {
                            renderUnindexedList(null, targetContainer, filterQuery);
                        }
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        targetContainer.removeAllViews();
                        TextView err = new TextView(MainActivity.this);
                        err.setText("Failed to load unindexed files.");
                        err.setTextColor(0xFFff5555);
                        err.setPadding(15, 20, 15, 20);
                        targetContainer.addView(err);
                    }
                });
            }
        });
    }

    private void renderUnindexedList(JSONArray files, LinearLayout targetContainer, String query) {
        if (targetContainer == null) return;
        targetContainer.removeAllViews();

        if (files == null || files.length() == 0) {
            TextView none = new TextView(this);
            none.setText("No unindexed files found. All files were successfully processed!");
            none.setTextColor(0xFF00ffaa);
            none.setTextSize(14);
            none.setPadding(15, 20, 15, 20);
            targetContainer.addView(none);
            return;
        }

        String q = query != null ? query.trim().toLowerCase() : "";
        int shownCount = 0;

        for (int i = 0; i < files.length(); i++) {
            JSONObject u = files.optJSONObject(i);
            if (u == null) continue;

            String fileName = u.optString("file_name", "Unknown File");
            String filePath = u.optString("file_path", "");
            String reason = u.optString("reason", "Not indexed");
            String errorDetails = u.optString("error_details", "");
            String size = u.optString("file_size_human", "");

            if (!q.isEmpty()) {
                if (!fileName.toLowerCase().contains(q) && !filePath.toLowerCase().contains(q) && !reason.toLowerCase().contains(q)) {
                    continue;
                }
            }
            shownCount++;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF111827);
            card.setPadding(12, 10, 12, 10);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 0, 0, 8);
            card.setLayoutParams(clp);

            // Row 1: File name + Type Badge
            LinearLayout r1 = new LinearLayout(this);
            r1.setOrientation(LinearLayout.HORIZONTAL);
            r1.setGravity(android.view.Gravity.CENTER_VERTICAL);
            r1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView tvName = new TextView(this);
            tvName.setText(fileName);
            tvName.setTextColor(0xFFffffff);
            tvName.setTextSize(14);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setSingleLine(true);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvName.setLayoutParams(nlp);
            r1.addView(tvName);

            boolean isCorrupt = reason.toLowerCase().contains("corrupt") || reason.toLowerCase().contains("failure") || (!errorDetails.isEmpty() && !"null".equals(errorDetails));
            TextView tvBadge = new TextView(this);
            tvBadge.setText(isCorrupt ? "  [CORRUPT]  " : "  [NON-AUDIO]  ");
            tvBadge.setTextColor(isCorrupt ? 0xFFff5555 : 0xFF8892b0);
            tvBadge.setTextSize(11);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setBackgroundColor(isCorrupt ? 0xFF3a1414 : 0xFF1a1f36);
            tvBadge.setPadding(6, 3, 6, 3);
            r1.addView(tvBadge);
            card.addView(r1);

            // Row 2: Reason
            TextView tvReason = new TextView(this);
            tvReason.setText("Reason: " + reason);
            tvReason.setTextColor(0xFFffa726);
            tvReason.setTextSize(12);
            tvReason.setPadding(0, 4, 0, 0);
            card.addView(tvReason);

            if (!errorDetails.isEmpty() && !"null".equals(errorDetails)) {
                TextView tvErr = new TextView(this);
                tvErr.setText("Error: " + errorDetails);
                tvErr.setTextColor(0xFFff7777);
                tvErr.setTextSize(11);
                tvErr.setPadding(0, 2, 0, 0);
                card.addView(tvErr);
            }

            // Row 3: Path + Size
            TextView tvPath = new TextView(this);
            String sub = filePath;
            if (!size.isEmpty()) sub += " (" + size + ")";
            tvPath.setText(sub);
            tvPath.setTextColor(0xFF667085);
            tvPath.setTextSize(11);
            tvPath.setSingleLine(true);
            tvPath.setEllipsize(TextUtils.TruncateAt.END);
            tvPath.setPadding(0, 2, 0, 0);
            card.addView(tvPath);

            targetContainer.addView(card);
        }

        if (shownCount == 0 && !q.isEmpty()) {
            TextView noMatch = new TextView(this);
            noMatch.setText("No unindexed files matched '" + query + "'.");
            noMatch.setTextColor(0xFF8892b0);
            noMatch.setPadding(15, 20, 15, 20);
            targetContainer.addView(noMatch);
        }
    }

    private void loadIncompleteSongs() {
        if (api == null) return;
        api.get("/api/indexer/incomplete", new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            cachedReviewWorked = json.optJSONArray("worked");
                            cachedReviewFailed = json.optJSONArray("failed");
                            if (cachedReviewFailed == null) {
                                cachedReviewFailed = json.optJSONArray("incomplete");
                            }

                            int totalSongs = json.optInt("total_songs", 0);
                            int needingFp = json.optInt("total_needing_fp", cachedReviewFailed != null ? cachedReviewFailed.length() : 0);
                            int workedCount = json.optInt("total_worked", cachedReviewWorked != null ? cachedReviewWorked.length() : 0);
                            int failedCount = json.optInt("total_failed", cachedReviewFailed != null ? cachedReviewFailed.length() : 0);
                            int missingArt = json.optInt("total_missing_art", 0);
                            int unindexedCount = json.optInt("total_unindexed", 0);

                            if (localFpStatsText != null) {
                                localFpStatsText.setText("Total Songs: " + totalSongs + " | Needing FP: " + needingFp + " | Worked: " + workedCount + " | Failed: " + failedCount);
                            }
                            if (localFpStatsSub != null) {
                                localFpStatsSub.setText("Missing Artwork: " + missingArt + " | Unindexed Files: " + unindexedCount);
                            }

                            if (btnLocalFilterAll != null) {
                                btnLocalFilterAll.setText("All Review (" + (workedCount + failedCount) + ")");
                            }
                            if (btnLocalFilterWorked != null) {
                                btnLocalFilterWorked.setText("Worked (" + workedCount + ")");
                            }
                            if (btnLocalFilterFailed != null) {
                                btnLocalFilterFailed.setText("Didn't Work (" + failedCount + ")");
                            }

                            renderReviewSplitList();
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {}
        });
    }

    private void renderReviewSplitList() {
        if (localFpItemsContainer == null) return;
        localFpItemsContainer.removeAllViews();

        JSONArray worked = cachedReviewWorked;
        JSONArray failed = cachedReviewFailed;

        int workedLen = worked != null ? worked.length() : 0;
        int failedLen = failed != null ? failed.length() : 0;

        if (workedLen == 0 && failedLen == 0) {
            TextView none = new TextView(this);
            none.setText("No incomplete tracks found! All tracks have complete tags and artwork.");
            none.setTextColor(0xFF00ffaa);
            none.setPadding(15, 20, 15, 20);
            localFpItemsContainer.addView(none);
            return;
        }

        // Section 1: Worked / Enriched
        if (currentReviewFilter == 0 || currentReviewFilter == 1) {
            if (workedLen > 0) {
                TextView secWorked = new TextView(this);
                secWorked.setText("--- WORKED / ENRICHED (" + workedLen + ") ---");
                secWorked.setTextColor(0xFF00ffaa);
                secWorked.setTextSize(13);
                secWorked.setTypeface(null, Typeface.BOLD);
                secWorked.setBackgroundColor(0xFF102d20);
                secWorked.setPadding(12, 8, 12, 8);
                localFpItemsContainer.addView(secWorked);

                for (int i = 0; i < workedLen; i++) {
                    JSONObject s = worked.optJSONObject(i);
                    if (s == null) continue;
                    renderReviewCard(s, true);
                }
            } else if (currentReviewFilter == 1) {
                TextView emptyW = new TextView(this);
                emptyW.setText("No tracks have been enriched yet. Run VLC Fingerprint or Fetch Art.");
                emptyW.setTextColor(0xFF8892b0);
                emptyW.setPadding(15, 20, 15, 20);
                localFpItemsContainer.addView(emptyW);
            }
        }

        // Section 2: Didn't Work / Failed / Incomplete
        if (currentReviewFilter == 0 || currentReviewFilter == 2) {
            if (failedLen > 0) {
                TextView secFailed = new TextView(this);
                secFailed.setText("--- DID NOT WORK / STILL INCOMPLETE (" + failedLen + ") ---");
                secFailed.setTextColor(0xFFffa726);
                secFailed.setTextSize(13);
                secFailed.setTypeface(null, Typeface.BOLD);
                secFailed.setBackgroundColor(0xFF3a2810);
                secFailed.setPadding(12, 8, 12, 8);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (currentReviewFilter == 0 && workedLen > 0) {
                    slp.setMargins(0, 14, 0, 0);
                }
                secFailed.setLayoutParams(slp);
                localFpItemsContainer.addView(secFailed);

                for (int i = 0; i < failedLen; i++) {
                    JSONObject s = failed.optJSONObject(i);
                    if (s == null) continue;
                    renderReviewCard(s, false);
                }
            } else if (currentReviewFilter == 2) {
                TextView emptyF = new TextView(this);
                emptyF.setText("Great news! There are no failed tracks.");
                emptyF.setTextColor(0xFF00ffaa);
                emptyF.setPadding(15, 20, 15, 20);
                localFpItemsContainer.addView(emptyF);
            }
        }
    }

    private void renderReviewCard(JSONObject s, boolean isWorked) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF111827);
        card.setPadding(12, 10, 12, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        card.setLayoutParams(lp);

        String title = s.optString("title", s.optString("file_name", "Untitled"));
        String artist = s.optString("artist", "Unknown Artist");
        String album = s.optString("album", "");
        String status = s.optString("status", "");

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r1.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFffffff);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(nlp);
        r1.addView(tvTitle);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(isWorked ? " [WORKED] " : " [NEEDS TAGS] ");
        tvBadge.setTextColor(isWorked ? 0xFF00ffaa : 0xFFffa726);
        tvBadge.setTextSize(11);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setBackgroundColor(isWorked ? 0xFF102d20 : 0xFF3a2810);
        tvBadge.setPadding(6, 3, 6, 3);
        r1.addView(tvBadge);
        card.addView(r1);

        TextView tvSub = new TextView(this);
        tvSub.setText(artist + (album.isEmpty() ? "" : " - " + album));
        tvSub.setTextColor(0xFF8892b0);
        tvSub.setTextSize(12);
        tvSub.setPadding(0, 2, 0, 0);
        card.addView(tvSub);

        if (!status.isEmpty()) {
            TextView tvStatus = new TextView(this);
            tvStatus.setText("Status: " + status);
            tvStatus.setTextColor(isWorked ? 0xFF00d4ff : 0xFF8892b0);
            tvStatus.setTextSize(11);
            tvStatus.setPadding(0, 2, 0, 0);
            card.addView(tvStatus);
        }

        JSONArray missing = s.optJSONArray("missing_fields_list");
        if (missing != null && missing.length() > 0) {
            StringBuilder sb = new StringBuilder("Missing: ");
            for (int m = 0; m < missing.length(); m++) {
                if (m > 0) sb.append(", ");
                sb.append(missing.optString(m));
            }
            TextView tvMissing = new TextView(this);
            tvMissing.setText(sb.toString());
            tvMissing.setTextColor(0xFFffa726);
            tvMissing.setTextSize(11);
            tvMissing.setPadding(0, 2, 0, 0);
            card.addView(tvMissing);
        }

        localFpItemsContainer.addView(card);
    }

    private boolean isLocalFpPolling = false;
    private String currentFpActionLabel = "Fingerprinting";
    private final Runnable pollLocalFpRunnable = new Runnable() {
        @Override
        public void run() {
            if (api == null) return;
            api.get("/api/indexer/status", new ApiClient.Callback() {
                @Override
                public void onSuccess(final String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject json = new JSONObject(response);
                                boolean isFp = json.optBoolean("is_fingerprinting", false);
                                JSONObject prog = json.optJSONObject("fingerprint_progress");
                                int pct = prog != null ? prog.optInt("progress_percent", 0) : 0;
                                int enriched = prog != null ? prog.optInt("enriched_count", 0) : 0;
                                int totalFiles = prog != null ? prog.optInt("total_files", 0) : 0;

                                if (localFpProgressBar != null) {
                                    localFpProgressBar.setVisibility(isFp ? View.VISIBLE : View.GONE);
                                    localFpProgressBar.setProgress(pct);
                                }
                                if (localFpStatus != null) {
                                    localFpStatus.setVisibility(View.VISIBLE);
                                    String tot = totalFiles > 0 ? (" / " + totalFiles) : "";
                                    localFpStatus.setText(currentFpActionLabel + ": " + pct + "% (Processed: " + enriched + tot + ")");
                                }

                                if (isFp) {
                                    if (isLocalFpPolling) {
                                        handler.postDelayed(pollLocalFpRunnable, 1200);
                                    }
                                } else {
                                    isLocalFpPolling = false;
                                    if (localFpProgressBar != null) localFpProgressBar.setVisibility(View.GONE);
                                    if (localFpStatus != null) localFpStatus.setText("Completed " + currentFpActionLabel + "!");
                                    showToast(currentFpActionLabel + " finished!", true);
                                    loadIncompleteSongs();
                                }
                            } catch (Exception e) {}
                        }
                    });
                }
                @Override
                public void onError(String error) {
                    if (isLocalFpPolling) {
                        handler.postDelayed(pollLocalFpRunnable, 2500);
                    }
                }
            });
        }
    };

    private void runFingerprintAction(final String endpoint, final String label) {
        if (api == null) return;
        currentFpActionLabel = label;
        if (localFpProgressBar != null) {
            localFpProgressBar.setVisibility(View.VISIBLE);
            localFpProgressBar.setProgress(0);
        }
        if (localFpStatus != null) {
            localFpStatus.setVisibility(View.VISIBLE);
            localFpStatus.setText("Running " + label + "...");
        }

        api.get(endpoint, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                startFingerprintActionPoll(label);
            }
            @Override
            public void onError(String error) {
                showToast("Action failed: " + error, false);
                if (localFpProgressBar != null) localFpProgressBar.setVisibility(View.GONE);
                if (localFpStatus != null) localFpStatus.setVisibility(View.GONE);
            }
        });
    }

    private void startFingerprintActionPoll(final String label) {
        if (label != null && !label.isEmpty()) {
            currentFpActionLabel = label;
        }
        isLocalFpPolling = true;
        handler.removeCallbacks(pollLocalFpRunnable);
        handler.post(pollLocalFpRunnable);
    }

    private void switchLocalLibrarySubTab(int index) {
        currentLocalSubTab = index;
        if (btnLocalSubSongs != null) {
            btnLocalSubSongs.setTextColor(index == 0 ? 0xFF00d4ff : 0xFF8892b0);
            btnLocalSubSongs.setBackgroundColor(index == 0 ? 0xFF1a1f36 : 0xFF111827);
        }
        if (btnLocalSubArtists != null) {
            btnLocalSubArtists.setTextColor(index == 1 ? 0xFF00d4ff : 0xFF8892b0);
            btnLocalSubArtists.setBackgroundColor(index == 1 ? 0xFF1a1f36 : 0xFF111827);
        }
        if (btnLocalSubAlbums != null) {
            btnLocalSubAlbums.setTextColor(index == 2 ? 0xFF00d4ff : 0xFF8892b0);
            btnLocalSubAlbums.setBackgroundColor(index == 2 ? 0xFF1a1f36 : 0xFF111827);
        }
        if (btnLocalSubUnindexed != null) {
            btnLocalSubUnindexed.setTextColor(index == 3 ? 0xFF00d4ff : 0xFF8892b0);
            btnLocalSubUnindexed.setBackgroundColor(index == 3 ? 0xFF1a1f36 : 0xFF111827);
        }

        String query = localSearchInput != null ? localSearchInput.getText().toString().trim() : "";
        if (index == 0) {
            loadLocalSongs(query);
        } else if (index == 1) {
            loadLocalArtists();
        } else if (index == 2) {
            loadLocalAlbums();
        } else if (index == 3) {
            loadLocalUnindexed(query);
        }
    }

    private void loadLocalSongs(String query) {
        if (api == null) return;
        String endpoint = "/api/indexer/songs";
        if (currentLocalFilterArtist != null && !currentLocalFilterArtist.isEmpty()) {
            try { endpoint += "?artist=" + java.net.URLEncoder.encode(currentLocalFilterArtist, "UTF-8"); } catch (Exception e) {}
        } else if (currentLocalFilterAlbum != null && !currentLocalFilterAlbum.isEmpty()) {
            try { endpoint += "?album=" + java.net.URLEncoder.encode(currentLocalFilterAlbum, "UTF-8"); } catch (Exception e) {}
        } else if (query != null && !query.isEmpty()) {
            try { endpoint += "?q=" + java.net.URLEncoder.encode(query, "UTF-8"); } catch (Exception e) {}
        }

        api.get(endpoint, new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONArray songs = json.optJSONArray("songs");
                            renderLocalSongsList(songs);
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {
                showToast("Failed to load songs: " + error, false);
            }
        });
    }

    private void renderLocalSongsList(JSONArray songs) {
        if (localLibraryItemsContainer == null) return;
        localLibraryItemsContainer.removeAllViews();

        if (currentLocalFilterArtist != null || currentLocalFilterAlbum != null) {
            LinearLayout filterBar = new LinearLayout(this);
            filterBar.setOrientation(LinearLayout.HORIZONTAL);
            filterBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            filterBar.setBackgroundColor(0xFF133854);
            filterBar.setPadding(10, 8, 10, 8);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            flp.setMargins(0, 0, 0, 8);
            filterBar.setLayoutParams(flp);

            TextView tvFilter = new TextView(this);
            tvFilter.setText("Showing: " + (currentLocalFilterArtist != null ? currentLocalFilterArtist : currentLocalFilterAlbum));
            tvFilter.setTextColor(0xFF00d4ff);
            tvFilter.setTextSize(13);
            tvFilter.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvFilter.setLayoutParams(tlp);
            filterBar.addView(tvFilter);

            Button btnClear = new Button(this);
            btnClear.setText("Show All");
            btnClear.setTextColor(0xFFffffff);
            styleDynamicButton(btnClear);
            btnClear.setPadding(12, 6, 12, 6);
            btnClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentLocalFilterArtist = null;
                    currentLocalFilterAlbum = null;
                    loadLocalSongs("");
                }
            });
            filterBar.addView(btnClear);
            localLibraryItemsContainer.addView(filterBar);
        }

        if (songs == null || songs.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No songs found. Try a different search or rescan folders.");
            empty.setTextColor(0xFF8892b0);
            empty.setPadding(15, 20, 15, 20);
            localLibraryItemsContainer.addView(empty);
            return;
        }

        for (int i = 0; i < songs.length(); i++) {
            final JSONObject s = songs.optJSONObject(i);
            if (s == null) continue;
            final String filePath = s.optString("file_path", "");
            final String title = s.optString("title", s.optString("file_name", "Untitled"));
            final String artist = s.optString("contributing_artists", s.optString("artist", "Unknown Artist"));
            final String album = s.optString("album", "");
            final String duration = s.optString("duration_formatted", "");
            final String format = s.optString("format", "").toUpperCase();
            final String bitrate = s.optString("bitrate_kbps", "");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF111827);
            row.setPadding(10, 10, 10, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 6);
            row.setLayoutParams(lp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            textCol.setLayoutParams(tlp);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextColor(0xFFffffff);
            tvTitle.setTextSize(14);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setSingleLine(true);
            tvTitle.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(tvTitle);

            TextView tvSub = new TextView(this);
            String subStr = artist;
            if (!album.isEmpty()) subStr += " • " + album;
            if (!duration.isEmpty()) subStr += " • " + duration;
            if (!format.isEmpty()) subStr += " [" + format + (bitrate.isEmpty() ? "" : " " + bitrate + "k") + "]";
            tvSub.setText(subStr);
            tvSub.setTextColor(0xFF8892b0);
            tvSub.setTextSize(12);
            tvSub.setSingleLine(true);
            tvSub.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(tvSub);

            row.addView(textCol);

            Button btnPlay = new Button(this);
            btnPlay.setText("Play");
            btnPlay.setTextColor(0xFF00d4ff);
            btnPlay.setTextSize(12);
            styleDynamicButton(btnPlay);
            btnPlay.setPadding(14, 8, 14, 8);
            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playLocalSong(filePath, title, artist);
                }
            });
            row.addView(btnPlay);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playLocalSong(filePath, title, artist);
                }
            });

            localLibraryItemsContainer.addView(row);
        }
    }

    private void loadLocalArtists() {
        if (api == null) return;
        api.get("/api/indexer/artists", new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONArray artists = json.optJSONArray("artists");
                            renderLocalArtistsList(artists);
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {}
        });
    }

    private void renderLocalArtistsList(JSONArray artists) {
        if (localLibraryItemsContainer == null) return;
        localLibraryItemsContainer.removeAllViews();
        if (artists == null || artists.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No artists found.");
            empty.setTextColor(0xFF8892b0);
            empty.setPadding(15, 20, 15, 20);
            localLibraryItemsContainer.addView(empty);
            return;
        }

        for (int i = 0; i < artists.length(); i++) {
            final JSONObject a = artists.optJSONObject(i);
            if (a == null) continue;
            final String name = a.optString("artist", "Unknown Artist");
            int songsCount = a.optInt("song_count", 0);
            int albumCount = a.optInt("album_count", 0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF111827);
            row.setPadding(14, 12, 14, 12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 6);
            row.setLayoutParams(lp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            textCol.setLayoutParams(tlp);

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextColor(0xFFffffff);
            tvName.setTextSize(15);
            tvName.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvName);

            TextView tvCount = new TextView(this);
            tvCount.setText(songsCount + " tracks" + (albumCount > 0 ? " • " + albumCount + " albums" : ""));
            tvCount.setTextColor(0xFF8892b0);
            tvCount.setTextSize(12);
            textCol.addView(tvCount);

            row.addView(textCol);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentLocalFilterArtist = name;
                    currentLocalFilterAlbum = null;
                    switchLocalLibrarySubTab(0);
                }
            });

            localLibraryItemsContainer.addView(row);
        }
    }

    private void loadLocalAlbums() {
        if (api == null) return;
        api.get("/api/indexer/albums", new ApiClient.Callback() {
            @Override
            public void onSuccess(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONArray albums = json.optJSONArray("albums");
                            renderLocalAlbumsList(albums);
                        } catch (Exception e) {}
                    }
                });
            }
            @Override
            public void onError(String error) {}
        });
    }

    private void renderLocalAlbumsList(JSONArray albums) {
        if (localLibraryItemsContainer == null) return;
        localLibraryItemsContainer.removeAllViews();
        if (albums == null || albums.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No albums found.");
            empty.setTextColor(0xFF8892b0);
            empty.setPadding(15, 20, 15, 20);
            localLibraryItemsContainer.addView(empty);
            return;
        }

        for (int i = 0; i < albums.length(); i++) {
            final JSONObject alb = albums.optJSONObject(i);
            if (alb == null) continue;
            final String albumName = alb.optString("album", "Unknown Album");
            final String artistName = alb.optString("artist", "Unknown Artist");
            int tracks = alb.optInt("track_count", 0);
            String year = alb.optString("year", "");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF111827);
            row.setPadding(14, 12, 14, 12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 6);
            row.setLayoutParams(lp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            textCol.setLayoutParams(tlp);

            TextView tvName = new TextView(this);
            tvName.setText(albumName);
            tvName.setTextColor(0xFFffffff);
            tvName.setTextSize(15);
            tvName.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvName);

            TextView tvSub = new TextView(this);
            String sub = artistName;
            if (!year.isEmpty()) sub += " (" + year + ")";
            sub += " • " + tracks + " tracks";
            tvSub.setText(sub);
            tvSub.setTextColor(0xFF8892b0);
            tvSub.setTextSize(12);
            textCol.addView(tvSub);

            row.addView(textCol);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentLocalFilterAlbum = albumName;
                    currentLocalFilterArtist = null;
                    switchLocalLibrarySubTab(0);
                }
            });

            localLibraryItemsContainer.addView(row);
        }
    }

    private void playLocalSong(final String filePath, final String title, final String artist) {
        if (api == null || filePath == null || filePath.isEmpty()) return;

        // Mutual Pause: Pause Spotify if playing
        if (isNpPlaying) {
            sendCommand("/api/command/spotify_play_pause");
            isNpPlaying = false;
        }

        String encoded = "";
        try { encoded = java.net.URLEncoder.encode(filePath, "UTF-8"); } catch (Exception e) {}
        api.get("/api/indexer/play?path=" + encoded, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isLocalPlaying = true;
                        if (localNpTitle != null) localNpTitle.setText(title);
                        if (localNpArtist != null) localNpArtist.setText(artist);
                        if (btnLocalNpPlayPause != null) btnLocalNpPlayPause.setText("Pause");
                        showToast("Playing: " + title, true);
                    }
                });
            }
            @Override
            public void onError(String error) {
                showToast("Playback failed: " + error, false);
            }
        });
    }

    private void updateLocalPlayerUi(JSONObject player) {
        if (player == null) return;
        isLocalPlaying = player.optBoolean("playing", false);
        JSONObject track = player.optJSONObject("current_track");
        if (track != null) {
            String title = track.optString("title", "Local Track");
            String artist = track.optString("artist", "");
            if (localNpTitle != null) localNpTitle.setText(title);
            if (localNpArtist != null) localNpArtist.setText(artist);
        }
        if (btnLocalNpPlayPause != null) {
            btnLocalNpPlayPause.setText(isLocalPlaying ? "Pause" : "Play");
        }
    }
}
