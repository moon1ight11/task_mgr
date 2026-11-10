package com.example.taskmgr.service;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateChecker {

    private static final String API_URL =
        "https://api.github.com/repos/moon1ight11/task_mgr/releases/latest";

    public interface Callback {
        void onResult(UpdateInfo info); // null, если обновлений нет или ошибка
    }

    public static class UpdateInfo {
        public final String version;      // например, "v1.2.3"
        public final String apkUrl;       // прямая ссылка на APK
        public final String releaseNotes; // тело релиза

        UpdateInfo(String version, String apkUrl, String releaseNotes) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.releaseNotes = releaseNotes;
        }
    }

    public static void check(String currentVersion, Callback callback) {
        new Thread(() -> {
            UpdateInfo result = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "TaskMGR-UpdateChecker");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());

                    // Пропускаем черновики и пререлизы
                    if (!json.optBoolean("draft") && !json.optBoolean("prerelease")) {
                        String tag = json.getString("tag_name");
                        String notes = json.optString("body", "");

                        if (isNewer(tag, currentVersion)) {
                            String apkUrl = null;
                            JSONArray assets = json.getJSONArray("assets");
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject asset = assets.getJSONObject(i);
                                String name = asset.getString("name");
                                if (name.startsWith("taskmgr-") && name.endsWith(".apk")) {
                                    apkUrl = asset.getString("browser_download_url");
                                    break;
                                }
                            }
                            if (apkUrl != null) {
                                result = new UpdateInfo(tag, apkUrl, notes);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }

            final UpdateInfo finalResult = result;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalResult));
        }).start();
    }

    private static boolean isNewer(String tag, String current) {
        if (tag == null || current == null) return false;
        String remote = tag.replaceFirst("^v", "");
        String local = current.replaceFirst("^v", "");
        return !remote.equals(local);
    }
}
