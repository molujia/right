package com.tinderbot;

/**
 * 所有可配置参数集中管理
 */
public class SwipeConfig {

    // ── 行为概率 ─────────────────────────────────────────
    /** 右划（Like）概率，范围 0.0 ~ 1.0 */
    public float likeProbability = 0.80f;

    // ── 右划间隔（毫秒）────────────────────────────────────
    public int swipeIntervalMinMs = 500;
    public int swipeIntervalMaxMs = 2000;

    // ── 左划前的停顿（毫秒）─────────────────────────────────
    public int dislikePauseMinMs = 1000;
    public int dislikePauseMaxMs = 3000;

    // ── 浏览模拟：下滑/上滑距离（像素）──────────────────────
    public int scrollDownPx = 400;
    public int scrollUpPx   = 400;

    // ── 批次大小（最少 90 次）───────────────────────────────
    public int sessionSizeMin = 90;
    public int sessionSizeMax = 150;   // 由 UI 调整，基础最大值

    // ── 批次间停顿（毫秒）───────────────────────────────────
    public int sessionBreakMinMs = 10_000;
    public int sessionBreakMaxMs = 90_000;

    // ── 手势持续时间（毫秒）──────────────────────────────────
    public int swipeGestureDurationMs = 200;
    public int scrollGestureDurationMs = 400;

    // ── 屏幕坐标（运行时从屏幕尺寸计算）────────────────────────
    public int screenWidth  = 1080;
    public int screenHeight = 2340;

    // ── 计算滑动的起止点 ─────────────────────────────────────
    public int swipeStartX() { return (int)(screenWidth * 0.75f); }
    public int swipeEndRightX() { return (int)(screenWidth * 0.95f); }
    public int swipeEndLeftX()  { return (int)(screenWidth * 0.05f); }
    public int swipeCenterY()   { return (int)(screenHeight * 0.50f); }
    public int scrollStartY()   { return (int)(screenHeight * 0.55f); }
    public int scrollEndDownY() { return scrollStartY() - scrollDownPx; }
    public int scrollEndUpY()   { return scrollStartY() + scrollUpPx; }
    public int scrollCenterX()  { return screenWidth / 2; }
}
