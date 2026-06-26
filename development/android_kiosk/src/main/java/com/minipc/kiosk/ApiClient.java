package com.minipc.kiosk;

import android.os.AsyncTask;
import java.io.BufferedReader;
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
                try {
                    String finalEndpoint = endpoint + (endpoint.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                    URL url = new URL(baseUrl + finalEndpoint);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setUseCaches(false);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    int code = conn.getResponseCode();
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    
                    if (code >= 200 && code < 300) {
                        return new String[]{"ok", sb.toString()};
                    } else {
                        return new String[]{"error", sb.toString()};
                    }
                } catch (Exception e) {
                    return new String[]{"error", e.getMessage()};
                }
            }
            
            @Override
            protected void onPostExecute(String[] result) {
                if (callback != null) {
                    if ("ok".equals(result[0])) {
                        callback.onSuccess(result[1]);
                    } else {
                        callback.onError(result[1]);
                    }
                }
            }
        }.execute();
    }
}
