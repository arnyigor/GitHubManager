# Kotlin Serialization uses generated serializers; do not strip model descriptors.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * { *** Companion; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
