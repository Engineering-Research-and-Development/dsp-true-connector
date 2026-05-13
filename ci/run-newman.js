#!/usr/bin/env node
/**
 * Thin Newman wrapper that injects maxResponseSize into postman-runtime's
 * requester options before Newman creates its internal runner instance.
 *
 * Newman does not expose maxResponseSize as a CLI flag, but postman-runtime
 * (which Newman uses internally) supports it.  Node.js module caching
 * guarantees that require('postman-runtime') here and inside Newman resolve to
 * the same module object, so patching the prototype before Newman instantiates
 * its runner is sufficient.
 *
 * Usage:
 *   node ci/run-newman.js <collection> [timeoutScript] [maxResponseSize]
 *
 * Defaults:
 *   timeoutScript    - 0 (unlimited)
 *   maxResponseSize  - 10 485 760 bytes (10 MB)
 */

'use strict';

const path = require('path');
const runtime = require('postman-runtime');
const newman = require('newman');

const collectionPath  = process.argv[2];
const timeoutScript   = parseInt(process.argv[3], 10) || 0;
const maxResponseSize = parseInt(process.argv[4], 10) || (10 * 1024 * 1024);

if (!collectionPath) {
    console.error('Usage: node ci/run-newman.js <collection> [timeoutScript] [maxResponseSize]');
    process.exit(1);
}

// Patch runtime.Runner so maxResponseSize is always merged into the requester
// config that Newman passes down.
const originalRun = runtime.Runner.prototype.run;
runtime.Runner.prototype.run = function (collection, options, callback) {
    if (options && options.requester) {
        options.requester.maxResponseSize = maxResponseSize;
    }
    return originalRun.call(this, collection, options, callback);
};

newman.run({
    collection: path.resolve(collectionPath),
    timeoutScript: timeoutScript,
    reporters: ['cli']
}, function (err) {
    if (err) { throw err; }
});
