package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.jar.JarFile;

/** Safely imports a local mod JAR into the currently selected modded instance. */
public final class ModFileImporter {
    private static final long MAX_MOD_BYTES = 512L * 1024L * 1024L;

    private ModFileImporter() {}

    public static File importJar(Context context, Uri source) throws IOException {
        if (context == null || source == null) throw new IOException("No mod file was selected");

        MinecraftProfile profile;
        try {
            profile = LauncherProfiles.getCurrentProfile();
        } catch (RuntimeException error) {
            throw new IOException("No Minecraft profile is selected", error);
        }
        String versionId = profile.lastVersionId == null ? "" : profile.lastVersionId.toLowerCase(Locale.ROOT);
        if (!(versionId.contains("fabric-loader-") || versionId.contains("forge-")
                || versionId.startsWith("neoforge-") || versionId.contains("quilt-loader-"))) {
            throw new IOException("Install Fabric, Forge, NeoForge or Quilt before importing mods");
        }

        File gameDir = Tools.getGameDirPath(profile);
        if (gameDir == null) throw new IOException("Minecraft instance folder was not found");
        File modsDir = new File(gameDir, "mods");
        if (!modsDir.exists() && !modsDir.mkdirs()) throw new IOException("Unable to create mods folder");

        String displayName = getDisplayName(context, source);
        String fileName = sanitizeJarName(displayName);
        File destination = uniqueFile(modsDir, fileName);
        File temporary = new File(modsDir, "." + destination.getName() + ".part");
        if (temporary.exists() && !temporary.delete()) throw new IOException("Unable to clear previous temporary file");

        long total = 0L;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("Unable to open selected mod file");
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_MOD_BYTES) throw new IOException("Mod file exceeds the 512 MB limit");
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }

            try (JarFile jar = new JarFile(temporary)) {
                if (jar.size() == 0) throw new IOException("The selected JAR is empty");
            } catch (IOException invalidJar) {
                throw new IOException("The selected file is not a valid JAR mod", invalidJar);
            }

            if (!temporary.renameTo(destination)) throw new IOException("Unable to save mod in mods folder");
            return destination;
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static String getDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        String segment = uri.getLastPathSegment();
        return segment == null ? "imported-mod.jar" : segment;
    }

    private static String sanitizeJarName(String name) throws IOException {
        if (name == null) throw new IOException("Mod filename is missing");
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Please select a .jar mod file");
        }
        if (safe.length() > 160) safe = safe.substring(0, 156) + ".jar";
        if (safe.equals(".jar")) throw new IOException("Invalid mod filename");
        return safe;
    }

    private static File uniqueFile(File directory, String name) throws IOException {
        File candidate = new File(directory, name);
        if (!candidate.exists()) return candidate;
        String base = name.substring(0, name.length() - 4);
        for (int i = 2; i < 10000; i++) {
            candidate = new File(directory, base + "-" + i + ".jar");
            if (!candidate.exists()) return candidate;
        }
        throw new IOException("Too many mods with the same filename");
    }
}
