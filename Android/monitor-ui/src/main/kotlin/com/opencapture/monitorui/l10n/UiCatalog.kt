package com.opencapture.monitorui.l10n

import android.content.Context
import android.content.res.Resources
import com.opencapture.monitorui.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Display-time translation of operator copy. Code keeps English as the source text; a catalog per
 * locale (`res/raw-<locale>/…_ui_catalog.tsv`) maps whole strings, or `{}` templates, to the
 * translation. The resource system picks the catalog for the device language, and the default
 * (unqualified) catalog is empty, so English devices see the code's text unchanged.
 *
 * Catalog lines: `English<TAB>Translation`. `#` starts a comment. `\n`, `\t` and `\\` are escapes.
 * A `{}` in the English side matches any run of text; the translation places the captures with
 * `{}` (in order) or `{1}`…`{9}`. A capture that is itself an exact catalog entry is translated too.
 */
object UiCatalog {
    private val registered = CopyOnWriteArrayList<Int>()
    private val tables = ConcurrentHashMap<String, UiTable>()

    /** Adds an app's own catalog (a raw resource with locale variants) after the shared one. */
    fun register(catalog: Int) {
        if (registered.addIfAbsent(catalog)) tables.clear()
    }

    fun table(context: Context): UiTable {
        val resources = context.resources
        val tag = resources.configuration.locales[0].toLanguageTag()
        return tables.getOrPut(tag) { UiTable.load(resources, listOf(R.raw.opencine_ui_catalog) + registered) }
    }

    fun translate(context: Context, text: String): String = table(context).translate(text)
}

/** One locale's catalog: exact entries, then templates from the most literal text to the least. */
class UiTable internal constructor(
    private val exact: Map<String, String>,
    private val templates: List<UiTemplate>,
) {
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > CACHE_LIMIT
    }

    val isEmpty: Boolean get() = exact.isEmpty() && templates.isEmpty()

    fun translate(text: String): String {
        if (isEmpty || !WORD.containsMatchIn(text)) return text
        exact[text]?.let { return it }
        synchronized(cache) { cache[text] }?.let { return it }
        val result = templates.firstNotNullOfOrNull { it.apply(text, exact) } ?: text
        synchronized(cache) { cache[text] = result }
        return result
    }

    companion object {
        private const val CACHE_LIMIT = 1024
        private val WORD = Regex("[A-Za-z]{2}")

        internal fun load(resources: Resources, catalogs: List<Int>): UiTable {
            val exact = HashMap<String, String>()
            val templates = ArrayList<UiTemplate>()
            for (id in catalogs) {
                val text = try {
                    resources.openRawResource(id).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (_: Resources.NotFoundException) {
                    continue
                }
                parse(text, exact, templates)
            }
            templates.sortByDescending { it.literalLength }
            return UiTable(exact, templates)
        }

        /** Parses catalog text; later entries replace earlier ones with the same English. */
        fun parse(text: String, exact: MutableMap<String, String>, templates: MutableList<UiTemplate>) {
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val english = unescape(line.substring(0, tab))
                val translated = unescape(line.substring(tab + 1).trimEnd('\r'))
                if (translated.isEmpty() || translated == english) continue
                if ("{}" in english) {
                    templates.removeAll { it.english == english }
                    templates += UiTemplate(english, translated)
                } else {
                    exact[english] = translated
                }
            }
        }

        fun of(text: String): UiTable {
            val exact = HashMap<String, String>()
            val templates = ArrayList<UiTemplate>()
            parse(text, exact, templates)
            templates.sortByDescending { it.literalLength }
            return UiTable(exact, templates)
        }

        private fun unescape(value: String): String {
            if ('\\' !in value) return value
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        '\\' -> out.append('\\')
                        else -> out.append(c).append(value[i + 1])
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}

/** A `{}` template: the English side becomes an anchored pattern, the captures fill the other side. */
class UiTemplate internal constructor(val english: String, private val translated: String) {
    private val pattern: Regex = english.split("{}").joinToString("(.+?)", "^", "$") { Regex.escape(it) }
        .let { Regex(it, RegexOption.DOT_MATCHES_ALL) }
    val literalLength: Int = english.replace("{}", "").length

    fun apply(text: String, exact: Map<String, String>): String? {
        val match = pattern.matchEntire(text) ?: return null
        val captures = match.groupValues.drop(1).map { exact[it] ?: it }
        var next = 0
        return PLACE.replace(translated) { hole ->
            val index = hole.groupValues[1].toIntOrNull()?.minus(1) ?: next++
            captures.getOrElse(index) { hole.value }
        }
    }

    private companion object {
        val PLACE = Regex("""\{([1-9]?)\}""")
    }
}
