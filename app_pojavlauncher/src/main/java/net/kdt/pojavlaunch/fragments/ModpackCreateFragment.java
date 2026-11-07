package net.kdt.pojavlaunch.fragments;

import android.app.Activity;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.MineButton;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackExporter;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

public class ModpackCreateFragment extends Fragment {
    public static final String TAG = "ModpackCreateFragment";
    private String pendingExportName = "My Launcher Modpack";

    private final ActivityResultLauncher<String> exportModpackLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/x-modrinth-modpack"), uri -> {
                if (uri == null || !isAdded()) return;
                if (ProgressLayout.hasProcesses()) {
                    Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                    return;
                }

                final Context appContext = requireContext().getApplicationContext();
                final MinecraftProfile profile;
                try {
                    profile = LauncherProfiles.getCurrentProfile();
                } catch (RuntimeException error) {
                    Toast.makeText(appContext, "ไม่พบโปรไฟล์ Minecraft ที่เลือก", Toast.LENGTH_LONG).show();
                    return;
                }
                final String exportName = pendingExportName;
                PojavApplication.sExecutorService.execute(() -> {
                    String message;
                    try {
                        ModpackExporter.export(appContext, uri, profile, exportName);
                        message = "สร้าง Modpack สำเร็จ";
                    } catch (Exception error) {
                        String detail = error.getMessage();
                        message = "สร้าง Modpack ไม่สำเร็จ: " +
                                (detail == null ? error.getClass().getSimpleName() : detail);
                    }
                    final String resultMessage = message;
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(appContext, resultMessage, Toast.LENGTH_LONG).show());
                });
            });
    public ModpackCreateFragment() {
        super(R.layout.fragment_create_modpack_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.button_export_modpack).setOnClickListener(v -> promptExportName());
        view.findViewById(R.id.button_browse_modpacks).setOnClickListener(v -> {
            tryInstall(SearchModFragment.class, SearchModFragment.TAG);
        });
        view.findViewById(R.id.button_import_mod_jar).setOnClickListener(v -> {
            Activity launcherActivity = requireActivity();
            if (!(launcherActivity instanceof LauncherActivity)) {
                throw new IllegalStateException("Cannot import a mod without LauncherActivity");
            }
            if (ProgressLayout.hasProcesses()) {
                Toast.makeText(launcherActivity, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                return;
            }
            ((LauncherActivity) launcherActivity).modJarImportLauncher.launch(null);
        });
        view.findViewById(R.id.button_import_modpack).setOnClickListener(v -> {
            Activity launcheractivity = requireActivity();
            if (!(launcheractivity instanceof LauncherActivity))
                    throw new IllegalStateException("Cannot import modpack without LauncherActivity");
            if(ProgressLayout.hasProcesses()){
                Toast.makeText(launcheractivity, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                return;
            }
            ((LauncherActivity) launcheractivity).modpackImportLauncher.launch(null);
        });;
    }

    private void promptExportName() {
        if (ProgressLayout.hasProcesses()) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        EditText nameInput = new EditText(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setText("My Launcher Modpack");
        new AlertDialog.Builder(requireContext())
                .setTitle("สร้าง Modpack ของฉัน")
                .setMessage("ส่งออก Mod และไฟล์ตั้งค่าของโปรไฟล์ปัจจุบันเป็นไฟล์ .mrpack")
                .setView(nameInput)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("เลือกที่บันทึก", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    pendingExportName = name.isEmpty() ? "My Launcher Modpack" : name;
                    String fileName = pendingExportName.replaceAll("[\\\\/:*?\"<>|]", "_");
                    exportModpackLauncher.launch(fileName + ".mrpack");
                })
                .show();
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        if(!hasOnlineProfile()){
            hasNoOnlineProfileDialog(requireActivity());
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }
}
