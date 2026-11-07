package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;
import android.net.Uri;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exports the selected modded instance as a portable Modrinth .mrpack. */
public final class ModpackExporter {
    private static final long MAX_EXPORT_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final int MAX_FILES = 100000;

    private ModpackExporter() {}

    public static void export(Context context, Uri destination, MinecraftProfile profile, String packName)
            throws IOException {
        if (context == null || destination == null || profile == null) {
            throw new IOException("Missing export destination or Minecraft profile");
        }
        LoaderInfo loader = resolveLoader(profile.lastVersionId);
        if (loader == null) {
            throw new IOException("The selected profile must use Fabric, Forge, NeoForge or Quilt");
        }

        File gameDir = Tools.getGameDirPath(profile);
        if (gameDir == null || !gameDir.isDirectory()) {
            throw new IOException("The selected Minecraft instance folder was not found");
        }

        String safeName = sanitizeName(packName);
        JsonObject index = new JsonObject();
        index.addProperty("formatVersion", 1);
        index.addProperty("game", "minecraft");
        index.addProperty("versionId", "1");
        index.addProperty("name", safeName);
        index.addProperty("summary", "Created with My Launcher");
        JsonObject dependencies = new JsonObject();
        dependencies.addProperty("minecraft", loader.minecraftVersion);
        dependencies.addProperty(loader.dependencyKey, loader.loaderVersion);
        index.add("dependencies", dependencies);
        index.add("files", new JsonArray());

        try (OutputStream raw = context.getContentResolver().openOutputStream(destination, "w")) {
            if (raw == null) throw new IOException("Unable to create the selected export file");
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                putText(zip, "modrinth.index.json", Tools.GLOBAL_GSON.toJson(index));

                ExportCounter counter = new ExportCounter();
                String[] folders = {"mods", "config", "resourcepacks", "shaderpacks", "defaultconfigs"};
                for (String folder : folders) {
                    File source = new File(gameDir, folder);
                    if (source.isDirectory()) {
                        addDirectory(zip, source, source, "overrides/" + folder + "/", counter);
                    }
                }
                if (counter.files == 0) {
                    throw new IOException("No mods or instance configuration were found. Install mods first.");
                }
            }
        }
    }

    private static void putText(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addDirectory(ZipOutputStream zip, File root, File current, String prefix,
                                     ExportCounter counter) throws IOException {
        File[] children = current.listFiles();
        if (children == null) throw new IOException("Unable to read instance folder: " + current.getName());
        for (File child : children) {
            if (child.isDirectory()) {
                addDirectory(zip, root, child, prefix, counter);
                continue;
            }
            if (!child.isFile()) continue;
            String name = child.getName().toLowerCase(Locale.ROOT);
            if (root.getName().equals("mods") && !name.endsWith(".jar")) continue;

            String rootPath = root.getCanonicalPath() + File.separator;
            String childPath = child.getCanonicalPath();
            if (!childPath.startsWith(rootPath)) throw new IOException("Unsafe file in instance folder");
            String relative = childPath.substring(rootPath.length()).replace(File.separatorChar, '/');
            if (relative.isEmpty() || relative.startsWith("/") || relative.contains("../")) {
                throw new IOException("Invalid instance file path");
            }

            counter.files++;
            counter.bytes += child.length();
            if (counter.files > MAX_FILES || counter.bytes > MAX_EXPORT_BYTES) {
                throw new IOException("Modpack is too large to export");
            }

            zip.putNextEntry(new ZipEntry(prefix + relative));
            try (FileInputStream input = new FileInputStream(child)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
            }
            zip.closeEntry();
        }
    }

    private static LoaderInfo resolveLoader(String versionId) {
        if (versionId == null) return null;
        Matcher matcher = Pattern.compile("^fabric-loader-(.+)-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)$").matcher(versionId);
        if (matcher.matches()) return new LoaderInfo("fabric-loader", matcher.group(1), matcher.group(2));
        matcher = Pattern.compile("^quilt-loader-(.+)-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)$").matcher(versionId);
        if (matcher.matches()) return new LoaderInfo("quilt-loader", matcher.group(1), matcher.group(2));
        matcher = Pattern.compile("^(.+)-forge-(.+)$").matcher(versionId);
        if (matcher.matches()) return new LoaderInfo("forge", matcher.group(2), matcher.group(1));
        matcher = Pattern.compile("^neoforge-([0-9]+)\\.([0-9]+)\\.(.+)$").matcher(versionId);
        if (matcher.matches()) return new LoaderInfo("neoforge", versionId.substring("neoforge-".length()),
                "1." + matcher.group(1) + "." + matcher.group(2));
        return null;
    }

    private static String sanitizeName(String value) {
        if (value == null || value.trim().isEmpty()) return "My Launcher Modpack";
        String safe = value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private static final class LoaderInfo {
        final String dependencyKey;
        final String loaderVersion;
        final String minecraftVersion;

        LoaderInfo(String dependencyKey, String loaderVersion, String minecraftVersion) {
            this.dependencyKey = dependencyKey;
            this.loaderVersion = loaderVersion;
            this.minecraftVersion = minecraftVersion;
        }
    }

    private static final class ExportCounter {
        int files;
        long bytes;
    }
}
