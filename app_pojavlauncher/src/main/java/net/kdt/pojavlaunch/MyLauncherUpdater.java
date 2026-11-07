package net.kdt.pojavlaunch;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MyLauncherUpdater {
    private static final String TAG = "MyLauncherUpdater";
    private static final String RELEASE_API =
            "https://api.github.com/repos/chloerangersonic0909-cmd/my-launcher/releases/latest";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MyLauncherUpdater() {}

    public static void checkForUpdates(Context context) {
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                JSONObject release = requestJson(RELEASE_API);
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) return;

                String tag = release.optString("tag_name", "");
                String latestVersion = normalizeVersion(tag);
                PackageInfo installed = appContext.getPackageManager()
                        .getPackageInfo(appContext.getPackageName(), 0);
                String installedVersion = installed.versionName == null ? "0" : installed.versionName;

                if (compareVersions(latestVersion, installedVersion) <= 0) return;

                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) throw new IllegalStateException("Release has no APK assets");
                JSONObject apkAsset = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
                    if (name.endsWith(".apk") && !name.contains("symbols")) {
                        apkAsset = asset;
                        break;
                    }
                }
                if (apkAsset == null) throw new IllegalStateException("No APK found in this GitHub Release");

                JSONObject selectedAsset = apkAsset;
                String notes = release.optString("body", "").trim();
                MAIN.post(() -> showUpdateDialog(context, latestVersion, notes, selectedAsset));
            } catch (Exception e) {
                Log.w(TAG, "Update check failed", e);
            }
        });
    }

    private static JSONObject requestJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "MyLauncher-Android-Updater");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("GitHub API returned HTTP " + status);
            }
            StringBuilder json = new StringBuilder();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    json.append(new String(buffer, 0, count, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return new JSONObject(json.toString());
        } finally {
            connection.disconnect();
        }
    }

    private static void showUpdateDialog(Context context, String version, String notes, JSONObject asset) {
        if (!(context instanceof android.app.Activity)) return;
        android.app.Activity activity = (android.app.Activity) context;
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;

        String message = "พบ My Launcher เวอร์ชันใหม่ " + version
                + "\nเวอร์ชันปัจจุบัน: " + getInstalledVersion(context)
                + (notes.isEmpty() ? "" : "\n\nรายการเปลี่ยนแปลง:\n" + notes)
                + "\n\nระบบจะดาวน์โหลด APK และเปิดตัวติดตั้ง Android ให้คุณยืนยัน การติดตั้งจะอัปเดตแอปเดิมโดยไม่ลบข้อมูล";
        new AlertDialog.Builder(activity)
                .setTitle("มีอัปเดต My Launcher")
                .setMessage(message)
                .setNegativeButton("ไว้ภายหลัง", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("ดาวน์โหลดและติดตั้ง", (dialog, which) ->
                        downloadAndInstall(activity, asset))
                .show();
    }

    private static String getInstalledVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "ไม่ทราบ" : info.versionName;
        } catch (Exception ignored) {
            return "ไม่ทราบ";
        }
    }

    private static void downloadAndInstall(android.app.Activity activity, JSONObject asset) {
        String downloadUrl = asset.optString("browser_download_url", "");
        String digest = asset.optString("digest", "");
        if (!downloadUrl.startsWith("https://github.com/")) {
            new AlertDialog.Builder(activity).setTitle("ลิงก์ดาวน์โหลดไม่ปลอดภัย")
                    .setMessage("ยกเลิกการดาวน์โหลด เนื่องจาก URL ไม่ใช่ GitHub HTTPS")
                    .setPositiveButton("ตกลง", null).show();
            return;
        }

        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("กำลังดาวน์โหลด My Launcher");
        progress.setMessage("กำลังเชื่อมต่อ GitHub Releases…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.show();

        EXECUTOR.execute(() -> {
            File apk = null;
            try {
                File folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (folder == null) throw new IllegalStateException("Download folder is unavailable");
                if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create download folder");
                apk = new File(folder, "my-launcher-update.apk");

                HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "MyLauncher-Android-Updater");
                try {
                    int status = connection.getResponseCode();
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw new IllegalStateException("Download failed: HTTP " + status);
                    }
                    long total = connection.getContentLengthLong();
                    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                    try (InputStream input = new BufferedInputStream(connection.getInputStream());
                         FileOutputStream output = new FileOutputStream(apk)) {
                        byte[] buffer = new byte[32768];
                        long downloaded = 0;
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                            sha256.update(buffer, 0, count);
                            downloaded += count;
                            if (total > 0) {
                                final int percent = (int) Math.min(100, downloaded * 100L / total);
                                MAIN.post(() -> {
                                    if (progress.isShowing()) {
                                        progress.setProgress(percent);
                                        progress.setMessage("ดาวน์โหลดแล้ว " + percent + "%");
                                    }
                                });
                            }
                        }
                        output.flush();
                    }
                    if (apk.length() == 0) throw new IllegalStateException("Downloaded APK is empty");
                    if (digest.startsWith("sha256:")) {
                        String actual = toHex(sha256.digest());
                        String expected = digest.substring("sha256:".length()).trim().toLowerCase(Locale.ROOT);
                        if (!actual.equals(expected)) throw new SecurityException("APK SHA-256 does not match GitHub release metadata");
                    }
                } finally {
                    connection.disconnect();
                }

                PackageManager pm = activity.getPackageManager();
                PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (archive == null) throw new SecurityException("Downloaded file is not a valid APK");
                if (!activity.getPackageName().equals(archive.packageName)) {
                    throw new SecurityException("APK package name does not match this installation");
                }
                PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), 0);
                long installedCode = Build.VERSION.SDK_INT >= 28
                        ? installed.getLongVersionCode() : installed.versionCode;
                long downloadedCode = Build.VERSION.SDK_INT >= 28
                        ? archive.getLongVersionCode() : archive.versionCode;
                if (downloadedCode <= installedCode) {
                    throw new SecurityException("APK versionCode is not newer than the installed app");
                }

                File finalApk = apk;
                MAIN.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    try {
                        Uri uri = FileProvider.getUriForFile(activity,
                                activity.getPackageName() + ".updateprovider", finalApk);
                        Intent install = new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(uri, "application/vnd.android.package-archive");
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(install);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to open Android installer", e);
                        showFailure(activity, "ไม่สามารถเปิดตัวติดตั้ง Android ได้: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Update download/install preparation failed", e);
                if (apk != null) apk.delete();
                String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                MAIN.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    showFailure(activity, "ดาวน์โหลดหรือ ตรวจสอบ APK ไม่สำเร็จ\n" + error);
                });
            }
        });
    }

    private static void showFailure(Context context, String message) {
        if (!(context instanceof android.app.Activity)) return;
        android.app.Activity activity = (android.app.Activity) context;
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle("อัปเดตไม่สำเร็จ")
                .setMessage(message).setPositiveButton("ตกลง", null).show();
    }

    private static String normalizeVersion(String value) {
        return value == null ? "" : value.trim().replaceFirst("^[vV]", "");
    }

    private static int compareVersions(String left, String right) {
        String[] a = normalizeVersion(left).split("[.-]");
        String[] b = normalizeVersion(right).split("[.-]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            long av = i < a.length ? numericPart(a[i]) : 0;
            long bv = i < b.length ? numericPart(b[i]) : 0;
            if (av != bv) return Long.compare(av, bv);
        }
        return 0;
    }

    private static long numericPart(String value) {
        String digits = value.replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) return 0;
        try { return Long.parseLong(digits); } catch (NumberFormatException ignored) { return 0; }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
