package com.mediacentre.kiosk;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {
    
    public interface Callback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    private String baseUrl;
    
    public ApiClient(String ip) {
        this.baseUrl = "http://" + ip + ":8080";
    }
    
    public void get(final String endpoint, final Callback callback) {
        new AsyncTask<Void, Void, String[]>() {
            @Override
            protected String[] doInBackground(Void... params) {
                HttpURLConnection conn = null;
                InputStream is = null;
                BufferedReader reader = null;
                try {
                    String finalEndpoint = endpoint + (endpoint.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                    URL url = new URL(baseUrl + finalEndpoint);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setUseCaches(false);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);
                    
                    int code = conn.getResponseCode();
                    is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    if (is == null) {
                        return new String[]{"error", "No response body from server"};
                    }
                    reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    
                    String body = sb.toString();
                    if (code >= 200 && code < 300) {
                        return new String[]{"ok", body};
                    } else {
                        return new String[]{"error", body.isEmpty() ? "HTTP error " + code : body};
                    }
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null || msg.trim().isEmpty()) {
                        msg = "Network error: " + e.getClass().getSimpleName();
                    }
                    return new String[]{"error", msg};
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                    }
                    if (is != null) {
                        try { is.close(); } catch (Exception ignored) {}
                    }
                    if (conn != null) {
                        try { conn.disconnect(); } catch (Exception ignored) {}
                    }
                }
            }
            
            @Override
            protected void onPostExecute(String[] result) {
                if (callback != null) {
                    if (result != null && result.length >= 2 && "ok".equals(result[0])) {
                        callback.onSuccess(result[1] != null ? result[1] : "");
                    } else {
                        String errMsg = (result != null && result.length >= 2 && result[1] != null) ? result[1] : "Network error";
                        callback.onError(errMsg);
                    }
                }
            }
        }.execute();
    }
}

