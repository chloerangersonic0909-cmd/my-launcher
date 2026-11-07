package net.kdt.pojavlaunch.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VersionInstallerFragment extends Fragment {
    public static final String TAG = "VersionInstallerFragment";
    private static final String[] FILTERS = {"release", "snapshot", "old_beta", "old_alpha"};
    private static final String[] LABELS = {"Release", "Snapshot", "Old Beta", "Old Alpha"};
    private final List<JMinecraftVersionList.Version> versions = new ArrayList<>();
    private LinearLayout list;
    private EditText search;
    private TextView status;
    private String selectedType = "release";

    public VersionInstallerFragment() { super(); }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        int white = Color.WHITE, muted = Color.WHITE;
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(5, 5, 12));
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(24));
        scroll.addView(root);

        TextView back = text("‹   BACK TO HOME", 12, white, true);
        back.setPadding(0, dp(6), 0, dp(18));
        back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        root.addView(back);

        root.addView(text("VERSION INSTALLER", 23, white, true));
        TextView subtitle = text("Choose a Minecraft version for your next profile", 12, muted, false);
        LinearLayout.LayoutParams sub = match(); sub.topMargin = dp(5); root.addView(subtitle, sub);

        search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setTextColor(white);
        search.setHintTextColor(Color.rgb(130, 130, 148));
        search.setHint("Search Minecraft versions…");
        search.setTextSize(14);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(panel("#171722", "#343044"));
        LinearLayout.LayoutParams sp = match(); sp.topMargin = dp(18); root.addView(search, sp);

        LinearLayout filters = new LinearLayout(requireContext());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(14), 0, dp(12));
        root.addView(filters, match());
        for (int i = 0; i < FILTERS.length; i++) {
            final String type = FILTERS[i];
            TextView chip = text(LABELS[i], 10, white, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(9), dp(10), dp(9), dp(10));
            chip.setBackground(panel(type.equals(selectedType) ? "#393052" : "#171722",
                    type.equals(selectedType) ? "#9A75D8" : "#343044"));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(38), 1);
            cp.setMargins(dp(2), 0, dp(2), 0);
            filters.addView(chip, cp);
            chip.setOnClickListener(v -> { selectedType = type; refreshFilters(filters); renderVersions(); });
        }

        status = text("Loading official version list…", 12, muted, false);
        root.addView(status, match());
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = match(); lp.topMargin = dp(8); root.addView(list, lp);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { renderVersions(); }
            public void afterTextChanged(Editable e) {}
        });
        loadVersions();
        return scroll;
    }

    private void loadVersions() {
        new AsyncVersionList().getVersionList(result -> {
            // AsyncVersionList invokes its listener on a worker thread.
            // All Fragment state and View operations must run on the main thread.
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null || list == null || status == null) return;
                versions.clear();
                if (result != null && result.versions != null) {
                    for (JMinecraftVersionList.Version version : result.versions) {
                        if (version != null && version.id != null) versions.add(version);
                    }
                }
                if (result == null || result.versions == null) {
                    status.setText("Unable to load versions. Check your connection and try again.");
                    list.removeAllViews();
                    return;
                }
                renderVersions();
            });
        }, false);
    }

    private void refreshFilters(LinearLayout filters) {
        for (int i = 0; i < filters.getChildCount(); i++) {
            TextView chip = (TextView) filters.getChildAt(i);
            boolean active = FILTERS[i].equals(selectedType);
            chip.setBackground(panel(active ? "#393052" : "#171722", active ? "#9A75D8" : "#343044"));
        }
    }

    private void renderVersions() {
        if (list == null || status == null) return;
        list.removeAllViews();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (JMinecraftVersionList.Version version : versions) {
            if (!selectedType.equals(version.type)) continue;
            if (!version.id.toLowerCase(Locale.ROOT).contains(query)) continue;
            count++;
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(12), dp(12), dp(12));
            card.setBackground(panel("#12121C", "#343044"));
            LinearLayout info = new LinearLayout(requireContext());
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(version.id, 15, Color.WHITE, true);
            TextView meta = text("Minecraft Java Edition  •  " + labelFor(version.type), 10,
                    Color.WHITE, false);
            info.addView(name); LinearLayout.LayoutParams mp = match(); mp.topMargin = dp(4); info.addView(meta, mp);
            card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView install = text("SELECT  ›", 11, Color.WHITE, true);
            install.setGravity(Gravity.CENTER);
            install.setPadding(dp(12), dp(9), dp(12), dp(9));
            install.setBackground(panel("#49376B", "#8B6AC5"));
            card.addView(install);
            card.setOnClickListener(v -> showInstallConfirmation(version));
            LinearLayout.LayoutParams cardParams = match(); cardParams.bottomMargin = dp(8);
            list.addView(card, cardParams);
        }
        status.setText(count == 0 ? "No versions found in this category." : count + " versions available");
    }

    private String labelFor(String type) {
        for (int i = 0; i < FILTERS.length; i++) if (FILTERS[i].equals(type)) return LABELS[i];
        return "Minecraft";
    }

    private void showInstallConfirmation(JMinecraftVersionList.Version version) {
        if (!isAdded() || version == null) return;
        ScrollView detailsScroll = new ScrollView(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(8));
        detailsScroll.addView(content);

        ImageView minecraftImage = new ImageView(requireContext());
        minecraftImage.setImageResource(R.mipmap.ic_launcher_round);
        minecraftImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        imageParams.bottomMargin = dp(12);
        content.addView(minecraftImage, imageParams);

        TextView heading = text("Minecraft Java Edition", 18, Color.WHITE, true);
        heading.setGravity(Gravity.CENTER);
        content.addView(heading, match());
        TextView versionName = text(version.id, 16, Color.rgb(190, 160, 255), true);
        versionName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = match();
        versionParams.topMargin = dp(5);
        versionParams.bottomMargin = dp(16);
        content.addView(versionName, versionParams);

        addDetailRow(content, "ประเภทเวอร์ชัน", labelFor(version.type));
        addDetailRow(content, "รหัสเวอร์ชัน", version.id);
        addDetailRow(content, "วันที่เผยแพร่", formatReleaseTime(version.releaseTime));
        addDetailRow(content, "Java ที่ต้องใช้", version.javaVersion == null
                ? "ไม่ได้ระบุ" : "Java " + version.javaVersion.majorVersion);
        addDetailRow(content, "เวอร์ชัน Launcher ขั้นต่ำ", version.minimumLauncherVersion > 0
                ? String.valueOf(version.minimumLauncherVersion) : "ไม่ได้ระบุ");
        addDetailRow(content, "ขนาดไฟล์เกม", version.downloads != null && version.downloads.get("client") != null
                ? formatBytes(version.downloads.get("client").size) : "ดูได้เมื่อดาวน์โหลด");
        addDetailRow(content, "Asset Index", version.assetIndex == null ? "ไม่ได้ระบุ" : version.assetIndex.id);
        addDetailRow(content, "Main Class", version.mainClass == null ? "ไม่ได้ระบุ" : version.mainClass);

        TextView note = text("เมื่อกดยืนยัน ระบบจะเปิดหน้าตั้งค่าโปรไฟล์พร้อมเลือกเวอร์ชันนี้ไว้ให้ จากนั้นบันทึกโปรไฟล์เพื่อดำเนินการดาวน์โหลดและติดตั้งผ่านขั้นตอนของ Launcher",
                12, Color.LTGRAY, false);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteParams = match();
        noteParams.topMargin = dp(14);
        content.addView(note, noteParams);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("ยืนยันการติดตั้ง")
                .setView(detailsScroll)
                .setNegativeButton("ยกเลิก", (d, which) -> d.dismiss())
                .setPositiveButton("ดำเนินการติดตั้ง", (d, which) -> openProfileForVersion(version.id))
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(125, 92, 190));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY);
        });
        dialog.show();
    }

    private void addDetailRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(11), dp(8), dp(11), dp(8));
        row.setBackground(panel("#171722", "#343044"));
        row.addView(text(label, 10, Color.LTGRAY, false), match());
        TextView detail = text(value == null || value.trim().isEmpty() ? "ไม่ได้ระบุ" : value,
                13, Color.WHITE, true);
        LinearLayout.LayoutParams detailParams = match();
        detailParams.topMargin = dp(3);
        row.addView(detail, detailParams);
        LinearLayout.LayoutParams rowParams = match();
        rowParams.bottomMargin = dp(6);
        parent.addView(row, rowParams);
    }

    private String formatReleaseTime(String value) {
        if (value == null || value.trim().isEmpty()) return "ไม่ได้ระบุ";
        try {
            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            java.util.Date date = input.parse(value);
            if (date != null) return new java.text.SimpleDateFormat(
                    "dd MMM yyyy, HH:mm", Locale.getDefault()).format(date);
        } catch (Exception ignored) { }
        return value;
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "ไม่ทราบขนาด";
        if (bytes >= 1024L * 1024L) return String.format(Locale.getDefault(),
                "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0);
    }

    private void openProfileForVersion(String version) {
        Bundle args = new Bundle();
        args.putString(ProfileEditorFragment.ARG_PRESELECT_VERSION, version);
        Tools.swapFragment(requireActivity(), ProfileEditorFragment.class, ProfileEditorFragment.TAG, args);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private GradientDrawable panel(String fill, String stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(fill)); d.setCornerRadius(dp(14));
        d.setStroke(dp(1), Color.parseColor(stroke)); return d;
    }
    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
