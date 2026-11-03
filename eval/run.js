/* node eval/run.js  : scores Linda's model on the held-out set. Usage: node eval/run.js [path-to-model.js] */
var M = require(process.argv[2] || "../android-native/model.js"), H = require("./heldout.js");
var tp = 0, fn = 0, hardFP = 0, softFP = 0, nS = 0, nL = 0, miss = [], fps = [];
H.forEach(function (x) {
  var r = M.scan(x[2], { sender: x[1], db: {}, contacts: [] });
  if (x[0] === "scam") { nS++; if (r.level === "scam" || r.level === "caution") tp++; else { fn++; miss.push(r.score + " " + x[2].slice(0, 70)); } }
  else { nL++; if (r.level === "scam") { hardFP++; fps.push("SCAM " + r.score + " " + x[2].slice(0, 70)); } else if (r.level === "caution") { softFP++; fps.push("caut " + r.score + " " + x[2].slice(0, 70)); } }
});
console.log("scams caught: " + tp + "/" + nS + "  (" + Math.round(100 * tp / nS) + "%)");
console.log("genuine flagged as scam: " + hardFP + "/" + nL + "   genuine flagged as caution: " + softFP + "/" + nL);
if (miss.length) console.log("MISSED:\n  " + miss.join("\n  "));
if (fps.length) console.log("FALSE ALARMS:\n  " + fps.join("\n  "));
/* Regression: a sender NAME must never match a different name in the fraud list (KCBINFO vs KilimallMKT). */
var db = { "KilimallMKT": { status: "pending", type: "other", count: 1, name: "" }, "0711 000 412": { status: "confirmed", type: "reversal", count: 9, name: "" } };
var a = M.scan("Pay Lipa Stima direct using the New KCB App or dial *522#.", { sender: "KCBINFO", db: db, contacts: [] });
var b = M.scan("hello", { sender: "+254711000412", db: db, contacts: [] });
console.log("name collision: " + (a.level === "ok" ? "PASS" : "FAIL") + "   number match: " + (b.level === "scam" ? "PASS" : "FAIL"));
