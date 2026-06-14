import * as admin from "firebase-admin";
import * as crypto from "crypto";

/**
 * Two-tier cache for deterministic AI responses (translation, repeated chat turns).
 *
 * Standard flow:
 *   Memory cache  -> HIT -> return
 *        | MISS
 *   Firestore     -> HIT -> save to memory -> return
 *        | MISS
 *   AI provider   -> save to Firestore -> save to memory -> return
 *
 * The Firestore tier is best-effort: any error (e.g. Admin SDK not initialised
 * in a unit test) is swallowed and treated as a miss, so the memory tier alone
 * keeps working.
 */

interface MemoryEntry {
  value: string;
  expiresAt: number;
}

const MEMORY_TTL_MS = 60 * 60 * 1000; // 1 hour
const MEMORY_MAX_ENTRIES = 1000;
const FIRESTORE_COLLECTION = "ai_cache";
const FIRESTORE_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

// Process-local cache. Persists across warm Cloud Function invocations.
const memoryCache = new Map<string, MemoryEntry>();

export type CacheSource = "memory" | "firestore" | "provider";

export interface CachedResult {
  value: string;
  source: CacheSource;
}

/** Deterministic cache key from a namespace + the inputs that define the output. */
export function makeCacheKey(
  namespace: string,
  parts: Record<string, unknown>
): string {
  const canonical = JSON.stringify(parts, Object.keys(parts).sort());
  const hash = crypto
    .createHash("sha256")
    .update(`${namespace}:${canonical}`)
    .digest("hex");
  return `${namespace}_${hash}`;
}

function readMemory(key: string): string | null {
  const entry = memoryCache.get(key);
  if (!entry) return null;
  if (entry.expiresAt < Date.now()) {
    memoryCache.delete(key);
    return null;
  }
  // Refresh LRU recency.
  memoryCache.delete(key);
  memoryCache.set(key, entry);
  return entry.value;
}

function writeMemory(key: string, value: string): void {
  if (memoryCache.size >= MEMORY_MAX_ENTRIES) {
    const oldest = memoryCache.keys().next().value;
    if (oldest !== undefined) memoryCache.delete(oldest);
  }
  memoryCache.set(key, { value, expiresAt: Date.now() + MEMORY_TTL_MS });
}

async function readFirestore(key: string): Promise<string | null> {
  try {
    const doc = await admin
      .firestore()
      .collection(FIRESTORE_COLLECTION)
      .doc(key)
      .get();
    if (!doc.exists) return null;
    const data = doc.data();
    if (!data || typeof data.value !== "string") return null;
    const createdAtMs: number = data.createdAtMs || 0;
    if (createdAtMs && Date.now() - createdAtMs > FIRESTORE_TTL_MS) return null;
    return data.value;
  } catch (error) {
    console.warn("ai_cache: Firestore read failed (treating as miss):", error);
    return null;
  }
}

async function writeFirestore(
  key: string,
  value: string,
  meta: Record<string, unknown>
): Promise<void> {
  try {
    await admin
      .firestore()
      .collection(FIRESTORE_COLLECTION)
      .doc(key)
      .set({
        ...meta,
        value,
        createdAtMs: Date.now(),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
  } catch (error) {
    console.warn("ai_cache: Firestore write failed (non-fatal):", error);
  }
}

/**
 * Resolve `key` through memory -> Firestore -> `provider`, populating the upper
 * tiers on a miss. Returns the value and which tier served it.
 */
export async function cachedCompute(
  key: string,
  provider: () => Promise<string>,
  meta: Record<string, unknown> = {}
): Promise<CachedResult> {
  const fromMemory = readMemory(key);
  if (fromMemory !== null) return { value: fromMemory, source: "memory" };

  const fromFirestore = await readFirestore(key);
  if (fromFirestore !== null) {
    writeMemory(key, fromFirestore);
    return { value: fromFirestore, source: "firestore" };
  }

  const value = await provider();
  await writeFirestore(key, value, meta);
  writeMemory(key, value);
  return { value, source: "provider" };
}

/** Test helper — clears the in-memory tier. */
export function __clearMemoryCache(): void {
  memoryCache.clear();
}
