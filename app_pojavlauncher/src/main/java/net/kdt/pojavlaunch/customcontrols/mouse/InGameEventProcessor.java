package net.kdt.pojavlaunch.customcontrols.mouse;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Touchscreen-as-mouse input while Minecraft has grabbed the pointer.
 * Drag moves the cursor, a tap clicks left, and a long press clicks right.
 */
public class InGameEventProcessor implements TouchEventProcessor {
    private static final long RIGHT_CLICK_HOLD_MS = 450L;
    private static final float MOVE_THRESHOLD_PX = 12f;

    private final Handler mGestureHandler = new Handler(Looper.getMainLooper());
    private final double mSensitivity;
    private final PointerTracker mTracker = new PointerTracker();

    private boolean mFingerDown;
    private boolean mHasMoved;
    private boolean mRightButtonDown;
    private float mTotalMoveX;
    private float mTotalMoveY;

    private final Runnable mLongPressRightClick = () -> {
        if (mFingerDown && !mHasMoved && !mRightButtonDown) {
            CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, true);
            mRightButtonDown = true;
        }
    };

    public InGameEventProcessor(double sensitivity) {
        mSensitivity = sensitivity;
    }

    @Override
    public boolean processTouchEvent(MotionEvent motionEvent) {
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelLongPress();
                releaseRightButton();
                mTracker.startTracking(motionEvent);
                mFingerDown = true;
                mHasMoved = false;
                mTotalMoveX = 0f;
                mTotalMoveY = 0f;
                mGestureHandler.postDelayed(mLongPressRightClick, RIGHT_CLICK_HOLD_MS);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mFingerDown) break;
                mTracker.trackEvent(motionEvent);
                float[] motionVector = mTracker.getMotionVector();
                float deltaX = (float) (motionVector[0] * mSensitivity);
                float deltaY = (float) (motionVector[1] * mSensitivity);

                mTotalMoveX += Math.abs(motionVector[0]);
                mTotalMoveY += Math.abs(motionVector[1]);
                if (!mHasMoved && Math.hypot(mTotalMoveX, mTotalMoveY) > MOVE_THRESHOLD_PX) {
                    mHasMoved = true;
                    cancelLongPress();
                }

                CallbackBridge.mouseX += deltaX;
                CallbackBridge.mouseY += deltaY;
                CallbackBridge.deltaX = deltaX;
                CallbackBridge.deltaY = deltaY;
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
                break;

            case MotionEvent.ACTION_UP:
                finishTouch(true);
                break;

            case MotionEvent.ACTION_CANCEL:
                finishTouch(false);
                break;
        }
        return true;
    }

    private void finishTouch(boolean allowTap) {
        cancelLongPress();
        mTracker.cancelTracking();

        if (mRightButtonDown) {
            releaseRightButton();
        } else if (allowTap && mFingerDown && !mHasMoved) {
            CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
            CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
        }

        mFingerDown = false;
        mHasMoved = false;
        mTotalMoveX = 0f;
        mTotalMoveY = 0f;
    }

    private void cancelLongPress() {
        mGestureHandler.removeCallbacks(mLongPressRightClick);
    }

    private void releaseRightButton() {
        if (mRightButtonDown) {
            CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, false);
            mRightButtonDown = false;
        }
    }

    @Override
    public void cancelPendingActions() {
        finishTouch(false);
    }
}
