package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Connects the Minecraft gameplay Activity to Android's game and thermal APIs.
 *
 * Platform API classes introduced after Android 5.0 are isolated in nested
 * bridge classes so older devices can load this class safely.
 */
public final class GamePerformanceManager {
    private static final String TAG = "GamePerformance";

    private final Activity activity;
    private final PowerManager powerManager;
    private GameApiBridge gameApiBridge;
    private ThermalApiBridge thermalApiBridge;
    private boolean severeThermalWarningShown;

    public GamePerformanceManager(Activity activity) {
        this.activity = activity;
        this.powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
    }

    /** Call while Minecraft's rendering Activity is in the foreground. */
    public void onResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (gameApiBridge == null) gameApiBridge = new GameApiBridge(activity);
            gameApiBridge.onResume();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            if (thermalApiBridge == null) {
                thermalApiBridge = new ThermalApiBridge(powerManager, this::onThermalStatusChanged);
            }
            thermalApiBridge.start();
        }
    }

    /** Mark gameplay as inactive when the Minecraft Activity leaves the foreground. */
    public void onPause() {
        if (gameApiBridge != null) gameApiBridge.onPause();
        if (thermalApiBridge != null) thermalApiBridge.stop();
    }

    public void destroy() {
        onPause();
    }

    private void onThermalStatusChanged(int status) {
        Log.i(TAG, "Thermal status changed: " + thermalStatusName(status));

        if (status >= PowerManager.THERMAL_STATUS_SEVERE && !severeThermalWarningShown) {
            severeThermalWarningShown = true;
            Toast.makeText(activity,
                    "อุณหภูมิเครื่องสูงมาก ระบบ Android อาจลดประสิทธิภาพเพื่อป้องกันความร้อน ลองลด Render Distance หรือพักเครื่อง",
                    Toast.LENGTH_LONG).show();
        } else if (status < PowerManager.THERMAL_STATUS_MODERATE) {
            severeThermalWarningShown = false;
        }
    }

    private static String thermalStatusName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "NONE";
            case PowerManager.THERMAL_STATUS_LIGHT: return "LIGHT";
            case PowerManager.THERMAL_STATUS_MODERATE: return "MODERATE";
            case PowerManager.THERMAL_STATUS_SEVERE: return "SEVERE";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "CRITICAL";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "EMERGENCY";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "SHUTDOWN";
            default: return "UNKNOWN";
        }
    }

    private interface ThermalStatusCallback {
        void onChanged(int status);
    }

    /** Android 10+ Thermal API bridge. */
    private static final class ThermalApiBridge {
        private final PowerManager powerManager;
        private final PowerManager.OnThermalStatusChangedListener listener;
        private boolean registered;

        ThermalApiBridge(PowerManager powerManager, ThermalStatusCallback callback) {
            this.powerManager = powerManager;
            this.listener = callback::onChanged;
        }

        void start() {
            if (registered) return;
            try {
                powerManager.addThermalStatusListener(listener);
                registered = true;
                listener.onThermalStatusChanged(powerManager.getCurrentThermalStatus());
            } catch (RuntimeException e) {
                Log.w(TAG, "Thermal API is unavailable on this device", e);
            }
        }

        void stop() {
            if (!registered) return;
            try {
                powerManager.removeThermalStatusListener(listener);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to remove thermal listener", e);
            } finally {
                registered = false;
            }
        }
    }

    /** Android 12+ Game Mode and Android 13+ Game State API bridge. */
    private static final class GameApiBridge {
        private final android.app.GameManager gameManager;
        private final boolean hasGameStateApi;

        GameApiBridge(Activity activity) {
            gameManager = activity.getSystemService(android.app.GameManager.class);
            hasGameStateApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        }

        void onResume() {
            if (gameManager == null) return;
            try {
                int mode = gameManager.getGameMode();
                Log.i(TAG, "Android Game Mode: " + gameModeName(mode));
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to read Android Game Mode", e);
            }

            if (hasGameStateApi) reportState(android.app.GameState.MODE_GAMEPLAY_INTERRUPTIBLE);
        }

        void onPause() {
            if (hasGameStateApi) reportState(android.app.GameState.MODE_NONE);
        }

        private void reportState(int mode) {
            if (gameManager == null) return;
            try {
                gameManager.setGameState(new android.app.GameState(false, mode));
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to report gameplay state to Android", e);
            }
        }

        private static String gameModeName(int mode) {
            if (mode == android.app.GameManager.GAME_MODE_PERFORMANCE) return "PERFORMANCE";
            if (mode == android.app.GameManager.GAME_MODE_BATTERY) return "BATTERY";
            if (mode == android.app.GameManager.GAME_MODE_STANDARD) return "STANDARD";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && mode == android.app.GameManager.GAME_MODE_CUSTOM) return "CUSTOM";
            return "UNSUPPORTED";
        }
    }
}
