package dev.ezaz.manazone

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val ambientAnimators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        applySystemBarInsets(root)

        findViewById<TextView>(R.id.greeting).setText(greetingForNow())
        applyBrandGradient(findViewById(R.id.brand))

        val getStarted = findViewById<View>(R.id.getStarted)
        getStarted.setOnClickListener {
            Snackbar.make(root, R.string.coming_soon, Snackbar.LENGTH_SHORT).show()
        }

        playEntrance(
            findViewById(R.id.orb),
            findViewById(R.id.greeting),
            findViewById(R.id.welcomeTo),
            findViewById(R.id.brand),
            findViewById(R.id.subtitle),
            getStarted,
        )
        startAmbientMotion(findViewById(R.id.orbGlow), findViewById(R.id.orbCore))
    }

    override fun onDestroy() {
        ambientAnimators.forEach { it.cancel() }
        ambientAnimators.clear()
        super.onDestroy()
    }

    private fun applySystemBarInsets(view: View) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom,
            )
            insets
        }
    }

    private fun greetingForNow(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..21 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }

    private fun applyBrandGradient(brand: TextView) {
        brand.doOnLayout {
            brand.paint.shader = LinearGradient(
                0f, 0f, brand.width.toFloat(), 0f,
                intArrayOf(
                    ContextCompat.getColor(this, R.color.mana_cyan),
                    ContextCompat.getColor(this, R.color.mana_violet),
                    ContextCompat.getColor(this, R.color.mana_magenta),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
            brand.invalidate()
        }
    }

    /** Fades and lifts each view into place, one after another. */
    private fun playEntrance(vararg views: View) {
        val offset = 32 * resources.displayMetrics.density
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offset
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(150L + index * 110L)
                .setDuration(700)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }
    }

    /** A slow breathing glow and a gentle float on the orb. */
    private fun startAmbientMotion(glow: View, core: View) {
        val float = 8 * resources.displayMetrics.density
        ambientAnimators += ObjectAnimator.ofPropertyValuesHolder(
            glow,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.12f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1f),
        ).apply { duration = 2400 }
        ambientAnimators += ObjectAnimator.ofFloat(core, View.TRANSLATION_Y, -float, float)
            .apply { duration = 3200 }

        ambientAnimators.forEach {
            (it as ObjectAnimator).apply {
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }
}
