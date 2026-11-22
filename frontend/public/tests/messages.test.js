// T-4 tests: messages.js, one row per cell of the architecture 7.2 mapping table (requirements 7.3).
// Browser, HUMAN-REPORTED. ASCII only.

import { describe, it, assert } from './harness.js';
import { task } from './fakes.js';
import { MESSAGES, outcomeFor } from '../js/messages.js';

const GENERIC = 'Something went wrong. Please try again.';
const CHECK = 'Please check your input and try again.';
const GONE = 'This task no longer exists';

const OPERATIONS = ['list', 'add', 'edit', 'status', 'delete'];

function validation(details) {
  return { ok: false, kind: 'validation', status: 400, code: 'VALIDATION_ERROR', details };
}

const NOT_FOUND = { ok: false, kind: 'notFound', status: 404, code: 'TODO_NOT_FOUND', details: [] };

// http, network, timeout and unexpected results; none of them may leak a status, a code or backend text.
const GENERIC_RESULTS = [
  ['http 500', { ok: false, kind: 'http', status: 500, code: 'INTERNAL_ERROR', details: [] }],
  ['http 502 without envelope', { ok: false, kind: 'http', status: 502, code: null, details: [] }],
  ['http 404 NOT_FOUND', { ok: false, kind: 'http', status: 404, code: 'NOT_FOUND', details: [] }],
  ['http 400 INVALID_REQUEST_BODY', { ok: false, kind: 'http', status: 400, code: 'INVALID_REQUEST_BODY', details: [] }],
  ['http 413', { ok: false, kind: 'http', status: 413, code: 'PAYLOAD_TOO_LARGE', details: [] }],
  ['network', { ok: false, kind: 'network' }],
  ['timeout', { ok: false, kind: 'timeout' }],
  ['unexpected', { ok: false, kind: 'unexpected', status: 200 }],
];

function check(operation, result, expected) {
  const outcome = outcomeFor(operation, result);
  assert.deepEqual(outcome, expected);
}

function error(message, fieldErrors = {}, refresh = false) {
  return { kind: 'error', message, fieldErrors, refresh };
}

function success(message) {
  return { kind: 'success', message, fieldErrors: {}, refresh: false };
}

describe('messages: ok results', () => {
  it('list: success with no message', () => {
    check('list', { ok: true, data: [] }, success(''));
  });

  it('add, edit, delete: the fixed success texts', () => {
    check('add', { ok: true, data: task() }, success('Task added'));
    check('edit', { ok: true, data: task() }, success('Task updated'));
    check('delete', { ok: true, data: null }, success('Task deleted'));
  });

  it('status: the text follows the returned status', () => {
    check('status', { ok: true, data: task({ status: 'Completed' }) }, success('Task marked as completed'));
    check('status', { ok: true, data: task({ status: 'Pending' }) }, success('Task marked as pending'));
  });
});

describe('messages: validation results', () => {
  it('add and edit: a title entry goes beside the title field and is the alert text', () => {
    for (const operation of ['add', 'edit']) {
      check(
        operation,
        validation([{ field: 'title', message: 'Title is required' }]),
        error('Title is required', { title: 'Title is required' }),
      );
    }
  });

  it('add and edit: a description entry goes beside the description field', () => {
    for (const operation of ['add', 'edit']) {
      check(
        operation,
        validation([{ field: 'description', message: 'Too long' }]),
        error('Too long', { description: 'Too long' }),
      );
    }
  });

  it('title and description entries: alert text in field order, whatever the backend order', () => {
    const details = [
      { field: 'description', message: 'Description must be 500 characters or fewer' },
      { field: 'title', message: 'Title is required' },
    ];
    check(
      'add',
      validation(details),
      error('Title is required Description must be 500 characters or fewer', {
        title: 'Title is required',
        description: 'Description must be 500 characters or fewer',
      }),
    );
  });

  it('an entry naming another field adds the general sentence after the field messages', () => {
    check(
      'edit',
      validation([
        { field: 'status', message: 'Invalid status' },
        { field: 'title', message: 'Title is required' },
      ]),
      error('Title is required ' + CHECK, { title: 'Title is required' }),
    );
  });

  it('only a status entry gives the general message and no field error', () => {
    check('add', validation([{ field: 'status', message: 'Invalid status' }]), error(CHECK));
  });

  it('no usable entries gives the general message (add, edit)', () => {
    for (const operation of ['add', 'edit']) {
      check(operation, validation([]), error(CHECK));
      check(operation, validation(undefined), error(CHECK));
      check(operation, validation([{ field: 'title', message: '  ' }]), error(CHECK));
    }
  });

  it('only the first message of a field is used', () => {
    check(
      'add',
      validation([
        { field: 'title', message: 'First' },
        { field: 'title', message: 'Second' },
      ]),
      error('First', { title: 'First' }),
    );
  });

  it('status: always the general message, with no field errors', () => {
    check('status', validation([{ field: 'status', message: 'Invalid status' }]), error(CHECK));
    check('status', validation([]), error(CHECK));
    check('status', validation([{ field: 'title', message: 'x' }]), error(CHECK));
  });

  it('list and delete: generic (cannot occur)', () => {
    for (const operation of ['list', 'delete']) {
      check(operation, validation([{ field: 'title', message: 'x' }]), error(GENERIC));
    }
  });
});

describe('messages: notFound results', () => {
  it('edit, status, delete: "This task no longer exists" with a refresh', () => {
    for (const operation of ['edit', 'status', 'delete']) {
      check(operation, NOT_FOUND, error(GONE, {}, true));
    }
  });

  it('list and add: generic, no refresh', () => {
    for (const operation of ['list', 'add']) {
      check(operation, NOT_FOUND, error(GENERIC));
    }
  });
});

describe('messages: http, network, timeout and unexpected results', () => {
  for (const operation of OPERATIONS) {
    for (const [label, result] of GENERIC_RESULTS) {
      it(`${operation} + ${label}: generic message, no refresh`, () => {
        check(operation, result, error(GENERIC));
      });
    }
  }
});

describe('messages: hygiene and fallbacks', () => {
  it('no message contains a status number, a code or the raw backend text', () => {
    const forbidden = ['400', '404', '405', '413', '415', '500', '502', '504', 'INTERNAL_ERROR', 'NOT_FOUND',
      'TODO_NOT_FOUND', 'VALIDATION_ERROR', 'BAD_GATEWAY', 'raw backend text'];
    const results = [
      ...GENERIC_RESULTS.map(([, result]) => ({ ...result, message: 'raw backend text' })),
      { ...NOT_FOUND, message: 'raw backend text' },
      { ...validation([{ field: 'status', message: 'Invalid status' }]), message: 'raw backend text' },
    ];
    for (const operation of OPERATIONS) {
      for (const result of results) {
        const text = outcomeFor(operation, result).message;
        for (const word of forbidden) {
          assert.ok(text.indexOf(word) === -1, `${operation} ${result.kind}: "${text}" contains ${word}`);
        }
      }
    }
  });

  it('the exported texts are the 7.3 wording', () => {
    assert.equal(MESSAGES.GENERIC, GENERIC);
    assert.equal(MESSAGES.CHECK_INPUT, CHECK);
    assert.equal(MESSAGES.NOT_FOUND, GONE);
    assert.equal(MESSAGES.ADDED, 'Task added');
    assert.equal(MESSAGES.UPDATED, 'Task updated');
    assert.equal(MESSAGES.DELETED, 'Task deleted');
    assert.equal(MESSAGES.MARKED_COMPLETED, 'Task marked as completed');
    assert.equal(MESSAGES.MARKED_PENDING, 'Task marked as pending');
  });

  it('a missing result or an unknown operation gives the generic message', () => {
    check('add', undefined, error(GENERIC));
    check('add', null, error(GENERIC));
    check('add', {}, error(GENERIC));
    check('nonsense', { ok: false, kind: 'http' }, error(GENERIC));
    check('add', { ok: false, kind: 'something-new' }, error(GENERIC));
  });
});
