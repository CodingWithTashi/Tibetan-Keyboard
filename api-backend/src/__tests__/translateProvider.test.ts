import { test } from "node:test";
import assert from "node:assert/strict";
import {
  resolveEngine,
  ENGINE_AZURE,
  DEFAULT_ENGINE,
  AZURE_FALLBACK_MODEL,
} from "../services/translateProvider";
import { HAIKU_MODEL, SONNET_MODEL } from "../services/anthropicService";

test("resolveEngine accepts the three allowed engine ids", () => {
  assert.equal(resolveEngine(ENGINE_AZURE), ENGINE_AZURE);
  assert.equal(resolveEngine(HAIKU_MODEL), HAIKU_MODEL);
  assert.equal(resolveEngine(SONNET_MODEL), SONNET_MODEL);
});

test("resolveEngine accepts friendly aliases (case-insensitive)", () => {
  assert.equal(resolveEngine("azure"), ENGINE_AZURE);
  assert.equal(resolveEngine("Microsoft"), ENGINE_AZURE);
  assert.equal(resolveEngine("haiku"), HAIKU_MODEL);
  assert.equal(resolveEngine("SONNET"), SONNET_MODEL);
});

test("resolveEngine falls back to the default for unknown/empty input", () => {
  assert.equal(DEFAULT_ENGINE, ENGINE_AZURE);
  assert.equal(resolveEngine(undefined), DEFAULT_ENGINE);
  assert.equal(resolveEngine(null), DEFAULT_ENGINE);
  assert.equal(resolveEngine(""), DEFAULT_ENGINE);
  assert.equal(resolveEngine("gpt-4"), DEFAULT_ENGINE);
});

test("Azure falls back to Claude Haiku", () => {
  assert.equal(AZURE_FALLBACK_MODEL, HAIKU_MODEL);
});
