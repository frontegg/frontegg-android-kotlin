#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");

/**
 * Small shards + weight-sorted round-robin keeps the slowest tests from landing on the same
 * shard as each other (e.g. Google Custom Tab vs password WebView).
 */
const MAX_TESTS_PER_SHARD = 2;

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

/**
 * Rough CI duration / resource weight (higher = round-robin into shards first so heavy tests
 * rarely share a shard with each other; catalog document order is unchanged).
 */
const TEST_WEIGHTS = {
  testEmbeddedGoogleSocialLoginWithSystemWebAuthenticationSession: 100,
  testAuthenticatedOfflineModeRecoversToOnlineAndRefreshesToken: 92,
  testEmbeddedGoogleSocialLoginOAuthErrorShowsToastAndKeepsLoginOpen: 78,
  testLogoutTerminateTransientNoConnectionThenCustomSSORecovers: 72,
  testScheduledTokenRefreshFiresBeforeExpiry: 68,
  testExpiredAccessTokenRefreshesOnAuthenticatedRelaunch: 62,
  testAuthenticatedRelaunchWithExpiredAccessTokenAndFreshRefreshToken: 60,
  testAuthenticatedOfflineModeKeepsUserLoggedInUntilReconnectRefreshesExpiredToken: 58,
  testPasswordLoginAndSessionRestore: 52,
  testLogoutClearsSessionAndRelaunchShowsLogin: 48,
  testAuthenticatedOfflineModeWhenNetworkPathUnavailable: 44,
  testExpiredRefreshTokenClearsSessionAndShowsLogin: 42,
  testOfflineModeDisabledPreservesSessionDuringConnectionLossAndRecovers: 38,
  testLogoutTerminateTransientProbeFailureDoesNotBlinkNoConnectionPage: 36,
  testEmbeddedSamlLogin: 34,
  testEmbeddedOidcLogin: 34,
  testCustomSSOBrowserHandoff: 30,
  testDirectSocialBrowserHandoff: 30,
  testRequestAuthorizeFlow: 28,
  testPasswordLoginWorksWithOfflineModeDisabled: 26,
  testColdLaunchWithOfflineModeDisabledReachesLoginQuickly: 12,
  testColdLaunchTransientProbeTimeoutsDoNotBlinkNoConnectionPage: 12,
};

function sortMethodsForSharding(methods) {
  const w = (m) => TEST_WEIGHTS[m] ?? 20;
  return [...methods].sort((a, b) => w(b) - w(a));
}

function splitIntoShards(items, shardCount) {
  const shards = Array.from({ length: shardCount }, () => []);
  items.forEach((item, i) => shards[i % shardCount].push(item));
  return shards;
}

/** Custom Tabs / heavy WebView last — reduces emulator OOM / process death when paired with lighter tests. */
const PREFER_RUN_LAST = new Set([
  "testEmbeddedGoogleSocialLoginWithSystemWebAuthenticationSession",
  "testEmbeddedGoogleSocialLoginOAuthErrorShowsToastAndKeepsLoginOpen",
  "testCustomSSOBrowserHandoff",
  "testDirectSocialBrowserHandoff",
  "testEmbeddedSamlLogin",
  "testEmbeddedOidcLogin",
]);

function orderShardStable(shard) {
  if (shard.length <= 1) return shard;
  return [...shard].sort((a, b) => {
    const la = PREFER_RUN_LAST.has(a) ? 1 : 0;
    const lb = PREFER_RUN_LAST.has(b) ? 1 : 0;
    if (la !== lb) return la - lb;
    return shard.indexOf(a) - shard.indexOf(b);
  });
}

/** One test per shard — Chrome Custom Tab, heavy token flows, or flaky tests
 *  that need isolation so their flaky flag applies to the whole shard. */
const SOLO_SHARD_METHODS = new Set([
  "testEmbeddedGoogleSocialLoginWithSystemWebAuthenticationSession",
  "testEmbeddedGoogleSocialLoginOAuthErrorShowsToastAndKeepsLoginOpen",
  "testCustomSSOBrowserHandoff",
  "testDirectSocialBrowserHandoff",
  "testAuthenticatedRelaunchWithExpiredAccessTokenAndFreshRefreshToken",
  "testLogoutTerminateTransientNoConnectionThenCustomSSORecovers",
  "testAuthenticatedOfflineModeRecoversToOnlineAndRefreshesToken",
  "testAuthenticatedOfflineModeKeepsUserLoggedInUntilReconnectRefreshesExpiredToken",
  "testOfflineModeDisabledPreservesSessionDuringConnectionLossAndRecovers",
]);

/**
 * Known-flaky tests on CI emulators. These still run and report results via
 * annotate-only JUnit, but the shard is marked continue-on-error and no GitHub
 * check run is created, so failures don't block PRs.
 *
 * Reasons:
 * - Google Custom Tab: Chrome on API 34 emulators blocks JS window.location.href
 *   to custom URL schemes, so the deep-link handoff back to the app never fires.
 *   Works on real devices.
 * - Offline mode token refresh: tight timing windows (access TTL 21s + refresh
 *   21s) are unreliable on slow CI emulators where a single GC pause can push
 *   the refresh past its window.
 * - Transient no-connection + Custom SSO: combines offline-mode retry AND
 *   Custom Tab handoff, multiplying both flaky behaviors.
 */
const FLAKY_METHODS = new Set([
  // Google Custom Tab: Chrome on API 34 emulators blocks deep-link handoff
  // back to the app from Chrome Custom Tab in some edge cases. Works on real devices.
  "testEmbeddedGoogleSocialLoginWithSystemWebAuthenticationSession",
  "testEmbeddedGoogleSocialLoginOAuthErrorShowsToastAndKeepsLoginOpen",
  // Offline mode token refresh: tight timing windows (access TTL 21s + refresh 21s)
  // are unreliable on slow CI emulators where a single GC pause can push the
  // refresh past its window.
  "testAuthenticatedOfflineModeRecoversToOnlineAndRefreshesToken",
  "testAuthenticatedOfflineModeKeepsUserLoggedInUntilReconnectRefreshesExpiredToken",
  "testLogoutTerminateTransientNoConnectionThenCustomSSORecovers",
  "testOfflineModeDisabledPreservesSessionDuringConnectionLossAndRecovers",
]);

function main() {
  const catalogMethods = readCatalogMethods(CONFIG.catalog);
  const sourceMethods = readKotlinTestMethods(CONFIG.testSources);
  validateCatalog(catalogMethods, sourceMethods);
  const methodsForShards = sortMethodsForSharding(catalogMethods);

  const include = [];
  if (catalogMethods.length === 0) {
    include.push({
      "shard-index": 1,
      "shard-total": 1,
      "test-class": CONFIG.testClass,
      "test-methods": "",
    });
  } else {
    const solo = methodsForShards.filter((m) => SOLO_SHARD_METHODS.has(m));
    const rest = methodsForShards.filter((m) => !SOLO_SHARD_METHODS.has(m));
    const restShardCount = Math.max(1, Math.ceil(rest.length / MAX_TESTS_PER_SHARD));
    const restShards = splitIntoShards(rest, restShardCount).map(orderShardStable);
    const soloShards = solo.map((m) => [m]);
    const shards = [...restShards, ...soloShards];
    const effectiveShardCount = shards.length;

    shards.forEach((shard, i) => {
      const flaky = shard.every((m) => FLAKY_METHODS.has(m));
      include.push({
        "shard-index": i + 1,
        "shard-total": effectiveShardCount,
        "test-class": CONFIG.testClass,
        "test-methods": shard.join(","),
        flaky,
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
