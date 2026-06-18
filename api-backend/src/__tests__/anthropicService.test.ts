import { test } from "node:test";
import assert from "node:assert/strict";
import {
  resolveModel,
  HAIKU_MODEL,
  SONNET_MODEL,
  DEFAULT_MODEL,
} from "../services/anthropicService";

test("resolveModel accepts the two allowed Claude model ids", () => {
  assert.equal(resolveModel(HAIKU_MODEL), HAIKU_MODEL);
  assert.equal(resolveModel(SONNET_MODEL), SONNET_MODEL);
});

test("resolveModel accepts friendly aliases (case-insensitive)", () => {
  assert.equal(resolveModel("haiku"), HAIKU_MODEL);
  assert.equal(resolveModel("Haiku"), HAIKU_MODEL);
  assert.equal(resolveModel("sonnet"), SONNET_MODEL);
  assert.equal(resolveModel("SONNET"), SONNET_MODEL);
});

test("resolveModel falls back to the default for unknown/empty input", () => {
  assert.equal(DEFAULT_MODEL, HAIKU_MODEL);
  assert.equal(resolveModel(undefined), DEFAULT_MODEL);
  assert.equal(resolveModel(null), DEFAULT_MODEL);
  assert.equal(resolveModel(""), DEFAULT_MODEL);
  assert.equal(resolveModel("gpt-4"), DEFAULT_MODEL);
  assert.equal(resolveModel("claude-opus-4-8"), DEFAULT_MODEL); // not offered in-app
});
