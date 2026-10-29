package com.homehub.protocol

/**
 * Catalogue des méthodes et événements de l'API locale v2.
 *
 * Point unique de vérité : le serveur TV enregistre ses handlers et le
 * client téléphone formule ses appels à partir de ces mêmes objets.
 * Ajouter une méthode : la documenter, la déclarer ici, ajouter une fixture.
 */
object Methods {
    val SessionHello = Method("session.hello", HelloParams.serializer(), HelloResult.serializer())
    val SessionAuth = Method("session.auth", AuthParams.serializer(), AuthResult.serializer())
    val SessionPair = Method("session.pair", PairParams.serializer(), PairResult.serializer())
    val SessionPing = Method("session.ping", Empty.serializer(), Empty.serializer())

    val InputKey = Method("input.key", KeyParams.serializer(), Applied.serializer())
    val InputText = Method("input.text", TextParams.serializer(), Applied.serializer())

    val AppList = Method("app.list", Empty.serializer(), AppListResult.serializer())
    val AppIcon = Method("app.icon", IconParams.serializer(), IconResult.serializer())
    val AppLaunch = Method("app.launch", LaunchParams.serializer(), LaunchResult.serializer())
    val AppOpen = Method("app.open", OpenParams.serializer(), OpenResult.serializer())

    val UiNavigate = Method("ui.navigate", NavigateParams.serializer(), NavigateResult.serializer())
    val UiToast = Method("ui.toast", ToastParams.serializer(), Empty.serializer())

    val CardsShow = Method("cards.show", CardsShowParams.serializer(), CardsShowResult.serializer())
    val CardsUpdate = Method("cards.update", CardsUpdateParams.serializer(), CardsUpdateResult.serializer())
    val CardsDismiss = Method("cards.dismiss", CardsDismissParams.serializer(), CardsDismissResult.serializer())

    /** Toutes les méthodes (tests de contrat, documentation). */
    val all: List<Method<*, *>> = listOf(
        SessionHello, SessionAuth, SessionPair, SessionPing,
        InputKey, InputText,
        AppList, AppIcon, AppLaunch, AppOpen,
        UiNavigate, UiToast,
        CardsShow, CardsUpdate, CardsDismiss,
    )

    /** Méthodes accessibles avant authentification. */
    val public: Set<String> = setOf(SessionHello.name, SessionAuth.name, SessionPair.name, SessionPing.name)
}

/** Événements TV → téléphone. */
object Events {
    val Status = Event("session.status", SessionStatus.serializer())
    val CardAction = Event("card.action", CardActionEvent.serializer())
    val CardsDismissed = Event("cards.dismissed", CardsDismissedEvent.serializer())

    /** Tous les événements (tests de contrat). */
    val all: List<Event<*>> = listOf(Status, CardAction, CardsDismissed)
}
