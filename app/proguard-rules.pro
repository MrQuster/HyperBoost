# Сохраняем классы системных аудиоэффектов Android
-keep class android.media.audiofx.** { *; }

# Сохраняем доступ к системным свойствам и методам NotificationManager, вызываемым через рефлексию
-keep class android.os.SystemProperties { *; }
-keepclassmembers class android.app.NotificationManager {
    public boolean canPostPromotedNotifications();
}

# Сохраняем JSON-сериализацию и модели данных приложения
-keep class org.json.** { *; }
-keep class com.example.volumeboost.data.** { *; }
-keep class com.example.volumeboost.device.** { *; }
-keep class com.example.volumeboost.island.** { *; }

# Убираем отладочные логи Log.d и Log.v из релизного APK
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
