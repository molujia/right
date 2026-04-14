package com.tinderbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Random;

/**
 * 核心无障碍服务：
 * - 执行右划 / 左划手势
 * - 左划前模拟浏览（下滑 + 上滑）
 * - 随机批次停顿
 */
public class AutoSwipeAccessibilityService extends AccessibilityService {

    // ── 单例引用，供外部调用 ──────────────────────────────────
    public static AutoSwipeAccessibilityService instance;

    // ── 运行状态 ─────────────────────────────────────────────
    private boolean running = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rng = new Random();

    // ── 统计 ─────────────────────────────────────────────────
    private int totalSwipes = 0;
    private int sessionCount = 0;
    private int currentSessionTarget = 0;

    // ── 配置（由 FloatingWindowService 注入）──────────────────
    public SwipeConfig config = new SwipeConfig();

    // ── 回调接口（更新悬浮窗 UI）─────────────────────────────
    public interface StatusCallback {
        void onUpdate(String status, int total);
    }
    public StatusCallback statusCallback;

    // ─────────────────────────────────────────────────────────
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        // 获取真实屏幕尺寸
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        config.screenWidth  = dm.widthPixels;
        config.screenHeight = dm.heightPixels;

        notifyStatus("服务已连接", totalSwipes);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { /* 不使用事件 */ }

    @Override
    public void onInterrupt() {
        stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        instance = null;
    }

    // ── 公共控制接口 ─────────────────────────────────────────

    public void start() {
        if (running) return;
        running = true;
        sessionCount = 0;
        currentSessionTarget = nextSessionTarget();
        notifyStatus("运行中…", totalSwipes);
        scheduleNext(0);
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        notifyStatus("已停止", totalSwipes);
    }

    public boolean isRunning() { return running; }

    public int getTotalSwipes() { return totalSwipes; }

    // ── 核心循环 ─────────────────────────────────────────────

    private void scheduleNext(long delayMs) {
        if (!running) return;
        handler.postDelayed(this::tick, delayMs);
    }

    private void tick() {
        if (!running) return;

        // ── 检查批次 ──────────────────────────────────────────
        if (sessionCount >= currentSessionTarget) {
            long breakMs = randBetween(config.sessionBreakMinMs, config.sessionBreakMaxMs);
            notifyStatus("批次停顿 " + (breakMs/1000) + "s…", totalSwipes);
            sessionCount = 0;
            currentSessionTarget = nextSessionTarget();
            scheduleNext(breakMs);
            return;
        }

        // ── 决定行为 ──────────────────────────────────────────
        boolean doLike = rng.nextFloat() < config.likeProbability;

        if (doLike) {
            performSwipeRight();
            sessionCount++;
            totalSwipes++;
            notifyStatus("▶ 右划 Like", totalSwipes);
            long delay = randBetween(config.swipeIntervalMinMs, config.swipeIntervalMaxMs);
            scheduleNext(delay);
        } else {
            // 左划：先停顿 → 下滑 → 上滑 → 左划
            long pause = randBetween(config.dislikePauseMinMs, config.dislikePauseMaxMs);
            notifyStatus("浏览中…", totalSwipes);
            handler.postDelayed(() -> {
                if (!running) return;
                performScrollDown();
                handler.postDelayed(() -> {
                    if (!running) return;
                    performScrollUp();
                    handler.postDelayed(() -> {
                        if (!running) return;
                        performSwipeLeft();
                        sessionCount++;
                        totalSwipes++;
                        notifyStatus("◀ 左划 Dislike", totalSwipes);
                        long delay = randBetween(config.swipeIntervalMinMs, config.swipeIntervalMaxMs);
                        scheduleNext(delay);
                    }, config.scrollGestureDurationMs + 100L);
                }, config.scrollGestureDurationMs + 100L);
            }, pause);
        }
    }

    // ── 手势实现 ─────────────────────────────────────────────

    private void performSwipeRight() {
        Path path = new Path();
        path.moveTo(config.swipeStartX(), config.swipeCenterY());
        path.lineTo(config.swipeEndRightX(), config.swipeCenterY());
        dispatchGesture(buildGesture(path, config.swipeGestureDurationMs), null, null);
    }

    private void performSwipeLeft() {
        Path path = new Path();
        path.moveTo(config.swipeStartX(), config.swipeCenterY());
        path.lineTo(config.swipeEndLeftX(), config.swipeCenterY());
        dispatchGesture(buildGesture(path, config.swipeGestureDurationMs), null, null);
    }

    private void performScrollDown() {
        Path path = new Path();
        path.moveTo(config.scrollCenterX(), config.scrollStartY());
        path.lineTo(config.scrollCenterX(), config.scrollEndDownY());
        dispatchGesture(buildGesture(path, config.scrollGestureDurationMs), null, null);
    }

    private void performScrollUp() {
        Path path = new Path();
        path.moveTo(config.scrollCenterX(), config.scrollEndDownY());
        path.lineTo(config.scrollCenterX(), config.scrollStartY());
        dispatchGesture(buildGesture(path, config.scrollGestureDurationMs), null, null);
    }

    private GestureDescription buildGesture(Path path, long durationMs) {
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, durationMs);
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    // ── 工具方法 ─────────────────────────────────────────────

    private long randBetween(long min, long max) {
        if (min >= max) return min;
        return min + (long)(rng.nextDouble() * (max - min));
    }

    private int nextSessionTarget() {
        int extra = config.sessionSizeMax - config.sessionSizeMin;
        if (extra <= 0) return config.sessionSizeMin;
        return config.sessionSizeMin + rng.nextInt(extra + 1);
    }

    private void notifyStatus(String status, int total) {
        if (statusCallback != null) {
            statusCallback.onUpdate(status, total);
        }
    }
}
