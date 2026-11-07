package net.kdt.pojavlaunch.maintenance;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Safe, profile-scoped backup/restore primitives for My Launcher.
 *
 * Backups intentionally exclude account databases, authentication tokens,
 * runtime binaries and Minecraft libraries. Restore merges selected game data;
 * it never deletes files that are not present in the archive.
 */
public final class BackupManager {
    private static final String TAG = "MyLauncherBackup";
    private static final String MANIFEST = "mylauncher-backup.txt";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_ENTRIES = 50000;
    private static final long MAX_UNCOMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L;

    private static final Set<String> ALLOWED_ROOTS = new HashSet<>(Arrays.asList(
            "saves", "mods", "resourcepacks", "shaderpacks", "config",
            "defaultconfigs", "options.txt", "optionsof.txt", "servers.dat"
    ));

    private BackupManager() {}

    public static File getBackupDirectory(Context context) throws IOException {
        File base = context.getExternalFilesDir("backups");
        if (base == null) base = new File(context.getFilesDir(), "backups");
        if (!base.exists() && !base.mkdirs()) {
            throw new IOException("Cannot create backup directory");
        }
        return base;
    }

    public static File createBackup(Context context, File gameDirectory) throws IOException {
        if (gameDirectory == null || !gameDirectory.isDirectory()) {
            throw new IOException("Minecraft profile directory is unavailable");
        }
        File outputDirectory = getBackupDirectory(context);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File destination = new File(outputDirectory, "mylauncher-backup-" + stamp + ".zip");
        File temporary = new File(outputDirectory, destination.getName() + ".tmp");

        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(temporary)))) {
            ZipEntry manifest = new ZipEntry(MANIFEST);
            zip.putNextEntry(manifest);
            String description = "My Launcher backup\nformat=1\ncreated=" + stamp
                    + "\ncontents=saves,mods,resourcepacks,shaderpacks,config,settings\n";
            zip.write(description.getBytes("UTF-8"));
            zip.closeEntry();

            for (String rootName : ALLOWED_ROOTS) {
                File source = new File(gameDirectory, rootName);
                if (source.exists()) addToZip(zip, gameDirectory, source);
            }
        } catch (IOException error) {
            temporary.delete();
            throw error;
        }

        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IOException("Cannot replace existing backup");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("Cannot finalize backup archive");
        }
        return destination;
    }

    private static void addToZip(ZipOutputStream zip, File root, File source) throws IOException {
        String relative = root.toURI().relativize(source.toURI()).getPath();
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot read " + relative);
            for (File child : children) addToZip(zip, root, child);
            return;
        }
        if (!source.isFile()) return;

        zip.putNextEntry(new ZipEntry(relative));
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    /**
     * Restores a My Launcher archive into the supplied profile directory.
     * The archive must contain this launcher's manifest and only whitelisted paths.
     */
    public static int restoreBackup(Context context, File archive, File gameDirectory)
            throws IOException {
        if (archive == null || !archive.isFile()) throw new IOException("Backup archive not found");
        if (gameDirectory == null) throw new IOException("Minecraft profile directory is unavailable");

        File staging = new File(context.getCacheDir(), "mylauncher-restore-" + System.nanoTime());
        if (!staging.mkdirs()) throw new IOException("Cannot create restore staging directory");

        boolean manifestFound = false;
        int restoredFiles = 0;
        long totalBytes = 0;
        int entries = 0;

        try {
            try (ZipInputStream zip = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(archive)))) {
                ZipEntry entry;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new IOException("Backup contains too many entries");
                    String name = entry.getName();
                    if (MANIFEST.equals(name)) {
                        manifestFound = true;
                        drain(zip, buffer);
                        zip.closeEntry();
                        continue;
                    }

                    File target = resolveSafeEntry(staging, name);
                    String normalized = name.replace('\\', '/');
                    String root = normalized.contains("/")
                            ? normalized.substring(0, normalized.indexOf('/')) : normalized;
                    if (!ALLOWED_ROOTS.contains(root)) {
                        throw new IOException("Backup contains an unsupported path: " + name);
                    }

                    if (entry.isDirectory()) {
                        if (!target.exists() && !target.mkdirs()) throw new IOException("Cannot stage directory");
                    } else {
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw new IOException("Cannot create staging directory");
                        }
                        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                totalBytes += read;
                                if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                    throw new IOException("Backup exceeds the 4 GiB restore limit");
                                }
                                output.write(buffer, 0, read);
                            }
                        }
                        restoredFiles++;
                    }
                    zip.closeEntry();
                }
            }

            if (!manifestFound) throw new IOException("This is not a My Launcher backup");

            File[] staged = staging.listFiles();
            if (staged != null) {
                for (File item : staged) copyInto(item, new File(gameDirectory, item.getName()));
            }
            return restoredFiles;
        } finally {
            deleteRecursively(staging);
        }
    }

    private static File resolveSafeEntry(File root, String entryName) throws IOException {
        if (entryName == null || entryName.length() == 0 || entryName.startsWith("/")
                || entryName.indexOf('\\') >= 0) {
            throw new IOException("Invalid backup path");
        }
        File result = new File(root, entryName);
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!result.getCanonicalPath().startsWith(rootPath)) {
            throw new IOException("Backup path escapes restore directory");
        }
        return result;
    }

    private static void drain(InputStream input, byte[] buffer) throws IOException {
        while (input.read(buffer) != -1) { /* consume manifest entry */ }
    }

    private static void copyInto(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Cannot create " + destination.getName());
            }
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot read staged data");
            for (File child : children) copyInto(child, new File(destination, child.getName()));
            return;
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create restore destination");
        }
        File temporary = new File(destination.getParentFile(), destination.getName() + ".mylauncher-tmp");
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IOException("Cannot replace " + destination.getName());
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("Cannot finalize restored file");
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
