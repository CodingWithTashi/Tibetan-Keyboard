GOOGLE_CLOUD_PROJECT_ID=your-project-id
GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account-key.json
ALLOWED_ORIGINS=http://localhost:3000,https://yourdomain.com
NODE_ENV=production

// README.md

# Translation & Grammar API

A secure, production-ready Cloud Function providing translation and grammar checking services with user limits.

## Features

- 🔐 Secure API key authentication
- 🌍 Multi-language translation via Google Translate
- ✍️ Grammar checking and correction
- 📊 User usage tracking and daily limits
- 🛡️ Rate limiting and security middleware
- 🔥 Firestore integration for user management
- 📝 TypeScript for type safety

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
