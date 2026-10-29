# Shared JVM/Compose runtime survival rules (M6, PodAura compose-desktop.pro portable subset).
# PodAura-only sections intentionally dropped: Room/Database (no DB in PoiShelf),
# PrettyTime (unused), JNA/mediamp player shims (no player), podaura artwork helpers.

# SLF4J
-keep class org.slf4j.** { *; }

# OkHttp engine
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn org.openjsse.**
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer

# ICU4J
-keep enum com.ibm.icu.text.DateTimePatternGenerator$DTPGflags { *; } # ClassCastException
-keep enum com.ibm.icu.text.DateFormat$BooleanAttribute { *; } # ClassCastException

# Ktor
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider

# Coil
-keep class coil3.util.DecoderServiceLoaderTarget { *; }
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.util.ServiceLoaderComponentRegistry { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }

# Okio
-keep class okio.Okio__OkioKt { *; } # VerifyError

# Compose
-keep, allowshrinking, allowobfuscation class androidx.compose.ui.text.ParagraphKt { <methods>; }

# Windows Window Frame
-keepnames class androidx.compose.foundation.HoverableNode
-keepnames class androidx.compose.foundation.gestures.ScrollableNode
-keepnames class androidx.compose.ui.scene.PlatformLayersComposeSceneImpl
-keepnames class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl
-keepnames class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl$AttachedComposeSceneLayer
-keepclassmembers class androidx.compose.ui.scene.PlatformLayersComposeSceneImpl {
    private *** getMainOwner();
}
-keepclassmembers class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl {
    private *** mainOwner;
    private *** _layersCopyCache;
    private *** focusedLayer;
}
-keepclassmembers class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl$AttachedComposeSceneLayer {
    private *** owner;
    private *** isInBounds(...);
}
