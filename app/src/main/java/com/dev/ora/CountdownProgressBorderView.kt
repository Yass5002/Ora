package com.dev.ora.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.cos
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

    private val shimmerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val shimmerHighlightPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private var progress: Float = 0f
    private var borderColor: Int = Color.RED
    private var borderWidth: Float = 8f
    private var cornerRadius: Float = 20f.dpToPx()
    private var animatedProgress: Float = 0f
    private var progressAnimator: ValueAnimator? = null

    // Glow animation for dark theme
    private var glowAnimator: ValueAnimator? = null
    private var glowIntensity: Float = 1f

    // Shimmer animation for light theme
    private var shimmerAnimator: ValueAnimator? = null
    private var shimmerPosition: Float = 0f
    private var shimmerSecondaryAnimator: ValueAnimator? = null
    private var shimmerSecondaryPosition: Float = 0f
    private var shimmerSparkleAnimator: ValueAnimator? = null
    private var shimmerSparkle: Float = 0f

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
    private var pathLength: Float = 0f

    // Hardware acceleration support
    private var useHardwareAcceleration = true
    private var hardwareCanvas: Canvas? = null
    private var softwareBitmap: Bitmap? = null
    private var softwareCanvas: Canvas? = null
    private val softwarePaint = Paint()

    init {
        // Determine layer type based on device capabilities and Android version
        setupLayerType()

        borderWidth = 4f.dpToPx()
        backgroundPaint.strokeWidth = borderWidth

        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)
        val trackColor = typedValue.data
        backgroundPaint.color = trackColor

        // Detect theme
        updateThemeDetection()

        // Start appropriate animation based on theme
        startThemeAnimation()
    }

    private fun setupLayerType() {
        // Check if hardware acceleration is available and suitable
        useHardwareAcceleration = isHardwareAccelerated

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android P+ has better hardware acceleration for blur effects
            setLayerType(LAYER_TYPE_HARDWARE, null)
            useHardwareAcceleration = true
        } else {
            // Older devices might need software layer for blur effects
            // But we'll try hardware first and fall back if needed
            setLayerType(LAYER_TYPE_HARDWARE, null)
            useHardwareAcceleration = true
        }
    }

    private fun updateThemeDetection() {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        // Set multiplier for light theme (reduce glow intensity and spread)
        themeGlowMultiplier = if (isDarkTheme) 1f else 0.4f
    }

    private fun startThemeAnimation() {
        // Stop all animations first
        stopAllAnimations()

        // ⛔ Don't start animations if dimmed (progress complete)
        if (isDimmed) return

        if (isDarkTheme) {
            startGlowAnimation()
        } else {
            startShimmerAnimation()
        }
    }

    private fun stopAllAnimations() {
        glowAnimator?.cancel()
        glowAnimator = null
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        shimmerSecondaryAnimator?.cancel()
        shimmerSecondaryAnimator = null
        shimmerSparkleAnimator?.cancel()
        shimmerSparkleAnimator = null
    }

    private fun startGlowAnimation() {
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                // Use sine wave for smoother pulsing
                val baseIntensity = if (isDimmed) 0.3f else 0.7f
                val pulseRange = if (isDimmed) 0.1f else 0.3f
                glowIntensity = baseIntensity + (pulseRange * sin(value * Math.PI).toFloat())
                invalidate()
            }
            start()
        }
    }

    private fun startShimmerAnimation() {
        // ✨ ENHANCED: Faster primary shimmer for more noticeable movement
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 // Reduced from 2500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                shimmerPosition = animator.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Secondary shimmer wave - slightly offset for depth
        shimmerSecondaryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800 // Reduced from 3200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                shimmerSecondaryPosition = animator.animatedValue as Float
            }
            start()
        }

        // ✨ ENHANCED: More pronounced brightness variation
        shimmerSparkleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                shimmerSparkle = 0.7f + 0.3f * sin(value * Math.PI * 2).toFloat() // Increased range
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

        // ⛔ STOP animations when dimmed, START when un-dimmed
        if (dimmed) {
            stopAllAnimations()
            // Reset animation values to defaults for static dimmed state
            glowIntensity = 0.3f
            shimmerSparkle = 1f
        } else {
            // Restart theme-appropriate animation when un-dimmed
            startThemeAnimation()
        }
    }

    private fun updateGlowPaints() {
        if (isDarkTheme) {
            // Dark theme - intense neon glow (KEEP AS IS)
            val glowAlpha = if (isDimmed) 0.2f else 0.4f
            val softGlowAlpha = if (isDimmed) 0.1f else 0.2f

            glowPaint.color = adjustAlpha(borderColor, glowAlpha * dimAlpha)
            glowPaint.strokeWidth = borderWidth * (if (isDimmed) 2.0f else 3.5f)

            // Check if we need to fall back to software for blur
            if (useHardwareAcceleration && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                // Skip blur on hardware for older devices
                glowPaint.maskFilter = null
            } else {
                glowPaint.maskFilter = BlurMaskFilter(
                    borderWidth * (if (isDimmed) 1.5f else 2.5f),
                    BlurMaskFilter.Blur.NORMAL
                )
            }

            softGlowPaint.color = adjustAlpha(borderColor, softGlowAlpha * dimAlpha)
            softGlowPaint.strokeWidth = borderWidth * (if (isDimmed) 3.5f else 5f)

            if (useHardwareAcceleration && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                softGlowPaint.maskFilter = null
            } else {
                softGlowPaint.maskFilter = BlurMaskFilter(
                    borderWidth * (if (isDimmed) 3f else 4f),
                    BlurMaskFilter.Blur.NORMAL
                )
            }
        } else {
            // Light theme - NO GLOW, just prepare paints for potential shimmer use
            glowPaint.color = Color.TRANSPARENT
            glowPaint.maskFilter = null
            softGlowPaint.color = Color.TRANSPARENT
            softGlowPaint.maskFilter = null
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBorderPath()
        updateGlowPaints()

        // Calculate path length for shimmer
        val pathMeasure = PathMeasure(borderPath, false)
        pathLength = pathMeasure.length

        // Setup software bitmap for fallback rendering if needed
        if (!useHardwareAcceleration || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            softwareBitmap?.recycle()
            if (w > 0 && h > 0) {
                softwareBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                softwareCanvas = Canvas(softwareBitmap!!)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // Update theme detection when configuration changes
        updateThemeDetection()
        updateGlowPaints()
        // Restart appropriate animation for new theme
        startThemeAnimation()
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

        // Determine if we need software rendering for blur effects
        val needsSoftwareRendering = isDarkTheme && // Only for dark theme now
                (glowPaint.maskFilter != null || softGlowPaint.maskFilter != null)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                && useHardwareAcceleration

        if (needsSoftwareRendering && softwareCanvas != null) {
            // Clear software bitmap
            softwareBitmap?.eraseColor(Color.TRANSPARENT)

            // Draw to software canvas
            drawContent(softwareCanvas!!)

            // Draw software bitmap to hardware canvas
            canvas.drawBitmap(softwareBitmap!!, 0f, 0f, softwarePaint)
        } else {
            // Direct hardware rendering
            drawContent(canvas)
        }
    }

    private fun drawContent(canvas: Canvas) {
        canvas.save()

        borderPaint.strokeWidth = borderWidth

        // Draw subtle background track
        backgroundPaint.alpha = (255 * 0.3f * dimAlpha).toInt()
        canvas.drawPath(borderPath, backgroundPaint)

        // Calculate the progress path
        val pathMeasure = PathMeasure(borderPath, false)
        val progressLength = pathLength * animatedProgress

        if (progressLength > 0) {
            val progressPath = Path()
            pathMeasure.getSegment(0f, progressLength, progressPath, true)

            if (isDarkTheme) {
                drawDarkThemeGlow(canvas, progressPath)
            } else {
                drawLightThemeShimmer(canvas, progressPath, pathMeasure, progressLength)
            }
        }

        canvas.restore()
    }

    private fun drawDarkThemeGlow(canvas: Canvas, progressPath: Path) {
        // Apply current glow intensity with dimming
        val currentAlpha = glowIntensity * dimAlpha

        // Layer 1: Soft outer glow
        softGlowPaint.alpha = (255 * 0.15f * currentAlpha).toInt()
        canvas.drawPath(progressPath, softGlowPaint)

        // Layer 2: Main glow
        glowPaint.alpha = (255 * 0.25f * currentAlpha).toInt()
        canvas.drawPath(progressPath, glowPaint)

        // Layer 3: Inner bright line with gradient
        val gradientColors = if (isDimmed) {
            intArrayOf(
                adjustAlpha(borderColor, 0.3f * dimAlpha),
                adjustAlpha(borderColor, 0.5f * dimAlpha),
                adjustAlpha(borderColor, 0.4f * dimAlpha),
                adjustAlpha(borderColor, 0.3f * dimAlpha)
            )
        } else {
            intArrayOf(
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
        borderPaint.alpha = (255 * dimAlpha).toInt()
        canvas.drawPath(progressPath, borderPaint)
        borderPaint.shader = null

        // Layer 4: Bright center line (only when NOT dimmed)
        if (!isDimmed && dimAlpha > 0.7f) {
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
    }

    private fun drawLightThemeShimmer(canvas: Canvas, progressPath: Path, pathMeasure: PathMeasure, progressLength: Float) {
        // 🚫 NO GLOW LAYERS IN LIGHT THEME - REMOVED softGlowPaint and glowPaint drawing

        // Clean base progress line with subtle gradient
        val baseGradient = LinearGradient(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            intArrayOf(
                adjustAlpha(borderColor, 0.9f * dimAlpha),
                adjustAlpha(borderColor, 1.0f * dimAlpha),
                adjustAlpha(borderColor, 0.95f * dimAlpha)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        borderPaint.shader = baseGradient
        borderPaint.strokeWidth = borderWidth
        borderPaint.alpha = (255 * dimAlpha * shimmerSparkle).toInt()
        canvas.drawPath(progressPath, borderPaint)
        borderPaint.shader = null

        // ⛔ ONLY show shimmer effects when NOT dimmed
        if (!isDimmed && progressLength > pathLength * 0.05f) {
            // Primary shimmer wave - bright and visible
            drawShimmerWave(canvas, pathMeasure, progressLength, shimmerPosition, 0.15f, 0.4f, true)

            // Secondary shimmer wave - subtle
            drawShimmerWave(canvas, pathMeasure, progressLength, shimmerSecondaryPosition, 0.12f, 0.2f, false)
        }
    }

    private fun drawShimmerWave(
        canvas: Canvas,
        pathMeasure: PathMeasure,
        progressLength: Float,
        position: Float,
        waveWidth: Float,
        intensity: Float,
        isPrimary: Boolean
    ) {
        val shimmerCenter = progressLength * position
        val shimmerWidth = pathLength * waveWidth

        // Calculate shimmer segment
        val shimmerStart = (shimmerCenter - shimmerWidth / 2).coerceAtLeast(0f)
        val shimmerEnd = (shimmerCenter + shimmerWidth / 2).coerceAtMost(progressLength)

        if (shimmerEnd > shimmerStart) {
            val shimmerPath = Path()
            pathMeasure.getSegment(shimmerStart, shimmerEnd, shimmerPath, true)

            // Get center point of shimmer for gradient
            val centerPoint = FloatArray(2)
            val centerTan = FloatArray(2)
            pathMeasure.getPosTan(shimmerCenter.coerceIn(0f, progressLength), centerPoint, centerTan)

            // Clean shimmer without glow - just highlight effect
            val shimmerGradient = if (isPrimary) {
                RadialGradient(
                    centerPoint[0], centerPoint[1],
                    shimmerWidth * 1.2f,
                    intArrayOf(
                        Color.TRANSPARENT,
                        adjustAlpha(Color.WHITE, intensity * 0.7f * dimAlpha),
                        adjustAlpha(lightenColor(borderColor), intensity * 1.0f * dimAlpha),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else {
                LinearGradient(
                    centerPoint[0] - shimmerWidth, centerPoint[1],
                    centerPoint[0] + shimmerWidth, centerPoint[1],
                    intArrayOf(
                        Color.TRANSPARENT,
                        adjustAlpha(lightenColor(borderColor), intensity * 0.8f * dimAlpha),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            // NO blur mask filter in light theme for cleaner look
            shimmerPaint.maskFilter = null

            shimmerPaint.shader = shimmerGradient
            shimmerPaint.strokeWidth = borderWidth * (if (isPrimary) 1.3f else 1.0f)
            shimmerPaint.alpha = (255 * dimAlpha).toInt()
            canvas.drawPath(shimmerPath, shimmerPaint)
            shimmerPaint.shader = null
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun lightenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1] * 0.4f // Reduced saturation more (from 0.5f) for brighter shimmer
        hsv[2] = minOf(1f, hsv[2] * 1.5f) // Increased brightness more (from 1.3f)
        return Color.HSVToColor(hsv)
    }

    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
        progressAnimator = null
        stopAllAnimations()
        dimAnimator?.cancel()
        dimAnimator = null

        // Clean up software rendering resources
        softwareBitmap?.recycle()
        softwareBitmap = null
        softwareCanvas = null
    }
}