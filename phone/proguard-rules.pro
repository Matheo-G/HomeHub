# Règles R8 de HomeHub Remote.
# Compose, DataStore, OkHttp, kotlinx.serialization et les coroutines fournissent leurs propres règles.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Tink (utilisé par androidx.security:security-crypto) référence des annotations
# de compilation et un téléchargeur de clés distant, absents à l'exécution.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
