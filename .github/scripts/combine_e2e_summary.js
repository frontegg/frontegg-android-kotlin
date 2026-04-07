#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");

function parseArgs(argv) {
  const o = {};
  for (let i = 0; i < argv.length; i += 1) {
    if (!argv[i].startsWith("--")) throw new Error(`Unexpected: ${argv[i]}`);
    const k = argv[i].slice(2);
    const v = argv[++i];
    if (!v || v.startsWith("--")) throw new Error(`Missing value for --${k}`);
    o[k] = v;
  }
  if (!o["artifacts-dir"] || !o.summary) throw new Error("Need --artifacts-dir and --summary");
  return o;
}

function walk(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p, acc);
    else if (name.startsWith("TEST-") && name.endsWith(".xml")) acc.push(p);
  }
  return acc;
}

function parseJUnitXml(xml) {
  const results = [];
  const tcRe = /<testcase[^>]*\sname="([^"]+)"[^>]*\sclassname="([^"]+)"[^>]*>([\s\S]*?)<\/testcase>/g;
  let m;
  while ((m = tcRe.exec(xml))) {
    const name = m[1];
    const classname = m[2];
    const body = m[3];
    const failed = /<failure/.test(body) || /<error/.test(body);
    results.push({ name, classname, failed });
  }
  return results;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const files = walk(path.resolve(args["artifacts-dir"]));
  const rows = [];
  for (const f of files) {
    try {
      rows.push(...parseJUnitXml(fs.readFileSync(f, "utf8")));
    } catch (e) {
      console.warn("skip", f, e.message);
    }
  }
  const failed = rows.filter((r) => r.failed);
  const lines = [
    "## Embedded E2E (Android)",
    "",
    `- Tests parsed: ${rows.length}`,
    `- Failed: ${failed.length}`,
    "",
  ];
  if (failed.length) {
    lines.push("### Failures");
    failed.forEach((r) => lines.push(`- \`${r.classname}#${r.name}\``));
  }
  fs.writeFileSync(path.resolve(args.summary), lines.join("\n"), "utf8");
}

main();
