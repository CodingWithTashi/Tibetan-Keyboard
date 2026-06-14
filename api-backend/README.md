GOOGLE_CLOUD_PROJECT_ID=your-project-id
GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account-key.json
ALLOWED_ORIGINS=http://localhost:3000,https://yourdomain.com
NODE_ENV=production

// README.md

# Translation & Grammar API

A secure, production-ready Cloud Function providing translation and grammar checking services with user limits.

## Features

- 🤖 Chat & translation powered by **Claude** (`claude-haiku-4-5` / `claude-sonnet-4-6`, user-switchable per request)
- ⚡ Two-tier response cache: in-memory → Firestore (`ai_cache`) → Claude
- 🛡️ Per-IP rate limiting on the AI endpoints (30 req/min) + global limiter
- ✍️ Grammar checking and correction
- 🔥 Firestore integration for user management
- 🧪 Unit tests (`npm test`) + structured logging (`firebase-functions/logger`)
- 📝 TypeScript for type safety

## Claude (Anthropic) configuration

`/chat` and `/translate` call Claude via the official `@anthropic-ai/sdk`. The
model is chosen per request via a `model` field/header (`claude-haiku-4-5` or
`claude-sonnet-4-6`; defaults to Haiku). Set the API key as a Firebase secret —
**do not commit it**:

```bash
firebase functions:secrets:set ANTHROPIC_API_KEY
```

For local emulation, put `ANTHROPIC_API_KEY=...` in `.env` (gitignored).

Run the unit tests (cache flow + model resolution + session helpers):

```bash
npm test
```

## Setup

1. **Install dependencies:**

   ```bash
   npm install
   ```

2. **Configure environment:**

   - Copy `.env.example` to `.env`
   - Update with your Google Cloud project details
   - Set up Google Cloud service account for Translate API

3. **Initialize Firestore:**

   ```bash
   firebase init firestore
   ```

4. **Deploy:**
   ```bash
   npm run deploy
   ```

## API Endpoints

### POST /translate

Translates text between languages.

**Headers:**

- `x-api-key`: Your API key

**Body:**

```json
{
  "text": "Hello world",
  "from": "en",
  "to": "es"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "translatedText": "Hola mundo"
  },
  "usage": {
    "charactersUsed": 11,
    "remainingCharacters": 4989
  }
}
```

### POST /grammar

Checks and corrects grammar in text.

**Headers:**

- `x-api-key`: Your API key

**Body:**

```json
{
  "text": "this is example text with grammar issue"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "correctedText": "This is an example text with grammar issues.",
    "corrections": [
      {
        "original": "this",
        "corrected": "This",
        "type": "capitalization",
        "position": 0
      }
    ],
    "confidence": 0.85
  },
  "usage": {
    "charactersUsed": 38,
    "remainingCharacters": 2962
  }
}
```

## Daily Limits

- Translation: 5,000 characters per day
- Grammar: 3,000 characters per day
- Maximum text length: 1,000 characters per request

## Security Features

- API key authentication
- Rate limiting (100 requests per 15 minutes per IP)
- Input validation and sanitization
- CORS protection
- Helmet security headers
- Error handling without sensitive data exposure

## Firestore Collections

### api_keys

```json
{
  "userId": "user123",
  "active": true,
  "createdAt": "timestamp",
  "expiresAt": "timestamp"
}
```

### users

```json
{
  "translationLimit": 5000,
  "grammarLimit": 3000,
  "translationUsed": 1250,
  "grammarUsed": 800,
  "resetDate": "2025-09-01"
}
```

## Development

```bash
# Build
npm run build

# Local development
npm run serve

# Deploy
npm run deploy

# View logs
npm run logs
```

## Environment Variables

- `GOOGLE_CLOUD_PROJECT_ID`: Your Google Cloud project ID
- `GOOGLE_APPLICATION_CREDENTIALS`: Path to service account JSON
- `ALLOWED_ORIGINS`: Comma-separated list of allowed CORS origins
- `NODE_ENV`: Environment (development/production)

## Notes

- Replace the grammar service implementation with your preferred provider
- Consider implementing JWT tokens for enhanced security
- Monitor usage and costs in Google Cloud Console
- Set up proper logging and monitoring for production use
