package com.kharagedition.tibetankeyboard.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the keyboard-layout invariants we changed by reading the actual res/xml files, so a
 * regression in any layout fails the build:
 *  - the AI-chat "+" key (codes="-20", the old Gemini chat launcher) is removed everywhere, and
 *  - the space bar absorbed its width (now 30%).
 */
class KeyboardLayoutTest {

    private val layouts = listOf(
        "tibetan_uchen_alphabet_1.xml",
        "tibetan_uchen_alphabet_2.xml",
        "tibetan_uchen_symbol_1.xml",
        "qwerty.xml",
        "qwerty_cap.xml",
        "symbol_en.xml",
    )

    private fun xmlDir(): File =
        listOf(File("src/main/res/xml"), File("app/src/main/res/xml"))
            .firstOrNull { it.isDirectory }
            ?: error("res/xml directory not found (cwd=${File("").absolutePath})")

    @Test
    fun noChatPlusKey_inAnyKeyboardLayout() {
        val dir = xmlDir()
        for (name in layouts) {
            val text = File(dir, name).readText()
            assertFalse(
                "$name still contains the removed AI-chat key (codes=\"-20\")",
                text.contains("codes=\"-20\"")
            )
        }
    }

    @Test
    fun spaceBarWidened_toAbsorbRemovedKey() {
        val dir = xmlDir()
        for (name in layouts) {
            val text = File(dir, name).readText()
            assertTrue(
                "$name space bar should be 30% wide after removing the + key",
                text.contains("android:keyWidth=\"30%p\" android:isRepeatable=\"true\" android:codes=\"32\"")
            )
        }
    }
}
