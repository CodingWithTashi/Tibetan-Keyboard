import { test } from "node:test";
import assert from "node:assert/strict";
import {
  extractTranscript,
  extractBilling,
  mapMonlamStatus,
} from "../services/sttResponse";

test("extractTranscript reads a top-level `text`", () => {
  assert.equal(extractTranscript({ text: "བཀྲ་ཤིས་བདེ་ལེགས།" }), "བཀྲ་ཤིས་བདེ་ལེགས།");
});

test("extractTranscript trims surrounding whitespace", () => {
  assert.equal(extractTranscript({ text: "  བཀྲ་ཤིས།\n" }), "བཀྲ་ཤིས།");
});

test("extractTranscript returns an empty string for silence (field present but empty)", () => {
  assert.equal(extractTranscript({ text: "" }), "");
  assert.equal(extractTranscript({ text: "   " }), "");
});

test("extractTranscript returns null when no known field holds a string", () => {
  assert.equal(extractTranscript({ foo: "bar" }), null);
  assert.equal(extractTranscript({ text: 42 }), null);
  assert.equal(extractTranscript({ text: null }), null);
});

test("extractTranscript returns null for non-object input", () => {
  assert.equal(extractTranscript(null), null);
  assert.equal(extractTranscript(undefined), null);
  assert.equal(extractTranscript("just a string"), null);
  assert.equal(extractTranscript([{ text: "x" }]), null);
});

test("extractTranscript does not treat job-status objects as a transcript", () => {
  assert.equal(extractTranscript({ job_id: "1", status: "PENDING" }), null);
});

test("extractBilling reads cost, latency and request id", () => {
  assert.deepEqual(
    extractBilling({ text: "x", cost: 0.012, latency_ms: 840, service_request_id: "req_9" }),
    { cost: 0.012, latencyMs: 840, requestId: "req_9" }
  );
});

test("extractBilling skips fields of the wrong type", () => {
  assert.deepEqual(extractBilling({ cost: "free", latency_ms: null, service_request_id: 7 }), {});
});

test("extractBilling returns an empty object for non-objects", () => {
  assert.deepEqual(extractBilling(null), {});
  assert.deepEqual(extractBilling("x"), {});
});

test("mapMonlamStatus: bad input maps to 400 BAD_AUDIO", () => {
  assert.deepEqual(mapMonlamStatus(400), { status: 400, error: "BAD_AUDIO" });
  assert.deepEqual(mapMonlamStatus(422), { status: 400, error: "BAD_AUDIO" });
});

test("mapMonlamStatus: our own credentials being refused is a 502 STT_CONFIG, not the user's fault", () => {
  assert.deepEqual(mapMonlamStatus(401), { status: 502, error: "STT_CONFIG" });
  assert.deepEqual(mapMonlamStatus(403), { status: 502, error: "STT_CONFIG" });
});

test("mapMonlamStatus: no allocation left maps to 503 STT_UNAVAILABLE", () => {
  assert.deepEqual(mapMonlamStatus(402), { status: 503, error: "STT_UNAVAILABLE" });
});

test("mapMonlamStatus: upstream rate limit maps to 429", () => {
  assert.deepEqual(mapMonlamStatus(429), { status: 429, error: "RATE_LIMITED" });
});

test("mapMonlamStatus: a timeout maps to 504", () => {
  assert.deepEqual(mapMonlamStatus("timeout"), { status: 504, error: "STT_TIMEOUT" });
});

test("mapMonlamStatus: anything else is a 502 STT_FAILED", () => {
  assert.deepEqual(mapMonlamStatus(500), { status: 502, error: "STT_FAILED" });
  assert.deepEqual(mapMonlamStatus(503), { status: 502, error: "STT_FAILED" });
  assert.deepEqual(mapMonlamStatus(404), { status: 502, error: "STT_FAILED" });
});

test("extractTranscript reads only `text`: other names are not Monlam's field", () => {
  assert.equal(extractTranscript({ transcription: "a" }), null);
  assert.equal(extractTranscript({ transcript: "b" }), null);
  assert.equal(extractTranscript({ result: { text: "nested" } }), null);
});

// Monlam answered a pure tone with a lone shad. Typing that into the user's field would look like
// a bug, so a transcript without a single letter or digit is "nothing was said".
test("extractTranscript treats a lone shad as nothing heard", () => {
  assert.equal(extractTranscript({ text: "།" }), "");
});

test("extractTranscript treats punctuation and spaces alone as nothing heard", () => {
  assert.equal(extractTranscript({ text: " ། ་ །། " }), "");
  assert.equal(extractTranscript({ text: "..." }), "");
});

test("extractTranscript keeps real text, including its punctuation", () => {
  assert.equal(extractTranscript({ text: "བཀྲ་ཤིས་བདེ་ལེགས།" }), "བཀྲ་ཤིས་བདེ་ལེགས།");
  assert.equal(extractTranscript({ text: "hello." }), "hello.");
  assert.equal(extractTranscript({ text: "༡༢༣" }), "༡༢༣");
});

// The reply Monlam really sent for a 4 s test tone (2026-11-05), minus the audio links.
const REAL_REPLY = {
  text: "།",
  segments: [],
  duration: 4.0,
  language: "bo",
  task: "transcribe",
  model: "monlamai-stt",
  cost: 0.0021333333,
  latency_ms: 1902,
  service_request_id: "bce3ec95-dfbb-43c3-abce-be5559c22f1b",
  status: "success",
};

test("the real Monlam reply yields its billing metadata and an empty transcript", () => {
  assert.equal(extractTranscript(REAL_REPLY), "");
  assert.deepEqual(extractBilling(REAL_REPLY), {
    cost: 0.0021333333,
    latencyMs: 1902,
    requestId: "bce3ec95-dfbb-43c3-abce-be5559c22f1b",
  });
});
