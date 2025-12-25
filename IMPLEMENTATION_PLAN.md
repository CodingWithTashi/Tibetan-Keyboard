# Tibetan Keyboard Premium Features - Implementation Plan

## Overview
Implementation of 3 advanced premium features with consistent Material Design 3 UI, smooth Lottie animations, and intuitive user interactions.

---

## DESIGN SYSTEM & UI STANDARDS

### Color Palette
- **Primary**: #FF704C04 (Brown)
- **Primary Dark**: #77530D (Dark Brown)
- **Primary Light**: #A87A3D (Light Brown)
- **Accent**: #C09A5B (Golden)
- **Premium Yellow**: #FFFFD700 (Gold highlight)
- **Background**: #FF704C04 / #77530D
- **Card Background**: White (#FFFFFFFF)
- **Text Primary**: #FF000000
- **Text Secondary**: #77530D (60% opacity)
- **Error**: #F44336
- **Success**: #4CAF50
- **Warning**: #FF9800

### Typography
- **Headlines**: Material Design 3 (Headline Large: 32sp, Medium: 28sp, Small: 24sp)
- **Body**: Body Large (16sp), Body Medium (14sp), Body Small (12sp)
- **Font**: Roboto (Material default), Qomolangma Tsutong for Tibetan text

### Animation Framework
- **Primary Animation Library**: Lottie (already integrated)
- **Transition Duration**: 300-400ms for standard transitions
- **Long-running Operations**: 1.5-2s Lottie animations
- **Micro-interactions**: 150-200ms ripple effects

### Component Standards
- **Corner Radius**: 16dp for cards, 24dp for input fields
- **Elevation**: 4dp for cards, 0dp for flat surfaces
- **Padding**: 16dp standard, 8dp compact
- **Spacing**: 8dp base unit

---

## FEATURE 1: ADVANCED TIBETAN GRAMMAR & WRITING STYLE ASSISTANT

### Architecture

#### Frontend Components

**1. GrammarActivity.kt**
```
- Display text analysis with real-time suggestions
- Show tone/style indicators
- Display confidence scores
- Allow inline corrections with suggestions
```

**2. GrammarToolbar.kt**
```
- Quick access button in keyboard
- Mini floating action button with grammar icon
- Overlay suggestion cards with animations
```

**3. GrammarSuggestionCard.kt (Composable-style or custom View)
```
- Animated suggestion cards that slide in from right
- Show: original text, correction, explanation, confidence score
- Action buttons: Accept (with ripple), Dismiss, Learn More
```

**4. ToneAnalysisView.kt**
```
- Tone gauge visualization (Formal ↔ Casual)
- Suggested alternatives in the detected tone
- Example sentences with Tibetan context
```

#### Backend Endpoints

**POST /api/grammar/analyze** (Premium)
```json
Request:
{
  "text": "Tibetan text here",
  "mode": "realtime|detailed",
  "style": "formal|casual|poetic|religious|modern",
  "contextualInfo": {
    "userLevel": "beginner|intermediate|advanced",
    "documentType": "formal|casual|literary|religious"
  }
}

Response:
{
  "success": true,
  "corrections": [
    {
      "id": "correction_1",
      "position": { "start": 5, "end": 15 },
      "originalText": "...",
      "correctedText": "...",
      "reason": "Grammar explanation",
      "confidence": 0.95,
      "alternatives": ["alt1", "alt2"],
      "explanation": "Detailed explanation in Tibetan"
    }
  ],
  "toneAnalysis": {
    "detectedTone": "formal",
    "score": 0.85,
    "suggestions": [...]
  },
  "overallScore": 0.88,
  "estimatedReadingLevel": "advanced"
}
```

**POST /api/grammar/suggestions** (Premium)
```json
Request:
{
  "text": "...",
  "correctionId": "correction_1",
  "type": "alternatives|explanation|examples"
}

Response:
{
  "alternatives": [...],
  "examples": [{ "text": "...", "meaning": "..." }],
  "culturalNotes": "..."
}
```

#### Database Schema (Firestore)

**Collection: `users/{userId}/grammar_history`**
```
{
  "documentId": auto-generated,
  "text": "original text",
  "corrections": [...],
  "timestamp": serverTimestamp,
  "documentType": "email|formal|casual",
  "savedByUser": boolean,
  "improvements": number
}
```

**Collection: `grammar_rules`** (Admin-managed)
```
{
  "ruleId": "rule_001",
  "category": "syntax|vocabulary|punctuation|tone",
  "pattern": "regex or rule definition",
  "correction": "correction template",
  "confidence": 0.85,
  "tibetanExplanation": "...",
  "examples": [...]
}
```

#### Tibetan Grammar Rules Engine
```
File: AIGrammarEngine.kt
- ~50 Tibetan-specific rules
- Real-time rule matching as user types
- Confidence scoring based on rule certainty
- Pattern matching for common errors
```

#### Integration Points
1. Hook into keyboard input (AIKeyboardView)
2. Optional button in IME toolbar
3. Floating action button in keyboard
4. Chat feature can trigger grammar check

---

## FEATURE 2: TIBETAN-ENGLISH PHONETIC TRANSLITERATION SYSTEM

### Architecture

#### Frontend Components

**1. TransliterationActivity.kt**
```
- Bidirectional input interface
- Two input fields: English phonetics ↔ Tibetan script
- Real-time conversion with animations
- Support multiple romanization systems
```

**2. RomanizationSystemSelector.kt**
```
- Radio buttons/toggle for different systems
- Wylie, DTS, Pinyin-style, IPA
- Visual indication of selected system
- Description tooltips
```

**3. TransliterationSuggestionChip.kt**
```
- Suggestion chips that expand on tap
- Show alternative spellings/romanizations
- Historical variants for names
- Pronunciation hints
```

**4. TransliterationKeyboard.kt** (Optional - for inline transliteration)
```
- Special keyboard layout for phonetic input
- Diacritical marks toolbar
- Quick access to common combinations
```

#### Backend Endpoints

**POST /api/transliterate/convert** (Premium)
```json
Request:
{
  "text": "input text",
  "sourceSystem": "tibetan|wylie|dts|pinyin|ipa",
  "targetSystem": "tibetan|wylie|dts|pinyin|ipa",
  "includeAlternatives": boolean,
  "context": "name|place|common|religious"
}

Response:
{
  "success": true,
  "result": "converted text",
  "alternatives": [
    { "text": "alt1", "frequency": 0.8, "context": "historical" },
    { "text": "alt2", "frequency": 0.6, "context": "colloquial" }
  ],
  "confidence": 0.95,
  "pronunciation": "phonetic guide if available"
}
```

**POST /api/transliterate/database/lookup** (Premium)
```json
Request:
{
  "query": "search term",
  "type": "name|place|common_word",
  "limit": 10
}

Response:
{
  "results": [
    {
      "tibetan": "...",
      "romanizations": { "wylie": "...", "dts": "..." },
      "meaning": "...",
      "category": "name|place|common",
      "frequency": 0.8
    }
  ]
}
```

**POST /api/transliterate/validate** (Free/Premium)
```json
Request: { "text": "...", "system": "..." }
Response: { "isValid": boolean, "suggestions": [...] }
```

#### Database Schema (Firestore)

**Collection: `transliteration/wylie/mappings`**
```
{
  "phonetic": "kha",
  "tibetan": "ཁ",
  "dts": "kha",
  "frequency": 0.95,
  "context": ["initial", "medial", "final"],
  "examples": [...]
}
```

**Collection: `transliteration/names_places`**
```
{
  "nameId": "auto",
  "tibetan": "དཔལ་ལྡན་",
  "wylie": "dpal ldan",
  "dts": "pal den",
  "meaning": "glorious/auspicious",
  "type": "name|place",
  "region": "Lhasa|Shigatse|etc",
  "frequency": 0.8,
  "searchIndex": "pal|dpal|ldan"
}
```

**Collection: `users/{userId}/transliteration_history`**
```
{
  "timestamp": serverTimestamp,
  "sourceText": "...",
  "sourceSystem": "wylie",
  "targetSystem": "tibetan",
  "result": "...",
  "savedByUser": boolean
}
```

#### Phonetic Mapping Engine
```
File: PhoneticTransliterator.kt
- Bidirectional phonetic-to-Unicode mapping tables
- Support for 4+ romanization systems
- Tone mark handling
- Context-aware disambiguation
- Caching for performance
```

#### Data Files Required
```
- phonetic_wylie_to_tibetan.json (comprehensive mapping)
- phonetic_dts_to_tibetan.json
- tibetan_names_database.json (50+ names with variants)
- tibetan_places_database.json (30+ common places)
- historical_variants.json (alternative spellings)
```

#### Integration Points
1. Standalone activity (Main menu)
2. Keyboard toolbar quick access
3. Chat contextual feature
4. Clipboard monitoring (optional)

---

## FEATURE 3: INTELLIGENT CHAT WITH PERSISTENT HISTORY & DOCUMENT SUPPORT

### Architecture

#### Frontend Components

**1. EnhancedChatActivity.kt** (Enhanced version of ChatActivity)
```
- New sections:
  - Chat History sidebar/tabs
  - Document upload button
  - Conversation search
  - New chat creation
  - Chat archive/favorites
```

**2. ChatHistoryBottomSheet.kt**
```
- List of all conversations with:
  - Last message preview
  - Date/time
  - Message count
  - Unread indicator
  - Delete/archive/favorite actions
- Search filter
- Sort options (recent, oldest, alphabetical)
```

**3. DocumentUploadBottomSheet.kt**
```
- File picker for Tibetan documents
- Supported formats: PDF, TXT, DOC
- Upload progress with Lottie animation
- Document preview
```

**4. DocumentAnalysisCard.kt**
```
- Display document:
  - Summary (AI generated)
  - Key points extracted
  - Grammar analysis
  - Readability score
- Actions: Improve, Translate, Explain, Ask Questions
```

**5. ChatCollectionCard.kt**
```
- Visually distinct chat group cards
- Category indicators (tutoring, translation, discussion)
- Last message preview
- Member count for shared chats
```

**6. ConversationSearchBar.kt**
```
- Real-time search through chat history
- Filter by date range
- Filter by topic/keyword
- Highlighted search results
```

**7. TutoringModePanel.kt**
```
- Toggle for tutoring mode
- Level selection (beginner/intermediate/advanced)
- Topic selection for focused learning
- Progress indicator
```

**8. ExportChatDialog.kt**
```
- Export formats: PDF, TXT, Markdown
- Include/exclude metadata
- Beautiful formatting with branding
- Email share option
```

#### Backend Endpoints

**POST /api/chat/message** (Premium - Enhanced)
```json
Request:
{
  "sessionId": "chat_session_xyz",
  "message": "user message",
  "userId": "user_id",
  "conversationMode": "general|tutoring|translation",
  "tutoringLevel": "beginner|intermediate|advanced",
  "documentContext": "doc_id_if_applicable",
  "includeExplanation": boolean
}

Response:
{
  "success": true,
  "data": {
    "sessionId": "chat_session_xyz",
    "messageId": "msg_123",
    "response": "AI response in Tibetan",
    "alternatives": ["alt1", "alt2"],
    "confidence": 0.92,
    "relatedTopics": ["grammar", "vocabulary"],
    "explanations": {
      "detailed": "Long explanation",
      "simple": "Simple explanation",
      "examples": [...]
    }
  },
  "timestamp": serverTimestamp
}
```

**GET /api/chat/history** (Premium)
```json
Request:
{
  "userId": "user_id",
  "limit": 20,
  "offset": 0,
  "filterByMode": "general|tutoring|all",
  "searchQuery": "optional search term",
  "dateRange": { "start": "2024-01-01", "end": "2024-12-31" }
}

Response:
{
  "conversations": [
    {
      "conversationId": "conv_1",
      "title": "Auto-generated title",
      "mode": "general",
      "messageCount": 15,
      "lastMessageTime": timestamp,
      "preview": "last message...",
      "archived": false,
      "favorite": true
    }
  ],
  "totalCount": 150
}
```

**POST /api/chat/document/upload** (Premium)
```json
Request: FormData
{
  "file": File,
  "sessionId": "chat_session_xyz",
  "documentType": "text|pdf",
  "language": "tibetan|english|mixed"
}

Response:
{
  "success": true,
  "documentId": "doc_123",
  "preview": "first 500 chars",
  "wordCount": 2500,
  "language": "tibetan",
  "extractedText": "full text",
  "summary": "AI-generated summary"
}
```

**POST /api/chat/document/analyze** (Premium)
```json
Request:
{
  "documentId": "doc_123",
  "analysisType": "summary|grammar|readability|keywords",
  "language": "tibetan|english"
}

Response:
{
  "summary": "...",
  "keyPoints": [...],
  "readabilityScore": 0.75,
  "grammaticalErrors": [...],
  "suggestedImprovements": [...]
}
```

**POST /api/chat/export** (Premium)
```json
Request:
{
  "conversationId": "conv_1",
  "format": "pdf|txt|markdown",
  "includeMetadata": boolean,
  "style": "dark|light|minimal"
}

Response:
{
  "success": true,
  "downloadUrl": "temporary_signed_url",
  "expiresIn": 3600
}
```

**POST /api/chat/conversation/create** (Premium)
```json
Request:
{
  "title": "Conversation title (optional, auto-generated if not provided)",
  "mode": "general|tutoring|translation",
  "tutoringLevel": "beginner|intermediate|advanced",
  "tags": ["tag1", "tag2"]
}

Response:
{
  "conversationId": "conv_xyz",
  "sessionId": "chat_session_xyz"
}
```

**POST /api/chat/conversation/search** (Premium)
```json
Request:
{
  "userId": "user_id",
  "query": "search term",
  "limit": 20,
  "filterByDate": { "start": "...", "end": "..." }
}

Response:
{
  "results": [
    {
      "conversationId": "conv_1",
      "messageId": "msg_456",
      "snippet": "matching text context...",
      "matchScore": 0.95
    }
  ]
}
```

**POST /api/chat/tutoring/mode** (Premium)
```json
Request:
{
  "sessionId": "chat_session_xyz",
  "enabled": true,
  "level": "beginner|intermediate|advanced",
  "topic": "grammar|vocabulary|writing|culture",
  "focusAreas": ["article_usage", "verb_conjugation"]
}

Response:
{
  "success": true,
  "systemPrompt": "updated AI instructions",
  "curriculum": {
    "currentLesson": "lesson_1",
    "nextLesson": "lesson_2",
    "progressPercentage": 25
  }
}
```

#### Database Schema (Firestore)

**Collection: `users/{userId}/conversations`**
```
{
  "conversationId": "auto-generated",
  "title": "Conversation Title (Auto-generated or user-set)",
  "mode": "general|tutoring|translation",
  "tutoringLevel": "beginner|intermediate|advanced",
  "createdAt": serverTimestamp,
  "updatedAt": serverTimestamp,
  "messageCount": 15,
  "archived": false,
  "favorite": true,
  "tags": ["tag1", "tag2"],
  "preview": "last message text (for quick access)",
  "documentIds": ["doc_1", "doc_2"],
  "participants": ["user_id"], // For future shared chats
  "searchIndex": "concatenated text for full-text search"
}
```

**Collection: `users/{userId}/conversations/{conversationId}/messages`**
```
{
  "messageId": "auto-generated",
  "sender": "user|assistant",
  "content": "message text",
  "timestamp": serverTimestamp,
  "mode": "general|tutoring|translation",
  "confidence": 0.92,
  "alternatives": ["alt1", "alt2"],
  "metadata": {
    "tokens_used": 150,
    "response_time_ms": 2500,
    "language": "tibetan|english|mixed"
  },
  "documentReference": "doc_id_if_applicable"
}
```

**Collection: `users/{userId}/documents`**
```
{
  "documentId": "auto-generated",
  "fileName": "document_name.pdf",
  "contentType": "application/pdf|text/plain",
  "uploadedAt": serverTimestamp,
  "fileSize": 512000,
  "extractedText": "full text content",
  "preview": "first 500 chars",
  "language": "tibetan|english|mixed",
  "wordCount": 2500,
  "summary": "AI-generated summary",
  "analysis": {
    "grammar": {...},
    "readability": {...},
    "keywords": [...]
  },
  "conversationId": "associated_conversation",
  "isArchived": false
}
```

**Collection: `chat_metadata`** (Global - for autocomplete/suggestions)
```
{
  "conversationTitle": "title",
  "frequency": count,
  "tags": ["tag1", "tag2"],
  "language": "tibetan"
}
```

#### Storage (Cloud Storage or Firebase Storage)

```
/documents/{userId}/{documentId}/original.pdf
/documents/{userId}/{documentId}/extracted_text.txt
/exports/{userId}/{conversationId}/export_{timestamp}.pdf
```

#### Chat Session Manager Enhancement (Backend)
```
File: ChatSessionManager.ts (Enhanced)
- Initialize multiple concurrent sessions
- Maintain conversation context
- Support tutoring mode with curriculum tracking
- Document context injection into prompts
- Conversation history retrieval
- Message search indexing
```

#### Gemini System Prompts

**General Chat Mode:**
```
You are Lundup, a helpful AI assistant specialized in Tibetan language and culture.
You respond ONLY in Tibetan script. Always provide clear, culturally appropriate responses.
```

**Tutoring Mode (Beginner):**
```
You are a Tibetan language tutor for beginners.
- Use simple, clear Tibetan script
- Explain grammar concepts with examples
- Provide encouragement
- Build foundational vocabulary
- Ask comprehension questions
```

**Tutoring Mode (Intermediate):**
```
You are an advanced Tibetan language tutor.
- Introduce complex grammar structures
- Discuss cultural context and nuances
- Encourage creative writing
- Provide detailed explanations
```

**Translation Mode:**
```
You are a specialized Tibetan-English translator.
- Provide accurate translations
- Offer alternatives with cultural notes
- Explain translation choices
- Support both directions
```

#### Integration Points
1. Enhanced existing ChatActivity
2. New History sidebar/tabs
3. Keyboard floating action button for quick access
4. Home activity menu item
5. Premium badge/indicator

---

## PREMIUM FEATURE GATING & MONETIZATION

### Subscription Tiers

**Free Tier (Limited)**
- Grammar: 5 checks/day, basic suggestions
- Transliteration: 3 conversions/day, basic system only
- Chat: 10 messages/day, no history persistence, no document upload

**Premium Tier (Full Access)**
- Grammar: Unlimited checks, all features
- Transliteration: Unlimited conversions, all romanization systems
- Chat: Unlimited messages, persistent history, document support, tutoring mode, export
- Offline support (grammar rules)

### Paywall Implementation

**Strategy:**
1. All three features have **free preview** (1-2 limited uses)
2. **Premium dialog** after limit exceeded
3. **Contextual upsell** - show benefits relevant to what user is doing
4. **RevenueCat integration** for subscription management (already in place)

**Points of Upsell:**
1. First grammar check (free)
2. First transliteration (free)
3. Chat message 11+ shows premium dialog
4. Document upload attempt without premium
5. History/export features gated

---

## ANIMATION STRATEGY

### Lottie Animations Required

1. **loading_animation.json** (Already exists)
   - Use for: API calls, document processing

2. **grammar_check_success.json**
   - Brief celebration animation (0.5s)
   - Checkmark with subtle bounce
   - Color: Success green

3. **transliteration_converting.json**
   - Animated arrows between languages (0.8s)
   - Smooth fluid motion
   - Color: Primary brown

4. **document_uploading.json**
   - File icon with upload progress (1.5-2s)
   - Completion burst
   - Color: Primary brown

5. **chat_message_arriving.json**
   - Subtle slide-in animation (0.3s)
   - Message bubble appearing
   - Color: Primary brown

6. **premium_unlock.json**
   - Celebratory animation (0.8s)
   - Lock to unlock transition
   - Color: Premium yellow

### Transition Animations

- **Activity Transitions**: Shared element transitions with 300ms duration
- **Card Expansions**: ScaleAnimation from center, 300ms
- **Message Arrival**: SlideIn from bottom + FadeIn, 200ms
- **Suggestion Cards**: SlideInRight with 400ms stagger
- **Bottom Sheet**: Standard material bottom sheet animation

### Micro-interactions

- **Button Ripple**: 200ms material ripple on tap
- **Input Focus**: Light color change + icon animation, 150ms
- **Chip Selection**: Scale up 1.05x + color change, 200ms
- **Error Shake**: Horizontal shake animation, 400ms
- **Success Check**: Rotating checkmark, 600ms

---

## SHARED UTILITIES & EXTENSIONS

### TibetanTextUtils.kt
```kotlin
- validateTibetanText(text: String): Boolean
- isTibetanScript(char: Char): Boolean
- cleanTibetanText(text: String): String
- detectLanguage(text: String): Language
```

### AnimationUtils.kt
```kotlin
- createSlideInAnimation(duration: Long): Animation
- createScaleAnimation(fromScale: Float, toScale: Float): Animation
- createShakeAnimation(): Animation
- playSuccessAnimation(view: View)
- playErrorAnimation(view: View)
```

### PremiumFeatureManager.kt
```kotlin
- isPremiumFeatureAvailable(feature: String): Boolean
- checkFeatureLimit(feature: String): Int (remaining uses)
- showPremiumDialog(feature: String)
- trackFeatureUsage(feature: String)
- incrementFeatureCounter(feature: String)
```

### FirestoreExtensions.kt
```kotlin
- Collection references with type safety
- Automatic timestamp handling
- Batch operations for efficiency
- Search query helpers
```

---

## IMPLEMENTATION SEQUENCE

### Phase 1: Backend Infrastructure (Day 1-2)
1. Create new API endpoints in Express backend
2. Add Firestore collections and indexes
3. Create ChatSessionManager enhancements
4. Add Gemini prompt templates

### Phase 2: Grammar Feature (Day 2-3)
1. Create GrammarActivity + UI components
2. Create grammar rule engine
3. Implement grammar API integration
4. Add animations and transitions
5. Premium gating

### Phase 3: Transliteration Feature (Day 3-4)
1. Create phonetic mapping files
2. Create TransliterationActivity + UI
3. Implement phonetic engine
4. Create transliteration API integration
5. Add animations
6. Premium gating

### Phase 4: Chat Enhancement (Day 4-5)
1. Enhance existing ChatActivity
2. Add history sidebar
3. Add document upload/analysis
4. Implement conversation search
5. Add export functionality
6. Add tutoring mode
7. Persistent storage integration
8. Animations and transitions

### Phase 5: Testing & Polish (Day 5-6)
1. End-to-end testing
2. Performance optimization
3. Animation refinement
4. UI/UX polish
5. Premium feature testing
6. Documentation

---

## KEY CONSIDERATIONS

### Performance
- Cache translation results in SharedPreferences
- Implement pagination for chat history (load 20 messages at a time)
- Lazy load documents
- Debounce grammar checks (200ms delay)
- Index Firestore collections properly

### Security
- Validate all user input before API calls
- Implement rate limiting on backend (already in place)
- Encrypt sensitive data in Firestore
- Use HTTPS for all API calls
- Validate file uploads (type, size, content scan)

### User Experience
- Clear loading states with animations
- Helpful error messages in Tibetan
- Undo/Redo for grammar corrections
- Offline fallback for common operations
- Intuitive navigation between features

### Accessibility
- Support system text scaling
- Sufficient color contrast
- Descriptive content descriptions
- Screen reader support
- Keyboard navigation

---

## DELIVERABLES

### Code
- ✅ 8 new Activities/Screens
- ✅ 15+ new UI Components
- ✅ 10+ new API endpoints
- ✅ Grammar rule engine (50+ rules)
- ✅ Phonetic transliteration engine
- ✅ Chat session manager enhancements
- ✅ 6 Lottie animation files
- ✅ Comprehensive error handling
- ✅ Premium feature gating

### Database
- ✅ 8 Firestore collections with indexes
- ✅ Cloud Storage integration
- ✅ Search indexing
- ✅ Data migration scripts (if needed)

### Documentation
- ✅ Inline code comments
- ✅ Architecture documentation
- ✅ API documentation
- ✅ User guide (in app)

---

## SUCCESS METRICS

- ✅ All features work with 90%+ reliability
- ✅ Smooth 60fps animations
- ✅ API response time < 2 seconds
- ✅ Premium signup increase by feature visibility
- ✅ User engagement increase in chat features
- ✅ 4.8+ star rating for new features

