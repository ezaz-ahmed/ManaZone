package dev.ezaz.manazone.feedback

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionSoundTest {

    @Test
    fun `every sound renders audible samples whose peak matches the requested volume`() {
        for (sound in CompletionSound.all) {
            val pcm = sound.render(0.5f)
            assertTrue("${sound.id} is empty", pcm.isNotEmpty())
            val peak = pcm.maxOf { abs(it.toInt()) }
            val expected = (0.5 * 0.95 * Short.MAX_VALUE).toInt()
            assertTrue("${sound.id} peak $peak vs $expected", abs(peak - expected) <= 2)
        }
    }

    @Test
    fun `volume is clamped to full scale and never clips`() {
        val pcm = CompletionSound.all.first().render(5f)
        assertTrue(pcm.maxOf { abs(it.toInt()) } <= Short.MAX_VALUE)
        assertEquals(pcm.size, CompletionSound.all.first().render(1f).size)
    }

    @Test
    fun `zero volume renders silence`() {
        assertTrue(CompletionSound.all.first().render(0f).all { it.toInt() == 0 })
    }

    @Test
    fun `unknown ids fall back to the first sound`() {
        assertEquals(CompletionSound.all.first(), CompletionSound.byId("nope"))
        assertEquals(CompletionSound.all.first(), CompletionSound.byId(null))
    }

    @Test
    fun `sound ids are unique and include the default`() {
        val ids = CompletionSound.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(CompletionSound.DEFAULT_ID in ids)
    }
}
