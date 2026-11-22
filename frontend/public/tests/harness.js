// C-12 self-test harness: dependency-free, async-aware. Run in a browser through tests/index.html.
// `createRunner()` makes an isolated runner (used by the harness meta-test); the default runner is exported as
// describe / it / beforeEach / afterEach / run.

const DEFAULT_TIMEOUT_MS = 5000;

// Readable rendering of a value for failure messages; non-ASCII characters are shown as \uXXXX so that
// whitespace vectors can be told apart.
export function show(value) {
  if (typeof value === 'string') {
    let out = '"';
    for (let i = 0; i < value.length; i++) {
      const code = value.charCodeAt(i);
      if (code >= 0x20 && code <= 0x7e && value[i] !== '"' && value[i] !== '\\') {
        out += value[i];
      } else {
        out += '\\u' + code.toString(16).toUpperCase().padStart(4, '0');
      }
    }
    return out + '"';
  }
  if (value === undefined) {
    return 'undefined';
  }
  try {
    const json = JSON.stringify(value);
    return json === undefined ? String(value) : json;
  } catch (error) {
    return String(value);
  }
}

function isObject(value) {
  return value !== null && typeof value === 'object';
}

function same(actual, expected) {
  if (actual === expected) {
    return true;
  }
  if (!isObject(actual) || !isObject(expected)) {
    return Number.isNaN(actual) && Number.isNaN(expected);
  }
  if (Array.isArray(actual) !== Array.isArray(expected)) {
    return false;
  }
  const actualKeys = Object.keys(actual);
  const expectedKeys = Object.keys(expected);
  if (actualKeys.length !== expectedKeys.length) {
    return false;
  }
  return expectedKeys.every(
    (key) => Object.prototype.hasOwnProperty.call(actual, key) && same(actual[key], expected[key]),
  );
}

function fail(message) {
  throw new Error(message);
}

export const assert = {
  ok(value, message) {
    if (!value) {
      fail(message || `expected a truthy value, got ${show(value)}`);
    }
  },
  equal(actual, expected, message) {
    if (actual !== expected) {
      fail(`${message ? message + ': ' : ''}expected ${show(expected)}, got ${show(actual)}`);
    }
  },
  notEqual(actual, unexpected, message) {
    if (actual === unexpected) {
      fail(`${message ? message + ': ' : ''}did not expect ${show(unexpected)}`);
    }
  },
  deepEqual(actual, expected, message) {
    if (!same(actual, expected)) {
      fail(`${message ? message + ': ' : ''}expected ${show(expected)}, got ${show(actual)}`);
    }
  },
  async rejects(promise, message) {
    try {
      await promise;
    } catch (error) {
      return;
    }
    fail(message || 'expected the promise to reject');
  },
  fail(message) {
    fail(message || 'failed');
  },
};

function newSuite(name, parent) {
  return { name, parent, items: [], before: [], after: [] };
}

export function createRunner({ timeoutMs = DEFAULT_TIMEOUT_MS } = {}) {
  const root = newSuite('', null);
  let current = root;

  function describe(name, body) {
    const suite = newSuite(name, current);
    current.items.push(suite);
    const previous = current;
    current = suite;
    try {
      body();
    } finally {
      current = previous;
    }
  }

  function it(name, body) {
    current.items.push({ test: true, name, body, suite: current });
  }

  function beforeEach(body) {
    current.before.push(body);
  }

  function afterEach(body) {
    current.after.push(body);
  }

  function withTimeout(body, label) {
    let timer;
    const timeout = new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error(`timed out after ${timeoutMs} ms (${label})`)), timeoutMs);
    });
    const work = Promise.resolve().then(() => body());
    return Promise.race([work, timeout]).finally(() => clearTimeout(timer));
  }

  async function runTest(item, path) {
    const chain = [];
    for (let suite = item.suite; suite; suite = suite.parent) {
      chain.unshift(suite);
    }
    const before = chain.flatMap((suite) => suite.before);
    const after = chain.slice().reverse().flatMap((suite) => suite.after);
    const name = [...path, item.name].join(' > ');
    let failure = null;
    try {
      for (const hook of before) {
        await withTimeout(hook, 'beforeEach');
      }
      await withTimeout(item.body, 'test');
    } catch (error) {
      failure = error;
    }
    for (const hook of after) {
      try {
        await withTimeout(hook, 'afterEach');
      } catch (error) {
        failure = failure || error;
      }
    }
    if (failure) {
      const message = failure && failure.message ? failure.message : String(failure);
      return { name, ok: false, message };
    }
    return { name, ok: true, message: '' };
  }

  async function run({ onResult } = {}) {
    const results = [];
    async function walk(suite, path) {
      for (const item of suite.items) {
        if (item.test) {
          const result = await runTest(item, path);
          results.push(result);
          if (onResult) {
            onResult(result);
          }
        } else {
          await walk(item, [...path, item.name]);
        }
      }
    }
    await walk(root, []);
    const passed = results.filter((result) => result.ok).length;
    return { passed, failed: results.length - passed, results };
  }

  return { describe, it, beforeEach, afterEach, run };
}

const defaultRunner = createRunner();
export const describe = defaultRunner.describe;
export const it = defaultRunner.it;
export const beforeEach = defaultRunner.beforeEach;
export const afterEach = defaultRunner.afterEach;
export const run = defaultRunner.run;

export function summaryLine(outcome) {
  return `${outcome.passed} passed, ${outcome.failed} failed`;
}

// Paste-ready text for the human: the summary line, the browser, and every failed test with its message.
export function pasteText(outcome, userAgent) {
  const lines = [summaryLine(outcome), `Browser: ${userAgent}`];
  const failed = outcome.results.filter((result) => !result.ok);
  if (failed.length > 0) {
    lines.push('Failed tests:');
    for (const result of failed) {
      lines.push(`FAIL ${result.name}: ${result.message}`);
    }
  }
  return lines.join('\n');
}

// Writes the outcome into the test page: #summary, #test-list (one line per test) and #results (paste block).
export function renderReport(outcome, doc = document) {
  const summary = doc.getElementById('summary');
  summary.textContent = summaryLine(outcome);
  summary.className = outcome.failed === 0 ? 'ok' : 'bad';
  const list = doc.getElementById('test-list');
  list.textContent = '';
  for (const result of outcome.results) {
    const line = doc.createElement('li');
    line.className = result.ok ? 'pass' : 'fail';
    line.textContent = result.ok ? `PASS ${result.name}` : `FAIL ${result.name}: ${result.message}`;
    list.appendChild(line);
  }
  doc.getElementById('results').textContent = pasteText(outcome, navigator.userAgent);
}
