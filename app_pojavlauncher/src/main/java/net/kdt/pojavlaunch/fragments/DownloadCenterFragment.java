package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.jar.JarFile;

public class DownloadCenterFragment extends Fragment {
    public static final String TAG = "DownloadCenterFragment";
    private enum ImportType { MOD, SHADER, RESOURCE_PACK, WORLD }

    private ImportType pendingType;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button cancelButton;
    private android.widget.EditText modpackSearchInput;
    private LinearLayout modpackResults;
    private CommonApi modpackApi;
    private Future<?> activeTask;
    private volatile boolean importing;
    private volatile Thread importThread;
    private Context appContext;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFileSelected);

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        appContext = context.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 12, 23));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(18));
        scroll.addView(root);

        Button back = makeButton("←  กลับหน้าแรก", "#1D2A42");
        back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        root.addView(back, matchWrap());

        TextView title = new TextView(context);
        title.setText("MY LAUNCHER  /  DOWNLOAD CENTER");
        title.setTextColor(Color.rgb(112, 204, 255));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("นำเข้าไฟล์เสริมจากอุปกรณ์ และติดตั้งลงในโปรไฟล์ Minecraft ที่เลือก");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(13);
        root.addView(subtitle, matchWrap());

        TextView selected = new TextView(context);
        selected.setText("ปลายทาง: โปรไฟล์ Minecraft ปัจจุบัน");
        selected.setTextColor(Color.WHITE);
        selected.setTextSize(12);
        selected.setPadding(dp(12), dp(10), dp(12), dp(10));
        selected.setBackground(panelBackground("#14243A"));
        LinearLayout.LayoutParams selectedParams = matchWrap();
        selectedParams.topMargin = dp(12);
        root.addView(selected, selectedParams);

        addSection(root, "ONLINE MODPACK CATALOG");
        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        modpackSearchInput = new android.widget.EditText(context);
        modpackSearchInput.setSingleLine(true);
        modpackSearchInput.setHint("ค้นหา Modpack บน Modrinth / CurseForge");
        modpackSearchInput.setHintTextColor(Color.GRAY);
        modpackSearchInput.setTextColor(Color.WHITE);
        modpackSearchInput.setTextSize(13);
        modpackSearchInput.setPadding(dp(10), 0, dp(10), 0);
        modpackSearchInput.setBackground(panelBackground("#111D31"));
        searchRow.addView(modpackSearchInput, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button searchButton = makeButton("ค้นหา", "#126A9B");
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(104), dp(48));
        searchButtonParams.leftMargin = dp(8);
        searchRow.addView(searchButton, searchButtonParams);
        root.addView(searchRow, matchWrap());
        searchButton.setOnClickListener(v -> searchOnlineModpacks());

        modpackResults = new LinearLayout(context);
        modpackResults.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams resultsParams = matchWrap();
        resultsParams.topMargin = dp(8);
        root.addView(modpackResults, resultsParams);

        addSection(root, "MOD CONTENT");
        addCard(root, "🧩  Mods (.jar)", "นำไฟล์ Mod JAR ไปไว้ในโฟลเดอร์ mods", () -> chooseFile(ImportType.MOD));
        addCard(root, "✨  Shaders (.zip)", "นำ Shader Pack ไปไว้ในโฟลเดอร์ shaderpacks", () -> chooseFile(ImportType.SHADER));
        addCard(root, "🎨  Resource Packs (.zip)", "ตรวจ pack.mcmeta ก่อนติดตั้ง", () -> chooseFile(ImportType.RESOURCE_PACK));
        addSection(root, "GAME CONTENT");
        addCard(root, "🌍  Worlds (.zip)", "แตกไฟล์อย่างปลอดภัยและตรวจหา level.dat", () -> chooseFile(ImportType.WORLD));
        addCard(root, "📦  Modpacks (.mrpack / .zip)", "ใช้ตัวติดตั้ง Modpack เดิมของ Amethyst", () -> {
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).modpackImportLauncher.launch(null);
            }
        });

        addSection(root, "IMPORT STATUS");
        statusText = new TextView(context);
        statusText.setText("พร้อมนำเข้าไฟล์");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(13);
        statusText.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.addView(statusText, matchWrap());

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));

        cancelButton = makeButton("หยุดการนำเข้า", "#7A2637");
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(v -> cancelImport());
        LinearLayout.LayoutParams cancelParams = matchWrap();
        cancelParams.topMargin = dp(8);
        root.addView(cancelButton, cancelParams);

        TextView note = new TextView(context);
        note.setText("หมายเหตุ: เลือกไฟล์จากแหล่งที่เชื่อถือได้เท่านั้น ตรวจสอบเวอร์ชัน Minecraft และ Mod Loader ให้ตรงกันก่อนเล่น");
        note.setTextColor(Color.rgb(160, 174, 195));
        note.setTextSize(11);
        note.setPadding(0, dp(14), 0, dp(8));
        root.addView(note);
        return scroll;
    }

    private void searchOnlineModpacks() {
        String query = modpackSearchInput == null ? "" : modpackSearchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "พิมพ์ชื่อ Modpack ที่ต้องการค้นหาก่อน", Toast.LENGTH_SHORT).show();
            return;
        }
        if (modpackResults != null) {
            modpackResults.removeAllViews();
            TextView loading = new TextView(requireContext());
            loading.setText("กำลังค้นหา Modpack จาก Modrinth และ CurseForge…");
            loading.setTextColor(Color.LTGRAY);
            modpackResults.addView(loading);
        }
        final String targetVersion;
        try {
            String selected = LauncherProfiles.getCurrentProfile().lastVersionId;
            targetVersion = resolveMinecraftVersion(selected);
        } catch (Exception e) {
            postStatus("ยังไม่มีโปรไฟล์ Minecraft ที่เลือก", 0, false);
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (modpackApi == null) modpackApi = new CommonApi(appContext.getString(R.string.curseforge_api_key));
                SearchFilters filters = new SearchFilters();
                filters.isModpack = true;
                filters.name = query;
                filters.mcVersion = targetVersion;
                SearchResult result = modpackApi.searchMod(filters);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> renderModpackResults(result, targetVersion));
            } catch (Exception e) {
                postStatus("ค้นหาไม่สำเร็จ: " + e.getMessage(), 0, false);
            }
        });
    }

    private String resolveMinecraftVersion(String versionId) {
        if (versionId == null || versionId.isEmpty()) return null;
        String normalized = AsyncMinecraftDownloader.normalizeVersionId(versionId);
        if (normalized == null) return null;

        int forgeIndex = normalized.indexOf("-forge-");
        if (forgeIndex > 0) return normalized.substring(0, forgeIndex);

        if (normalized.startsWith("fabric-loader-") || normalized.startsWith("quilt-loader-")) {
            int lastDash = normalized.lastIndexOf('-');
            if (lastDash > 0 && lastDash < normalized.length() - 1) {
                return normalized.substring(lastDash + 1);
            }
        }

        if (normalized.startsWith("neoforge-")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^neoforge-(\\d+)\\.(\\d+)\\..*$")
                    .matcher(normalized);
            if (matcher.matches()) return "1." + matcher.group(1) + "." + matcher.group(2);
        }
        return normalized;
    }

    private void renderModpackResults(SearchResult result, String targetVersion) {
        if (modpackResults == null) return;
        modpackResults.removeAllViews();
        if (result == null || result.results == null || result.results.length == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText("ไม่พบ Modpack หรือบริการค้นหาไม่พร้อมใช้งาน");
            empty.setTextColor(Color.LTGRAY);
            modpackResults.addView(empty);
            return;
        }
        for (ModItem item : result.results) {
            addCard(modpackResults, "📦  " + item.title,
                    item.description == null ? "Modpack" : item.description,
                    v -> loadModpackVersions(item, targetVersion));
        }
    }

    private void loadModpackVersions(ModItem item, String targetVersion) {
        setStatus("กำลังตรวจสอบเวอร์ชันของ " + item.title, 0, true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                ModDetail detail = modpackApi.getModDetails(item);
                if (detail == null || detail.versionNames == null || detail.versionNames.length == 0) {
                    postStatus("ไม่พบเวอร์ชันของ Modpack นี้", 0, false);
                    return;
                }
                java.util.ArrayList<Integer> compatible = new java.util.ArrayList<>();
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                for (int i = 0; i < detail.versionNames.length; i++) {
                    String mc = detail.mcVersionNames[i];
                    if (targetVersion == null || targetVersion.equals(mc)) {
                        compatible.add(i);
                        labels.add(detail.versionNames[i] + "  •  Minecraft " + mc);
                    }
                }
                if (compatible.isEmpty()) {
                    postStatus("ไม่พบเวอร์ชันที่ตรงกับ Minecraft " + targetVersion, 0, false);
                    return;
                }
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                        .setTitle("เลือกเวอร์ชัน • " + item.title)
                        .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                            int selectedIndex = compatible.get(which);
                            setStatus("กำลังติดตั้ง " + item.title + "… ดูความคืบหน้าได้จากแถบงานและการแจ้งเตือน", 0, true);
                            modpackApi.handleInstallation(requireContext(), detail, selectedIndex);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
            } catch (Exception e) {
                postStatus("อ่านรายละเอียด Modpack ไม่สำเร็จ: " + e.getMessage(), 0, false);
            }
        });
    }

    private void chooseFile(ImportType type) {
        if (importing) {
            Toast.makeText(requireContext(), "กำลังนำเข้าไฟล์อยู่", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingType = type;
        String[] mimeTypes;
        if (type == ImportType.MOD) {
            mimeTypes = new String[]{"application/java-archive", "application/octet-stream", "*/*"};
        } else {
            mimeTypes = new String[]{"application/zip", "application/octet-stream", "*/*"};
        }
        filePicker.launch(mimeTypes);
    }

    private void onFileSelected(Uri uri) {
        if (uri == null || pendingType == null || !isAdded()) return;
        final ImportType type = pendingType;
        final String name = queryDisplayName(uri);
        final String lower = name.toLowerCase(Locale.ROOT);
        if (type == ImportType.MOD && !lower.endsWith(".jar")) {
            setStatus("ไฟล์ Mod ต้องเป็น .jar", 0, false);
            return;
        }
        if (type != ImportType.MOD && !lower.endsWith(".zip")) {
            setStatus("ประเภทนี้ต้องใช้ไฟล์ .zip", 0, false);
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("ยืนยันการนำเข้า")
                .setMessage("ไฟล์: " + name + "\n\nนำเข้าไปยังโปรไฟล์ Minecraft ปัจจุบันหรือไม่? ควรใช้ไฟล์จากแหล่งที่เชื่อถือได้")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("นำเข้า", (dialog, which) -> startImport(uri, name, type))
                .show();
    }

    private void startImport(Uri uri, String displayName, ImportType type) {
        if (importing) return;
        importing = true;
        setStatus("กำลังเตรียมไฟล์: " + displayName, 0, true);
        activeTask = PojavApplication.sExecutorService.submit(() -> {
            importThread = Thread.currentThread();
            try {
                File gameDir = Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());
                File result;
                switch (type) {
                    case MOD:
                        validateModJar(uri);
                        result = copyInto(uri, displayName, new File(gameDir, "mods"), 512L * 1024L * 1024L);
                        break;
                    case SHADER:
                        validateShaderZip(uri);
                        result = copyInto(uri, displayName, new File(gameDir, "shaderpacks"), 1024L * 1024L * 1024L);
                        break;
                    case RESOURCE_PACK:
                        validateResourcePack(uri);
                        result = copyInto(uri, displayName, new File(gameDir, "resourcepacks"), 1024L * 1024L * 1024L);
                        break;
                    case WORLD:
                        result = importWorld(uri, displayName, new File(gameDir, "saves"));
                        break;
                    default:
                        throw new IOException("Unsupported import type");
                }
                postStatus("ติดตั้งสำเร็จ: " + result.getName(), 100, false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postStatus("หยุดการนำเข้าแล้ว", 0, false);
            } catch (Exception e) {
                postStatus("นำเข้าไม่สำเร็จ: " + e.getMessage(), 0, false);
            } finally {
                importing = false;
                importThread = null;
                activeTask = null;
                postCancelEnabled(false);
            }
        });
    }

    private File copyInto(Uri uri, String name, File directory, long maxBytes) throws IOException, InterruptedException {
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("สร้างโฟลเดอร์ปลายทางไม่ได้");
        String safeName = safeName(name);
        File destination = uniqueFile(directory, safeName);
        File temporary = new File(directory, destination.getName() + ".part");
        long copied = 0;
        long expectedSize = queryFileSize(uri);
        try (InputStream input = appContext.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temporary)) {
            if (input == null) throw new IOException("อ่านไฟล์ต้นทางไม่ได้");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                copied += read;
                if (copied > maxBytes) throw new IOException("ไฟล์มีขนาดเกินขีดจำกัด");
                output.write(buffer, 0, read);
                int progress = expectedSize > 0 ? (int) Math.min(99, copied * 100 / expectedSize) : 0;
                postStatus("กำลังคัดลอก " + name + " (" + humanSize(copied) + ")", progress, true);
            }
            output.flush();
        } catch (IOException | InterruptedException e) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw e;
        }
        if (!temporary.renameTo(destination)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("ย้ายไฟล์ไปยังโฟลเดอร์เกมไม่สำเร็จ");
        }
        return destination;
    }

    private void validateModJar(Uri uri) throws IOException {
        File temp = copyToCache(uri, "check_mod_" + UUID.randomUUID() + ".jar", 512L * 1024L * 1024L);
        try (JarFile jar = new JarFile(temp)) {
            if (!jar.entries().hasMoreElements()) throw new IOException("ไฟล์ JAR ว่างเปล่า");
        } catch (java.util.zip.ZipException e) {
            throw new IOException("ไฟล์นี้ไม่ใช่ JAR ที่สมบูรณ์", e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void validateResourcePack(Uri uri) throws IOException {
        File temp = copyToCache(uri, "check_resource_" + UUID.randomUUID() + ".zip", 1024L * 1024L * 1024L);
        try (ZipFile zip = new ZipFile(temp)) {
            if (zip.getEntry("pack.mcmeta") == null) throw new IOException("ไม่พบ pack.mcmeta ใน ZIP นี้");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void validateShaderZip(Uri uri) throws IOException {
        File temp = copyToCache(uri, "check_shader_" + UUID.randomUUID() + ".zip", 1024L * 1024L * 1024L);
        try (ZipFile zip = new ZipFile(temp)) {
            boolean found = false;
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName().toLowerCase(Locale.ROOT);
                if (path.startsWith("shaders/") || path.contains("/shaders/")) {
                    found = true;
                    break;
                }
            }
            if (!found) throw new IOException("ไม่พบโฟลเดอร์ shaders ใน ZIP นี้");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private File copyToCache(Uri uri, String name, long maxBytes) throws IOException {
        File target = new File(Tools.DIR_CACHE, name);
        try (InputStream input = appContext.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("อ่านไฟล์ต้นทางไม่ได้");
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("ยกเลิกแล้ว");
                total += read;
                if (total > maxBytes) throw new IOException("ไฟล์มีขนาดเกินขีดจำกัด");
                output.write(buffer, 0, read);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }
        return target;
    }

    private File importWorld(Uri uri, String name, File savesDir) throws IOException, InterruptedException {
        if (!savesDir.exists() && !savesDir.mkdirs()) throw new IOException("สร้างโฟลเดอร์ saves ไม่ได้");
        File tempDir = new File(savesDir, ".import_" + UUID.randomUUID());
        if (!tempDir.mkdirs()) throw new IOException("สร้างโฟลเดอร์ชั่วคราวไม่ได้");
        long expanded = 0;
        int entriesCount = 0;
        InputStream source = appContext.getContentResolver().openInputStream(uri);
        if (source == null) throw new IOException("อ่าน ZIP ไม่ได้");
        try (InputStream raw = source; ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (++entriesCount > 10000) throw new IOException("ZIP มีจำนวนไฟล์มากเกินไป");
                String entryName = entry.getName().replace('\\', '/');
                if (entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*")) throw new IOException("พบ path ที่ไม่ปลอดภัย");
                File out = new File(tempDir, entryName).getCanonicalFile();
                String rootPath = tempDir.getCanonicalPath() + File.separator;
                if (!out.getPath().equals(tempDir.getCanonicalPath()) && !out.getPath().startsWith(rootPath)) {
                    throw new IOException("ZIP พยายามเขียนออกนอกโฟลเดอร์โลก");
                }
                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("สร้างโฟลเดอร์ในโลกไม่ได้");
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("สร้างโฟลเดอร์ไม่ได้");
                    try (FileOutputStream output = new FileOutputStream(out)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            expanded += read;
                            if (expanded > 1024L * 1024L * 1024L) throw new IOException("ขนาดหลังแตกไฟล์เกิน 1 GB");
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException | InterruptedException e) {
            deleteRecursively(tempDir);
            throw e;
        }

        File worldRoot = findWorldRoot(tempDir);
        if (worldRoot == null) {
            deleteRecursively(tempDir);
            throw new IOException("ไม่พบ level.dat จึงไม่ใช่โฟลเดอร์โลก Minecraft Java ที่รองรับ");
        }
        String baseName = safeName(name.replaceFirst("(?i)\\.zip$", ""));
        File destination = uniqueFile(savesDir, baseName);
        if (!worldRoot.renameTo(destination)) {
            deleteRecursively(tempDir);
            throw new IOException("ย้ายโลกไปยัง saves ไม่สำเร็จ");
        }
        if (!worldRoot.equals(tempDir)) deleteRecursively(tempDir);
        return destination;
    }

    private File findWorldRoot(File extracted) {
        if (new File(extracted, "level.dat").isFile()) return extracted;
        File[] children = extracted.listFiles(File::isDirectory);
        if (children != null && children.length == 1 && new File(children[0], "level.dat").isFile()) return children[0];
        return null;
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private String queryDisplayName(Uri uri) {
        String name = "imported_file";
        try (Cursor cursor = appContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return name == null ? "imported_file" : name;
    }

    private File uniqueFile(File directory, String name) {
        File candidate = new File(directory, name);
        if (!candidate.exists()) return candidate;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int index = 2;
        do {
            candidate = new File(directory, base + "_" + index++ + ext);
        } while (candidate.exists());
        return candidate;
    }

    private String safeName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "imported_file";
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private long queryFileSize(Uri uri) {
        try (Cursor cursor = appContext.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(0);
        } catch (Exception ignored) {}
        return -1;
    }

    private String humanSize(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void cancelImport() {
        Future<?> task = activeTask;
        if (task != null) task.cancel(true);
        importing = false;
        setStatus("ส่งคำสั่งหยุดแล้ว", 0, false);
    }

    private void postStatus(String text, int progress, boolean busy) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> setStatus(text, progress, busy));
    }

    private void postCancelEnabled(boolean enabled) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (cancelButton != null) cancelButton.setEnabled(enabled);
        });
    }

    private void setStatus(String text, int progress, boolean busy) {
        if (statusText != null) statusText.setText(text);
        if (progressBar != null) progressBar.setProgress(progress);
        if (cancelButton != null) cancelButton.setEnabled(busy);
    }

    private void addSection(LinearLayout root, String label) {
        TextView section = new TextView(requireContext());
        section.setText(label);
        section.setTextColor(Color.rgb(91, 188, 255));
        section.setTextSize(11);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setPadding(0, dp(18), 0, dp(6));
        root.addView(section, matchWrap());
    }

    private void addCard(LinearLayout root, String title, String description, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(panelBackground("#111D31"));
        TextView heading = new TextView(requireContext());
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(15);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(heading, matchWrap());
        TextView detail = new TextView(requireContext());
        detail.setText(description);
        detail.setTextColor(Color.LTGRAY);
        detail.setTextSize(11);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(3);
        card.addView(detail, detailParams);
        card.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(7);
        root.addView(card, params);
    }

    private Button makeButton(String text, String color) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setBackground(panelBackground(color));
        return button;
    }

    private GradientDrawable panelBackground(String color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), Color.rgb(35, 65, 96));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
