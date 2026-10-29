package com.homehub.launcher.remote

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homehub.protocol.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Téléphone appairé, tel que la TV le mémorise. */
@Immutable
data class PairedPhone(
    val deviceId: String,
    val name: String,
)

/** Issue d'une tentative d'appairage. */
sealed interface PairOutcome {
    data class Success(val token: String) : PairOutcome
    data object WrongPin : PairOutcome
    data object PairingClosed : PairOutcome
    data object TooManyAttempts : PairOutcome
}

/**
 * Appairage des téléphones, sur le modèle des télécommandes TV officielles :
 *
 *  - l'appairage n'est possible que pendant que l'écran dédié est ouvert sur
 *    la TV, qui affiche alors un code à 4 chiffres ;
 *  - le téléphone qui saisit le bon code reçoit un jeton aléatoire de 256 bits ;
 *  - la TV ne conserve que l'empreinte SHA-256 du jeton ;
 *  - après 5 codes erronés, un nouveau code est tiré.
 *
 * Une personne présente sur le Wi-Fi mais qui ne voit pas l'écran ne peut
 * donc pas prendre le contrôle de la TV.
 */
class PairingManager(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val random = SecureRandom()
    private val mutex = Mutex()

    private val pinState = MutableStateFlow<String?>(null)
    private var wrongAttempts = 0

    /** Code affiché sur la TV ; `null` quand l'appairage est fermé. */
    val pairingPin: StateFlow<String?> = pinState.asStateFlow()

    private val pairedEvents = MutableSharedFlow<PairedPhone>(extraBufferCapacity = 4)

    /** Émet chaque téléphone nouvellement appairé (pour l'écran d'appairage). */
    val newlyPaired: SharedFlow<PairedPhone> = pairedEvents.asSharedFlow()

    /** Téléphones appairés. */
    val pairedPhones: StateFlow<List<PairedPhone>> = dataStore.data
        .map { prefs -> decode(prefs[KEY_PHONES]).map { PairedPhone(it.deviceId, it.name) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val isPairingOpen: Boolean get() = pinState.value != null

    /** Identifiant stable de la box, créé au premier besoin. */
    suspend fun boxId(): String {
        dataStore.data.first()[KEY_BOX_ID]?.let { return it }
        var created = ""
        dataStore.edit { prefs ->
            created = prefs[KEY_BOX_ID] ?: UUID.randomUUID().toString().also { prefs[KEY_BOX_ID] = it }
        }
        return created
    }

    /** Ouvre l'appairage et tire un nouveau code. */
    fun openPairing() {
        wrongAttempts = 0
        pinState.value = newPin()
    }

    /** Ferme l'appairage (écran quitté). */
    fun closePairing() {
        pinState.value = null
        wrongAttempts = 0
    }

    /** Vérifie le jeton présenté par un téléphone. */
    suspend fun authenticate(deviceId: String, token: String): Boolean {
        val hash = sha256(token)
        return decode(dataStore.data.first()[KEY_PHONES]).any { it.deviceId == deviceId && it.tokenHash == hash }
    }

    /** Tente l'appairage d'un téléphone avec le code saisi. */
    suspend fun pair(deviceId: String, deviceName: String, pin: String): PairOutcome = mutex.withLock {
        val expected = pinState.value ?: return PairOutcome.PairingClosed
        if (pin.trim() != expected) {
            wrongAttempts++
            if (wrongAttempts >= MAX_WRONG_ATTEMPTS) {
                wrongAttempts = 0
                pinState.value = newPin()
                return PairOutcome.TooManyAttempts
            }
            return PairOutcome.WrongPin
        }

        val token = newToken()
        val name = sanitize(deviceName)
        dataStore.edit { prefs ->
            val phones = decode(prefs[KEY_PHONES]).filterNot { it.deviceId == deviceId } +
                StoredPhone(deviceId, name, sha256(token))
            prefs[KEY_PHONES] = encode(phones.takeLast(MAX_PHONES))
        }
        // Un code ne sert qu'une fois : le suivant téléphone en obtient un nouveau.
        pinState.value = newPin()
        wrongAttempts = 0
        pairedEvents.tryEmit(PairedPhone(deviceId, name))
        PairOutcome.Success(token)
    }

    /** Révoque tous les téléphones : ils devront s'appairer de nouveau. */
    suspend fun forgetAll() {
        dataStore.edit { it.remove(KEY_PHONES) }
    }

    private fun newPin(): String {
        val max = Math.pow(10.0, Protocol.PIN_LENGTH.toDouble()).toInt()
        return random.nextInt(max).toString().padStart(Protocol.PIN_LENGTH, '0')
    }

    private fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Les séparateurs du format de stockage sont retirés du nom affiché. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[\\t\\n\\r]"), " ").trim().take(40).ifEmpty { "Téléphone" }

    /** Entrée persistée : une ligne par téléphone, champs séparés par une tabulation. */
    private data class StoredPhone(val deviceId: String, val name: String, val tokenHash: String)

    private fun decode(raw: String?): List<StoredPhone> =
        raw.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 3 && parts.none { it.isBlank() }) StoredPhone(parts[0], parts[1], parts[2]) else null
        }

    private fun encode(phones: List<StoredPhone>): String =
        phones.joinToString("\n") { "${it.deviceId}\t${it.name}\t${it.tokenHash}" }

    private companion object {
        val KEY_BOX_ID = stringPreferencesKey("remote_box_id")
        val KEY_PHONES = stringPreferencesKey("remote_phones")
        const val MAX_WRONG_ATTEMPTS = 5
        const val MAX_PHONES = 10
    }
}
