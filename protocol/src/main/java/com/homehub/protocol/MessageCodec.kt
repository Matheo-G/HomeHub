package com.homehub.protocol

import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Encodage / décodage JSON des messages (une ligne par message).
 *
 * Le décodage ne lève jamais d'exception : une trame invalide ou d'un type
 * inconnu donne `null`, que l'appelant traite comme une requête erronée.
 * Un champ ajouté par une version future est ainsi simplement ignoré.
 */
object MessageCodec {

    // --- Téléphone → TV -------------------------------------------------

    fun encode(message: ClientMessage): String {
        val json = JSONObject()
        when (message) {
            is ClientMessage.Hello -> json.type("hello")
                .put("id", message.deviceId)
                .put("name", message.deviceName)
                .put("v", message.version)
            is ClientMessage.Auth -> json.type("auth").put("token", message.token)
            is ClientMessage.Pair -> json.type("pair").put("pin", message.pin)
            is ClientMessage.Key -> json.type("key").put("k", message.key.name)
            is ClientMessage.Voice -> json.type("voice").put("q", message.query)
            is ClientMessage.Text -> json.type("text").put("q", message.text)
            is ClientMessage.Launch -> json.type("launch").put("p", message.packageName)
            ClientMessage.Apps -> json.type("apps")
            is ClientMessage.Icon -> json.type("icon").put("p", message.packageName)
            ClientMessage.Ping -> json.type("ping")
        }
        return json.toString()
    }

    fun decodeClient(line: String): ClientMessage? = parse(line) { json ->
        when (json.optString("t")) {
            "hello" -> ClientMessage.Hello(
                deviceId = json.requireText("id"),
                deviceName = json.optString("name").ifBlank { "Téléphone" },
                version = json.optInt("v", Protocol.VERSION),
            )
            "auth" -> ClientMessage.Auth(json.requireText("token"))
            "pair" -> ClientMessage.Pair(json.requireText("pin"))
            "key" -> RemoteKey.fromWire(json.optString("k"))?.let(ClientMessage::Key)
            "voice" -> ClientMessage.Voice(json.requireText("q"))
            "text" -> ClientMessage.Text(json.optString("q"))
            "launch" -> ClientMessage.Launch(json.requireText("p"))
            "apps" -> ClientMessage.Apps
            "icon" -> ClientMessage.Icon(json.requireText("p"))
            "ping" -> ClientMessage.Ping
            else -> null
        }
    }

    // --- TV → téléphone -------------------------------------------------

    fun encode(message: ServerMessage): String {
        val json = JSONObject()
        when (message) {
            is ServerMessage.Info -> json.type("info")
                .put("id", message.boxId)
                .put("name", message.boxName)
                .put("sdk", message.sdk)
                .put("v", message.version)
            is ServerMessage.Welcome -> json.type("welcome").put("full", message.fullControl)
            is ServerMessage.PairingRequired -> json.type("pairing").put("open", message.pairingOpen)
            is ServerMessage.Paired -> json.type("paired").put("token", message.token)
            is ServerMessage.Result -> json.type("result").put("ok", message.ok).put("msg", message.message)
            is ServerMessage.Apps -> {
                val apps = JSONArray()
                message.apps.forEach { app ->
                    apps.put(
                        JSONObject()
                            .put("p", app.packageName)
                            .put("l", app.label)
                            .put("f", app.favorite),
                    )
                }
                json.type("apps").put("apps", apps)
            }
            is ServerMessage.Icon -> json.type("icon")
                .put("p", message.packageName)
                .put("png", message.png?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            is ServerMessage.Status -> json.type("status").put("full", message.fullControl)
            is ServerMessage.Error -> json.type("error").put("code", message.code.name).put("msg", message.message)
            ServerMessage.Pong -> json.type("pong")
        }
        return json.toString()
    }

    fun decodeServer(line: String): ServerMessage? = parse(line) { json ->
        when (json.optString("t")) {
            "info" -> ServerMessage.Info(
                boxId = json.requireText("id"),
                boxName = json.optString("name").ifBlank { "HomeHub" },
                sdk = json.optInt("sdk"),
                version = json.optInt("v", Protocol.VERSION),
            )
            "welcome" -> ServerMessage.Welcome(json.optBoolean("full"))
            "pairing" -> ServerMessage.PairingRequired(json.optBoolean("open"))
            "paired" -> ServerMessage.Paired(json.requireText("token"))
            "result" -> ServerMessage.Result(json.optBoolean("ok"), json.optString("msg"))
            "apps" -> {
                val array = json.optJSONArray("apps") ?: JSONArray()
                val apps = (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val packageName = item.optString("p").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    RemoteApp(packageName, item.optString("l").ifBlank { packageName }, item.optBoolean("f"))
                }
                ServerMessage.Apps(apps)
            }
            "icon" -> ServerMessage.Icon(
                packageName = json.requireText("p"),
                png = json.optString("png").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() },
            )
            "status" -> ServerMessage.Status(json.optBoolean("full"))
            "error" -> ServerMessage.Error(ErrorCode.fromWire(json.optString("code")), json.optString("msg"))
            "pong" -> ServerMessage.Pong
            else -> null
        }
    }

    // --- Outils ---------------------------------------------------------

    private fun JSONObject.type(value: String): JSONObject = put("t", value)

    /** Champ texte obligatoire : absent ou vide, le message entier est rejeté. */
    private fun JSONObject.requireText(name: String): String {
        val value = optString(name)
        if (value.isBlank()) throw JSONException("Champ manquant : $name")
        return value
    }

    private inline fun <T> parse(line: String, block: (JSONObject) -> T?): T? = try {
        if (line.length > Protocol.MAX_LINE_LENGTH) null else block(JSONObject(line))
    } catch (_: JSONException) {
        null
    }
}
