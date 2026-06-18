import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  cachedCompute,
  makeCacheKey,
  __clearMemoryCache,
} from "../services/cacheService";

// These tests exercise the memory tier only. The Admin SDK is intentionally not
// initialised, so the Firestore tier always throws and is treated as a miss —
// which is exactly the behaviour cacheService promises.

beforeEach(() => {
  __clearMemoryCache();
});

test("makeCacheKey is deterministic and order-independent", () => {
  const a = makeCacheKey("translate", { text: "hi", targetLang: "bo", model: "m" });
  const b = makeCacheKey("translate", { model: "m", targetLang: "bo", text: "hi" });
  assert.equal(a, b);
  assert.match(a, /^translate_[0-9a-f]{64}$/);
});

test("different inputs produce different keys", () => {
  const a = makeCacheKey("translate", { text: "hi", model: "m" });
  const b = makeCacheKey("translate", { text: "bye", model: "m" });
  assert.notEqual(a, b);
});

test("nested object content differentiates keys (chat messages)", () => {
  // Regression: the array-replacer form of JSON.stringify stripped nested keys,
  // collapsing every first-turn chat message onto one cache key.
  const a = makeCacheKey("chat", {
    model: "m",
    system: "sys",
    messages: [{ role: "user", content: "hello" }],
  });
  const b = makeCacheKey("chat", {
    model: "m",
    system: "sys",
    messages: [{ role: "user", content: "what is the weather?" }],
  });
  assert.notEqual(a, b);
});

test("nested keys are order-independent", () => {
  const a = makeCacheKey("chat", {
    messages: [{ role: "user", content: "hi" }],
    model: "m",
  });
  const b = makeCacheKey("chat", {
    model: "m",
    messages: [{ content: "hi", role: "user" }],
  });
  assert.equal(a, b);
});

test("first call hits provider, second identical call hits memory", async () => {
  let calls = 0;
  const provider = async () => {
    calls += 1;
    return `value-${calls}`;
  };
  const key = makeCacheKey("translate", { text: "hello", model: "haiku" });

  const first = await cachedCompute(key, provider);
  assert.equal(first.source, "provider");
  assert.equal(first.value, "value-1");

  const second = await cachedCompute(key, provider);
  assert.equal(second.source, "memory");
  assert.equal(second.value, "value-1"); // same cached value
  assert.equal(calls, 1, "provider must only be called once");
});

test("distinct keys each invoke the provider", async () => {
  let calls = 0;
  const provider = async () => {
    calls += 1;
    return `value-${calls}`;
  };

  await cachedCompute(makeCacheKey("translate", { text: "a" }), provider);
  await cachedCompute(makeCacheKey("translate", { text: "b" }), provider);

  assert.equal(calls, 2);
});

test("provider rejection is propagated and not cached", async () => {
  const key = makeCacheKey("translate", { text: "boom" });
  await assert.rejects(
    cachedCompute(key, async () => {
      throw new Error("provider failed");
    }),
    /provider failed/
  );

  // A subsequent successful call must run the provider (nothing was cached).
  const ok = await cachedCompute(key, async () => "recovered");
  assert.equal(ok.source, "provider");
  assert.equal(ok.value, "recovered");
});
