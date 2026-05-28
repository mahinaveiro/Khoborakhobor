package mahin.studio.khoborakhobor

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundThreadGuardTest {
    @Test
    fun sourceJsonLoadRejectsMainThread() {
        val error = assertThrows(IllegalStateException::class.java) {
            BackgroundThreadGuard.requireBackgroundThread("loadSources") { true }
        }

        assertTrue(error.message.orEmpty().contains("loadSources"))
    }
}
