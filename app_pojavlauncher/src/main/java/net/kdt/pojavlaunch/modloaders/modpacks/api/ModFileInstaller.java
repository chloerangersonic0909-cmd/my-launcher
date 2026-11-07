package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.jar.JarFile;

/** Installs one verified mod JAR into the currently selected Minecraft instance. */
public final class ModFileInstaller {
    private static final long MAX_MOD_BYTES = 512L * 1024L * 1024L;

    private ModFileInstaller() {}

    public static void installAsync(Context context, ModDetail detail, int selectedVersion) {
        final Context app = context.getApplicationContext();
        show(app, "กำลังดาวน์โหลดและตรวจสอบไฟล์ม็อด…");
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File installed = install(detail, selectedVersion);
                String message = "ติดตั้งม็อดสำเร็จ: " + installed.getName();
                ModDetail.Dependencies[] dependencies = detail.dependencies != null
                        && selectedVersion < detail.dependencies.length
                        ? detail.dependencies[selectedVersion] : null;
                int required = 0;
                if (dependencies != null) {
                    for (ModDetail.Dependencies dependency : dependencies) {
                        if (dependency != null && "required".equalsIgnoreCase(dependency.dependency_type)) required++;
                    }
                }
                if (required > 0) {
                    message += " • มี Required dependencies " + required + " รายการที่ต้องตรวจสอบเพิ่มเติม";
                }
                show(app, message);
            } catch (Exception error) {
                show(app, "ติดตั้งม็อดไม่สำเร็จ: " + safeMessage(error));
            }
        });
    }

    private static File install(ModDetail detail, int selectedVersion) throws Exception {
        if (detail == null || detail.isModpack) throw new IllegalArgumentException("รายการนี้ไม่ใช่ม็อดเดี่ยว");
        if (detail.versionUrls == null || selectedVersion < 0 || selectedVersion >= detail.versionUrls.length) {
            throw new IllegalArgumentException("ไม่ได้เลือกเวอร์ชันม็อด");
        }

        String source = detail.versionUrls[selectedVersion];
        if (source == null || !source.startsWith("https://")) {
            throw new IllegalArgumentException("ลิงก์ดาวน์โหลดไม่ปลอดภัยหรือไม่ถูกต้อง");
        }

        File gameDir = Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());
        if (gameDir == null) throw new IllegalStateException("ไม่พบโฟลเดอร์เกมของโปรไฟล์ปัจจุบัน");
        File modsDir = new File(gameDir, "mods");
        if (!modsDir.exists() && !modsDir.mkdirs()) throw new IllegalStateException("สร้างโฟลเดอร์ mods ไม่สำเร็จ");

        String fileName = Uri.parse(source).getLastPathSegment();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            fileName = safeFileName(detail.title) + "-" + selectedVersion + ".jar";
        }
        fileName = safeFileName(fileName);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) fileName += ".jar";

        File destination = uniqueFile(modsDir, fileName);
        File temporary = new File(modsDir, destination.getName() + ".part");
        if (temporary.exists()) temporary.delete();

        HttpURLConnection connection = null;
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        long total = 0;
        try {
            connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(45000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "MyLauncher/1.0 (Minecraft mod installer)");
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) throw new java.io.IOException("เซิร์ฟเวอร์ตอบกลับ HTTP " + response);
            long announced = connection.getContentLengthLong();
            if (announced > MAX_MOD_BYTES) throw new java.io.IOException("ไฟล์มีขนาดใหญ่เกิน 512 MB");

            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("ยกเลิกการดาวน์โหลด");
                    total += read;
                    if (total > MAX_MOD_BYTES) throw new java.io.IOException("ไฟล์มีขนาดใหญ่เกิน 512 MB");
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
        } catch (Exception error) {
            temporary.delete();
            throw error;
        } finally {
            if (connection != null) connection.disconnect();
        }

        String expected = detail.versionHashes != null && selectedVersion < detail.versionHashes.length
                ? detail.versionHashes[selectedVersion] : null;
        if (expected != null && !expected.trim().isEmpty()) {
            String actual = toHex(digest.digest());
            if (!actual.equalsIgnoreCase(expected.trim())) {
                temporary.delete();
                throw new java.io.IOException("SHA-1 ไม่ตรงกัน ไฟล์อาจเสียหาย");
            }
        }

        try (JarFile jar = new JarFile(temporary)) {
            if (jar.size() == 0) throw new java.io.IOException("ไฟล์ JAR ว่างเปล่า");
        } catch (Exception error) {
            temporary.delete();
            throw new java.io.IOException("ไฟล์ที่ดาวน์โหลดไม่ใช่ JAR ที่ถูกต้อง", error);
        }

        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new java.io.IOException("บันทึกไฟล์ลงโฟลเดอร์ mods ไม่สำเร็จ");
        }
        return destination;
    }

    private static File uniqueFile(File directory, String name) {
        File candidate = new File(directory, name);
        if (!candidate.exists()) return candidate;
        String base = name.substring(0, name.length() - 4);
        for (int i = 2; i < 10000; i++) {
            candidate = new File(directory, base + "-" + i + ".jar");
            if (!candidate.exists()) return candidate;
        }
        return new File(directory, base + "-" + System.currentTimeMillis() + ".jar");
    }

    private static String safeFileName(String name) {
        if (name == null) return "mod.jar";
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) return "mod.jar";
        return safe.length() > 180 ? safe.substring(0, 180) : safe;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static void show(Context context, String message) {
        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
        handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }
}
