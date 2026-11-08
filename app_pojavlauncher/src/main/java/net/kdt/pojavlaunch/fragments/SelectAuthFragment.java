package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";
    private static final String ELY_AUTH_URL = "https://authserver.ely.by/auth/authenticate";
    private static final String AUTHLIB_METADATA_URL = "https://authlib-injector.yushi.moe/artifact/latest.json";

    public SelectAuthFragment() {
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button microsoftButton = view.findViewById(R.id.button_microsoft_authentication);
        Button localButton = view.findViewById(R.id.button_local_authentication);
        Button elyByButton = view.findViewById(R.id.button_elyby_authentication);

        microsoftButton.setOnClickListener(v -> Tools.swapFragment(
                requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        localButton.setOnClickListener(v -> Tools.swapFragment(
                requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null));
        elyByButton.setOnClickListener(v -> showElyByLoginDialog());
    }

    private void showElyByLoginDialog() {
        Context context = requireContext();
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, 0);

        EditText username = new EditText(context);
        username.setSingleLine(true);
        username.setHint("ชื่อผู้ใช้หรืออีเมล Ely.by");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        form.addView(username, new LinearLayout.LayoutParams(-1, -2));

        EditText password = new EditText(context);
        password.setSingleLine(true);
        password.setHint("รหัสผ่าน");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password, new LinearLayout.LayoutParams(-1, -2));

        EditText twoFactor = new EditText(context);
        twoFactor.setSingleLine(true);
        twoFactor.setHint("รหัสยืนยัน 2FA (ถ้ามี)");
        twoFactor.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(twoFactor, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("เข้าสู่ระบบ Ely.by")
                .setMessage("ใช้บัญชี Ely.by ที่มีอยู่ได้เลย หากยังไม่มีบัญชีให้เลือก “สมัครบัญชี”")
                .setView(form)
                .setNegativeButton("ยกเลิก", null)
                .setNeutralButton("สมัครบัญชี", (d, which) ->
                        Tools.openURL(requireActivity(), "https://ely.by/"))
                .setPositiveButton("เข้าสู่ระบบ", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String login = username.getText().toString().trim();
                    String pass = password.getText().toString();
                    String otp = twoFactor.getText().toString().trim();
                    if (login.isEmpty() || pass.isEmpty()) {
                        Toast.makeText(context, "กรอกชื่อผู้ใช้/อีเมลและรหัสผ่านก่อน", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Button submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    submit.setEnabled(false);
                    submit.setText("กำลังเข้าสู่ระบบ…");
                    authenticateElyBy(login, pass, otp, dialog, submit);
                }));
        dialog.show();
    }

    private void authenticateElyBy(String login, String password, String otp,
                                   AlertDialog dialog, Button submit) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File injector = ensureAuthlibInjector();
                String clientToken = UUID.randomUUID().toString().replace("-", "");
                JSONObject request = new JSONObject();
                request.put("agent", new JSONObject().put("name", "Minecraft").put("version", 1));
                request.put("username", login);
                request.put("password", otp.isEmpty() ? password : password + ":" + otp);
                request.put("clientToken", clientToken);
                request.put("requestUser", true);

                JSONObject response = postJson(ELY_AUTH_URL, request);
                JSONObject profile = response.optJSONObject("selectedProfile");
                if (profile == null) {
                    JSONArray profiles = response.optJSONArray("availableProfiles");
                    if (profiles != null && profiles.length() > 0) profile = profiles.getJSONObject(0);
                }
                if (profile == null || !profile.has("id") || !profile.has("name")) {
                    throw new IllegalStateException("บัญชี Ely.by นี้ยังไม่มีโปรไฟล์ Minecraft ให้เลือก กรุณาสร้างโปรไฟล์ในบัญชี Ely.by ก่อน");
                }

                String playerName = profile.getString("name");
                MinecraftAccount account = new MinecraftAccount();
                account.username = playerName;
                account.profileId = formatUuid(profile.getString("id"));
                account.accessToken = response.getString("accessToken");
                account.clientToken = response.optString("clientToken", clientToken);
                account.isElyBy = true;
                account.save();
                PojavProfile.setCurrentProfile(requireContext(), playerName);

                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "เข้าสู่ระบบ Ely.by สำเร็จ", Toast.LENGTH_LONG).show();
                    Tools.backToMainMenu(requireActivity());
                });
            } catch (Exception error) {
                android.util.Log.e("ElyByLogin", "Ely.by authentication failed", error);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    submit.setEnabled(true);
                    submit.setText("เข้าสู่ระบบ");
                    String message = error.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = "เข้าสู่ระบบไม่สำเร็จ ตรวจสอบข้อมูลแล้วลองอีกครั้ง";
                    } else if (message.toLowerCase(Locale.ROOT).contains("token")) {
                        message = "รหัสยืนยัน 2FA ไม่ถูกต้องหรือหมดอายุ กรุณาตรวจสอบแล้วลองใหม่";
                    } else if (message.toLowerCase(Locale.ROOT).contains("invalid") ||
                            message.toLowerCase(Locale.ROOT).contains("credentials")) {
                        message = "ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง";
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static JSONObject postJson(String endpoint, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "MyLauncher/0.0.0.2 (Android)");
        try {
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(body);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readStream(stream);
            if (status < 200 || status >= 300) {
                String reason = "เข้าสู่ระบบ Ely.by ไม่สำเร็จ";
                try {
                    JSONObject error = new JSONObject(response);
                    reason = error.optString("errorMessage", error.optString("error", reason));
                } catch (Exception ignored) {}
                throw new IllegalStateException(reason);
            }
            return new JSONObject(response);
        } finally {
            connection.disconnect();
        }
    }

    private static File ensureAuthlibInjector() throws Exception {
        File target = new File(Tools.DIR_DATA, "authlib-injector.jar");
        if (target.isFile() && target.length() > 100000) return target;

        JSONObject metadata = new JSONObject(readUrl(AUTHLIB_METADATA_URL));
        String downloadUrl = metadata.getString("download_url");
        String expectedHash = metadata.getJSONObject("checksums").getString("sha256")
                .toLowerCase(Locale.ROOT);

        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } finally {
            connection.disconnect();
        }

        String actualHash = sha256(temporary);
        if (!expectedHash.equals(actualHash)) {
            temporary.delete();
            throw new IllegalStateException("ตรวจสอบความถูกต้องของไฟล์ Authlib Injector ไม่ผ่าน");
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IllegalStateException("ไม่สามารถแทนที่ไฟล์ Authlib Injector ได้");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IllegalStateException("บันทึกไฟล์ Authlib Injector ไม่สำเร็จ");
        }
        return target;
    }

    private static String readUrl(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "MyLauncher/0.0.0.2 (Android)");
        try {
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IllegalStateException("ดาวน์โหลดข้อมูล Authlib Injector ไม่สำเร็จ");
            }
            return readStream(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStreamCompat(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static String formatUuid(String uuid) {
        String value = uuid.replace("-", "");
        if (value.length() != 32) return uuid;
        return value.substring(0, 8) + "-" + value.substring(8, 12) + "-" +
                value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20);
    }

    private static final class FileInputStreamCompat extends java.io.FileInputStream {
        FileInputStreamCompat(File file) throws java.io.FileNotFoundException { super(file); }
    }
}
