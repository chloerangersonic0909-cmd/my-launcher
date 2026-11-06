package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.GameManager;
import android.app.GameState;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Connects the Minecraft gameplay Activity to Android's game and thermal APIs.
 *
 * The OS remains responsible for power/thermal policy. This class reports gameplay
 * state and warns when the device reaches a severe thermal level; it never changes
 * CPU frequencies or bypasses Android thermal safeguards.
 */
public final class GamePerformanceManager {
    private static final String TAG = "GamePerformance";

    private final Activity activity;
    private final PowerManager powerManager;
    private final GameManager gameManager;
    private boolean thermalListenerRegistered;
    private boolean severeThermalWarningShown;

    private final PowerManager.OnThermalStatusChangedListener thermalListener =
            this::onThermalStatusChanged;

    public GamePerformanceManager(Activity activity) {
        this.activity = activity;
        this.powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.gameManager = activity.getSystemService(GameManager.class);
        } else {
            this.gameManager = null;
        }
    }

    /** Call while Minecraft's rendering Activity is in the foreground. */
    public void onResume() {
        reportGameState(GameState.MODE_GAMEPLAY_INTERRUPTIBLE, false);
        logSelectedGameMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null
                && !thermalListenerRegistered) {
            try {
                powerManager.addThermalStatusListener(thermalListener);
                thermalListenerRegistered = true;
                onThermalStatusChanged(powerManager.getCurrentThermalStatus());
            } catch (RuntimeException e) {
                Log.w(TAG, "Thermal API is unavailable on this device", e);
            }
        }
    }

    /** Mark gameplay as inactive when the Minecraft Activity leaves the foreground. */
    public void onPause() {
        reportGameState(GameState.MODE_NONE, false);
        unregisterThermalListener();
    }

    public void destroy() {
        unregisterThermalListener();
    }

    private void logSelectedGameMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gameManager == null) return;

        try {
            int mode = gameManager.getGameMode();
            Log.i(TAG, "Android Game Mode: " + gameModeName(mode));
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read Android Game Mode", e);
        }
    }

    private void reportGameState(int mode, boolean isLoading) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || gameManager == null) return;

        try {
            gameManager.setGameState(new GameState(isLoading, mode));
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to report gameplay state to Android", e);
        }
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

    private void unregisterThermalListener() {
        if (!thermalListenerRegistered || powerManager == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        try {
            powerManager.removeThermalStatusListener(thermalListener);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to remove thermal listener", e);
        } finally {
            thermalListenerRegistered = false;
        }
    }

    private static String gameModeName(int mode) {
        if (mode == GameManager.GAME_MODE_PERFORMANCE) return "PERFORMANCE";
        if (mode == GameManager.GAME_MODE_BATTERY) return "BATTERY";
        if (mode == GameManager.GAME_MODE_STANDARD) return "STANDARD";
        if (Build.VERSION.SDK_INT >= 34 && mode == GameManager.GAME_MODE_CUSTOM) return "CUSTOM";
        return "UNSUPPORTED";
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
}
