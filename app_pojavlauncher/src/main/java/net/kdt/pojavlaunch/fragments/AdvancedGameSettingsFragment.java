package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;

/**
 * Hidden advanced settings hub. Uses the launcher's existing settings screens
 * so changes continue to use the original persistent preference storage.
 */
public class AdvancedGameSettingsFragment extends Fragment {
    public static final String TAG = "AdvancedGameSettings";

    public AdvancedGameSettingsFragment() { super(); }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        android.content.Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setBackgroundColor(0xFFF4FFF1);

        Button back = button("← กลับหน้าหลัก");
        back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        root.addView(back, params());

        TextView title = new TextView(context);
        title.setText("ADVANCED GAME SETTINGS");
        title.setTextColor(0xFF111111);
        title.setTextSize(21);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(24), 0, dp(6));
        root.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("ศูนย์รวมการตั้งค่าระดับสูงของ My Launcher");
        subtitle.setTextColor(0xFF52604F);
        subtitle.setTextSize(13);
        root.addView(subtitle, params());

        addSection(root, "RENDERER & PERFORMANCE");
        addCard(root, "⚙️  การตั้งค่าตัว Launcher", "Renderer, ความละเอียด, RAM และ JVM", v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class, "LauncherPreferenceFragment", null));
        addSection(root, "CONTROL SYSTEM");
        addCard(root, "🎮  ปรับแต่งปุ่มควบคุม", "จัดการปุ่มสัมผัสและรูปแบบการควบคุม", v ->
                startActivity(new android.content.Intent(requireContext(), CustomControlsActivity.class)));
        addSection(root, "NOTICE");
        TextView note = new TextView(context);
        note.setText("การตั้งค่าจะถูกบันทึกผ่านระบบเดิมของ Launcher เพื่อรักษาความเข้ากันได้กับระบบปัจจุบัน");
        note.setTextColor(0xFF52604F);
        note.setTextSize(12);
        note.setPadding(dp(12), dp(12), dp(12), dp(12));
        note.setBackgroundColor(0xFFFFFFFF);
        root.addView(note, params());
        return root;
    }

    private void addSection(LinearLayout root, String label) {
        TextView text = new TextView(requireContext());
        text.setText(label);
        text.setTextColor(0xFF159900);
        text.setTextSize(11);
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setPadding(0, dp(20), 0, dp(8));
        root.addView(text);
    }

    private void addCard(LinearLayout root, String title, String detail, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFFFFFFFF);
        TextView heading = new TextView(requireContext());
        heading.setText(title);
        heading.setTextColor(0xFF111111);
        heading.setTextSize(15);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(heading);
        TextView description = new TextView(requireContext());
        description.setText(detail);
        description.setTextColor(0xFF555555);
        description.setTextSize(12);
        card.addView(description);
        card.setOnClickListener(click);
        LinearLayout.LayoutParams p = params();
        p.topMargin = dp(6);
        root.addView(card, p);
    }

    private Button button(String text) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
