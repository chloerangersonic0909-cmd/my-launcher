package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.dialogOnUiThread;
import static net.kdt.pojavlaunch.Tools.hasMods;
import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.runOnUiThread;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.services.GameService;
import net.kdt.pojavlaunch.fragments.DownloadCenterFragment;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.LWJGL3ifyUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.List;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private android.widget.TextView mBackgroundStatus;
    private Button mStopGameButton;

    private final ActivityResultLauncher<String> mNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (isAdded()) {
                    Toast.makeText(requireContext(), granted
                            ? R.string.mylauncher_notification_permission_granted
                            : R.string.mylauncher_notification_permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button downloadCenterButton = view.findViewById(R.id.download_center_button);
        Button installVersionButton = view.findViewById(R.id.install_version_button);
        Button securityCenterButton = view.findViewById(R.id.security_center_button);
        if (securityCenterButton != null) {
            securityCenterButton.setOnClickListener(v -> showSecurityCenter());
        }
        if (downloadCenterButton != null) {
            downloadCenterButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), DownloadCenterFragment.class, DownloadCenterFragment.TAG, null));
        }
        if (installVersionButton != null) {
            installVersionButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                    VersionInstallerFragment.class, VersionInstallerFragment.TAG, null));
        }
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        View settingsButton = view.findViewById(R.id.mylauncher_settings_button);
        if (settingsButton != null) settingsButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class, "LauncherPreferenceFragment", null));

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        View addAccountButton = view.findViewById(R.id.add_account_button);
        if (addAccountButton != null) addAccountButton.setOnClickListener(v -> showAddAccountDialog());
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mBackgroundStatus = view.findViewById(R.id.background_status_text);
        mStopGameButton = view.findViewById(R.id.stop_game_button);
        Button backgroundPermissionButton = view.findViewById(R.id.background_permission_button);

        if (backgroundPermissionButton != null) {
            backgroundPermissionButton.setOnClickListener(v -> requestBackgroundNotificationPermission());
        }
        if (mStopGameButton != null) {
            mStopGameButton.setOnClickListener(v -> confirmStopGame());
        }
        View hiddenAccess = view.findViewById(R.id.mylauncher_brand_title);
        if (hiddenAccess != null) hiddenAccess.setOnLongClickListener(v -> { showHiddenAdvancedAccess(); return true; });

        applyCustomBackground(view);
        refreshBackgroundControls();

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        if (hasOnlineProfile()) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            mInstallJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        } else mInstallJarButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity()));
        mEditProfileButton.setOnClickListener(v -> showAddAccountDialog());

        mPlayButton.setOnClickListener(v -> {
            if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
                AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.sodium_warning_title)
                        .setMessage(R.string.sodium_warning_message)
                        .setNeutralButton(R.string.delete_sodium, (d,w)-> {
                            Tools.deleteSodiumMods();
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        })
                        .create();
                sodiumWarningDialog.show();
            } else ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> {
            if (Tools.isDemoProfile(v.getContext())){ // Say a different message when on demo profile since they might see the hidden demo folder
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);

        });


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }


    /**
     * Performs a local, best-effort security review of this installed launcher.
     * This is a configuration check, not an antivirus or malware scan.
     */
    /** Hidden access to advanced game settings. The passcode is never displayed. */
    private void showAddAccountDialog() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mylauncher_add_account)
                .setItems(new String[]{"Microsoft account", "Offline account"}, (dialog, which) -> {
                    if (which == 0) {
                        Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class,
                                MicrosoftLoginFragment.TAG, null);
                    } else {
                        Tools.swapFragment(requireActivity(), LocalLoginFragment.class,
                                LocalLoginFragment.TAG, null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHiddenAdvancedAccess() {
        if (!isAdded()) return;
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Access code");
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(requireContext())
                .setTitle("Advanced access")
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("ยืนยัน", (dialog, which) -> {
                    String entered = input.getText() == null ? "" : input.getText().toString();
                    if ("Bb00u".equals(entered)) {
                        Tools.swapFragment(requireActivity(), AdvancedGameSettingsFragment.class,
                                AdvancedGameSettingsFragment.TAG, null);
                    } else {
                        Toast.makeText(requireContext(), "รหัสไม่ถูกต้อง", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showSecurityCenter() {
        if (!isAdded()) return;
        StringBuilder report = new StringBuilder();
        int warnings = 0;
        try {
            String packageName = requireContext().getPackageName();
            PackageManager pm = requireContext().getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);

            report.append("🛡️ MY LAUNCHER SECURITY CENTER\n");
            report.append("เวอร์ชัน: ").append(packageInfo.versionName == null ? "ไม่ทราบ" : packageInfo.versionName).append("\n");
            report.append("แพ็กเกจ: ").append(packageName).append("\n\n");

            boolean debuggable = (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            report.append(debuggable ? "⚠️ " : "✅ ")
                    .append("โหมด Debug: ").append(debuggable ? "เปิดอยู่ (เหมาะกับทดสอบเท่านั้น)" : "ปิดอยู่").append("\n");
            if (debuggable) warnings++;

            boolean backupAllowed = (appInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP) != 0;
            report.append(backupAllowed ? "ℹ️ " : "✅ ")
                    .append("การสำรองข้อมูลแอป: ").append(backupAllowed ? "อนุญาตโดยการตั้งค่าแอป" : "ปิดอยู่").append("\n");
            if (backupAllowed) warnings++;

            String installer = null;
            try {
                installer = pm.getInstallerPackageName(packageName);
            } catch (Exception ignored) {
                // Installer attribution is optional and may be unavailable.
            }
            report.append(installer == null ? "ℹ️ " : "✅ ")
                    .append("แหล่งติดตั้ง: ").append(installer == null ? "Android ไม่ระบุแหล่งติดตั้ง" : installer).append("\n");
            if (installer == null) warnings++;

            report.append("\nสิทธิ์ที่แอปร้องขอ:\n");
            String[] permissions = packageInfo.requestedPermissions;
            if (permissions == null || permissions.length == 0) {
                report.append("ไม่มีรายการสิทธิ์ที่ร้องขอ\n");
            } else {
                for (String permission : permissions) {
                    String shortName = permission.substring(permission.lastIndexOf('.') + 1);
                    boolean granted = pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED;
                    report.append(granted ? "• " : "• ")
                            .append(shortName)
                            .append(granted ? " — อนุญาตแล้ว" : " — ยังไม่ได้อนุญาต")
                            .append("\n");
                }
            }

            report.append("\nผลตรวจเบื้องต้น: ")
                    .append(warnings == 0 ? "ไม่พบการตั้งค่าที่ควรแจ้งเตือน" : "พบ ").append(warnings)
                    .append(" รายการที่ควรตรวจสอบ");
            report.append("\n\nหมายเหตุ: นี่เป็นการตรวจการตั้งค่าพื้นฐานของแอป ไม่ใช่การสแกนไวรัส และไม่สามารถรับรองความปลอดภัย 100% ได้.");
        } catch (Exception e) {
            report.append("ไม่สามารถตรวจสอบข้อมูลแอปได้: ").append(e.getClass().getSimpleName());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("🛡️ Security Center")
                .setMessage(report.toString())
                .setPositiveButton("ปิด", null)
                .show();
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
        if (getView() != null) applyCustomBackground(getView());
        refreshBackgroundControls();
    }

    private void applyCustomBackground(View root) {
        if (!isAdded() || root == null) return;
        String savedUri = requireContext().getSharedPreferences(
                "mylauncher_customization", android.content.Context.MODE_PRIVATE)
                .getString("background_uri", null);
        if (savedUri == null) {
            root.setBackgroundResource(R.color.background_app);
            return;
        }
        try (java.io.InputStream input = requireContext().getContentResolver()
                .openInputStream(android.net.Uri.parse(savedUri))) {
            Drawable image = Drawable.createFromStream(input, null);
            if (image == null) throw new java.io.IOException("Unsupported background image");
            image.setColorFilter(new PorterDuffColorFilter(0x99FFFFFF, PorterDuff.Mode.SRC_ATOP));
            root.setBackground(image);
        } catch (Exception e) {
            requireContext().getSharedPreferences(
                    "mylauncher_customization", android.content.Context.MODE_PRIVATE)
                    .edit().remove("background_uri").apply();
            root.setBackgroundResource(R.color.background_app);
            Toast.makeText(requireContext(), "ไม่พบภาพพื้นหลังเดิม คืนค่าเริ่มต้นแล้ว", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestBackgroundNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), R.string.mylauncher_notification_permission_not_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), R.string.mylauncher_notification_permission_granted, Toast.LENGTH_LONG).show();
        } else {
            mNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private boolean isGameProcessRunning() {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        String gameProcessName = requireContext().getPackageName() + ":game";
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) return false;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (gameProcessName.equals(process.processName)) return true;
        }
        return false;
    }

    private void refreshBackgroundControls() {
        if (mBackgroundStatus == null || mStopGameButton == null || !isAdded()) return;
        boolean running = isGameProcessRunning();
        mBackgroundStatus.setText(running
                ? R.string.mylauncher_game_running_background
                : R.string.mylauncher_game_not_running);
        mStopGameButton.setEnabled(running);
        mStopGameButton.setAlpha(running ? 1.0f : 0.45f);
    }

    private void confirmStopGame() {
        if (!isGameProcessRunning()) {
            refreshBackgroundControls();
            Toast.makeText(requireContext(), R.string.mylauncher_game_not_running, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mylauncher_stop_game_title)
                .setMessage(R.string.mylauncher_stop_game_warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mylauncher_stop_game, (dialog, which) -> {
                    Intent stopIntent = new Intent(requireContext(), GameService.class);
                    stopIntent.putExtra("kill", true);
                    requireContext().startService(stopIntent);
                    Toast.makeText(requireContext(), R.string.mylauncher_stop_game_sent, Toast.LENGTH_LONG).show();
                    mStopGameButton.postDelayed(this::refreshBackgroundControls, 700);
                })
                .show();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
