import { test } from "node:test";
import assert from "node:assert/strict";
import type { Request, Response } from "express";
import { validateSpeechRequest } from "../middleware/validation";
import { STT_MESSAGES } from "../middleware/responses";
import { mapMonlamStatus } from "../services/sttResponse";
import { wav } from "./helpers/wav";

interface Outcome {
  status?: number;
  json?: Record<string, unknown>;
  nextCalled: boolean;
  locals: Record<string, any>;
}

function run(opts: {
  body?: Buffer | null;
  viaRawBody?: boolean;
  headers?: Record<string, string>;
}): Outcome {
  const out: Outcome = { nextCalled: false, locals: {} };
  const headers = { userid: "user-1", ...(opts.headers ?? {}) };
  const req = {
    headers,
    body: opts.viaRawBody ? undefined : (opts.body ?? undefined),
    rawBody: opts.viaRawBody ? opts.body : undefined,
  } as unknown as Request;
  const res = {
    locals: out.locals,
    status(code: number) {
      out.status = code;
      return this;
    },
    json(payload: Record<string, unknown>) {
      out.json = payload;
      return this;
    },
  } as unknown as Response;
  validateSpeechRequest(req, res, () => {
    out.nextCalled = true;
  });
  return out;
}

test("accepts a valid 5 s recording and hands the audio on", () => {
  const audio = wav({ seconds: 5 });
  const out = run({ body: audio });
  assert.equal(out.nextCalled, true);
  assert.equal(out.locals.speech.wav, audio);
  assert.equal(out.locals.speech.durationMs, 5000);
});

test("reads the audio from rawBody when the framework provides it", () => {
  const audio = wav({ seconds: 3 });
  const out = run({ body: audio, viaRawBody: true });
  assert.equal(out.nextCalled, true);
  assert.equal(out.locals.speech.durationMs, 3000);
});

test("rejects a request without a userid header", () => {
  const out = run({ body: wav({ seconds: 2 }), headers: { userid: "undefined" } });
  assert.equal(out.nextCalled, false);
  assert.equal(out.status, 400);
});

test("rejects an empty or missing body as BAD_AUDIO", () => {
  for (const body of [null, Buffer.alloc(0)]) {
    const out = run({ body });
    assert.equal(out.nextCalled, false);
    assert.equal(out.status, 400);
    assert.equal(out.json?.error, "BAD_AUDIO");
  }
});

test("rejects a body that is not a Buffer (e.g. parsed JSON) as BAD_AUDIO", () => {
  const req = { headers: { userid: "u" }, body: { text: "hi" } } as unknown as Request;
  let status = 0;
  let nextCalled = false;
  const res = {
    locals: {},
    status(c: number) {
      status = c;
      return this;
    },
    json() {
      return this;
    },
  } as unknown as Response;
  validateSpeechRequest(req, res, () => {
    nextCalled = true;
  });
  assert.equal(nextCalled, false);
  assert.equal(status, 400);
});

test("rejects a tiny body (under 1 KB) as BAD_AUDIO", () => {
  const out = run({ body: Buffer.alloc(500) });
  assert.equal(out.status, 400);
  assert.equal(out.json?.error, "BAD_AUDIO");
});

test("rejects stereo 44.1 kHz audio as BAD_AUDIO", () => {
  const out = run({ body: wav({ seconds: 2, rate: 44100, channels: 2 }) });
  assert.equal(out.status, 400);
  assert.equal(out.json?.error, "BAD_AUDIO");
});

test("the 60 s gate: 61 s of audio gets 413 TOO_LONG", () => {
  const out = run({ body: wav({ seconds: 61 }) });
  assert.equal(out.nextCalled, false);
  assert.equal(out.status, 413);
  assert.equal(out.json?.error, "TOO_LONG");
});

test("the 60 s gate: exactly 60 s is accepted", () => {
  const out = run({ body: wav({ seconds: 60 }) });
  assert.equal(out.nextCalled, true);
  assert.equal(out.locals.speech.durationMs, 60000);
});

test("failures use the app's { success:false, error, message } envelope", () => {
  const out = run({ body: wav({ seconds: 61 }) });
  assert.equal(out.json?.success, false);
  assert.equal(typeof out.json?.message, "string");
  assert.ok((out.json?.message as string).length > 0);
});

test("keeps a well-formed x-idempotency-key from the app", () => {
  const out = run({
    body: wav({ seconds: 2 }),
    headers: { "x-idempotency-key": "8b1d6f0e-1f43-4a53-9a77-0a1b2c3d4e5f" },
  });
  assert.equal(out.locals.speech.idempotencyKey, "8b1d6f0e-1f43-4a53-9a77-0a1b2c3d4e5f");
});

test("replaces a missing or malformed idempotency key with a fresh UUID", () => {
  for (const headers of [{}, { "x-idempotency-key": "short" }, { "x-idempotency-key": "has spaces and !!" }]) {
    const out = run({ body: wav({ seconds: 2 }), headers });
    assert.match(out.locals.speech.idempotencyKey, /^[0-9a-f-]{36}$/);
  }
});

test("every failure code the service can produce has a user-facing message", () => {
  const codes = new Set(
    [400, 401, 402, 429, 500, "timeout" as const].map((s) => mapMonlamStatus(s).error)
  );
  codes.add("TOO_LONG");
  for (const code of codes) {
    assert.equal(typeof STT_MESSAGES[code], "string", `no message for ${code}`);
    assert.ok(STT_MESSAGES[code].length > 0);
  }
});
