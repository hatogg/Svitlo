# kotlinx.serialization: згенеровані серіалізатори моделей даних
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class ua.x44to.svitlo.**$$serializer { *; }
-keepclassmembers class ua.x44to.svitlo.** { *** Companion; }
-keepclasseswithmembers class ua.x44to.svitlo.** { kotlinx.serialization.KSerializer serializer(...); }
