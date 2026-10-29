package com.homehub.launcher.remote

import com.homehub.launcher.data.models.AppInfo
import com.homehub.protocol.RemoteKey
import java.text.Normalizer
import java.util.Locale

/** Action déduite d'une phrase prononcée. */
sealed interface VoiceAction {
    data class OpenApp(val app: AppInfo) : VoiceAction
    data class Search(val query: String, val app: AppInfo?) : VoiceAction
    data class PlayMedia(val query: String, val app: AppInfo?) : VoiceAction
    data class Press(val key: RemoteKey, val times: Int = 1, val label: String) : VoiceAction
    data object GoHome : VoiceAction
    data object OpenHomeHubSettings : VoiceAction
    data object OpenSystemSettings : VoiceAction
    data object OpenAllApps : VoiceAction

    /** Phrase non reconnue comme commande : texte libre (saisie ou recherche). */
    data class FreeText(val text: String) : VoiceAction
}

/**
 * Interprète les commandes vocales en français.
 *
 * Conception : fonction pure, sans dépendance Android, qui transforme une
 * phrase en [VoiceAction]. La normalisation (minuscules, accents, formules de
 * politesse) rend la grammaire tolérante aux variantes de la reconnaissance
 * vocale : « Ouvre Netflix s'il te plaît », « lance netflix », « Netflix ».
 *
 * Exemples reconnus :
 *  - ouvrir : « ouvre YouTube », « lance Plex », « va sur Prime Video », « Netflix »
 *  - chercher : « cherche Stranger Things sur Netflix », « recherche des recettes »
 *  - jouer : « joue Daft Punk sur Deezer », « mets du jazz sur YouTube »
 *  - lecture : « pause », « reprends », « suivant », « précédent »
 *  - volume : « monte le son », « baisse le volume », « coupe le son »
 *  - navigation : « retour », « accueil », « paramètres », « toutes les applications »
 *
 * Point d'extension naturel pour un futur assistant IA : il suffira de
 * remplacer ou compléter cette grammaire par un modèle, sans toucher au reste.
 */
class VoiceCommandInterpreter {

    fun interpret(rawQuery: String, apps: List<AppInfo>): VoiceAction {
        val phrase = normalize(rawQuery)
        if (phrase.isEmpty()) return VoiceAction.FreeText(rawQuery.trim())
        val original = rawQuery.trim()

        controlAction(phrase)?.let { return it }
        screenAction(phrase)?.let { return it }

        // « cherche X (sur APP) »
        SEARCH.matchEntire(phrase)?.let { match ->
            val body = match.groupValues[2]
            val (subject, app) = splitTarget(body, apps)
            return VoiceAction.Search(restoreCase(subject, original), app)
        }

        // « joue X sur APP » : l'application cible est obligatoire, sinon c'est une ouverture.
        PLAY.matchEntire(phrase)?.let { match ->
            val (subject, app) = splitTarget(match.groupValues[2], apps)
            if (app != null && subject.isNotBlank()) return VoiceAction.PlayMedia(restoreCase(subject, original), app)
        }

        // « ouvre APP »
        OPEN.matchEntire(phrase)?.let { match ->
            val target = stripArticles(match.groupValues[2])
            findApp(target, apps)?.let { return VoiceAction.OpenApp(it) }
            // Verbe d'ouverture suivi d'un contenu (« mets les infos ») : on cherche le contenu.
            return VoiceAction.Search(restoreCase(target, original), null)
        }

        // Nom d'application seul.
        findApp(stripArticles(phrase), apps, exactOnly = true)?.let { return VoiceAction.OpenApp(it) }

        return VoiceAction.FreeText(original)
    }

    /** Lecture, volume et navigation. */
    private fun controlAction(phrase: String): VoiceAction? {
        val words = phrase.split(' ').toSet()
        fun has(vararg candidates: String) = candidates.any { it in words || phrase.contains(it) && it.contains(' ') }

        val aboutSound = has("son", "volume", "fort", "plus fort", "moins fort")
        return when {
            has("coupe le son", "mode muet", "muet", "silence", "remets le son", "retablis le son") ->
                VoiceAction.Press(RemoteKey.MUTE, label = "Son coupé / rétabli")
            aboutSound && has("monte", "augmente", "plus fort", "hausse", "plus") ->
                VoiceAction.Press(RemoteKey.VOLUME_UP, times = VOLUME_STEPS, label = "Volume augmenté")
            aboutSound && has("baisse", "diminue", "moins fort", "moins", "descends") ->
                VoiceAction.Press(RemoteKey.VOLUME_DOWN, times = VOLUME_STEPS, label = "Volume baissé")
            phrase in PAUSE_PHRASES ->
                VoiceAction.Press(RemoteKey.PLAY_PAUSE, label = "Pause")
            phrase in RESUME_PHRASES ->
                VoiceAction.Press(RemoteKey.PLAY_PAUSE, label = "Lecture")
            phrase in NEXT_PHRASES ->
                VoiceAction.Press(RemoteKey.NEXT, label = "Suivant")
            phrase in PREVIOUS_PHRASES ->
                VoiceAction.Press(RemoteKey.PREVIOUS, label = "Précédent")
            phrase in FORWARD_PHRASES ->
                VoiceAction.Press(RemoteKey.FAST_FORWARD, label = "Avance rapide")
            phrase in REWIND_PHRASES ->
                VoiceAction.Press(RemoteKey.REWIND, label = "Retour rapide")
            phrase in BACK_PHRASES ->
                VoiceAction.Press(RemoteKey.BACK, label = "Retour")
            phrase in HOME_PHRASES -> VoiceAction.GoHome
            phrase in UP_PHRASES -> VoiceAction.Press(RemoteKey.UP, label = "Haut")
            phrase in DOWN_PHRASES -> VoiceAction.Press(RemoteKey.DOWN, label = "Bas")
            phrase in LEFT_PHRASES -> VoiceAction.Press(RemoteKey.LEFT, label = "Gauche")
            phrase in RIGHT_PHRASES -> VoiceAction.Press(RemoteKey.RIGHT, label = "Droite")
            phrase in OK_PHRASES -> VoiceAction.Press(RemoteKey.CENTER, label = "OK")
            else -> null
        }
    }

    /** Écrans HomeHub et système. */
    private fun screenAction(phrase: String): VoiceAction? {
        val target = OPEN.matchEntire(phrase)?.let { stripArticles(it.groupValues[2]) } ?: stripArticles(phrase)
        val isSettings = target.startsWith("parametre") || target.startsWith("reglage")
        return when {
            isSettings && SYSTEM_WORDS.any { target.contains(it) } -> VoiceAction.OpenSystemSettings
            isSettings -> VoiceAction.OpenHomeHubSettings
            target in ALL_APPS_PHRASES -> VoiceAction.OpenAllApps
            else -> null
        }
    }

    /**
     * Sépare « sujet sur application ». Si la partie après « sur/dans » n'est
     * pas une application connue, la phrase entière est le sujet.
     */
    private fun splitTarget(body: String, apps: List<AppInfo>): Pair<String, AppInfo?> {
        val match = TARGET.matchEntire(body) ?: return stripArticles(body) to null
        val app = findApp(stripArticles(match.groupValues[2]), apps)
            ?: return stripArticles(body) to null
        return stripArticles(match.groupValues[1]) to app
    }

    /**
     * Retrouve une application par son nom prononcé.
     * Ordre : égalité, alias connus, puis inclusion (le nom le plus long gagne).
     */
    fun findApp(spoken: String, apps: List<AppInfo>, exactOnly: Boolean = false): AppInfo? {
        val target = normalize(spoken).replace(" ", "")
        if (target.isEmpty()) return null
        val labelled = apps.map { it to normalize(it.label).replace(" ", "") }

        labelled.firstOrNull { it.second == target }?.let { return it.first }
        ALIASES[target]?.let { canonical ->
            labelled.firstOrNull { it.second.contains(canonical) }?.let { return it.first }
        }
        if (exactOnly) return null
        return labelled
            .filter { (_, label) -> label.length >= MIN_PARTIAL_LENGTH && (target.contains(label) || label.startsWith(target)) }
            .maxByOrNull { it.second.length }
            ?.first
    }

    /**
     * Retrouve la casse d'origine d'un fragment (titre de film, artiste…)
     * à partir de la phrase brute, pour une recherche plus naturelle.
     */
    private fun restoreCase(fragment: String, original: String): String {
        if (fragment.isBlank()) return fragment
        // Même transformation que normalize(), mais à longueur constante :
        // les positions trouvées s'appliquent directement à la phrase d'origine.
        val aligned = stripDiacritics(original.lowercase(Locale.FRENCH)).replace(Regex("[^a-z0-9]"), " ")
        val index = aligned.indexOf(fragment)
        return if (index >= 0 && aligned.length == original.length) {
            original.substring(index, index + fragment.length)
        } else {
            fragment
        }
    }

    private fun stripArticles(text: String): String {
        var result = text.trim()
        var changed = true
        while (changed) {
            changed = false
            for (article in ARTICLES) {
                if (result.startsWith("$article ")) {
                    result = result.removePrefix("$article ").trim()
                    changed = true
                }
            }
        }
        return result
    }

    companion object {
        private const val VOLUME_STEPS = 3
        private const val MIN_PARTIAL_LENGTH = 3

        /** Minuscules, sans accents ni ponctuation, formules de politesse retirées. */
        fun normalize(text: String): String {
            var value = stripDiacritics(text.lowercase(Locale.FRENCH))
                .replace(Regex("[’'`\\-]"), " ")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            for (filler in FILLERS) {
                value = value.replace(Regex("(^| )$filler( |$)"), " ").trim()
            }
            return value.replace(Regex("\\s+"), " ").trim()
        }

        private fun stripDiacritics(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        private val FILLERS = listOf(
            "s il te plait", "s il vous plait", "stp", "svp", "merci",
            "ok google", "dis homehub", "hey homehub", "homehub", "hey",
            "est ce que tu peux", "est ce que vous pouvez", "tu peux", "peux tu", "pourrais tu", "vous pouvez",
            "je veux", "je voudrais", "j aimerais", "on va", "allez", "vas y",
        )

        private val ARTICLES = listOf(
            "l", "le", "la", "les", "un", "une", "des", "du", "de", "d",
            "application", "appli", "app", "l application", "chaine",
        )

        private val SEARCH = Regex("^(cherche|recherche|chercher|rechercher|trouve|trouver|montre moi|montre)\\s+(.+)$")
        private val PLAY = Regex("^(joue|jouer|ecoute|ecouter|mets|met|mettre|lance|lancer|passe|regarde|regarder)\\s+(.+)$")
        private val OPEN = Regex(
            "^(ouvre|ouvrir|lance|lancer|demarre|demarrer|mets|met|mettre|va sur|aller sur|va dans|affiche|afficher|allume|passe sur)\\s+(.+)$",
        )
        /** « sujet sur/dans/avec application » : groupe 1 = sujet, groupe 2 = application. */
        private val TARGET = Regex("^(.+?)\\s+(?:sur|dans|avec)\\s+(.+)$")

        private val SYSTEM_WORDS = listOf("android", "systeme", "tv", "tele", "television", "box", "appareil", "reseau", "wifi")
        private val ALL_APPS_PHRASES = setOf(
            "toutes les applications", "toutes applications", "applications", "mes applications",
            "toutes les applis", "mes applis", "applis",
        )
        private val PAUSE_PHRASES = setOf("pause", "mets en pause", "met en pause", "mettre en pause", "stop", "arrete", "arrete la lecture")
        private val RESUME_PHRASES = setOf("lecture", "reprends", "reprendre", "reprend", "play", "continue", "relance", "remets en route")
        private val NEXT_PHRASES = setOf("suivant", "suivante", "episode suivant", "chanson suivante", "piste suivante", "titre suivant", "video suivante")
        private val PREVIOUS_PHRASES = setOf("precedent", "precedente", "episode precedent", "chanson precedente", "piste precedente", "titre precedent")
        private val FORWARD_PHRASES = setOf("avance", "avance rapide", "avancer")
        private val REWIND_PHRASES = setOf("recule", "reculer", "retour rapide", "retour arriere")
        private val BACK_PHRASES = setOf("retour", "reviens", "revenir", "precedent ecran", "en arriere")
        private val HOME_PHRASES = setOf("accueil", "ecran d accueil", "menu", "menu principal", "maison", "retour a l accueil", "va a l accueil")
        private val UP_PHRASES = setOf("haut", "en haut", "monte")
        private val DOWN_PHRASES = setOf("bas", "en bas", "descends", "descend")
        private val LEFT_PHRASES = setOf("gauche", "a gauche")
        private val RIGHT_PHRASES = setOf("droite", "a droite")
        private val OK_PHRASES = setOf("ok", "valide", "valider", "entree", "selectionne", "clique")

        /** Noms courants qui ne correspondent pas au libellé exact de l'application. */
        private val ALIASES = mapOf(
            "prime" to "primevideo",
            "amazon" to "primevideo",
            "amazonprime" to "primevideo",
            "amazonprimevideo" to "primevideo",
            "yt" to "youtube",
            "plexe" to "plex",
            "dezer" to "deezer",
            "playstore" to "playstore",
            "googleplay" to "playstore",
        )
    }
}
