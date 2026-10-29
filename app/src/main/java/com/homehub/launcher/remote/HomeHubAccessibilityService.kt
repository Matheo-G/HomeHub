package com.homehub.launcher.remote

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.homehub.protocol.RemoteKey

/**
 * « Contrôle complet » : permet à la télécommande d'agir dans les autres
 * applications (Netflix, YouTube…), pas seulement dans HomeHub.
 *
 * Android interdit à une application ordinaire d'injecter des touches dans
 * une autre. Le service d'accessibilité, activé explicitement par
 * l'utilisateur, offre une alternative officielle :
 *  - Retour, sur toutes les versions ;
 *  - pavé directionnel et OK natifs à partir d'Android 13 ;
 *  - avant Android 13 (Mi Box : Android 8/9), déplacement du focus et clic via
 *    l'arbre d'accessibilité : fiable dans les interfaces Android classiques,
 *    plus limité dans les applications qui dessinent leur propre interface ;
 *  - saisie de texte dans le champ actif (recherche Netflix, YouTube…).
 *
 * Le service ne lit aucun contenu en continu : il ne s'abonne qu'aux
 * changements de fenêtre et n'interroge l'écran qu'au moment d'une commande.
 */
class HomeHubAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }

    /** Exécute une touche de navigation dans l'application au premier plan. */
    @Suppress("InlinedApi")
    fun navigate(key: RemoteKey): Boolean = when (key) {
        RemoteKey.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        RemoteKey.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        RemoteKey.UP -> directional(View.FOCUS_UP, globalAction = GLOBAL_ACTION_DPAD_UP)
        RemoteKey.DOWN -> directional(View.FOCUS_DOWN, globalAction = GLOBAL_ACTION_DPAD_DOWN)
        RemoteKey.LEFT -> directional(View.FOCUS_LEFT, globalAction = GLOBAL_ACTION_DPAD_LEFT)
        RemoteKey.RIGHT -> directional(View.FOCUS_RIGHT, globalAction = GLOBAL_ACTION_DPAD_RIGHT)
        RemoteKey.CENTER ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)
            } else {
                clickFocused()
            }
        else -> false
    }

    /**
     * Remplace le texte du champ de saisie actif (ou du premier champ visible).
     * Renvoie `false` si l'écran n'en contient aucun.
     */
    fun setText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: findNode(root) { it.isEditable && it.isVisibleToUser }
            ?: return false
        if (!field.isFocused) field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /** Vrai si un champ de saisie a le focus dans l'application au premier plan. */
    fun hasFocusedTextField(): Boolean =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.isEditable == true

    /**
     * Pavé directionnel : action globale native sur Android 13+
     * (constantes GLOBAL_ACTION_DPAD_*, inlinées à la compilation et
     * utilisées uniquement derrière le test de version), sinon recherche de focus.
     */
    @Suppress("InlinedApi")
    private fun directional(focusDirection: Int, globalAction: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            performGlobalAction(globalAction)
        } else {
            moveFocus(focusDirection)
        }

    private fun moveFocus(direction: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return focusFirst(root)
        val next = focused.focusSearch(direction) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    /** Aucun élément focalisé : donne le focus au premier élément focalisable visible. */
    private fun focusFirst(root: AccessibilityNodeInfo): Boolean {
        val target = findNode(root) { it.isFocusable && it.isVisibleToUser } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    /** Clique sur l'élément focalisé, ou sur son premier parent cliquable. */
    private fun clickFocused(): Boolean {
        val root = rootInActiveWindow ?: return false
        var node: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        var depth = 0
        while (node != null && depth < MAX_PARENT_DEPTH) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent
            depth++
        }
        return false
    }

    /** Parcours en largeur borné de l'arbre d'accessibilité. */
    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_VISITED_NODES) {
            val node = queue.removeFirst()
            visited++
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return null
    }

    private companion object {
        const val MAX_PARENT_DEPTH = 6
        const val MAX_VISITED_NODES = 600
    }
}
