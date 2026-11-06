import { test } from "node:test";
import assert from "node:assert/strict";
import {
  readWav,
  checkSpeechAudio,
  MAX_AUDIO_MS,
  MAX_AUDIO_BYTES,
} from "../services/wavInfo";
import { wav } from "./helpers/wav";

test("readWav reads format and duration from a canonical header", () => {
  const info = readWav(wav({ seconds: 5 }));
  assert.deepEqual(info, {
    format: 1,
    channels: 1,
    sampleRate: 16000,
    bitsPerSample: 16,
    durationMs: 5000,
  });
});

test("readWav returns null for a buffer shorter than a header", () => {
  assert.equal(readWav(Buffer.alloc(20)), null);
});

test("readWav returns null when the RIFF/WAVE magic is missing", () => {
  const bad = wav({ seconds: 1 });
  bad.write("JUNK", 0, "ascii");
  assert.equal(readWav(bad), null);
});

test("readWav finds the data chunk after an extra LIST chunk", () => {
  const base = wav({ seconds: 2 });
  const list = Buffer.alloc(8 + 6);
  list.write("LIST", 0, "ascii");
  list.writeUInt32LE(6, 4);
  const withList = Buffer.concat([base.subarray(0, 36), list, base.subarray(36)]);
  assert.equal(readWav(withList)?.durationMs, 2000);
});

test("checkSpeechAudio accepts 16 kHz mono 16-bit PCM", () => {
  assert.deepEqual(checkSpeechAudio(wav({ seconds: 5 })), { ok: true, durationMs: 5000 });
});

test("checkSpeechAudio accepts a recording just under the limit (60.4 s)", () => {
  const result = checkSpeechAudio(wav({ seconds: 60.4 }));
  assert.equal(result.ok, true);
});

test("checkSpeechAudio rejects a recording over the limit (60.6 s) as too long", () => {
  const result = checkSpeechAudio(wav({ seconds: 60.6 }));
  assert.deepEqual(result, { ok: false, reason: "TOO_LONG" });
});

test("checkSpeechAudio rejects 44.1 kHz audio", () => {
  assert.deepEqual(checkSpeechAudio(wav({ seconds: 2, rate: 44100 })), {
    ok: false,
    reason: "BAD_AUDIO",
  });
});

test("checkSpeechAudio rejects stereo audio", () => {
  assert.deepEqual(checkSpeechAudio(wav({ seconds: 2, channels: 2 })), {
    ok: false,
    reason: "BAD_AUDIO",
  });
});

test("checkSpeechAudio rejects 8-bit audio", () => {
  assert.deepEqual(checkSpeechAudio(wav({ seconds: 2, bits: 8 })), {
    ok: false,
    reason: "BAD_AUDIO",
  });
});

test("checkSpeechAudio rejects non-PCM encodings", () => {
  assert.deepEqual(checkSpeechAudio(wav({ seconds: 2, format: 3 })), {
    ok: false,
    reason: "BAD_AUDIO",
  });
});

test("checkSpeechAudio rejects a body that is not a WAV at all", () => {
  assert.deepEqual(checkSpeechAudio(Buffer.from("not audio, just text".repeat(100))), {
    ok: false,
    reason: "BAD_AUDIO",
  });
});

test("checkSpeechAudio rejects a body larger than the byte cap before parsing", () => {
  const huge = Buffer.alloc(MAX_AUDIO_BYTES + 1);
  assert.deepEqual(checkSpeechAudio(huge), { ok: false, reason: "TOO_LONG" });
});

test("the 60 s gate and the byte cap agree: 60 s of valid audio fits under the cap", () => {
  assert.equal(MAX_AUDIO_MS, 60_500);
  assert.ok(wav({ seconds: 60 }).length < MAX_AUDIO_BYTES);
});
