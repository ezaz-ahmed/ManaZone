package dev.ezaz.manazone.feedback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** One struck tone: [partials] are (frequency ratio, amplitude) pairs that give it its timbre. */
private class Strike(
    val freq: Double,
    val startSec: Double,
    val lengthSec: Double,
    val decay: Double,
    val partials: List<Pair<Double, Double>>,
)

/** A synthesized completion sound. Nothing is bundled; each is rendered from a few tone recipes. */
class CompletionSound private constructor(
    val id: String,
    val name: String,
    private val totalSec: Double,
    private val strikes: List<Strike>,
) {
    /** Renders mono 16-bit PCM, normalized so the loudest sample sits at [volume] (0..1) of full scale. */
    fun render(volume: Float): ShortArray {
        val total = (SAMPLE_RATE * totalSec).toInt()
        val mix = DoubleArray(total)
        for (s in strikes) {
            val start = (s.startSec * SAMPLE_RATE).toInt()
            val length = (s.lengthSec * SAMPLE_RATE).toInt()
            for (i in 0 until length) {
                val idx = start + i
                if (idx >= total) break
                val t = i.toDouble() / SAMPLE_RATE
                val env = (t / 0.008).coerceAtMost(1.0) * exp(-t * s.decay)
                var tone = 0.0
                for ((ratio, amp) in s.partials) tone += amp * sin(2 * PI * s.freq * ratio * t)
                mix[idx] += tone * env
            }
        }
        val peak = mix.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: return ShortArray(total)
        val scale = volume.coerceIn(0f, 1f) * 0.95 * Short.MAX_VALUE / peak
        return ShortArray(total) { (mix[it] * scale).toInt().toShort() }
    }

    companion object {
        const val SAMPLE_RATE = 22_050
        const val DEFAULT_ID = "ding_dong"

        private val plain = listOf(1.0 to 1.0, 2.0 to 0.25)
        private val bell = listOf(1.0 to 1.0, 2.76 to 0.3, 5.4 to 0.1)

        val all: List<CompletionSound> = listOf(
            CompletionSound(
                "ding_dong", "Ding dong", 1.9,
                listOf(Strike(659.25, 0.0, 1.2, 3.2, plain), Strike(523.25, 0.55, 1.35, 3.2, plain)),
            ),
            CompletionSound(
                "bell", "Bell", 2.4,
                listOf(Strike(1046.5, 0.0, 2.3, 2.4, bell)),
            ),
            CompletionSound(
                "chime", "Rising chime", 2.2,
                listOf(
                    Strike(523.25, 0.0, 1.5, 3.0, plain),
                    Strike(659.25, 0.25, 1.5, 3.0, plain),
                    Strike(783.99, 0.5, 1.6, 3.0, plain),
                ),
            ),
            CompletionSound(
                "gong", "Soft gong", 3.4,
                listOf(Strike(220.0, 0.0, 3.3, 1.3, listOf(1.0 to 1.0, 1.5 to 0.4, 2.4 to 0.3, 3.1 to 0.15))),
            ),
            CompletionSound(
                "marimba", "Marimba", 1.5,
                listOf(
                    Strike(659.25, 0.0, 0.7, 9.0, listOf(1.0 to 1.0, 4.0 to 0.2)),
                    Strike(783.99, 0.18, 0.7, 9.0, listOf(1.0 to 1.0, 4.0 to 0.2)),
                    Strike(1046.5, 0.36, 0.9, 9.0, listOf(1.0 to 1.0, 4.0 to 0.2)),
                ),
            ),
            CompletionSound(
                "triple", "Triple ding", 1.9,
                listOf(0.0, 0.35, 0.7).map { Strike(880.0, it, 0.9, 5.0, listOf(1.0 to 1.0, 2.0 to 0.15)) },
            ),
        )

        fun byId(id: String?): CompletionSound = all.firstOrNull { it.id == id } ?: all.first()
    }
}
