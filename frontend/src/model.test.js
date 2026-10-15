// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { cents, money } from "./model.js";
test("decimal money conversion does not round silently", () => {
  assert.equal(cents("0.29"), 29);
  assert.equal(cents("1123.4"), 112340);
  assert.throws(() => cents("1.001"));
  assert.throws(() => cents("-1"));
  assert.throws(() => cents("1e4"));
  assert.throws(() => cents("0"));
});
test("money display preserves large aggregated cents and negative settlements", () => {
  assert.equal(money("9007199254740993"), "¥90,071,992,547,409.93");
  assert.equal(money("-7"), "-¥0.07");
  assert.equal(money(101), "¥1.01");
});
