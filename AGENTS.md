# Project: Core Assistant (Android)

## Build

JDK is at `/home/kees/.jdks/temurin-24.0.2`. Set `JAVA_HOME` before building:

```bash
export JAVA_HOME=/home/kees/.jdks/temurin-24.0.2
export PATH=$JAVA_HOME/bin:$PATH
./gradlew assembleDebug
```

## Tech Stack

- Jetpack Compose with Material 3
- Preferences DataStore for settings
- Coroutines + StateFlow
- Room (with SQLCipher) for persistence
- Retrofit + Gson for Gemini API
- Navigation Compose
- Coil for image loading
- Multiplatform Markdown Renderer

## Architecture Notes

- **Theme**: `MaterialTheme` wraps the app in `MainActivity.kt`. Dark/light mode is controlled by the `darkMode` setting ("system"/"light"/"dark") in `SettingsManager.kt`, stored in DataStore. The color scheme is selected reactively via `darkColorScheme()` / `lightColorScheme()` with `isSystemInDarkTheme()` as the fallback.
- **SettingsManager**: Thin wrapper around DataStore. Each setting exposes a `Flow<T>` and a `suspend fun save*(...)`. Preferences keys live in the companion object.
- **ViewModels**: Each screen has its own ViewModel with a factory. State flows are collected in composables via `collectAsState()`.
- **Navigation**: Defined in `MainActivity.kt` via `NavHost`. Routes: `setup`, `conversations`, `chat/{conversationId}`, `settings`, `licenses`.
- **Database**: `ChatDatabase` (Room) with encrypted SQLCipher. DAOs: `chatDao()`, `geminiModelDao()`.
- **API**: `GeminiApiService` Retrofit interface. Models are fetched and cached in Room.
- **Color Scheme**: Custom Material 3 blue-based scheme defined in `MainActivity.kt` as `CustomLightColorScheme` and `CustomDarkColorScheme`, generated via the Material Design tool with primary key `#1565C0`.
