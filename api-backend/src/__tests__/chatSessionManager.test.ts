import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ChatSessionManager,
  generateSessionId,
} from "../manager/ChatSessionManager";

// Pure, network-free behaviour. The Claude call in sendMessage is covered by
// the live smoke test, not unit tests.

test("getOrCreateSession is idempotent and starts with empty history", () => {
  const id = "session-test-1";
  ChatSessionManager.resetSession(id);
  const a = ChatSessionManager.getOrCreateSession(id);
  const b = ChatSessionManager.getOrCreateSession(id);
  assert.equal(a, b, "same session object is reused");
  assert.deepEqual(a.history, []);
  assert.equal(a.mode, "general");
  ChatSessionManager.resetSession(id);
});

test("resetSession clears state", () => {
  const id = "session-test-2";
  const session = ChatSessionManager.getOrCreateSession(id);
  session.history.push({ role: "user", content: "hi" });
  assert.equal(ChatSessionManager.getSessionInfo(id)?.history.length, 1);
  ChatSessionManager.resetSession(id);
  assert.equal(ChatSessionManager.getSessionInfo(id), undefined);
});

test("updateSessionMode switches mode and tutoring level", () => {
  const id = "session-test-3";
  ChatSessionManager.resetSession(id);
  ChatSessionManager.updateSessionMode(id, "tutoring", "beginner");
  const info = ChatSessionManager.getSessionInfo(id);
  assert.equal(info?.mode, "tutoring");
  assert.equal(info?.tutoringLevel, "beginner");
  ChatSessionManager.resetSession(id);
});

test("getTutoringCurriculum returns the curriculum for a level", () => {
  const beginner = ChatSessionManager.getTutoringCurriculum("beginner");
  assert.equal(beginner.currentLesson, "lesson_1_alphabet");
  assert.ok(beginner.focusAreas.includes("alphabet"));
});

test("generateSessionId produces unique prefixed ids", () => {
  const a = generateSessionId();
  const b = generateSessionId();
  assert.match(a, /^chat_/);
  assert.notEqual(a, b);
});
