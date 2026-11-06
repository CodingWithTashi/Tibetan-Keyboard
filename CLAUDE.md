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
> `:app:testDebugUnitTest` now compiles and runs (the stale `AIGrammarViewModelTest`, which
> referenced deleted Grammar classes, was removed). It holds **pure JVM tests** for the
> conversion-critical decision logic — `ProStripState` (free-vs-PRO strip), `WordComposer`
> (suggestion tap / tsheg rules), `UnlockRouter` (auth→paywall routing), `PostLoginDestination`
> (post-login routing), `EnableTrip` (coming back from the system keyboard settings),
> `KeyboardUpsell` (the keyboard bar asks before the paywall), `KeyboardSizing` (how tall the
> keyboard is on each screen), the whole voice typing decision layer under `ui/keyboard/voice`
> (`VoiceMachine`, `VoiceController` with a fake mic and network, `VoiceContent`, `VoicePanelLayout`,
> `WavEncoder`, …), and `KeyboardLayoutTest` (guards
> the removed "+"/`-20` key across all `res/xml` layouts). Keep view/IME/RevenueCat-SDK behaviour
> out of these (no mockito/Robolectric);
> test it by extracting the decision into a pure unit (see those classes) or via on-device
> verification. `:botok:test` remains the tokenizer suite.

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
│   ├── repository/    # ChatRepository, UserRepository, RevenueCatManager,
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
- Line height follows the font size: `Text` defaults to `UiLineHeight` (1.4em), `BoText` to
  `TibetanLineHeight` (1.6em). Screens set `fontSize` inline, and Material's fixed 24sp made
  wrapped captions look double-spaced. Set `lineHeight` only where a block needs its own.

**Every screen size.** Screens are checked at 320dp wide, at 130% and 200% font size, in
landscape, and on Android 12 with three navigation buttons, not only on a Pixel:
- No fixed `height` around text: use `heightIn(min = …)`, so a large font size or stacked Tibetan
  letters grow the box instead of being cut off.
- Text beside other content wraps or ends in an ellipsis on purpose (`maxLines` + `overflow`);
  cards in one `Row` share a height (`IntrinsicSize.Max` + `fillMaxHeight`).
- A badge pinned over other content uses `PillBadge(fixedSize = true)`.
- `TibetanKeyboardApp` makes every activity of ours edge to edge on every Android version, and
  the theme's `windowBackground` is the screens' own colour, so launch and screen changes never
  flash a lighter window.

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
- Reuse `ScreenScaffold { … }` for screens (full-bleed background, scroll, and insets for the
  system bars, the display cutout and the keyboard; content stays a centred column of at most
  `ScreenMaxWidth` on tablets and in landscape). Every screen is edge to edge, so one that does
  not use it must consume `WindowInsets.safeDrawing` itself (see `ChatScreen`). An activity with a
  text field declares `adjustResize`; the scaffold then scrolls the field above the keyboard.
- Build `RevenueCatManager.SubscriptionCallback`s with `subscriptionCallback(onSuccess =, onError =)`
  rather than hand-written anonymous objects.
- **Strings**: user-facing UI chrome lives in `res/values/strings.xml` via `stringResource(...)`.

### Data Flow

```
Compose screen (collectAsStateWithLifecycle)
   ↑ StateFlow                        ↓ actions (lambdas)
ViewModel  ──── Repository.apiCall() [Coroutine on IO] ──→ Retrofit → Backend
```

### Keyboard setup (Home)

Two steps on Home: switch the keyboard on in the system settings, then pick it.
- Step 1 leaves the app. `EnableTrip` (pure, unit-tested) follows the trip: `HomeActivity` polls
  the enabled-keyboards list and logs `keyboard_enabled` the moment the keyboard is switched on,
  while the user is still away. Once they are back, the ViewModel asks for the picker so step 2
  needs no tap. The trip is kept in saved instance state, so a process killed mid-trip still
  opens the picker.
- Home comes back **by itself** only below Android 15. There the settings screen is opened
  without `NEW_TASK` (and with no other flag: nothing may change how the system screen itself
  behaves), and the platform lets an app start an activity when it has one in the task on screen
  (`BackgroundLaunchProcessController`: "process has activity in foreground task"; the public
  list of exceptions no longer names it). From Android 15 the activity security model can refuse
  an app covering a system screen from below, so there the settings keep their own task, as
  before, and the user comes back unaided. Never drop `NEW_TASK` on those versions.
- Two consequences of the in-task screen, both handled: a user who leaves it with the Home
  button and taps the icon again gets a new Splash, which reuses the existing Home
  (`CLEAR_TOP|SINGLE_TOP`) and so clears the leftover screen instead of stacking a second Home;
  and for 2.5 s after Home brought itself back it swallows Back, because a press meant for the
  settings screen would close the app (Home is the bottom of its task). The toast over the
  settings screen says "go back to the app" only where Home will not come back by itself.
- Funnel: `keyboard_enable_clicked` → `keyboard_enabled` (`method`: `setup` / `other`) →
  `keyboard_picker_opened` (`method`: `auto_return` / `manual_return` / `tap`) →
  `keyboard_setup_completed`. `keyboard_enabled` exists since 2.3.3; the last two are checked on
  Home only. Since 2.3.3 `keyboard_picker_opened` also fires when Home opens the picker itself:
  filter `method = tap` for "the user chose to". `auto_return` is exact (the intent Home sent
  itself arrived), not a guess.
- The IME service label is `@string/app_name`, so the system's keyboard list shows the name
  setup tells people to switch on. `values-mcc402` renames the app "Dzongkha Keyboard" for
  Bhutanese SIMs; never hard-code the label.

### IME Service Flow (View-based, NOT Compose)

`TibetanKeyboard` (InputMethodService) rebuilds the keyboard on every `onStartInputView`, so
preference changes apply the next time the keyboard opens.
- `AIKeyboardView` — top toolbar (★ PRO pill, suggestion strip, Chat, Translate, **mic**) + the
  keyboard container. AI features run **inside** this panel (`showAIInterface()`), not as separate
  screens. The bar has no streak chip and no autocomplete icon (the locked suggestion chips carry
  the padlock); Journey is reached from Home.
- `TibetanKeyboardView extends KeyboardView` — the typing surface. Custom `onDraw` paints the
  **Enter key gold** on top of any layout (visual only; key codes untouched).
- `KeyboardSizing` (pure, unit-tested) decides how tall the keyboard is. `res/xml` declares 60dp
  rows; `SizedKeyboard` loads every layout and only ever makes its rows shorter, at runtime, so
  the bar and five rows take at most 47% of the screen's height upright and 60% on its side
  (down to 34dp rows). A screen from 766dp high, upright, keeps 60dp. With fixed rows the
  keyboard covered the whole screen in landscape. The AI panel and the emoji grid take their
  heights from the same rule. Heights are of the whole screen (`getRealMetrics`):
  `Resources.displayMetrics` counts the system bars only from Android 15.
- Key labels follow the system font size up to 130% (`KeyboardSizing.labelFontScale`); past that
  the views are built with a capped font scale (`TibetanKeyboard.keyboardContext`), because at
  200% neighbouring letters ran into each other.
- Bottom inset (`AIKeyboardView.applyBottomInsetPadding`): from Android 15 the keyboard's window
  runs under the navigation bar and the view pads itself; before that the system keeps it above
  the bar, so the fallback there is 0 (a non-zero one stayed as an empty band under the keys).
- `SuggestionStripView` — Botok autocomplete; top suggestion gold, rest cream.
- `WordComposer` (pure, unit-tested) decides what a suggestion tap does: it replaces only the part
  of the typed run the engine matched (`SuggestionEngine.suggest(...).matched`: the whole run, or
  the longest tail of it starting after a tsheg), writes the next tsheg (`བོ` → `བོད་`), swallows
  a tsheg typed onto it and lets a shad take its place (`བོད།`, but `རང་།` after nga). Every edit
  is checked against the text before the cursor (`onUpdateSelection` re-syncs), so a cursor move
  can never make a tap delete the wrong characters. Enter (`KEYCODE_DONE`) closes the run too.
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

### Voice typing (Tibetan speech to text)

The mic at the end of the keyboard bar opens a panel that takes the bar's and the keys' place
(`AIKeyboardView.showVoicePanel`, same height as the AI panel), listens at once, and types the
text Monlam AI writes down. **Open to everyone**: no PRO check, login or quota.
- **Decisions live in pure units** (`ui/keyboard/voice`, all unit-tested): `VoiceMachine` (states),
  `VoiceController` (mic + upload + field, tested with fakes), `VoiceContent` (what each state shows),
  `VoicePanelLayout` (sizes; **compact below 300dp panel height**, because landscape leaves as little
  as 230dp), `VoicePolicy` (the 60 s limit, hold/tap, cancel slide), `WavEncoder`, `AudioLevel`,
  `VoiceInsert` (separator and Undo safety), `VoiceAvailability` (no mic in password fields).
  `VoicePanelView`, `VoiceOrbView`, `WaveformView` and `AudioRecordRecorder` only draw and record.
- **The session belongs to the service** (`TibetanKeyboard.voice`), not a view: the keyboard rebuilds
  its views on every open. It survives that only on the same field (`VoiceField`); the keyboard hiding,
  or a move to another field, cancels. An answer that arrives after Cancel is dropped.
- **Gesture:** tap the orb to start and tap to finish, or hold (280 ms) to talk and let go to send;
  slide 60dp left to cancel. The bar's mic starts listening by itself.
- **Audio:** 16 kHz mono 16-bit WAV held in memory (never on disk), sent as the raw body of
  `POST /speech-to-text`. Recordings stop at 60 s on the phone **and** the server refuses more
  (`wavInfo.ts`); keep `VoicePolicy.MAX_MS` within the server's limit (a test guards it).
- **Mic permission:** an IME cannot show the dialog, so `VoicePermissionActivity` (transparent, own
  task) asks; the keyboard resumes listening only if the user is back on the same field within 60 s.
- **Never log audio or transcripts.** The voice client has its own OkHttp client (HEADERS logging: the
  shared one logs bodies). Analytics carry labels and coarse buckets only (`VoiceEvents`).
- **Streak:** dictated Tibetan text counts toward the streak like typed text.
- **Backend:** `api-backend` `POST /speech-to-text` (secret `MONLAM_API_KEY`, per-IP limiter of its
  own, no attachPro / free limit / daily budget). The `stt:` log lines carry Monlam's `cost`: with no
  gate they are the only view of spend. **Not yet:** App Check, so anyone who finds the URL can call it
  (the limiter and the cost logs are the guard). Add it before any heavy promotion.
- **Privacy:** we do not store the audio, but **Monlam keeps a copy**: its reply carries `audio_s3_path`
  and a CloudFront `audio_url` (our route returns only the text). The mic panel says the voice is sent to
  Monlam AI; never reword it to "not kept". How long Monlam retains recordings is unknown: ask them.
- **Replies seen live (2026-11-05):** `{ text, cost, latency_ms, service_request_id, status }`; about
  $0.032 per minute of audio, about 1.9 s for 4 s of audio, sync and `/live` equally fast. Noise comes
  back as a lone "།", which the backend turns into "nothing heard".
- **Release checklist:** Play Console Data safety (audio is sent to Monlam AI and retained by them),
  privacy policy, "What's new".

### Premium Feature Gating

`RevenueCatManager` (entitlement `"pro"`) is the single source of truth on the client. It is
configured at process start — **anonymously when nobody is signed in** — so a signed-out user can
still be recognised as a subscriber and can buy without logging in first (`UnlockRouter` always
routes to the paywall). Sign-in is asked for afterwards, when a server-backed AI screen needs a UID.

Free-tier limits live in the **backend**, not the client: `FREE_DAILY_CHAT_MESSAGES=100`,
`DAILY_TRANSLATION_CHARS=5000`, and `20`/`2000` for anonymous callers
(`api-backend/src/config/constants.ts`), enforced by `enforceFreeLimit` on every Claude-reaching
route. The old client-side `PremiumFeatureManager` and its "Grammar 10/day, Transliteration 20/day"
counters were dead code with no call sites and have been removed.

Theme/layout entitlement is enforced at *apply* time via `SettingsPrefs.effectiveColor/effectiveStyle`,
not just in the Settings picker — the IME reads the preference store directly, so a lapsed
subscriber must fall back to the free defaults without their saved choice being overwritten.

Products live in the RevenueCat `pro_v2` offering (monthly / annual / lifetime), read by
identifier rather than `offerings.current`. Regional prices are set with `tools/play_pricing.py`;
never price one plan by converting another plan's price from a different currency.

**Paywall & upsell flow (2.3.0).** The decisions are pure, unit-tested units in `subscription/`
and `billing/`; keep new rules there. RevenueCat/Play ids live only in `subscription/BillingCatalog`.
- `PremiumActivity` asks `RevenueCatManager.resolvePaywall(placement)`. The placement comes from
  `PaywallPlacements.forSource(UpgradeSource)`, and the choice from `PaywallOfferingResolver`:
  - the placement's offering, else RevenueCat's `current` offering. A targeting rule or experiment
    variant without placements sends no placement mapping, so the placement lookup returns null;
  - an offering with a dashboard paywall → RevenueCat's `Paywall()` composable;
  - otherwise → our Compose `PremiumScreen`, which reports views with `trackCustomPaywallImpression`
    so experiments can compare it with dashboard paywalls;
  - `sale` is never shown.
  - The GA4 funnel lives in `PremiumViewModel` (the Activity only launches Play, login and the
    checkout tab); RevenueCat's paywall reports into it through `PaywallListener`.
  - Paywall screens share `ui/subscription/ProComponents.kt`: `PaywallScaffold` (the pitch scrolls,
    the price line and button stay pinned), hero card, benefits list, `PlanCard`, 18dp margins.
- RevenueCat dashboard (all rules gated on App version ≥ 2.3.0; older builds keep `sale`):
  - Targeting, in order: Nepal → `pro_v2` (our screen with card help), India → `pro_trial_monthly`,
    everyone else → `pro_trial_annual`. `pro_web_bt` is only for the Bhutan web checkout: it gets
    no targeting rule and no dashboard paywall, because its web products reach the Android SDK as
    an offering with 0 packages.
  - Experiments (new customers): "Paywall design - India" and "Paywall design - Rest of world"
    (excludes IN/NP/BT), each `pro_v2` (A, control) vs the RevenueCat paywall (B).
- `DefaultPlanPolicy` pre-selects the plan: monthly for storefront IN, otherwise a plan with a trial.
- `BillingAvailability` decides Play vs card checkout (`WebCheckoutScreen`, RevenueCat Web Purchase
  Link `WEB_PURCHASE_LINK`, sandbox in debug). **Policy line:** card checkout only where Google Play
  can't sell (storefront BT, or MCC 402 + proof Play can't sell). Never in a Play-billing country.
  - The sandbox link is read from `local.properties` (`webPurchaseLinkSandbox=`), never committed:
    the repo is public and that page accepts Stripe's test card. Without it a debug build shows
    "checkout coming soon".
  - `WebCheckoutScreen` is `PremiumScreen` with card plans: the plan is picked in the app and the
    link opens that plan's checkout (`?package_id=`). Play can't return web products, so plans and
    USD prices are `BillingCatalog.WEB_PLANS`; change them together with the dashboard's.
  - The trial is promised only to customers who never bought anything (`awaitNeverPurchased`),
    which is the web products' eligibility rule.
  - The card route is decided from the storefront **before** offerings load, and both the paywall
    (`PremiumViewModel.load`) and Home's onboarding check skip `loadOfferings()` for it: Play is
    slowest where it can't sell, and waiting lost ~half of Bhutan's paywall opens (2.3.2).
  - Continue while signed out → login → back on the paywall, which opens the checkout by itself
    (`PremiumEvent.OpenWebCheckout`). Funnel: `web_checkout_continue` (`signed_in`) →
    `web_checkout_opened`; `paywall_dismissed` carries `paywall_type`, `loading` = closed on the spinner.
  - Its A/B test is ours, not a RevenueCat experiment: `WebPaywallExperiment` pre-selects monthly
    (control) or annual from a number stored per install. The variant is on the GA4 events
    (`variant`, user property `web_paywall_variant`) and on the RevenueCat customer (attribute
    `web_paywall_variant`).
- `canSellHere()` keeps upsells off where nothing can be bought.
- `RevenueCatManager.isPremiumKnown` is false until RevenueCat answers; `isPremiumUser` starts as
  `false`, which must not be read as "free" (no quota, no upsell for an unknown user).
- The keyboard gives free users `SuggestionQuota` (20/day) live suggestions, then locked chips.
  The strip re-lays out only when its `ProStripState` changes.
- A tap on a locked PRO control in the keyboard bar (pill, chat, translate, locked chip) does not
  open the paywall: the bar asks first (`KeyboardUpsell`, event `keyboard_pro_prompt_shown`), and
  only its "See plans" button logs `upgrade_clicked` and opens it. A tap right above the keys is
  often a slip, and the paywall covers the app being typed in (2.3.3). The question's buttons
  sit where the bar's icons are, so taps within half a second of the bar swapping are ignored.
  **Analytics:** for sources `keyboard` and `keyboard_suggestions`, `upgrade_clicked` means "tapped
  See plans" from 2.3.3 on, and "tapped a lock" before it or while `keyboard_upsell_confirm` is
  `false`; do not compare its count across that line.
- Home opens the paywall by itself via `OnboardingPaywallPolicy` (after setup, day 3, once for
  existing users). It is not an `upgrade_clicked`.
- Dashboard switches (metadata on `pro_v2`, parsed by `subscription/RemoteConfig`), no release
  needed: `free_suggestions_per_day`, `onboarding_paywall_enabled`, `keyboard_upsell_confirm`
  (`false` restores the direct paywall open), `web_checkout_countries`,
  `web_prices` (`{"monthly": 1.99}`), `web_annual_first_percent` (0 or 100 ends the test).
  `web_checkout_countries` can only remove countries: it is clamped to Play-unavailable ones.
  They arrive with the offerings, so where Play returns no products the defaults apply.
- **Manage subscription** is our own Compose flow (`ManageSubscriptionActivity`/`ViewModel`/`Screen`),
  not RevenueCat's Customer Center, whose layout can't be styled to match the app.
  - Steps: overview → reason → (retention offer) → hand-off to Google Play. Cancelling always
    happens in Play; the rules are in `subscription/ManageSubscriptionModel.kt`.
  - Settings-style rows are shared via `ui/compose/components/SettingsRows.kt`.
  - Play can't apply a discount offer on the base plan the user is already on. A plain purchase
    fails with "Account identifiers don't match", and a plan change to the same base plan gets
    "Server error". So the offer shows only when it sits on another base plan
    (`ManageSubscriptionPolicy.offerApplies`).
- Debug builds: `SplashScreenActivity` extras `debug_force_free`, `debug_storefront` (`none` clears),
  `debug_suggestions_used`, `debug_reset_onboarding`, `debug_web_variant` (`annual_first` /
  `monthly_first`), `debug_own_paywall` (see `util/DebugOverrides.kt`). Launch with `-f 0x10008000`.

### Backend (`api-backend/`)

Node.js/TypeScript Express on Firebase Functions. AI: **Claude** via `anthropicService`
(`claude-haiku-4-5` / `claude-sonnet-4-6`, user-switchable per request). Translation: **Azure**,
falling back to Claude server-side. Auth middleware: API key + `attachPro` + per-user Firestore
usage limits + a project-wide daily budget.
The app calls `POST /chat` and `POST /translate`. The `/api/grammar/*`, `/api/transliterate/*` and
`/api/chat/message` routes have no client call sites (their screens were removed) but still reach
Claude, so they carry the same middleware chain.

### Key Libraries

| Purpose | Library |
|---------|---------|
| UI | Jetpack Compose (BOM 2024.09.00, Material3) + Compose compiler plugin 2.0.0 |
| Compose↔ViewModel | lifecycle-viewmodel-compose / lifecycle-runtime-compose 2.8.7, runtime-livedata |
| Images | Coil 2.7.0 (Compose) + Glide 4.16.0 (GIF in `AndroidView`) |
| HTTP | Retrofit 2 + OkHttp3 |
| Async | Kotlin Coroutines 1.8.1 |
| Subscriptions | RevenueCat 9.29.1 (`purchases` + `purchases-ui`, keep in step) |
| Ads | AdMob 23.6.0 (`TemplateView` native, `AdView` banner via `AndroidView`) |
| Tokenizer | `:botok` module |

### Build Configuration

- **App ID**: `com.kharagedition.tibetankeyboard` · **Min SDK** 24 (RevenueCatUI) · **Target/Compile SDK** 36
- **Kotlin** 2.0.0 · **Java** 17 · **AGP** 8.3.2 · **Gradle** 8.6
- `buildFeatures { compose = true; viewBinding = true; buildConfig = true }`
- Release: ProGuard + resource shrinking; `app/lint-baseline.xml` suppresses known lint issues.

## Conventions & gotchas (future reference)

- **Reskinning a screen?** Keep the ViewModel/repository; only swap the composable. State stays
  hoisted to the VM.
- **New screen?** `XxxActivity` (glue) + `XxxViewModel` (StateFlow state) + `XxxScreen` (stateless
  composable in `TibetanKeyboardTheme` + `ScreenScaffold`). Wrap callbacks in an `XxxActions` class.
- **Touching the keyboard?** It "functions exact" — restyle drawables/`onDraw`/colours only; never
  alter key codes, `onKey()` routing, or the `res/xml` key definitions. Its size on a screen is
  `KeyboardSizing`'s to decide: change the rule and its test, not the XML.
- **Adding strings?** Check `strings.xml` for an existing key first (duplicate names fail the
  resource merge).
- **Always device-verify Compose changes** (`installDebug` + screenshot), not just `assembleDebug` —
  a green build can still crash at runtime (e.g. the empty-font bug).
- **Never start the Android emulator** unless the user asked for it, or said yes when asked. It is
  slow to boot and device checks belong on the connected phone. If the phone can't take the build
  (a debug build can't replace a Play-installed one: the signatures differ, and uninstalling wipes
  the app's data), stop and ask how to verify instead of reaching for the emulator.
- A few inline strings remain (Premium feature descriptions, quick-action labels, Tibetan
  placeholders) — externalize if expanding i18n.
