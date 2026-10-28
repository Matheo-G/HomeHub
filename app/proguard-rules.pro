# Règles R8 de HomeHub.
#
# Compose, DataStore et les coroutines embarquent leurs propres règles
# (consumer rules) : aucune règle supplémentaire n'est nécessaire pour la V1.
# Conserver les numéros de ligne rend les traces de plantage lisibles.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
