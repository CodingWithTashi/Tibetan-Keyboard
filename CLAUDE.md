# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./gradlew build
./gradlew assembleRelease
./gradlew assembleDebug

# Install debug build to connected device
./gradlew installDebug

# Testing
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Instrumented tests (requires device/emulator)

# Code quality
./gradlew lint

# Backend (api-backend/)
npm run build     # Compile TypeScript
npm run deploy    # Deploy to Firebase Functions (asia-south1)
```

## Architecture Overview

**MVVM + Repository pattern** throughout the app.

### Android App (`app/`)

```
com.kharagedition.tibetankeyboard/
├── app/               # Application class (RevenueCat, Firebase init)
├── service/           # TibetanKeyboard.kt — the IME InputMethodService
├── ui/                # Activities + ViewModels (one ViewModel per screen)
│   ├── keyboard/      # TibetanKeyboardView, AIKeyboardView, EmojiKeyboardView
│   ├── grammar/       # GrammarActivity + GrammarViewModel
│   ├── transliteration/
│   ├── chat/          # ChatActivity with history, document upload, tutoring mode
│   ├── home/, settings/, login/, splash/, subscription/
├── data/
│   ├── repository/    # AIRepository (central API hub), ChatRepository, UserRepository
│   ├── remote/        # RetrofitClient singleton + API interface definitions
│   ├── local/         # UserPreferences (SharedPreferences wrapper)
│   └── model/         # Data classes
├── auth/              # AuthManager (Firebase Auth)
└── util/              # Extensions, PerformanceOptimizer (LRU cache + debounce)
```

### Backend (`api-backend/`)

Node.js/TypeScript Express app deployed as Firebase Cloud Functions.

- **AI**: Google Gemini 2.0 Flash (grammar, chat)
- **Translation**: Google Translate API
- **Auth middleware**: API key validation + per-user usage limits enforced in Firestore

Key endpoints: `POST /api/grammar/analyze`, `POST /api/transliterate/convert`, `POST /chat`, `POST /translate`

### IME Service Flow

`TibetanKeyboard` (InputMethodService) creates the keyboard UI:
- `AIKeyboardView` — top toolbar with AI feature buttons
- `TibetanKeyboardView` — the actual typing surface (Tibetan Uchen or QWERTY)
- `KeyboardMode` enum: `NORMAL`, `AI_GRAMMAR`, `AI_REPHRASE`

Grammar/transliteration/chat features open as separate Activities launched from the toolbar.

### Data Flow

```
Activity → ViewModel.someAction()
         → Repository.apiCall() [Coroutine on IO]
         → Retrofit → Backend API
         → _liveData.value = result
         → Activity observer → UI update
```

### Premium Feature Gating

`PremiumFeatureManager` + `RevenueCatManager` control access. Free tier has daily usage limits stored in Firestore:
- Grammar: 10/day free
- Transliteration: 20/day free
- Chat: 100 messages free

### Key Libraries

| Purpose | Library |
|---------|---------|
| HTTP | Retrofit 2 + OkHttp3 |
| JSON | Kotlinx Serialization 1.6.3 |
| Async | Kotlin Coroutines 1.8.1 |
| Images | Glide 4.16.0 |
| Animations | Lottie 6.6.2 |
| Subscriptions | RevenueCat 9.2.0 |
| Ads | AdMob 23.6.0 |
| Backend AI | Gemini 2.0 Flash via `@google/generative-ai` |

### Build Configuration

- **App ID**: `com.kharagedition.tibetankeyboard`
- **Min SDK**: 23 (Android 6.0)
- **Target/Compile SDK**: 36
- **Kotlin**: 2.0.0, **Java**: 17
- Release builds use ProGuard + resource shrinking; `app/lint-baseline.xml` suppresses known lint issues
- View Binding is enabled
