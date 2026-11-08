package com.opencapture.monitorui.l10n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiTableTest {
    private val table = UiTable.of(
        """
        # comment
        Gimbal	雲台
        White balance	白平衡
        {} was not sent.	{} 未送出。
        Saved {} clip{} to Photos	已將 {1} 個片段儲存到相簿
        Joining Wi-Fi · attempt {} of {}	正在加入 Wi-Fi · 第 {} 次，共 {} 次
        Buffering from camera…\n{}	正在從相機緩衝…\n{}
        Same	Same
        """.trimIndent(),
    )

    @Test
    fun exactEntriesTranslate() {
        assertEquals("雲台", table.translate("Gimbal"))
    }

    @Test
    fun unknownTextAndTextWithoutWordsPassThrough() {
        assertEquals("Shutter", table.translate("Shutter"))
        assertEquals("00:01:23", table.translate("00:01:23"))
        assertEquals("Same", table.translate("Same"))
    }

    @Test
    fun templatesFillCapturesAndTranslateExactCaptures() {
        assertEquals("白平衡 未送出。", table.translate("White balance was not sent."))
        assertEquals("已將 3 個片段儲存到相簿", table.translate("Saved 3 clips to Photos"))
        assertEquals("正在加入 Wi-Fi · 第 1 次，共 3 次", table.translate("Joining Wi-Fi · attempt 1 of 3"))
        assertEquals("正在從相機緩衝…\n42%", table.translate("Buffering from camera…\n42%"))
    }

    @Test
    fun repeatedLookupsUseTheSameResult() {
        repeat(3) { assertEquals("Mode 4 未送出。", table.translate("Mode 4 was not sent.")) }
    }

    @Test
    fun emptyCatalogIsIdentity() {
        val empty = UiTable.of("# nothing\n")
        assertTrue(empty.isEmpty)
        assertEquals("Gimbal", empty.translate("Gimbal"))
    }

    /** Every shipped catalog line parses, and a template's translation has a place for each capture. */
    @Test
    fun shippedCatalogsAreWellFormed() {
        val catalogs = File("src/main/res").walk().filter { it.name == "opencine_ui_catalog.tsv" }.toList()
        assertTrue(catalogs.isNotEmpty())
        val holes = Regex("""\{[1-9]?\}""")
        for (file in catalogs) {
            file.readLines().forEachIndexed { index, line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
                val parts = line.split('\t')
                assertEquals(2, parts.size, "${file.path}:${index + 1} needs exactly one tab")
                val captures = parts[0].split("{}").size - 1
                val placed = holes.findAll(parts[1]).count()
                assertTrue(parts[1].isNotBlank(), "${file.path}:${index + 1} has no translation")
                assertTrue(placed <= captures, "${file.path}:${index + 1} places more than it captures")
            }
            UiTable.of(file.readText()) // every template compiles
        }
    }
}
