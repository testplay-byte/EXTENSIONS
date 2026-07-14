# ════════════════════════════════════════════════════════════════════
# Miruro — ProGuard / R8 rules
# ════════════════════════════════════════════════════════════════════

# ── 1. Keep ALL extension classes ────────────────────────────────────
# The Aniyomi app instantiates the source class reflectively, and the
# extension uses kotlinx.serialization heavily (DTOs for pipe API
# responses, episode lists, search results, etc.). Keeping everything in
# the miruro package (including subpackages) prevents R8 from stripping
# or renaming serializers, companion objects, or @SerialName fields.
-keep class eu.kanade.tachiyomi.animeextension.en.miruro.** { *; }

# ── 2. Keep generated kotlinx.serialization serializers ─────────────
# The compiler plugin generates $$serializer classes for each @Serializable
# type. R8 can strip these if not explicitly kept.
-keep class **$$serializer { *; }
-keepclassmembers class **$$serializer { *; }

# ── 3. Keep kotlinx.serialization attributes + companions ──────────
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
