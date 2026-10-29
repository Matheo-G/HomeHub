package com.homehub.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Méthode typée de l'API : nom, paramètres, résultat.
 * Déclarées une seule fois dans [Methods], utilisées par la TV (serveur)
 * comme par le téléphone (client).
 */
class Method<P, R>(
    val name: String,
    val params: KSerializer<P>,
    val result: KSerializer<R>,
)

/** Événement typé (notification JSON-RPC, sans réponse). */
class Event<P>(
    val name: String,
    val params: KSerializer<P>,
)

/** Erreur JSON-RPC, levée par un handler ou reçue d'un appel distant. */
class RpcException(
    val code: Int,
    override val message: String,
    val data: JsonElement? = null,
) : Exception(message)

/** Codes d'erreur (docs/api/local-api.md, section Erreurs). */
object ErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    const val NOT_AUTHENTICATED = 1001
    const val PAIRING_CLOSED = 1002
    const val WRONG_PIN = 1003
    const val TOO_MANY_ATTEMPTS = 1004
    const val INCOMPATIBLE_PROTOCOL = 1005
    const val UNKNOWN_TOKEN = 1006

    const val APP_NOT_INSTALLED = 2001
    const val FULL_CONTROL_REQUIRED = 2002
    const val NO_TEXT_FIELD = 2003
    const val UNSUPPORTED = 2004

    const val RATE_LIMITED = 3001
}

/** Message JSON-RPC reçu, après analyse de l'enveloppe. */
sealed interface RpcMessage {
    /** Requête (avec `id`) ou notification (`id` absent). */
    data class Call(val id: JsonElement?, val method: String, val params: JsonElement) : RpcMessage {
        val isNotification: Boolean get() = id == null
    }

    data class Result(val id: JsonElement, val result: JsonElement) : RpcMessage

    data class Error(val id: JsonElement?, val error: RpcException) : RpcMessage
}

/**
 * Encodage et décodage JSON-RPC 2.0.
 *
 * Tolérant par conception : les champs inconnus sont ignorés, ce qui permet
 * au téléphone et à la TV d'évoluer à des rythmes différents.
 */
object JsonRpc {

    /** Configuration JSON commune aux deux applications. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Analyse une trame ; lève [RpcException] (-32700 / -32600) si elle est invalide. */
    fun parse(text: String): RpcMessage {
        if (text.length > Protocol.MAX_MESSAGE_LENGTH) {
            throw RpcException(ErrorCodes.INVALID_REQUEST, "Message trop long")
        }
        val element = try {
            json.parseToJsonElement(text)
        } catch (error: SerializationException) {
            throw RpcException(ErrorCodes.PARSE_ERROR, "JSON illisible")
        }
        val obj = element as? JsonObject ?: throw RpcException(ErrorCodes.INVALID_REQUEST, "Objet attendu")
        val id = obj["id"]?.takeUnless { it is JsonNull }
        val method = (obj["method"] as? JsonPrimitive)?.contentOrNull
        return when {
            method != null -> RpcMessage.Call(id, method, obj["params"] ?: JsonObject(emptyMap()))
            obj.containsKey("error") -> {
                val error = obj["error"]?.jsonObject ?: throw RpcException(ErrorCodes.INVALID_REQUEST, "Erreur invalide")
                RpcMessage.Error(
                    id,
                    RpcException(
                        code = error["code"]?.jsonPrimitive?.intOrNull ?: ErrorCodes.INTERNAL_ERROR,
                        message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Erreur",
                        data = error["data"],
                    ),
                )
            }
            obj.containsKey("result") && id != null -> RpcMessage.Result(id, obj["result"] ?: JsonNull)
            else -> throw RpcException(ErrorCodes.INVALID_REQUEST, "Requête invalide")
        }
    }

    fun <P, R> request(id: Long, method: Method<P, R>, params: P): String =
        envelope(JsonPrimitive(id)) {
            put("method", method.name)
            put("params", json.encodeToJsonElement(method.params, params))
        }

    fun <P, R> notification(method: Method<P, R>, params: P): String =
        envelope(null) {
            put("method", method.name)
            put("params", json.encodeToJsonElement(method.params, params))
        }

    fun <P> event(event: Event<P>, params: P): String =
        envelope(null) {
            put("method", event.name)
            put("params", json.encodeToJsonElement(event.params, params))
        }

    fun <R> result(id: JsonElement, serializer: KSerializer<R>, value: R): String =
        envelope(id) { put("result", json.encodeToJsonElement(serializer, value)) }

    fun error(id: JsonElement?, error: RpcException): String =
        envelope(id ?: JsonNull) {
            put(
                "error",
                buildJsonObject {
                    put("code", error.code)
                    put("message", error.message)
                    error.data?.let { put("data", it) }
                },
            )
        }

    /** Décode des paramètres ; lève -32602 si leur forme est invalide. */
    fun <P> decodeParams(serializer: KSerializer<P>, params: JsonElement): P = try {
        json.decodeFromJsonElement(serializer, params)
    } catch (error: SerializationException) {
        throw RpcException(ErrorCodes.INVALID_PARAMS, "Paramètres invalides : ${error.message}")
    } catch (error: IllegalArgumentException) {
        throw RpcException(ErrorCodes.INVALID_PARAMS, "Paramètres invalides : ${error.message}")
    }

    /** Décode un résultat reçu par le client. */
    fun <R> decodeResult(serializer: KSerializer<R>, result: JsonElement): R = try {
        json.decodeFromJsonElement(serializer, result)
    } catch (error: SerializationException) {
        throw RpcException(ErrorCodes.INTERNAL_ERROR, "Réponse invalide : ${error.message}")
    } catch (error: IllegalArgumentException) {
        throw RpcException(ErrorCodes.INTERNAL_ERROR, "Réponse invalide : ${error.message}")
    }

    private inline fun envelope(id: JsonElement?, body: JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            body()
        }.toString()
}
