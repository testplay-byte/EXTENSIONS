# ════════════════════════════════════════════════════════════════════
# UniQuestream — ProGuard / R8 rules
# ════════════════════════════════════════════════════════════════════

# ── 1. Keep ALL extension classes ────────────────────────────────────
-keep class eu.kanade.tachiyomi.animeextension.en.uniquestream.** { *; }

# ── 2. Keep generated kotlinx.serialization serializers ─────────────
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
