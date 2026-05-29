# Hibernator ProGuard Rules
# Keep accessibility service classes - critical for automation
-keep class com.example.hibernator.accessibility.** { *; }

# Keep Room entities and DAOs
-keep class com.example.hibernator.data.database.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep WorkManager workers
-keep class com.example.hibernator.workers.** { *; }

# Keep domain models
-keep class com.example.hibernator.domain.model.** { *; }

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
