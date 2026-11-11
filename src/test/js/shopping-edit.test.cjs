const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { JSDOM } = require("jsdom");

test("shopping editor sends explicit date clearing and keeps an unchanged adjustment", async t => {
  const dom = new JSDOM('<main><button class="shopping-edit-button" data-id="7">Bearbeiten</button></main>',
    { runScripts: "outside-only", url: "http://localhost:18080/einkaufsliste" });
  t.after(() => dom.window.close());
  const { window } = dom;
  const calls = [];
  window.MangaLibFeedback = {
    async request(path, options) {
      calls.push([path, options]);
      if (options.method === "PATCH") throw new Error("Test response");
      return { id: "7", editable: true, editToken: "a".repeat(64), titel: "<b>Test</b>",
        erscheinungsdatum: "2026-11-10", gesamtpreisAenderung: "-0.01", istEbayPreis: true };
    }
  };
  window.eval(readFileSync("src/main/resources/static/js/shopping-edit.js", "utf8"));
  window.document.querySelector("button").click();
  await new Promise(resolve => setImmediate(resolve));
  const form = window.document.querySelector(".shopping-editor form");
  assert.equal(form.querySelector('input[type="text"]').value, "<b>Test</b>");
  assert.equal(form.querySelector("b"), null);
  const label = name => [...form.querySelectorAll("label")].find(node => node.firstChild.textContent === name);
  assert.equal(label("Gesamtanpassung in €").querySelector("input").value, "-0.01");
  label("Preisanpassung").querySelector("select").value = "false";
  form.querySelector('input[type="date"]').value = "";
  form.dispatchEvent(new window.Event("submit", { cancelable: true }));
  assert.deepEqual(JSON.parse(calls[1][1].body), {
    editToken: "a".repeat(64), erscheinungsdatum: null, istEbayPreis: false
  });
});
