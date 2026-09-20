# Screen Translator ProGuard Rules
# Keep KlipSelectionController public API methods accessed from FloatingService
-keepclassmembers class com.example.screentranslator.FloatingService {
    public *;
}

# Keep ScreenCaptureManager public methods
-keepclassmembers class com.example.screentranslator.ScreenCaptureManager {
    public *;
}

# Keep OcrManager public methods
-keepclassmembers class com.example.screentranslator.OcrManager {
    public *;
}

# Keep TranslationManager public methods
-keepclassmembers class com.example.screentranslator.TranslationManager {
    public *;
}

# Keep TranslationProvider implementations
-keep class com.example.screentranslator.**TranslationProvider** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }

# Keep OkHttp classes
-keep class okhttp3.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep JSON classes
-keep class org.json.** { *; }

# Keep AndroidX classes
-keep class androidx.** { *; }

# Keep Material3 classes
-keep class com.google.android.material.** { *; }

# Prevent obfuscation of enum values used in serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep custom views
-keep class com.example.screentranslator.KlipManualButton { *; }
-keep class com.example.screentranslator.KlipCancelButton { *; }
-keep class com.example.screentranslator.TranslationOverlayView { *; }
-keep class com.example.screentranslator.TranslationOverlayViewCompat { *; }
-keep class com.example.screentranslator.KlipResultOverlayView { *; }

# Keep performance trace and log classes
-keep class com.example.screentranslator.ScreenTLPerformanceTrace { *; }
-keep class com.example.screentranslator.PerformanceLogStore { *; }
-keep class com.example.screentranslator.PerformanceLogEntry { *; }

# Keep history classes
-keep class com.example.screentranslator.TranslationHistory { *; }

# Keep API settings
-keep class com.example.screentranslator.ApiSettings { *; }
-keep class com.example.screentranslator.SecureApiKeyStore { *; }

# Keep TextLayoutAnalyzer
-keep class com.example.screentranslator.TextLayoutAnalyzer { *; }
-keep class com.example.screentranslator.TextLayoutAnalyzer$Result { *; }
-keep class com.example.screentranslator.TextLayoutAnalyzer$WritingOrientation { *; }

# Keep DetectedText and TranslationOverlayItem
-keep class com.example.screentranslator.OcrManager$DetectedText { *; }
-keep class com.example.screentranslator.TranslationOverlayItem { *; }

# Keep ScreenCaptureSession
-keep class com.example.screentranslator.ScreenCaptureSession { *; }

# Keep KlipSelectionController
-keep class com.example.screentranslator.KlipSelectionController { *; }
-keep class com.example.screentranslator.KlipSelectionController$KlipMaskView { *; }

# Prevent warnings
-dontwarn com.google.mlkit.**
-dontwarn okhttp3.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.json.**
