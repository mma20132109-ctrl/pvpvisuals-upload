package com.pvpvisuals.gui.echo;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Time-based morph transition between a small source rectangle and a settings panel.
 * Coordinates are GUI-logical coordinates, matching EchoScreen's 440x296 canvas.
 */
public final class MenuAnimation {
    public static final long OPEN_DURATION_NS = 480_000_000L;

    private static final long DEFAULT_CLOSE_DURATION_NS = 420_000_000L;

    // Current geometry.
    public float currentX;
    public float currentY;
    public float currentWidth;
    public float currentHeight;

    // Target geometry for the active transition.
    public float targetX;
    public float targetY;
    public float targetWidth;
    public float targetHeight;

    // Raw transition progress in [0, 1].
    public float progress;

    // Alpha follows eased progress in the active direction.
    public float currentAlpha;

    private float originX;
    private float originY;
    private float originWidth;
    private float originHeight;

    // Full-size panel rectangle used by SettingsView.
    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;

    private float fromX;
    private float fromY;
    private float fromWidth;
    private float fromHeight;

    private long startTimeNs;
    private long durationNs = OPEN_DURATION_NS;

    private float fromAlpha;

    private boolean visible;
    private boolean closing;

    /**
     * Start opening from the clicked card/button into the full settings panel.
     */
    public void open(
            float sourceX,
            float sourceY,
            float sourceWidth,
            float sourceHeight,
            float destinationX,
            float destinationY,
            float destinationWidth,
            float destinationHeight,
            long nowNs
    ) {
        originX = sourceX;
        originY = sourceY;
        originWidth = Math.max(1f, sourceWidth);
        originHeight = Math.max(1f, sourceHeight);

        panelX = destinationX;
        panelY = destinationY;
        panelWidth = Math.max(1f, destinationWidth);
        panelHeight = Math.max(1f, destinationHeight);

        fromX = sourceX;
        fromY = sourceY;
        fromWidth = originWidth;
        fromHeight = originHeight;

        targetX = panelX;
        targetY = panelY;
        targetWidth = panelWidth;
        targetHeight = panelHeight;

        currentX = fromX;
        currentY = fromY;
        currentWidth = fromWidth;
        currentHeight = fromHeight;

        progress = 0f;
        fromAlpha = 0f;
        currentAlpha = 0f;
        startTimeNs = nowNs;
        durationNs = OPEN_DURATION_NS;
        visible = true;
        closing = false;
    }

    /**
     * Start the reverse transition. The current frame is sampled first,
     * so closing can begin without a positional jump.
     */
    public void close(long nowNs) {
        close(nowNs, DEFAULT_CLOSE_DURATION_NS);
    }

    public void close(long nowNs, long requestedDurationNs) {
        if (!visible || closing) {
            return;
        }

        update(nowNs);

        fromX = currentX;
        fromY = currentY;
        fromWidth = currentWidth;
        fromHeight = currentHeight;

        targetX = originX;
        targetY = originY;
        targetWidth = originWidth;
        targetHeight = originHeight;

        progress = 0f;
        fromAlpha = currentAlpha;
        startTimeNs = nowNs;
        durationNs = Math.max(1L, requestedDurationNs);
        closing = true;
    }

    /** Update once per render frame. */
    public void update() {
        update(System.nanoTime());
    }

    /** Update using an explicit monotonic timestamp (nanoseconds). */
    public void update(long nowNs) {
        if (!visible) {
            return;
        }

        long elapsedNs = Math.max(0L, nowNs - startTimeNs);
        progress = clamp01(elapsedNs / (float) durationNs);

        float eased = easeOutCubic(progress);
        currentX = lerp(fromX, targetX, eased);
        currentY = lerp(fromY, targetY, eased);
        currentWidth = lerp(fromWidth, targetWidth, eased);
        currentHeight = lerp(fromHeight, targetHeight, eased);
        currentAlpha = closing ? fromAlpha * (1f - eased) : eased;

        if (progress >= 1f) {
            currentX = targetX;
            currentY = targetY;
            currentWidth = targetWidth;
            currentHeight = targetHeight;

            if (closing) {
                currentAlpha = 0f;
                visible = false;
            } else {
                currentAlpha = 1f;
            }
        }
    }

    /**
     * Move the active transition together with the main Click GUI when the user drags it.
     * This keeps both the attached panel and its return-to-card anchor aligned.
     */
    public void shift(float deltaX, float deltaY) {
        if (!visible || (deltaX == 0f && deltaY == 0f)) {
            return;
        }

        currentX += deltaX;
        currentY += deltaY;
        targetX += deltaX;
        targetY += deltaY;
        originX += deltaX;
        originY += deltaY;
        fromX += deltaX;
        fromY += deltaY;
        panelX += deltaX;
        panelY += deltaY;
    }

    /** Eased opacity of the morphing panel and its contents. */
    public float revealProgress() {
        return visible ? currentAlpha : 0f;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isClosing() {
        return closing;
    }

    /** Input is enabled only after the opening morph has completely finished. */
    public boolean isOpen() {
        return visible && !closing && progress >= 1f;
    }

    /**
     * Transform full-panel drawing coordinates into the current morph rectangle.
     * Pair every call with restore() in a finally block.
     */
    public void apply(GuiGraphics graphics) {
        if (!visible) {
            return;
        }

        float scaleX = Math.max(0.0001f, currentWidth / panelWidth);
        float scaleY = Math.max(0.0001f, currentHeight / panelHeight);

        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(scaleX, scaleY);
        graphics.pose().translate(-panelX, -panelY);
    }

    /** Restore the matrix pushed by apply(). */
    public void restore(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    /** Ease-out cubic: fast start, smooth deceleration near the destination. */
    private static float easeOutCubic(float value) {
        float t = clamp01(value);
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
