package net.kdt.pojavlaunch.prefs.screens;

import static net.kdt.pojavlaunch.Architecture.is32BitsDevice;
import static net.kdt.pojavlaunch.Tools.getTotalDeviceMemory;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class LauncherPreferenceJavaFragment extends LauncherPreferenceFragment {
    private MultiRTConfigDialog mDialogScreen;
    private SwitchPreference mSwitchAutoJRE;
    private final ActivityResultLauncher<Object> mVmInstallLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("xz"), (data)->{
                if(data != null) Tools.installRuntimeFromUri(getContext(), data);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSwitchAutoJRE = findPreference("disable_autojre_select");
        mSwitchAutoJRE.setSummary("Stops automatic selection of which runtime to use in \"" + getString(R.string.main_install_jar_file) + "\"");

    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        int ramAllocation = LauncherPreferences.PREF_RAM_ALLOCATION;
        // Triggers a write for some reason
        addPreferencesFromResource(R.xml.pref_java);

        CustomSeekBarPreference memorySeekbar = requirePreference("allocation",
                CustomSeekBarPreference.class);

        int maxRAM;
        int deviceRam = getTotalDeviceMemory(memorySeekbar.getContext());

        if(is32BitsDevice() || deviceRam < 2048) maxRAM = Math.min(1024, deviceRam);
        else maxRAM = deviceRam - (deviceRam < 3064 ? 800 : 1024); //To have a minimum for the device to breathe

        memorySeekbar.setMaxKeepIncrement(maxRAM);
        memorySeekbar.setSuffix(" MB");

        SwitchPreference autoRamPreference = findPreference("auto_ram_allocation");
        boolean autoRamEnabled = LauncherPreferences.PREF_AUTO_RAM_ALLOCATION;
        if (autoRamPreference != null) {
            autoRamPreference.setChecked(autoRamEnabled);
            if (autoRamEnabled) {
                ramAllocation = Math.min(maxRAM,
                        LauncherPreferences.getRecommendedRAMAllocation(memorySeekbar.getContext()));
            }
            memorySeekbar.setEnabled(!autoRamEnabled);
            memorySeekbar.setSummary(autoRamEnabled
                    ? getString(R.string.mylauncher_auto_ram_active, ramAllocation)
                    : getString(R.string.mcl_memory_allocation_subtitle));
            autoRamPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                LauncherPreferences.PREF_AUTO_RAM_ALLOCATION = enabled;
                if (enabled) {
                    int recommended = Math.min(maxRAM,
                            LauncherPreferences.getRecommendedRAMAllocation(memorySeekbar.getContext()));
                    memorySeekbar.setValue(recommended);
                    memorySeekbar.setSummary(getString(R.string.mylauncher_auto_ram_active, recommended));
                } else {
                    memorySeekbar.setSummary(getString(R.string.mcl_memory_allocation_subtitle));
                }
                memorySeekbar.setEnabled(!enabled);
                return true;
            });
        }
        memorySeekbar.setValue(ramAllocation);

        EditTextPreference editJVMArgs = findPreference("javaArgs");
        if (editJVMArgs != null) {
            editJVMArgs.setOnBindEditTextListener(TextView::setSingleLine);
        }

        requirePreference("install_jre").setOnPreferenceClickListener(preference->{
            openMultiRTDialog();
            return true;
        });
    }

    private void openMultiRTDialog() {
        if (mDialogScreen == null) {
            mDialogScreen = new MultiRTConfigDialog();
            mDialogScreen.prepare(getContext(), mVmInstallLauncher);
        }
        mDialogScreen.show();
    }
}
