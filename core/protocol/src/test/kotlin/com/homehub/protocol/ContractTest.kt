package com.homehub.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Tests de contrat : chaque exemple JSON de l'API (fixtures/) doit être lu
 * puis réécrit sans perte par les types partagés par la TV et le téléphone.
 * Toute méthode déclarée doit avoir sa fixture.
 */
class ContractTest {

    private val fixtures: Map<String, JsonObject> by lazy {
        val directory = File(javaClass.classLoader!!.getResource("fixtures")!!.toURI())
        directory.listFiles { file -> file.extension == "json" }!!
            .associate { file ->
                val json = JsonRpc.json.parseToJsonElement(file.readText()).jsonObject
                json.getValue("method").jsonPrimitive.content to json
            }
    }

    @Test
    fun everyMethodHasAFixture() {
        val missing = Methods.all.map { it.name }.filterNot { it in fixtures }
        assertTrue("Fixtures manquantes : $missing", missing.isEmpty())
    }

    @Test
    fun fixturesRoundTripWithoutLoss() {
        Methods.all.forEach { method ->
            val fixture = fixtures[method.name] ?: return@forEach
            assertRoundTrip(method.name + " params", method.params, fixture.getValue("params"))
            assertRoundTrip(method.name + " result", method.result, fixture.getValue("result"))
        }
    }

    @Test
    fun everyEventHasAFixtureAndRoundTrips() {
        val directory = File(javaClass.classLoader!!.getResource("events")!!.toURI())
        val events = directory.listFiles { file -> file.extension == "json" }!!.associate { file ->
            val json = JsonRpc.json.parseToJsonElement(file.readText()).jsonObject
            json.getValue("event").jsonPrimitive.content to json.getValue("params")
        }
        Events.all.forEach { event ->
            val params = events[event.name] ?: return@forEach fail("Fixture d'événement manquante : ${event.name}")
            assertRoundTrip(event.name, event.params, params)
        }
    }

    @Test
    fun unknownCardKindFallsBackToGeneric() {
        val json = JsonRpc.json.parseToJsonElement("""{"id":"x","kind":"hologram","title":"Futur"}""")
        val card = JsonRpc.json.decodeFromJsonElement(Card.serializer(), json)
        assertEquals(CardKind.GENERIC, card.kind)
    }

    @Test
    fun requestEnvelopeIsValidJsonRpc() {
        val text = JsonRpc.request(7, Methods.AppLaunch, LaunchParams("com.netflix.ninja"))
        val message = JsonRpc.parse(text)
        assertTrue(message is RpcMessage.Call)
        message as RpcMessage.Call
        assertEquals("app.launch", message.method)
        assertEquals("7", message.id.toString())
        assertEquals(
            LaunchParams("com.netflix.ninja"),
            JsonRpc.decodeParams(Methods.AppLaunch.params, message.params),
        )
    }

    @Test
    fun errorEnvelopeIsParsed() {
        val text = JsonRpc.error(
            JsonRpc.json.parseToJsonElement("3"),
            RpcException(ErrorCodes.APP_NOT_INSTALLED, "Application non installée"),
        )
        val message = JsonRpc.parse(text)
        assertTrue(message is RpcMessage.Error)
        assertEquals(ErrorCodes.APP_NOT_INSTALLED, (message as RpcMessage.Error).error.code)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val params = JsonRpc.json.parseToJsonElement("""{"package":"a.b","futureField":42}""")
        assertEquals(LaunchParams("a.b"), JsonRpc.decodeParams(Methods.AppLaunch.params, params))
    }

    @Test
    fun invalidJsonIsAParseError() {
        try {
            JsonRpc.parse("{pas du json")
            fail("Une erreur était attendue")
        } catch (error: RpcException) {
            assertEquals(ErrorCodes.PARSE_ERROR, error.code)
        }
    }

    @Test
    fun invalidParamsAreReported() {
        val params = JsonRpc.json.parseToJsonElement("""{"key":"NOT_A_KEY"}""")
        try {
            JsonRpc.decodeParams(Methods.InputKey.params, params)
            fail("Une erreur était attendue")
        } catch (error: RpcException) {
            assertEquals(ErrorCodes.INVALID_PARAMS, error.code)
        }
    }

    /** Décode, réencode, et vérifie que chaque champ de l'exemple est conservé à l'identique. */
    private fun <T> assertRoundTrip(label: String, serializer: KSerializer<T>, original: JsonElement) {
        val decoded = JsonRpc.json.decodeFromJsonElement(serializer, original)
        assertNotNull(label, decoded)
        val encoded = JsonRpc.json.encodeToJsonElement(serializer, decoded)
        assertSubset(label, original, encoded)
    }

    private fun assertSubset(path: String, expected: JsonElement, actual: JsonElement) {
        if (expected is JsonObject) {
            val actualObject = actual as? JsonObject ?: return fail("$path : objet attendu, obtenu $actual")
            expected.forEach { (key, value) ->
                val actualValue = actualObject[key] ?: return fail("$path.$key absent après réécriture")
                assertSubset("$path.$key", value, actualValue)
            }
        } else {
            assertEquals(path, expected, actual)
        }
    }
}
