// T-3 tests: validation.js (AC-5, AC-6, AC-16, AC-17), plus meta-tests of the harness and the fakes.
// The vector table below is a copy of the White_Space list in
// backend/src/main/java/com/capstone/todo/validation/UnicodeText.java:17-33 and can be compared with it line by line.
// This file is ASCII only on purpose: every special character is built from its code point.

import { describe, it, assert, createRunner } from './harness.js';
import { createFakeFetch, createFakeTimer, createManualDefer, settle } from './fakes.js';
import {
  WHITE_SPACE_CODE_POINTS,
  codePointLength,
  isWhiteSpace,
  trimText,
  validateTask,
} from '../js/validation.js';

// The 25 backend White_Space code points, as listed in UnicodeText.java (javadoc and isWhiteSpace body).
const BACKEND_WHITE_SPACE = [
  0x0009, 0x000a, 0x000b, 0x000c, 0x000d, // U+0009 to U+000D
  0x0020,
  0x0085,
  0x00a0,
  0x1680,
  0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200a, // U+2000 to U+200A
  0x2028,
  0x2029,
  0x202f,
  0x205f,
  0x3000,
];

// Code points the backend does NOT strip, including those the JS built-in trim() treats differently.
const NOT_WHITE_SPACE = [
  [0xfeff, 'U+FEFF (zero width no-break space; JS trim() strips it)'],
  [0x001c, 'U+001C'],
  [0x001d, 'U+001D'],
  [0x001e, 'U+001E'],
  [0x001f, 'U+001F'],
  [0x180e, 'U+180E (Mongolian vowel separator)'],
  [0x200b, 'U+200B (zero width space)'],
  [0x0008, 'U+0008'],
  [0x000e, 'U+000E'],
  [0x2060, 'U+2060 (word joiner)'],
  [0x0021, 'U+0021 (!)'],
];

const EMOJI = String.fromCodePoint(0x1f600);
const BOM = String.fromCharCode(0xfeff);
const NEL = String.fromCharCode(0x0085);
const MONGOLIAN_VOWEL_SEPARATOR = String.fromCharCode(0x180e);
const IDEOGRAPHIC_SPACE = String.fromCharCode(0x3000);
const NBSP = String.fromCharCode(0x00a0);
const FIGURE_SPACE = String.fromCharCode(0x2007);
const NARROW_NBSP = String.fromCharCode(0x202f);

function hex(cp) {
  return 'U+' + cp.toString(16).toUpperCase().padStart(4, '0');
}

function repeat(text, count) {
  return text.repeat(count);
}

describe('validation: White_Space set copied from UnicodeText.java', () => {
  it('has exactly the 25 backend code points', () => {
    assert.equal(BACKEND_WHITE_SPACE.length, 25);
    assert.deepEqual(Array.from(WHITE_SPACE_CODE_POINTS), BACKEND_WHITE_SPACE);
  });

  it('isWhiteSpace agrees with the backend list for every code point U+0000..U+FFFF', () => {
    const expected = new Set(BACKEND_WHITE_SPACE);
    const wrong = [];
    for (let cp = 0; cp <= 0xffff; cp++) {
      if (isWhiteSpace(cp) !== expected.has(cp)) {
        wrong.push(hex(cp));
      }
    }
    assert.deepEqual(wrong, []);
  });

  for (const cp of BACKEND_WHITE_SPACE) {
    it(`strips ${hex(cp)} at both ends`, () => {
      const ch = String.fromCharCode(cp);
      assert.equal(trimText(ch + 'a' + ch), 'a');
      assert.equal(validateTask({ title: ch + 'a' + ch }).value.title, 'a');
    });
  }

  for (const [cp, label] of NOT_WHITE_SPACE) {
    it(`keeps ${label}`, () => {
      const ch = String.fromCharCode(cp);
      assert.equal(trimText(ch + 'a' + ch), ch + 'a' + ch);
    });
  }

  it('keeps whitespace inside the text', () => {
    assert.equal(trimText('  a \t b  '), 'a \t b');
  });
});

describe('validation: named vectors where JS built-ins differ from the backend (AC-16)', () => {
  it('U+FEFF is kept (JS String.prototype.trim strips it)', () => {
    assert.equal(trimText(BOM + 'a'), BOM + 'a');
    assert.equal(validateTask({ title: BOM + 'a' + BOM }).value.title, BOM + 'a' + BOM);
  });

  it('a title that is only U+FEFF is not empty, so it is accepted', () => {
    const result = validateTask({ title: BOM });
    assert.ok(result.ok);
    assert.equal(result.value.title, BOM);
  });

  it('U+0085 is stripped (JS trim() keeps it)', () => {
    assert.equal(trimText(NEL + 'a' + NEL), 'a');
    assert.equal(validateTask({ title: NEL }).errors.title, 'Title is required');
  });

  it('U+001C..U+001F are kept', () => {
    assert.equal(trimText('\u001Ca\u001F'), '\u001Ca\u001F');
  });

  it('U+180E is kept', () => {
    assert.equal(trimText(MONGOLIAN_VOWEL_SEPARATOR + 'a'), MONGOLIAN_VOWEL_SEPARATOR + 'a');
  });

  it('U+3000 is stripped', () => {
    assert.equal(trimText(IDEOGRAPHIC_SPACE + 'a' + IDEOGRAPHIC_SPACE), 'a');
    assert.equal(validateTask({ title: IDEOGRAPHIC_SPACE }).errors.title, 'Title is required');
  });

  it('U+00A0, U+2007 and U+202F are stripped', () => {
    assert.equal(trimText(NBSP + FIGURE_SPACE + 'a' + NARROW_NBSP + NBSP), 'a');
  });

  it('an astral emoji counts as 1 code point (JS .length says 2)', () => {
    assert.equal(EMOJI.length, 2);
    assert.equal(codePointLength(EMOJI), 1);
  });

  it('100 emoji are accepted as a title and 101 are rejected', () => {
    assert.ok(validateTask({ title: repeat(EMOJI, 100) }).ok);
    assert.equal(validateTask({ title: repeat(EMOJI, 101) }).errors.title, 'Title must be 100 characters or fewer');
  });

  it('500 emoji are accepted as a description and 501 are rejected', () => {
    assert.ok(validateTask({ title: 'a', description: repeat(EMOJI, 500) }).ok);
    assert.equal(
      validateTask({ title: 'a', description: repeat(EMOJI, 501) }).errors.description,
      'Description must be 500 characters or fewer',
    );
  });

  it('documented difference: the JS built-in trim() strips U+FEFF and keeps U+0085', () => {
    assert.equal((BOM + 'a').trim(), 'a');
    assert.equal((NEL + 'a').trim(), NEL + 'a');
  });
});

describe('validation: lone surrogates and missing input', () => {
  it('a lone surrogate gets no client rule: it is accepted and counts as 1 (the backend answers INVALID_REQUEST_BODY)', () => {
    assert.ok(validateTask({ title: '\uD800' }).ok);
    assert.ok(validateTask({ title: 'a\uDC00' }).ok);
    assert.equal(codePointLength('\uD800'), 1);
    assert.equal(codePointLength('\uDC00'), 1);
    assert.equal(codePointLength('\uD83Da'), 2);
  });

  it('a lone surrogate is not stripped by trimming', () => {
    assert.equal(trimText(' \uD800 '), '\uD800');
  });

  it('a valid surrogate pair counts as 1', () => {
    assert.equal(codePointLength(EMOJI), 1);
    assert.equal(codePointLength('a' + EMOJI + 'b'), 3);
  });

  it('null and undefined behave as empty', () => {
    assert.equal(trimText(null), '');
    assert.equal(trimText(undefined), '');
    assert.equal(codePointLength(null), 0);
    assert.equal(validateTask({ title: null, description: null }).errors.title, 'Title is required');
    assert.equal(validateTask({}).errors.title, 'Title is required');
    assert.equal(validateTask().errors.title, 'Title is required');
    assert.deepEqual(validateTask({ title: 'a' }).value, { title: 'a', description: '' });
  });
});

describe('validation: validateTask rules and messages (AC-5, AC-6, AC-17)', () => {
  it('an empty title gives "Title is required"', () => {
    const result = validateTask({ title: '', description: '' });
    assert.equal(result.ok, false);
    assert.deepEqual(result.errors, { title: 'Title is required' });
  });

  it('a whitespace-only title gives "Title is required"', () => {
    assert.equal(validateTask({ title: ' \t\n' + IDEOGRAPHIC_SPACE + ' ' }).errors.title, 'Title is required');
  });

  it('title boundary: 100 accepted, 101 rejected', () => {
    assert.ok(validateTask({ title: repeat('a', 100) }).ok);
    const result = validateTask({ title: repeat('a', 101) });
    assert.equal(result.ok, false);
    assert.deepEqual(result.errors, { title: 'Title must be 100 characters or fewer' });
  });

  it('description boundary: 500 accepted, 501 rejected', () => {
    assert.ok(validateTask({ title: 'a', description: repeat('b', 500) }).ok);
    const result = validateTask({ title: 'a', description: repeat('b', 501) });
    assert.equal(result.ok, false);
    assert.deepEqual(result.errors, { description: 'Description must be 500 characters or fewer' });
  });

  it('length is counted after trimming', () => {
    assert.ok(validateTask({ title: '  ' + repeat('a', 100) + '  ' }).ok);
    assert.ok(validateTask({ title: 'a', description: '  ' + repeat('b', 500) + '  ' }).ok);
  });

  it('a kept U+FEFF counts towards the length', () => {
    assert.equal(
      validateTask({ title: repeat('a', 100) + BOM }).errors.title,
      'Title must be 100 characters or fewer',
    );
  });

  it('an empty description is valid', () => {
    assert.ok(validateTask({ title: 'a', description: '' }).ok);
    assert.ok(validateTask({ title: 'a', description: '   ' }).ok);
  });

  it('both fields can fail at once', () => {
    const result = validateTask({ title: '', description: repeat('b', 501) });
    assert.deepEqual(result.errors, {
      title: 'Title is required',
      description: 'Description must be 500 characters or fewer',
    });
  });

  it('returns the trimmed values, also when invalid', () => {
    const ok = validateTask({ title: '  Buy milk  ', description: '\n two\nlines \n' });
    assert.deepEqual(ok.value, { title: 'Buy milk', description: 'two\nlines' });
    const bad = validateTask({ title: '  ', description: ' x ' });
    assert.deepEqual(bad.value, { title: '', description: 'x' });
  });
});

describe('harness meta-test (counters, async, hooks, timeout)', () => {
  it('counts passed and failed tests without leaving a failing test in the suite', async () => {
    const runner = createRunner({ timeoutMs: 50 });
    const order = [];
    runner.describe('inner', () => {
      runner.beforeEach(() => order.push('before'));
      runner.afterEach(() => order.push('after'));
      runner.it('passes', () => {
        order.push('t1');
      });
      runner.it('fails', () => {
        throw new Error('boom');
      });
      runner.it('async pass', async () => {
        await settle();
      });
      runner.it('async reject', async () => {
        await settle();
        throw new Error('late boom');
      });
      runner.it('hangs', () => new Promise(() => {}));
    });
    const outcome = await runner.run();
    assert.equal(outcome.passed, 2);
    assert.equal(outcome.failed, 3);
    assert.deepEqual(
      outcome.results.map((result) => result.ok),
      [true, false, true, false, false],
    );
    assert.equal(outcome.results[1].name, 'inner > fails');
    assert.equal(outcome.results[1].message, 'boom');
    assert.ok(outcome.results[4].message.indexOf('timed out') === 0);
    assert.equal(order.filter((entry) => entry === 'before').length, 5);
    assert.equal(order.filter((entry) => entry === 'after').length, 5);
  });

  it('assert helpers fail with a message and deepEqual compares structure', () => {
    assert.deepEqual({ a: [1, { b: 2 }] }, { a: [1, { b: 2 }] });
    let threw = false;
    try {
      assert.deepEqual({ a: 1 }, { a: 2 });
    } catch (error) {
      threw = true;
    }
    assert.ok(threw);
    threw = false;
    try {
      assert.equal(1, 2);
    } catch (error) {
      threw = true;
    }
    assert.ok(threw);
  });
});

describe('fakes meta-test', () => {
  it('fake fetch records a request and answers from the script', async () => {
    const fetchFn = createFakeFetch();
    fetchFn.enqueue({ status: 200, body: '{"data":[]}' });
    const response = await fetchFn('/todos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    assert.equal(response.status, 200);
    assert.equal(await response.text(), '{"data":[]}');
    assert.equal(fetchFn.calls.length, 1);
    assert.equal(fetchFn.calls[0].method, 'POST');
    assert.equal(fetchFn.calls[0].headers['content-type'], 'application/json');
    assert.equal(fetchFn.calls[0].body, '{}');
  });

  it('fake fetch holds a call until it is released, and an abort rejects it', async () => {
    const fetchFn = createFakeFetch();
    const controller = new AbortController();
    const held = fetchFn('/todos', { signal: controller.signal });
    let state = 'waiting';
    held.then(
      () => (state = 'resolved'),
      () => (state = 'rejected'),
    );
    await settle();
    assert.equal(state, 'waiting');
    controller.abort();
    await settle();
    assert.equal(state, 'rejected');

    const second = fetchFn('/todos');
    fetchFn.calls[1].respond({ status: 204 });
    assert.equal((await second).status, 204);
  });

  it('fake timer fires the oldest active timer and records clearing', () => {
    const timer = createFakeTimer();
    let fired = 0;
    const first = timer.setTimer(() => fired++, 10000);
    const second = timer.setTimer(() => fired++, 5);
    timer.clearTimer(second);
    assert.equal(timer.active().length, 1);
    timer.fire();
    assert.equal(fired, 1);
    assert.equal(timer.active().length, 0);
    assert.equal(first, 1);
    assert.equal(timer.timers[0].ms, 10000);
  });

  it('manual defer queues until flushed', () => {
    const manual = createManualDefer();
    const seen = [];
    manual.defer(() => seen.push('a'));
    manual.defer(() => seen.push('b'));
    assert.equal(seen.length, 0);
    assert.equal(manual.size(), 2);
    manual.flush();
    assert.deepEqual(seen, ['a', 'b']);
  });
});
