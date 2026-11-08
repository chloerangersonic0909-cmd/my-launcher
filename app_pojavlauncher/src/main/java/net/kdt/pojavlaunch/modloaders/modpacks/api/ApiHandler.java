package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.ArrayMap;
import android.util.Log;

import com.google.gson.Gson;

import net.kdt.pojavlaunch.Tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public class ApiHandler {
    private static final String TAG = "ApiHandler";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String USER_AGENT = "MyLauncher/0.0.0.2 (Android)";

    public final String baseUrl;
    public final Map<String, String> additionalHeaders;

    public ApiHandler(String url) {
        baseUrl = url;
        additionalHeaders = null;
    }

    public ApiHandler(String url, String apiKey) {
        baseUrl = url;
        additionalHeaders = new ArrayMap<>();
        if (apiKey != null && !apiKey.trim().isEmpty() && !"DUMMY".equals(apiKey)) {
            additionalHeaders.put("x-api-key", apiKey.trim());
        }
    }

    public <T> T get(String endpoint, Class<T> tClass) {
        return getFullUrl(additionalHeaders, baseUrl + "/" + endpoint, tClass);
    }

    public <T> T get(String endpoint, HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(additionalHeaders, baseUrl + "/" + endpoint, query, tClass);
    }

    public <T> T post(String endpoint, T body, Class<T> tClass) {
        return postFullUrl(additionalHeaders, baseUrl + "/" + endpoint, body, tClass);
    }

    public <T> T post(String endpoint, HashMap<String, Object> query, T body, Class<T> tClass) {
        return postFullUrl(additionalHeaders, baseUrl + "/" + endpoint, query, body, tClass);
    }

    public static String getRaw(String url) {
        return getRaw(null, url);
    }

    public static String getRaw(Map<String, String> headers, String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            addHeaders(connection, headers);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                Log.w(TAG, "GET request failed with HTTP " + status + " for " + safeUrl(url));
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return Tools.read(inputStream);
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "GET request failed for " + safeUrl(url), e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static String postRaw(String url, String body) {
        return postRaw(null, url, body);
    }

    public static String postRaw(Map<String, String> headers, String url, String body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            addHeaders(connection, headers);
            connection.setDoOutput(true);

            byte[] input = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(input.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(input);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                Log.w(TAG, "POST request failed with HTTP " + status + " for " + safeUrl(url));
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return Tools.read(inputStream);
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "POST request failed for " + safeUrl(url), e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String safeUrl(String url) {
        if (url == null) return "(unknown URL)";
        int queryStart = url.indexOf('?');
        return queryStart >= 0 ? url.substring(0, queryStart) : url;
    }

    private static void addHeaders(HttpURLConnection connection, Map<String, String> headers) {
        if (headers == null) return;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String parseQueries(HashMap<String, Object> query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder params = new StringBuilder("?");
        for (String param : query.keySet()) {
            if (param == null) continue;
            String value = Objects.toString(query.get(param), "");
            if (params.length() > 1) params.append("&");
            params.append(urlEncodeUTF8(param))
                    .append("=")
                    .append(urlEncodeUTF8(value));
        }
        return params.length() > 1 ? params.toString() : "";
    }

    public static <T> T getFullUrl(String url, Class<T> tClass) {
        return getFullUrl(null, url, tClass);
    }

    public static <T> T getFullUrl(String url, HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(null, url, query, tClass);
    }

    public static <T> T postFullUrl(String url, T body, Class<T> tClass) {
        return postFullUrl(null, url, body, tClass);
    }

    public static <T> T postFullUrl(String url, HashMap<String, Object> query, T body, Class<T> tClass) {
        return postFullUrl(null, url, query, body, tClass);
    }

    public static <T> T getFullUrl(Map<String, String> headers, String url, Class<T> tClass) {
        String response = getRaw(headers, url);
        return response == null || response.isEmpty() ? null : new Gson().fromJson(response, tClass);
    }

    public static <T> T getFullUrl(Map<String, String> headers, String url,
                                   HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(headers, url + parseQueries(query), tClass);
    }

    public static <T> T postFullUrl(Map<String, String> headers, String url, T body, Class<T> tClass) {
        String response = postRaw(headers, url, body == null ? null : body.toString());
        return response == null || response.isEmpty() ? null : new Gson().fromJson(response, tClass);
    }

    public static <T> T postFullUrl(Map<String, String> headers, String url,
                                    HashMap<String, Object> query, T body, Class<T> tClass) {
        return postFullUrl(headers, url + parseQueries(query), body, tClass);
    }

    private static String urlEncodeUTF8(String input) {
        try {
            return URLEncoder.encode(input, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 is required", e);
        }
    }
}
