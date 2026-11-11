package com.lottiefiles.sample

import android.graphics.PointF
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dotlottie.dlplayer.AssetResolver
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.createDefaultConfig
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the player APIs that sit directly on the dotlottie-rs C API. */
@RunWith(AndroidJUnit4::class)
class PlayerParityInstrumentedTest {
    private lateinit var player: DotLottiePlayer

    private fun readTestAsset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    @Before
    fun setUp() {
        player = DotLottiePlayer(createDefaultConfig())
        val buffer = player.allocateBuffer(SIZE, SIZE)
        assertTrue(player.setSwTarget(buffer, SIZE.toUInt(), SIZE.toUInt()))
    }

    @After
    fun tearDown() {
        player.destroy()
    }

    // bezier_path.json exposes a triangle under "shape_path" and its fill under "shape_color".
    private fun loadBezierAnimation() {
        assertTrue(player.loadAnimationData(String(readTestAsset("bezier_path.json"))))
    }

    @Test
    fun slotIdsAndTypes() {
        loadBezierAnimation()

        assertEquals(setOf("shape_path", "shape_color"), player.getSlotIds().toSet())
        assertEquals("bezier_path", player.getSlotType("shape_path"))
        assertEquals("color", player.getSlotType("shape_color"))
        assertNull(player.getSlotType("missing"))
        assertNull(player.getSlot("missing"))
    }

    @Test
    fun setSlotThenReadItBack() {
        loadBezierAnimation()

        assertTrue(player.setSlot("shape_color", """{"a":0,"k":[0,1,0,1]}"""))

        val color = JSONObject(player.getSlot("shape_color")!!).getJSONArray("k")
        assertEquals(0.0, color.getDouble(0), 0.0)
        assertEquals(1.0, color.getDouble(1), 0.0)
        assertTrue(JSONObject(player.getSlots()).has("shape_color"))
        assertFalse(player.setSlot("shape_color", "not json"))
    }

    @Test
    fun resetSlotRestoresTheAnimationValue() {
        loadBezierAnimation()
        val original = player.getSlot("shape_color")

        assertTrue(player.setSlot("shape_color", """{"a":0,"k":[0,1,0,1]}"""))
        assertTrue(player.resetSlot("shape_color"))
        assertEquals(original, player.getSlot("shape_color"))

        assertTrue(player.setSlot("shape_color", """{"a":0,"k":[0,0,1,1]}"""))
        assertTrue(player.resetSlots())
        assertEquals(original, player.getSlot("shape_color"))
    }

    @Test
    fun bezierPathSlot() {
        loadBezierAnimation()
        val square = listOf(PointF(-80f, -80f), PointF(80f, -80f), PointF(80f, 80f), PointF(-80f, 80f))

        assertTrue(player.setBezierPathSlot("shape_path", square, closed = true))

        val path = JSONObject(player.getSlot("shape_path")!!).getJSONObject("k")
        assertEquals(4, path.getJSONArray("v").length())
        assertEquals(-80.0, path.getJSONArray("v").getJSONArray(0).getDouble(0), 0.0)
        assertEquals(0.0, path.getJSONArray("i").getJSONArray(1).getDouble(0), 0.0)
        assertTrue(path.getBoolean("c"))

        val tangents = square.map { PointF(10f, 10f) }
        assertTrue(player.setBezierPathSlot("shape_path", square, closed = false, tangents, tangents))
        val curved = JSONObject(player.getSlot("shape_path")!!).getJSONObject("k")
        assertEquals(10.0, curved.getJSONArray("o").getJSONArray(2).getDouble(1), 0.0)
        assertFalse(curved.getBoolean("c"))

        // Tangent arrays must match the vertex count, and a path needs at least one vertex.
        assertFalse(player.setBezierPathSlot("shape_path", square, true, tangents.take(2), null))
        assertFalse(player.setBezierPathSlot("shape_path", emptyList(), true))
    }

    @Test
    fun stateMachineDefinitionById() {
        assertTrue(player.loadDotlottieData(readTestAsset("star_rating.lottie")))

        val id = player.manifest().stateMachines!!.first().id
        assertEquals("star-rating", id)
        assertTrue(JSONObject(player.getStateMachine(id)!!).has("states"))
        assertNull(player.getStateMachine("missing"))

        assertTrue(player.loadStateMachine(id))
        assertTrue(player.stateMachineStart())
        assertEquals("Running", player.stateMachineStatus())
    }

    @Test
    fun stateMachineOverrideResetAndInputs() {
        loadBezierAnimation()
        assertTrue(player.loadStateMachineData(STATE_MACHINE))
        assertTrue(player.stateMachineStart())

        assertEquals(
            mapOf("rating" to "Numeric", "on" to "Boolean", "label" to "String", "tap" to "Event"),
            player.stateMachineGetInputs().filterKeys { !it.startsWith("@") }
        )

        assertEquals("first", player.stateMachineCurrentState())
        assertTrue(player.stateMachineOverrideCurrentState("second", immediate = true))
        assertEquals("second", player.stateMachineCurrentState())
        assertFalse(player.stateMachineOverrideCurrentState("missing", immediate = true))

        assertTrue(player.stateMachineSetNumericInput("rating", 5f))
        assertEquals(5f, player.stateMachineGetNumericInput("rating"))
        assertTrue(player.stateMachineResetInput("rating"))
        assertEquals(3f, player.stateMachineGetNumericInput("rating"))
    }

    @Test
    fun assetResolverSuppliesExternalImages() {
        val requested = mutableListOf<String>()
        val redPng = Base64.decode(RED_PNG, Base64.DEFAULT)

        assertTrue(
            player.setAssetResolver { src ->
                requested.add(src)
                redPng
            }
        )
        // external_assets.json references one remote image and one external path.
        assertTrue(player.loadAnimationData(String(readTestAsset("external_assets.json"))))
        player.setFrame(0f)
        player.render()

        assertEquals(2, requested.size)
        assertTrue(requested.any { it.startsWith("https://") })
    }

    @Test
    fun assetResolverThatThrowsOrSkipsDoesNotBreakLoading() {
        var calls = 0
        val resolver = AssetResolver { src ->
            calls++
            if (src.startsWith("https://")) throw IllegalStateException("boom") else null
        }

        assertTrue(player.setAssetResolver(resolver))
        assertTrue(player.loadAnimationData(String(readTestAsset("external_assets.json"))))
        player.render()
        assertEquals(2, calls)

        // Clearing stops further calls on the next load.
        assertTrue(player.setAssetResolver(null))
        assertTrue(player.loadAnimationData(String(readTestAsset("external_assets.json"))))
        player.render()
        assertEquals(2, calls)
    }

    private companion object {
        const val SIZE = 128

        const val STATE_MACHINE = """
            {
              "initial": "first",
              "states": [
                {"name": "first", "type": "PlaybackState", "animation": "", "transitions": []},
                {"name": "second", "type": "PlaybackState", "animation": "", "transitions": []}
              ],
              "inputs": [
                {"type": "Numeric", "name": "rating", "value": 3.0},
                {"type": "Boolean", "name": "on", "value": false},
                {"type": "String", "name": "label", "value": "hello"},
                {"type": "Event", "name": "tap"}
              ],
              "interactions": []
            }
        """

        // 8x8 solid red PNG, the same asset the dotlottie-rs resolver tests use.
        const val RED_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAEklEQVR4nGP4z8DwHx9mGBkKAMLXf4EvceABAAAAAElFTkSuQmCC"
    }
}
