# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

> [!IMPORTANT]
> **Builds require JDK 17.** The machine default may be newer (e.g. JDK 23), which Gradle 8.6
> rejects ("Unsupported class file major version 67"). Prefix every Gradle command:
> ```bash
> JAVA_HOME=/path/to/jdk-17 ./gradlew <task>
> ```
> Do **not** hardcode `org.gradle.java.home` in the committed `gradle.properties` (Android Studio
> uses its own JDK). A clean full build takes ~6–7 min cold, seconds when warm.

```bash
./gradlew :app:assembleDebug          # Debug APK (does NOT compile unit tests)
./gradlew :app:compileDebugKotlin     # Fast Kotlin-only check while iterating
./gradlew :app:installDebug           # Install to connected device
./gradlew :botok:test                 # The real unit suite (~198 tokenizer tests)
./gradlew lint

# Backend (api-backend/)
npm run build     # Compile TypeScript
npm run deploy    # Deploy to Firebase Functions (asia-south1)
```

> [!NOTE]
> `:app:testDebugUnitTest` currently fails to **compile** a pre-existing test
> (`AIGrammarViewModelTest`) because `mockito` / `androidx.arch.core:core-testing` were never
> added as test deps. This is unrelated to app code. Use `:botok:test` for the real suite, and
> verify the app via `assembleDebug` + on-device install.

## Architecture Overview

**MVVM** for app screens (Jetpack **Compose** UI) + **View-based IME** for the keyboard itself.

```
com.kharagedition.tibetankeyboard/
├── app/               # Application (global night mode, RevenueCat, FCM init)
├── service/           # TibetanKeyboard.kt — the IME InputMethodService
├── ui/
│   ├── compose/
│   │   ├── theme/     # Color, Tokens (gradients/radii), Type, Theme — design system
│   │   └── components/# AppIcons, Components (IconTile, GoldToggle, BackHeader…), ScreenScaffold
│   ├── keyboard/      # TibetanKeyboardView, AIKeyboardView, EmojiKeyboardView, SuggestionStripView
│   ├── home/          # HomeActivity + HomeViewModel + HomeScreen (Compose)
│   ├── settings/      # SettingsActivity + SettingsViewModel + SettingsScreen + SettingsPrefs
│   ├── chat/          # ChatActivity + ChatViewModel + ChatScreen
│   ├── subscription/  # PremiumActivity + PremiumViewModel + PremiumScreen
│   ├── about/         # AboutActivity + AboutScreen
│   ├── login/         # LoginActivity + LoginViewModel + LoginScreen
│   └── splash/        # SplashScreenActivity (Compose)
├── data/
│   ├── repository/    # ChatRepository, UserRepository, RevenueCatManager, PremiumFeatureManager,
│   │                  #   subscriptionCallback() helper
│   ├── remote/        # RetrofitClient singleton + API interfaces
│   ├── local/         # UserPreferences (SharedPreferences wrapper)
│   └── model/         # Data classes
├── auth/              # AuthManager (Firebase Auth)
└── util/              # TKExtension (showToast, showConfirmationDialog, …), CommonUtils, AppConstant
```

### UI: Jetpack Compose

All app screens are **Compose** (Splash, Login, Home, Settings, Chat, Premium, About). The only
View-based UI left is the **IME keyboard** (see below) and ad views embedded via `AndroidView`.

**Design system** — `ui/compose/theme` mirrors `docs/tibetan-keyboard/project/tokens.css`
(warm brown/gold "monk-robe" palette):
- `TibetanColors` — the full colour ramp. `TibetanTokens` — gradients, radii, elevation.
- `TibetanKeyboardTheme { … }` wraps every screen's `setContent`. The app is a single
  premium **dark-warm** look (forced light mode globally in `Application`).
- Tibetan text uses **Uchen** (block) via the **system font** (`FontFamily.Default` → Noto Sans
  Tibetan). Do NOT bundle `jomolhari_regular.ttf` (it's a 0-byte/corrupt file and crashes
  Compose's font loader); never use the cursive Ume face.

### MVVM conventions (follow these for new screens)

- **ViewModels own UI state** as `StateFlow<XxxUiState>`; screens collect via
  `collectAsStateWithLifecycle()`. Premium status is observed inside the VM from
  `RevenueCatManager.isPremiumUser` (LiveData) with `observeForever` + `removeObserver` in
  `onCleared()`.
- **Composable screens are stateless**: take a `state` object + an `actions` class of lambdas.
  No business logic in composables.
- **Activities hold only framework glue** that genuinely needs an Activity: `InputMethodManager`
  queries, `ActivityResultLauncher` (Google sign-in), `RevenueCatManager.purchasePremium(activity)`,
  ad `AndroidView`s, intents/navigation. This is correct MVVM — those APIs require an Activity, so
  they do **not** belong in a ViewModel.
- Reuse `ScreenScaffold { … }` for scrollable screens (handles full-bleed background + status/nav
  bar insets + scroll). `targetSdk 36` forces edge-to-edge, so **always** consume insets or content
  draws under the status bar.
- Build `RevenueCatManager.SubscriptionCallback`s with `subscriptionCallback(onSuccess =, onError =)`
  rather than hand-written anonymous objects.
- **Strings**: user-facing UI chrome lives in `res/values/strings.xml` via `stringResource(...)`.

### Data Flow

```
Compose screen (collectAsStateWithLifecycle)
   ↑ StateFlow                        ↓ actions (lambdas)
ViewModel  ──── Repository.apiCall() [Coroutine on IO] ──→ Retrofit → Backend
```

### IME Service Flow (View-based, NOT Compose)

`TibetanKeyboard` (InputMethodService) rebuilds the keyboard on every `onStartInputView`, so
preference changes apply the next time the keyboard opens.
- `AIKeyboardView` — top toolbar (AI grammar/translate/rephrase) + suggestion strip + the keyboard
  container. AI features run **inside** this panel (`showAIInterface()`), not as separate screens.
- `TibetanKeyboardView extends KeyboardView` — the typing surface. Custom `onDraw` paints the
  **Enter key gold** on top of any layout (visual only; key codes untouched).
- `SuggestionStripView` — Botok autocomplete; top suggestion gold, rest cream.
- `KeyboardMode` enum: `NORMAL`, `AI_GRAMMAR`, `AI_REPHRASE`.

**Keyboard layouts & theming** are driven by `PreferenceManager.getDefaultSharedPreferences`
(the same store the Compose Settings screen writes — see `SettingsPrefs`):
- `keyboard_style`: `modern` (flat), `classic` (raised gradient caps), `borderless` (glyphs only).
  Selected in Settings → Keyboard Layout. Brown key drawables: `normal/pressed_{classic,modern}_brown`,
  `key_background_borderless`.
- `colors`: brown/black/green; the IME darkens the chosen colour to an espresso surface at runtime
  (`darkenColor`, and `AIKeyboardView.applyTheme`) so brown caps pop.
- `vibrate`, `sound`, `event_notification`: behaviour flags read by the IME.
- Keyboard key XML layouts live in `res/xml/` (tibetan_uchen_alphabet_1/2, qwerty, etc.) — **never
  change key codes** there; they drive `onKey()`.

### Premium Feature Gating

`PremiumFeatureManager` + `RevenueCatManager` (entitlement `"pro"`). Free-tier daily limits in
Firestore: Grammar 10/day, Transliteration 20/day, Chat 100 messages.

### Backend (`api-backend/`)

Node.js/TypeScript Express on Firebase Functions. AI: Gemini 2.0 Flash. Translation: Google
Translate. Auth middleware: API key + per-user Firestore usage limits.
Endpoints: `POST /chat`, `POST /translate` (grammar/transliterate endpoints exist server-side but
the in-app Grammar/Transliteration screens were removed as dead code).

### Key Libraries

| Purpose | Library |
|---------|---------|
| UI | Jetpack Compose (BOM 2024.09.00, Material3) + Compose compiler plugin 2.0.0 |
| Compose↔ViewModel | lifecycle-viewmodel-compose / lifecycle-runtime-compose 2.8.7, runtime-livedata |
| Images | Coil 2.7.0 (Compose) + Glide 4.16.0 (GIF in `AndroidView`) |
| HTTP | Retrofit 2 + OkHttp3 |
| Async | Kotlin Coroutines 1.8.1 |
| Subscriptions | RevenueCat 9.2.0 |
| Ads | AdMob 23.6.0 (`TemplateView` native, `AdView` banner via `AndroidView`) |
| Tokenizer | `:botok` module |

### Build Configuration

- **App ID**: `com.kharagedition.tibetankeyboard` · **Min SDK** 23 · **Target/Compile SDK** 36
- **Kotlin** 2.0.0 · **Java** 17 · **AGP** 8.3.2 · **Gradle** 8.6
- `buildFeatures { compose = true; viewBinding = true; buildConfig = true }`
- Release: ProGuard + resource shrinking; `app/lint-baseline.xml` suppresses known lint issues.

## Conventions & gotchas (future reference)

- **Reskinning a screen?** Keep the ViewModel/repository; only swap the composable. State stays
  hoisted to the VM.
- **New screen?** `XxxActivity` (glue) + `XxxViewModel` (StateFlow state) + `XxxScreen` (stateless
  composable in `TibetanKeyboardTheme` + `ScreenScaffold`). Wrap callbacks in an `XxxActions` class.
- **Touching the keyboard?** It "functions exact" — restyle drawables/`onDraw`/colours only; never
  alter key codes, `onKey()` routing, or the `res/xml` key definitions.
- **Adding strings?** Check `strings.xml` for an existing key first (duplicate names fail the
  resource merge).
- **Always device-verify Compose changes** (`installDebug` + screenshot), not just `assembleDebug` —
  a green build can still crash at runtime (e.g. the empty-font bug).
- A few inline strings remain (Premium feature descriptions, quick-action labels, Tibetan
  placeholders) — externalize if expanding i18n.
