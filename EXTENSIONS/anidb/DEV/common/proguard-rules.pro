# ════════════════════════════════════════════════════════════════════
# AniDB — ProGuard / R8 rules
# ════════════════════════════════════════════════════════════════════

# ── 1. Keep ALL extension classes ────────────────────────────────────
# The Aniyomi app instantiates the source class reflectively, and the
# extension uses kotlinx.serialization heavily (DTOs for episode lists,
# languages, etc.). Keeping everything in the anidb package (including
# nested classes + subpackages like .extractor) prevents R8 from stripping
# or renaming serializers, companion objects, or @SerialName fields.
-keep class eu.kanade.tachiyomi.animeextension.en.anidb.** { *; }
-keep class eu.kanade.tachiyomi.animeextension.en.anidb.extractor.** { *; }

# ── 2. Keep generated kotlinx.serialization serializers ─────────────
# The compiler plugin generates $$serializer classes for each @Serializable
# type. R8 can strip these if not explicitly kept (the @Serializable annotation
# is on the DTO class, NOT on the generated serializer).
-keep class **$$serializer { *; }
-keepclassmembers class **$$serializer { *; }

# ── 3. Keep kotlinx.serialization attributes + companions ──────────
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt

# Keep serializer companion objects + the serializer() method
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @SerialName annotated fields (used for case-sensitive JSON mapping)
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
