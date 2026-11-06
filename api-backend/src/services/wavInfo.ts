/**
 * Pure helpers that read a RIFF/WAVE header, so the speech route can reject bad
 * audio (wrong format, too long) before anything is sent to the paid Monlam API.
 *
 * The app records 16 kHz mono 16-bit PCM and wraps it in a canonical WAV
 * (`WavEncoder` on the Android side). Anything else is not from our app.
 */

/** The only format the app produces: PCM, 16 kHz, mono, 16-bit. */
const WAV_FORMAT_PCM = 1;
const REQUIRED_SAMPLE_RATE = 16000;
const REQUIRED_CHANNELS = 1;
const REQUIRED_BITS = 16;

/**
 * A recording may last 60 s on the phone, which stops itself at exactly that.
 * The extra half second absorbs rounding so a legitimate recording is never
 * refused, while 60.6 s and up is.
 */
export const MAX_AUDIO_MS = 60_500;

/** 60 s of the required format is 1.92 MB; 2 MB is the hard byte cap. */
export const MAX_AUDIO_BYTES = 2 * 1024 * 1024;

export interface WavInfo {
  format: number;
  channels: number;
  sampleRate: number;
  bitsPerSample: number;
  durationMs: number;
}

export type SpeechAudioCheck =
  | { ok: true; durationMs: number }
  | { ok: false; reason: "BAD_AUDIO" | "TOO_LONG" };

/**
 * Reads the header of a WAV buffer. Walks the chunk list, so an extra `LIST`
 * chunk before `data` does not hide the audio. Returns null when this is not a
 * well-formed WAV.
 */
export function readWav(buf: Buffer): WavInfo | null {
  if (buf.length < 12) return null;
  if (buf.toString("ascii", 0, 4) !== "RIFF") return null;
  if (buf.toString("ascii", 8, 12) !== "WAVE") return null;

  let format = 0;
  let channels = 0;
  let sampleRate = 0;
  let bitsPerSample = 0;
  let haveFmt = false;

  let offset = 12;
  while (offset + 8 <= buf.length) {
    const id = buf.toString("ascii", offset, offset + 4);
    const size = buf.readUInt32LE(offset + 4);
    const body = offset + 8;

    if (id === "fmt ") {
      if (size < 16 || body + 16 > buf.length) return null;
      format = buf.readUInt16LE(body);
      channels = buf.readUInt16LE(body + 2);
      sampleRate = buf.readUInt32LE(body + 4);
      bitsPerSample = buf.readUInt16LE(body + 14);
      haveFmt = true;
    } else if (id === "data") {
      if (!haveFmt || channels === 0 || sampleRate === 0 || bitsPerSample === 0) return null;
      // The bytes actually present are what gets sent on and billed, whatever the header claims:
      // one that overstates them is ignored, and one that understates them (a short data chunk
      // followed by more audio) cannot slip a long recording past the 60 s gate.
      const dataBytes = buf.length - body;
      const bytesPerSecond = (sampleRate * channels * bitsPerSample) / 8;
      const durationMs = Math.round((dataBytes / bytesPerSecond) * 1000);
      return { format, channels, sampleRate, bitsPerSample, durationMs };
    }

    // Chunks are word-aligned: an odd size is followed by one pad byte.
    offset = body + size + (size % 2);
  }
  return null;
}

/** Whether `buf` is audio our app could have recorded, and how long it is. */
export function checkSpeechAudio(buf: Buffer): SpeechAudioCheck {
  if (buf.length > MAX_AUDIO_BYTES) return { ok: false, reason: "TOO_LONG" };

  const info = readWav(buf);
  if (
    !info ||
    info.format !== WAV_FORMAT_PCM ||
    info.sampleRate !== REQUIRED_SAMPLE_RATE ||
    info.channels !== REQUIRED_CHANNELS ||
    info.bitsPerSample !== REQUIRED_BITS
  ) {
    return { ok: false, reason: "BAD_AUDIO" };
  }

  if (info.durationMs > MAX_AUDIO_MS) return { ok: false, reason: "TOO_LONG" };
  return { ok: true, durationMs: info.durationMs };
}
