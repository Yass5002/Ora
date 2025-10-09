package com.dev.ora.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.sin

class CountdownProgressBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val softGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var progress: Float = 0f
    private var borderColor: Int = Color.RED
    private var borderWidth: Float = 8f
    private var cornerRadius: Float = 20f.dpToPx()
    private var animatedProgress: Float = 0f
    private var progressAnimator: ValueAnimator? = null

    // Glow animation
    private var glowAnimator: ValueAnimator? = null
    private var glowIntensity: Float = 1f

    // Dimming for expired events
    private var isDimmed: Boolean = false
    private var dimAlpha: Float = 1f
    private var dimAnimator: ValueAnimator? = null

    // Theme detection
    private var isDarkTheme: Boolean = false
    private var themeGlowMultiplier: Float = 1f

    // Padding for glow overflow (matching the negative margin in XML)
    private val glowPadding: Float = 16f.dpToPx()

    private val borderPath = Path()
    private val borderRect = RectF()

    init {
        // Use SOFTWARE layer for proper blur rendering
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        borderWidth = 4f.dpToPx()
        backgroundPaint.strokeWidth = borderWidth

        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)
        val trackColor = typedValue.data
        backgroundPaint.color = trackColor

        // Detect theme
        updateThemeDetection()

        // Start subtle glow pulsing animation
        startGlowAnimation()
    }

    private fun updateThemeDetection() {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        // Set multiplier for light theme (reduce glow intensity and spread)
        themeGlowMultiplier = if (isDarkTheme) 1f else 0.4f
    }

    private fun startGlowAnimation() {
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                // Use sine wave for smoother pulsing - adjusted for theme
                val baseIntensity = when {
                    isDimmed && !isDarkTheme -> 0.2f
                    isDimmed && isDarkTheme -> 0.3f
                    !isDimmed && !isDarkTheme -> 0.4f
                    else -> 0.7f
                }
                val pulseRange = when {
                    isDimmed && !isDarkTheme -> 0.05f
                    isDimmed && isDarkTheme -> 0.1f
                    !isDimmed && !isDarkTheme -> 0.15f
                    else -> 0.3f
                }
                glowIntensity = baseIntensity + (pulseRange * sin(value * Math.PI).toFloat())
                invalidate()
            }
            start()
        }
    }

    fun setProgress(newProgress: Float, animate: Boolean = true, duration: Long = 1200) {
        // When progress reaches 1.0 (expired), automatically dim
        if (newProgress >= 1f && !isDimmed) {
            setDimmed(true, animate = true)
        }

        progressAnimator?.cancel()

        if (animate) {
            progressAnimator = ValueAnimator.ofFloat(animatedProgress, newProgress).apply {
                this.duration = duration
                interpolator = DecelerateInterpolator(2f)
                addUpdateListener { animator ->
                    animatedProgress = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animatedProgress = newProgress
            invalidate()
        }
        progress = newProgress
    }

    fun setBorderColor(color: String) {
        try {
            borderColor = color.toColorInt()
            borderPaint.color = borderColor
            updateGlowPaints()
            invalidate()
        } catch (e: Exception) {
            borderColor = Color.RED
            borderPaint.color = borderColor
            updateGlowPaints()
        }
    }

    fun setDimmed(dimmed: Boolean, animate: Boolean = true) {
        if (isDimmed == dimmed) return
        isDimmed = dimmed

        dimAnimator?.cancel()

        val targetAlpha = if (dimmed) 0.4f else 1f

        if (animate) {
            dimAnimator = ValueAnimator.ofFloat(dimAlpha, targetAlpha).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    dimAlpha = animator.animatedValue as Float
                    updateGlowPaints()
                    invalidate()
                }
                start()
            }
        } else {
            dimAlpha = targetAlpha
            updateGlowPaints()
            invalidate()
        }

        // Restart glow animation with new intensity
        glowAnimator?.cancel()
        startGlowAnimation()
    }

    private fun updateGlowPaints() {
        // Apply dimming to glow intensity with theme adjustment
        val glowAlpha = when {
            isDimmed && !isDarkTheme -> 0.1f
            isDimmed && isDarkTheme -> 0.2f
            !isDimmed && !isDarkTheme -> 0.2f
            else -> 0.4f
        }
        val softGlowAlpha = when {
            isDimmed && !isDarkTheme -> 0.05f
            isDimmed && isDarkTheme -> 0.1f
            !isDimmed && !isDarkTheme -> 0.1f
            else -> 0.2f
        }

        // Tighter glow with reduced spread for light theme
        val glowStrokeMultiplier = if (isDarkTheme) {
            if (isDimmed) 2.0f else 3.5f
        } else {
            if (isDimmed) 1.5f else 2.5f
        }

        glowPaint.color = adjustAlpha(borderColor, glowAlpha * dimAlpha * themeGlowMultiplier)
        glowPaint.strokeWidth = borderWidth * glowStrokeMultiplier

        val blurRadius = if (isDarkTheme) {
            if (isDimmed) borderWidth * 1.5f else borderWidth * 2.5f
        } else {
            if (isDimmed) borderWidth * 1.0f else borderWidth * 1.5f
        }
        glowPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

        // Soft outer glow - reduced spread for light theme
        val softGlowStrokeMultiplier = if (isDarkTheme) {
            if (isDimmed) 3.5f else 5f
        } else {
            if (isDimmed) 2.5f else 3.5f
        }

        softGlowPaint.color = adjustAlpha(borderColor, softGlowAlpha * dimAlpha * themeGlowMultiplier)
        softGlowPaint.strokeWidth = borderWidth * softGlowStrokeMultiplier

        val softBlurRadius = if (isDarkTheme) {
            if (isDimmed) borderWidth * 3f else borderWidth * 4f
        } else {
            if (isDimmed) borderWidth * 2f else borderWidth * 2.5f
        }
        softGlowPaint.maskFilter = BlurMaskFilter(softBlurRadius, BlurMaskFilter.Blur.NORMAL)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBorderPath()
        updateGlowPaints()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // Update theme detection when configuration changes
        updateThemeDetection()
        updateGlowPaints()
        // Restart glow animation with new theme settings
        glowAnimator?.cancel()
        startGlowAnimation()
    }

    private fun updateBorderPath() {
        // Account for the glow padding - draw inside the padding area
        val inset = glowPadding + borderWidth / 2f
        borderRect.set(
            inset,
            inset,
            width - inset,
            height - inset
        )

        borderPath.reset()

        val left = inset
        val top = inset
        val right = width - inset
        val bottom = height - inset
        val centerX = width / 2f

        // Start from top center
        borderPath.moveTo(centerX, top)
        // Draw to top-right corner
        borderPath.lineTo(right - cornerRadius, top)
        // Top-right corner arc
        borderPath.arcTo(
            RectF(right - cornerRadius * 2, top, right, top + cornerRadius * 2),
            -90f, 90f, false
        )
        // Right edge
        borderPath.lineTo(right, bottom - cornerRadius)
        // Bottom-right corner arc
        borderPath.arcTo(
            RectF(right - cornerRadius * 2, bottom - cornerRadius * 2, right, bottom),
            0f, 90f, false
        )
        // Bottom edge
        borderPath.lineTo(left + cornerRadius, bottom)
        // Bottom-left corner arc
        borderPath.arcTo(
            RectF(left, bottom - cornerRadius * 2, left + cornerRadius * 2, bottom),
            90f, 90f, false
        )
        // Left edge
        borderPath.lineTo(left, top + cornerRadius)
        // Top-left corner arc
        borderPath.arcTo(
            RectF(left, top, left + cornerRadius * 2, top + cornerRadius * 2),
            180f, 90f, false
        )
        // Complete the path back to top center
        borderPath.lineTo(centerX, top)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // Save canvas state to ensure we don't affect other drawing
        canvas.save()

        borderPaint.strokeWidth = borderWidth

        // Draw subtle background track (also dimmed)
        backgroundPaint.alpha = (255 * 0.3f * dimAlpha).toInt()
        canvas.drawPath(borderPath, backgroundPaint)

        // Calculate the path length and draw progress
        val pathMeasure = PathMeasure(borderPath, false)
        val pathLength = pathMeasure.length
        val progressLength = pathLength * animatedProgress

        if (progressLength > 0) {
            val progressPath = Path()
            pathMeasure.getSegment(0f, progressLength, progressPath, true)

            // Apply current glow intensity with dimming and theme adjustment
            val currentAlpha = glowIntensity * dimAlpha * themeGlowMultiplier

            // Draw glow layers - adjusted for theme
            // Layer 1: Soft outer glow (widest, most transparent)
            val softGlowLayerAlpha = if (isDarkTheme) 0.15f else 0.08f
            softGlowPaint.alpha = (255 * softGlowLayerAlpha * currentAlpha).toInt()
            canvas.drawPath(progressPath, softGlowPaint)

            // Layer 2: Main glow (medium width, medium transparency)
            val glowLayerAlpha = if (isDarkTheme) 0.25f else 0.12f
            glowPaint.alpha = (255 * glowLayerAlpha * currentAlpha).toInt()
            canvas.drawPath(progressPath, glowPaint)

            // Layer 3: Inner bright line with gradient (subdued when dimmed or in light theme)
            val gradientColors = when {
                isDimmed && !isDarkTheme -> intArrayOf(
                    adjustAlpha(borderColor, 0.2f * dimAlpha),
                    adjustAlpha(borderColor, 0.3f * dimAlpha),
                    adjustAlpha(borderColor, 0.25f * dimAlpha),
                    adjustAlpha(borderColor, 0.2f * dimAlpha)
                )
                isDimmed && isDarkTheme -> intArrayOf(
                    adjustAlpha(borderColor, 0.3f * dimAlpha),
                    adjustAlpha(borderColor, 0.5f * dimAlpha),
                    adjustAlpha(borderColor, 0.4f * dimAlpha),
                    adjustAlpha(borderColor, 0.3f * dimAlpha)
                )
                !isDimmed && !isDarkTheme -> intArrayOf(
                    adjustAlpha(borderColor, 0.4f),
                    adjustAlpha(borderColor, 0.7f),
                    adjustAlpha(lightenColor(borderColor), 0.6f),
                    adjustAlpha(borderColor, 0.7f)
                )
                else -> intArrayOf(
                    adjustAlpha(borderColor, 0.6f),
                    borderColor,
                    adjustAlpha(lightenColor(borderColor), 0.9f),
                    borderColor
                )
            }

            val gradient = LinearGradient(
                0f, 0f,
                width.toFloat(), height.toFloat(),
                gradientColors,
                floatArrayOf(0f, 0.3f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )

            borderPaint.shader = gradient
            borderPaint.strokeWidth = borderWidth
            borderPaint.alpha = (255 * dimAlpha).toInt()
            canvas.drawPath(progressPath, borderPaint)

            // Layer 4: Bright center line (only when not dimmed and in dark theme)
            if (!isDimmed && isDarkTheme && dimAlpha > 0.7f) {
                val centerPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    isAntiAlias = true
                    color = lightenColor(borderColor)
                    strokeWidth = borderWidth * 0.3f
                    alpha = (255 * 0.6f * currentAlpha).toInt()
                }
                canvas.drawPath(progressPath, centerPaint)
            }

            borderPaint.shader = null

            // Add a subtle bright spot at the progress end (adjusted for theme)
            if (!isDimmed && animatedProgress > 0.01f && animatedProgress < 0.99f) {
                val endPoint = FloatArray(2)
                val endTan = FloatArray(2)
                pathMeasure.getPosTan(progressLength, endPoint, endTan)

                val spotRadius = borderWidth * (if (isDarkTheme) 3f else 2f)
                val spotAlpha = if (isDarkTheme) 0.5f else 0.3f
                val spotAlpha2 = if (isDarkTheme) 0.3f else 0.15f

                val spotPaint = Paint().apply {
                    style = Paint.Style.FILL
                    isAntiAlias = true
                    shader = RadialGradient(
                        endPoint[0], endPoint[1],
                        spotRadius,
                        intArrayOf(
                            adjustAlpha(lightenColor(borderColor), spotAlpha * dimAlpha * themeGlowMultiplier),
                            adjustAlpha(borderColor, spotAlpha2 * dimAlpha * themeGlowMultiplier),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    alpha = (255 * currentAlpha).toInt()
                }
                canvas.drawCircle(endPoint[0], endPoint[1], spotRadius, spotPaint)
            }
        }

        canvas.restore()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun lightenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1] * 0.5f // Reduce saturation
        hsv[2] = minOf(1f, hsv[2] * 1.3f) // Increase brightness
        return Color.HSVToColor(hsv)
    }

    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
        progressAnimator = null
        glowAnimator?.cancel()
        glowAnimator = null
        dimAnimator?.cancel()
        dimAnimator = null
    }
}