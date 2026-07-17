package com.dotlottie.dlplayer

import android.graphics.Color
import androidx.annotation.ColorInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Lottie text document used to drive a text slot.
 *
 * Field names are friendly Kotlin names; they are serialized to/from the short
 * Lottie keys used by the native player (t, f, s, fc, sc, sw, of, lh, tr, j, ca,
 * ls, sz, ps). Only [text] is required — leave the rest null to keep the value
 * already present in the animation for that property.
 *
 * Colors are RGBA components in the 0..1 range, e.g. red = [1f, 0f, 0f, 1f].
 * Use [fillColorOf] / [strokeColorOf] to build them from an Android `@ColorInt`.
 */
data class TextDocument(
    val text: String,
    val fontName: String? = null,
    val fontSize: Float? = null,
    val fillColor: List<Float>? = null,
    val strokeColor: List<Float>? = null,
    val strokeWidth: Float? = null,
    val strokeOverFill: Boolean? = null,
    val lineHeight: Float? = null,
    val tracking: Float? = null,
    val justify: Int? = null,
    val textCaps: Int? = null,
    val baselineShift: Float? = null,
    val wrapSize: List<Float>? = null,
    val wrapPosition: List<Float>? = null,
) {
    /** Serializes this document to the Lottie text-document object (the `s` payload). */
    internal fun toDocumentJson(): JSONObject = JSONObject().apply {
        put("t", text)
        fontName?.let { put("f", it) }
        fontSize?.let { put("s", it.toDouble()) }
        fillColor?.let { put("fc", it.toJsonArray()) }
        strokeColor?.let { put("sc", it.toJsonArray()) }
        strokeWidth?.let { put("sw", it.toDouble()) }
        strokeOverFill?.let { put("of", it) }
        lineHeight?.let { put("lh", it.toDouble()) }
        tracking?.let { put("tr", it.toDouble()) }
        justify?.let { put("j", it) }
        textCaps?.let { put("ca", it) }
        baselineShift?.let { put("ls", it.toDouble()) }
        wrapSize?.let { put("sz", it.toJsonArray()) }
        wrapPosition?.let { put("ps", it.toJsonArray()) }
    }

    /**
     * Serializes this document to a full text-slot JSON string
     * (`{"k":[{"t":<frame>,"s":{...}}]}`), the format accepted by the native
     * `set_slot_str` API.
     */
    fun toSlotJson(frame: Int = 0): String {
        val keyframe = JSONObject().apply {
            put("t", frame)
            put("s", toDocumentJson())
        }
        return JSONObject().apply {
            put("k", JSONArray().put(keyframe))
        }.toString()
    }

    companion object {
        /** Builds an RGBA 0..1 list from an Android `@ColorInt`. */
        fun fillColorOf(@ColorInt color: Int): List<Float> = listOf(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f,
            Color.alpha(color) / 255f,
        )

        /** Alias of [fillColorOf] for stroke colors. */
        fun strokeColorOf(@ColorInt color: Int): List<Float> = fillColorOf(color)

        /**
         * Parses a text-slot JSON string (as returned by the native `get_slot_str`)
         * into a [TextDocument], using the first keyframe's document. Returns null
         * if the JSON does not contain a usable text document.
         */
        fun fromSlotJson(json: String): TextDocument? {
            if (json.isBlank()) return null
            return runCatching {
                val root = JSONObject(json)
                val keyframes = root.optJSONArray("k") ?: return null
                if (keyframes.length() == 0) return null
                val doc = keyframes.getJSONObject(0).optJSONObject("s") ?: return null
                TextDocument(
                    text = doc.optString("t", ""),
                    fontName = doc.optStringOrNull("f"),
                    fontSize = doc.optFloatOrNull("s"),
                    fillColor = doc.optFloatList("fc"),
                    strokeColor = doc.optFloatList("sc"),
                    strokeWidth = doc.optFloatOrNull("sw"),
                    strokeOverFill = if (doc.has("of")) doc.optBoolean("of") else null,
                    lineHeight = doc.optFloatOrNull("lh"),
                    tracking = doc.optFloatOrNull("tr"),
                    justify = doc.optIntOrNull("j"),
                    textCaps = doc.optIntOrNull("ca"),
                    baselineShift = doc.optFloatOrNull("ls"),
                    wrapSize = doc.optFloatList("sz"),
                    wrapPosition = doc.optFloatList("ps"),
                )
            }.getOrNull()
        }
    }
}

private fun List<Float>.toJsonArray(): JSONArray =
    JSONArray().also { arr -> forEach { arr.put(it.toDouble()) } }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private fun JSONObject.optFloatOrNull(key: String): Float? =
    if (has(key) && !isNull(key)) getDouble(key).toFloat() else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null

private fun JSONObject.optFloatList(key: String): List<Float>? {
    val arr = optJSONArray(key) ?: return null
    return (0 until arr.length()).map { arr.getDouble(it).toFloat() }
}
