#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");

const MAX_TESTS_PER_SHARD = 4;

const ROOT = path.resolve(__dirname, "../..");
const CONFIG = {
  catalog: path.join(ROOT, "embedded/e2e/scenario-catalog.json"),
  testSources: [
    path.join(ROOT, "embedded/src/androidTest/java/com/frontegg/demo/e2e/EmbeddedE2ETests.kt"),
  ],
  testClass: "com.frontegg.demo.e2e.EmbeddedE2ETests",
};

function readCatalogMethods(catalogPath) {
  const raw = JSON.parse(fs.readFileSync(catalogPath, "utf-8"));
  const entries = raw.tests || raw.scenarios || [];
  return entries
    .map((entry) => entry.method)
    .filter((method) => typeof method === "string" && method.length > 0);
}

function readKotlinTestMethods(testSources) {
  const methods = new Set();
  const re = /^\s*fun\s+(test[A-Za-z0-9_]+)\s*\(/gm;
  for (const sourcePath of testSources) {
    const source = fs.readFileSync(sourcePath, "utf-8");
    for (const match of source.matchAll(re)) {
      methods.add(match[1]);
    }
  }
  return [...methods];
}

function validateCatalog(catalogMethods, sourceMethods) {
  const catalogSet = new Set(catalogMethods);
  const sourceSet = new Set(sourceMethods);
  const catalogOnly = catalogMethods.filter((m) => !sourceSet.has(m));
  const sourceOnly = sourceMethods.filter((m) => !catalogSet.has(m));
  if (catalogOnly.length === 0 && sourceOnly.length === 0) return;
  const problems = [];
  if (catalogOnly.length) problems.push(`catalog-only: ${catalogOnly.join(", ")}`);
  if (sourceOnly.length) problems.push(`uncatalogued: ${sourceOnly.join(", ")}`);
  throw new Error(`embedded E2E catalog drift: ${problems.join("; ")}`);
}

function splitIntoShards(items, shardCount) {
  const shards = Array.from({ length: shardCount }, () => []);
  items.forEach((item, i) => shards[i % shardCount].push(item));
  return shards;
}

function main() {
  const parsed = parseInt(process.env.INPUT_SHARD_COUNT || "1", 10);
  const shardCount = Number.isNaN(parsed) ? 1 : Math.max(1, parsed);

  const methods = readCatalogMethods(CONFIG.catalog);
  const sourceMethods = readKotlinTestMethods(CONFIG.testSources);
  validateCatalog(methods, sourceMethods);

  const autoShards = Math.ceil(methods.length / MAX_TESTS_PER_SHARD);
  const effectiveShardCount =
    shardCount > 1 ? Math.min(shardCount, methods.length || 1) : Math.max(1, autoShards);

  const include = [];
  if (effectiveShardCount <= 1 || methods.length === 0) {
    include.push({
      "shard-index": 1,
      "shard-total": 1,
      "test-class": CONFIG.testClass,
      "test-methods": "",
    });
  } else {
    const shards = splitIntoShards(methods, effectiveShardCount);
    shards.forEach((shard, i) => {
      include.push({
        "shard-index": i + 1,
        "shard-total": effectiveShardCount,
        "test-class": CONFIG.testClass,
        "test-methods": shard.join(","),
      });
    });
  }

  const matrix = JSON.stringify({ include });
  const outputFile = process.env.GITHUB_OUTPUT;
  if (outputFile) {
    fs.appendFileSync(outputFile, `matrix=${matrix}\n`);
  }
  console.log(matrix);
}

main();
