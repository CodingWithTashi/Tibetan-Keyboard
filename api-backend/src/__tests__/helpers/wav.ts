/** A canonical 44-byte PCM WAV header followed by `seconds` of silence (test helper). */
export function wav(opts: {
  seconds: number;
  rate?: number;
  channels?: number;
  bits?: number;
  format?: number;
}): Buffer {
  const rate = opts.rate ?? 16000;
  const channels = opts.channels ?? 1;
  const bits = opts.bits ?? 16;
  const format = opts.format ?? 1;
  const byteRate = (rate * channels * bits) / 8;
  const dataLen = Math.round(opts.seconds * byteRate);
  const buf = Buffer.alloc(44 + dataLen);
  buf.write("RIFF", 0, "ascii");
  buf.writeUInt32LE(36 + dataLen, 4);
  buf.write("WAVE", 8, "ascii");
  buf.write("fmt ", 12, "ascii");
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(format, 20);
  buf.writeUInt16LE(channels, 22);
  buf.writeUInt32LE(rate, 24);
  buf.writeUInt32LE(byteRate, 28);
  buf.writeUInt16LE((channels * bits) / 8, 32);
  buf.writeUInt16LE(bits, 34);
  buf.write("data", 36, "ascii");
  buf.writeUInt32LE(dataLen, 40);
  return buf;
}
