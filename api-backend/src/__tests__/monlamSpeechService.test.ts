import { test } from "node:test";
import assert from "node:assert/strict";
import { transcribe, SpeechError } from "../services/monlamSpeechService";

const WAV = Buffer.from("RIFF....WAVEfmt fake-audio-bytes");
const URL_UNDER_TEST = "https://monlam.test/api/v1/speech-to-text/";

interface Captured {
  url?: string;
  init?: RequestInit;
}

function okFetch(body: unknown, captured: Captured = {}): typeof fetch {
  return (async (url: string, init?: RequestInit) => {
    captured.url = url;
    captured.init = init;
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as unknown as typeof fetch;
}

function statusFetch(status: number): typeof fetch {
  return (async () => new Response("{}", { status })) as unknown as typeof fetch;
}

function opts(fetchImpl: typeof fetch) {
  return { apiKey: "secret-key", idempotencyKey: "rec-123", url: URL_UNDER_TEST, fetchImpl };
}

test("transcribe posts the audio as multipart with the key and idempotency headers", async () => {
  const captured: Captured = {};
  await transcribe(WAV, opts(okFetch({ text: "བཀྲ་ཤིས།" }, captured)));

  assert.equal(captured.url, URL_UNDER_TEST);
  assert.equal(captured.init?.method, "POST");

  const headers = captured.init?.headers as Record<string, string>;
  assert.equal(headers["X-API-Key"], "secret-key");
  assert.equal(headers["x-idempotency-key"], "rec-123");

  const form = captured.init?.body as FormData;
  assert.equal(form.get("language"), "bo");
  assert.equal(form.get("task"), "transcribe");
  const file = form.get("file") as File;
  assert.equal(file.name, "audio.wav");
  assert.equal(file.type, "audio/wav");
  assert.equal(file.size, WAV.length);
});

test("transcribe returns the transcript and the billing metadata", async () => {
  const result = await transcribe(
    WAV,
    opts(okFetch({ text: "བཀྲ་ཤིས།", cost: 0.01, latency_ms: 700, service_request_id: "r1" }))
  );
  assert.equal(result.text, "བཀྲ་ཤིས།");
  assert.deepEqual(result.billing, { cost: 0.01, latencyMs: 700, requestId: "r1" });
});

test("transcribe returns an empty transcript for silence instead of failing", async () => {
  const result = await transcribe(WAV, opts(okFetch({ text: "" })));
  assert.equal(result.text, "");
});

test("transcribe never puts the API key in the request body or URL", async () => {
  const captured: Captured = {};
  await transcribe(WAV, opts(okFetch({ text: "x" }, captured)));
  assert.equal(captured.url?.includes("secret-key"), false);
  const form = captured.init?.body as FormData;
  for (const [, value] of form.entries()) {
    if (typeof value === "string") assert.equal(value.includes("secret-key"), false);
  }
});

test("transcribe maps a 429 from Monlam to a 429 SpeechError", async () => {
  await assert.rejects(transcribe(WAV, opts(statusFetch(429))), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.deepEqual(err.mapped, { status: 429, error: "RATE_LIMITED" });
    assert.equal(err.upstreamStatus, 429);
    return true;
  });
});

test("transcribe maps a 401 (our key refused) to STT_CONFIG", async () => {
  await assert.rejects(transcribe(WAV, opts(statusFetch(401))), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.deepEqual(err.mapped, { status: 502, error: "STT_CONFIG" });
    return true;
  });
});

test("transcribe maps a timeout to a 504 SpeechError", async () => {
  const timingOut = (async () => {
    const e = new Error("The operation was aborted due to timeout");
    e.name = "TimeoutError";
    throw e;
  }) as unknown as typeof fetch;
  await assert.rejects(transcribe(WAV, opts(timingOut)), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.deepEqual(err.mapped, { status: 504, error: "STT_TIMEOUT" });
    return true;
  });
});

test("transcribe maps a network failure to STT_FAILED", async () => {
  const down = (async () => {
    throw new TypeError("fetch failed");
  }) as unknown as typeof fetch;
  await assert.rejects(transcribe(WAV, opts(down)), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.deepEqual(err.mapped, { status: 502, error: "STT_FAILED" });
    return true;
  });
});

test("transcribe treats a 200 with no recognisable transcript as STT_FAILED", async () => {
  await assert.rejects(transcribe(WAV, opts(okFetch({ unexpected: true }))), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.deepEqual(err.mapped, { status: 502, error: "STT_FAILED" });
    return true;
  });
});

test("transcribe treats a 200 that is not JSON as STT_FAILED", async () => {
  const notJson = (async () => new Response("<html>oops</html>", { status: 200 })) as unknown as typeof fetch;
  await assert.rejects(transcribe(WAV, opts(notJson)), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.equal(err.mapped.error, "STT_FAILED");
    return true;
  });
});

// --- diagnosing upstream refusals: what Monlam said, never what we sent ---

function bodyFetch(status: number, body: string): typeof fetch {
  return (async () => new Response(body, { status })) as unknown as typeof fetch;
}

test("a refusal carries a short snippet of Monlam's error body for the server log", async () => {
  await assert.rejects(
    transcribe(WAV, opts(bodyFetch(403, '{"detail":"Audio too short for this model"}'))),
    (err: unknown) => {
      assert.ok(err instanceof SpeechError);
      assert.equal(err.upstreamBody, '{"detail":"Audio too short for this model"}');
      return true;
    }
  );
});

test("the error body is flattened to one line and capped at 300 characters", async () => {
  const long = "line one\n\n   line two " + "x".repeat(500);
  await assert.rejects(transcribe(WAV, opts(bodyFetch(403, long))), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.equal(err.upstreamBody?.includes("\n"), false);
    assert.equal(err.upstreamBody?.length, 300);
    assert.ok(err.upstreamBody?.startsWith("line one line two xxx"));
    return true;
  });
});

test("the API key is redacted if Monlam ever echoes it back", async () => {
  await assert.rejects(
    transcribe(WAV, opts(bodyFetch(401, 'Invalid key "secret-key" for this org'))),
    (err: unknown) => {
      assert.ok(err instanceof SpeechError);
      assert.equal(err.upstreamBody?.includes("secret-key"), false);
      assert.ok(err.upstreamBody?.includes("[key]"));
      return true;
    }
  );
});

test("an empty or unreadable error body leaves no snippet", async () => {
  await assert.rejects(transcribe(WAV, opts(bodyFetch(403, ""))), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.equal(err.upstreamBody, undefined);
    return true;
  });

  const unreadable = (async () => {
    const res = new Response("x", { status: 403 });
    Object.defineProperty(res, "text", { value: () => Promise.reject(new Error("stream failed")) });
    return res;
  }) as unknown as typeof fetch;
  await assert.rejects(transcribe(WAV, opts(unreadable)), (err: unknown) => {
    assert.ok(err instanceof SpeechError);
    assert.equal(err.upstreamBody, undefined);
    assert.equal(err.mapped.error, "STT_CONFIG");
    return true;
  });
});

