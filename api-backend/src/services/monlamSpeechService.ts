import { STT } from "../config/constants";
import {
  Billing,
  MappedFailure,
  extractBilling,
  extractTranscript,
  mapMonlamStatus,
} from "./sttResponse";

/** A failed transcription, already translated into what we tell the app. */
export class SpeechError extends Error {
  constructor(
    readonly mapped: MappedFailure,
    /** Monlam's HTTP status, when it answered at all; for the server log only. */
    readonly upstreamStatus?: number,
    detail?: string,
    /** A short, one-line snippet of Monlam's error body, so a refusal can be diagnosed. Log only. */
    readonly upstreamBody?: string
  ) {
    super(detail ?? mapped.error);
    this.name = "SpeechError";
  }
}

export interface TranscribeOptions {
  /** The Monlam API key (the `MONLAM_API_KEY` secret). */
  apiKey: string;
  /** One id per recording, so a retry is not billed twice. */
  idempotencyKey: string;
  url?: string;
  timeoutMs?: number;
  /** Injected in tests. */
  fetchImpl?: typeof fetch;
}

export interface TranscribeResult {
  /** The transcript; empty when nothing was said. */
  text: string;
  billing: Billing;
}

/** How much of an upstream error body is worth a log line. */
const MAX_BODY_LOG = 300;

/**
 * What Monlam said when it refused a request: one line, capped, with our key redacted in case it
 * is ever echoed back. Never the audio, and never anything we sent. Undefined when there is none.
 */
async function errorSnippet(response: Response, apiKey: string): Promise<string | undefined> {
  try {
    const raw = await response.text();
    // A tiny or empty key would shred the text, so only redact one that looks like a real key.
    const redacted = apiKey.length >= 8 ? raw.split(apiKey).join("[key]") : raw;
    const flat = redacted.replace(/\s+/g, " ").trim();
    return flat ? flat.slice(0, MAX_BODY_LOG) : undefined;
  } catch {
    return undefined;
  }
}

function isTimeout(err: unknown): boolean {
  return err instanceof Error && (err.name === "TimeoutError" || err.name === "AbortError");
}

/**
 * Sends one recording (a 16 kHz mono 16-bit WAV) to Monlam and returns the
 * transcript. Throws [SpeechError] for every failure, so the route has one
 * thing to catch. Never logs or returns the audio.
 */
export async function transcribe(
  wav: Buffer,
  { apiKey, idempotencyKey, url = STT.URL, timeoutMs = STT.TIMEOUT_MS, fetchImpl = fetch }: TranscribeOptions
): Promise<TranscribeResult> {
  const form = new FormData();
  form.append("file", new Blob([wav], { type: "audio/wav" }), "audio.wav");
  form.append("language", "bo");
  form.append("task", "transcribe");

  let response: Response;
  try {
    response = await fetchImpl(url, {
      method: "POST",
      headers: { "X-API-Key": apiKey, "x-idempotency-key": idempotencyKey },
      body: form,
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (err) {
    const mapped = mapMonlamStatus(isTimeout(err) ? "timeout" : 0);
    throw new SpeechError(mapped, undefined, err instanceof Error ? err.message : String(err));
  }

  if (!response.ok) {
    throw new SpeechError(
      mapMonlamStatus(response.status),
      response.status,
      undefined,
      await errorSnippet(response, apiKey)
    );
  }

  let json: unknown;
  try {
    json = await response.json();
  } catch {
    throw new SpeechError(mapMonlamStatus(0), response.status, "reply was not JSON");
  }

  const text = extractTranscript(json);
  if (text === null) {
    throw new SpeechError(mapMonlamStatus(0), response.status, "reply held no transcript");
  }
  return { text, billing: extractBilling(json) };
}
