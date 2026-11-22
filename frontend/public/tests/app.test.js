// T-6 and T-7 tests: view.js rendering and app.js part A (load, add, status). Browser, HUMAN-REPORTED. ASCII only.
// The app is mounted on a copy of the real <main id="app"> (sandbox), with a fake API whose calls are held until the
// test answers them, and an announcer whose deferral is a queue flushed by hand. Named cases: architecture 10.1 row A
// (c), (d), (e) add/status part; DR-1 (message survives a refresh), DR-2 (generation and dirty rule), DR-3 (field
// errors also in #msg-alert), DR-13b; AC-1..AC-6, AC-10, AC-11, AC-13, AC-15, AC-17..AC-19, AC-21.
// None of this has been executed by an agent (no JS engine available); the human's run is the evidence.

import { describe, it, assert, afterEach } from './harness.js';
import { mountSandbox, settle, createFakeApi, createManualDefer, task, ok, fail } from './fakes.js';
import { createApp } from '../js/app.js';
import { createAnnouncer } from '../js/announcer.js';

describe('app part A: view, load, add, status', () => {
  let sandbox;
  let api;
  let manual;
  let app;

  const q = (selector) => sandbox.root.querySelector(selector);
  const rows = () => Array.from(sandbox.root.querySelectorAll('#task-list > li'));
  const rowOf = (id) => sandbox.root.querySelector(`#task-list > li[data-id="${id}"]`);
  const button = (id, action) => rowOf(id).querySelector(`button[data-action="${action}"]`);
  const titles = () => rows().map((li) => li.querySelector('h3').textContent);
  const ids = () => rows().map((li) => li.getAttribute('data-id'));

  function messages() {
    manual.flush();
    return { status: q('#msg-status').textContent, alert: q('#msg-alert').textContent };
  }

  function type(input, value) {
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  function fillAdd(title, description = '') {
    type(q('#add-title'), title);
    type(q('#add-description'), description);
  }

  async function start() {
    sandbox = await mountSandbox();
    api = createFakeApi();
    manual = createManualDefer();
    const announcer = createAnnouncer({
      statusEl: sandbox.root.querySelector('#msg-status'),
      alertEl: sandbox.root.querySelector('#msg-alert'),
      defer: manual.defer,
    });
    app = createApp({ root: sandbox.root, api, announcer });
  }

  // Starts the app and answers the initial list.
  async function boot(tasks) {
    await start();
    api.callsOf('list')[0].respond(ok(tasks));
    await app.ready;
  }

  afterEach(() => {
    sandbox.remove();
  });

  describe('view and initial load (AC-1, AC-2, AC-3, AC-18, AC-21, FR-6)', () => {
    it('shows "Loading tasks..." with the list busy while the first load is held, and sends one GET', async () => {
      await start();
      assert.equal(q('#list-status').textContent, 'Loading tasks...');
      assert.equal(q('#task-list').getAttribute('aria-busy'), 'true');
      assert.equal(q('#retry').hidden, true);
      assert.equal(api.callsOf('list').length, 1);
    });

    it('renders the tasks in backend order, without sorting', async () => {
      await boot([task({ id: 'c', title: 'Cc' }), task({ id: 'a', title: 'Aa' }), task({ id: 'b', title: 'Bb' })]);
      assert.deepEqual(ids(), ['c', 'a', 'b']);
      assert.equal(q('#list-status').textContent, '');
      assert.equal(q('#task-list').hasAttribute('aria-busy'), false);
    });

    it('a row shows title, description only when not empty, badge, and Edit / toggle / Delete buttons', async () => {
      await boot([
        task({ id: 'a', title: 'With text', description: 'line1\nline2' }),
        task({ id: 'b', title: 'No text', description: '' }),
      ]);
      const a = rowOf('a');
      assert.equal(a.querySelector('h3').textContent, 'With text');
      assert.equal(a.querySelector('.desc').textContent, 'line1\nline2');
      assert.equal(a.querySelector('.badge').textContent, 'Pending');
      assert.equal(button('a', 'edit').textContent, 'Edit');
      assert.equal(button('a', 'toggle').textContent, 'Mark as completed');
      assert.equal(button('a', 'delete').textContent, 'Delete');
      assert.equal(button('a', 'delete').getAttribute('aria-label'), 'Delete: With text');
      assert.equal(rowOf('b').querySelector('.desc'), null, 'no description element for an empty description');
    });

    it('Completed differs from Pending by text (badge, button text) and data-status, not colour alone', async () => {
      await boot([task({ id: 'a', status: 'Completed' }), task({ id: 'b', status: 'Pending' })]);
      assert.equal(rowOf('a').getAttribute('data-status'), 'Completed');
      assert.equal(rowOf('a').querySelector('.badge').textContent, 'Completed');
      assert.equal(button('a', 'toggle').textContent, 'Mark as pending');
      assert.equal(rowOf('b').getAttribute('data-status'), 'Pending');
      assert.equal(rowOf('b').querySelector('.badge').textContent, 'Pending');
    });

    it('AC-21: markup in a title or description is shown as literal text', async () => {
      const evil = '<b>x</b><script>1</script>';
      await boot([task({ id: 'a', title: evil, description: '<i>d</i>' })]);
      const li = rowOf('a');
      assert.equal(li.querySelector('h3').textContent, evil);
      assert.equal(li.querySelector('h3').children.length, 0);
      assert.equal(li.querySelector('.desc').textContent, '<i>d</i>');
      assert.equal(li.querySelector('.desc').children.length, 0);
      assert.equal(li.querySelectorAll('script, b, i').length, 0);
    });

    it('AC-2: an empty list shows "No tasks yet" and the add form stays visible', async () => {
      await boot([]);
      assert.equal(q('#list-status').textContent, 'No tasks yet');
      assert.equal(rows().length, 0);
      assert.equal(q('#add-form').hidden, false);
      assert.equal(q('#retry').hidden, true);
    });

    it('AC-3: a load failure shows the generic message and Retry, never "No tasks yet"', async () => {
      await start();
      api.callsOf('list')[0].respond(fail('network'));
      await app.ready;
      assert.equal(q('#list-status').textContent, 'Something went wrong. Please try again.');
      assert.equal(q('#retry').hidden, false);
      assert.equal(q('#retry').disabled, false);
      assert.equal(messages().alert, 'Something went wrong. Please try again.');
      assert.equal(rows().length, 0);
      assert.equal(app.getState().loadStatus, 'failed');
    });

    it('AC-18: Retry sends GET /todos again and a success clears the message and shows the tasks', async () => {
      await start();
      api.callsOf('list')[0].respond(fail('timeout'));
      await app.ready;
      q('#retry').click();
      assert.equal(api.callsOf('list').length, 2);
      assert.equal(q('#list-status').textContent, 'Loading tasks...');
      assert.equal(q('#retry').hidden, true);
      api.callsOf('list')[1].respond(ok([task({ id: 'a', title: 'A' })]));
      await settle();
      assert.deepEqual(ids(), ['a']);
      assert.equal(q('#list-status').textContent, '');
      assert.deepEqual(messages(), { status: '', alert: '' });
    });

    it('CR-5: after a Retry that succeeds, focus moves from the hidden Retry button to the list heading', async () => {
      await start();
      api.callsOf('list')[0].respond(fail('network'));
      await app.ready;
      q('#retry').focus();
      q('#retry').click();
      api.callsOf('list')[1].respond(ok([task({ id: 'a' })]));
      await settle();
      assert.equal(q('#retry').hidden, true);
      assert.equal(document.activeElement, q('#list-heading'));
    });

    it('CR-5: after a Retry that fails, focus returns to the Retry button', async () => {
      await start();
      api.callsOf('list')[0].respond(fail('network'));
      await app.ready;
      q('#retry').focus();
      q('#retry').click();
      api.callsOf('list')[1].respond(fail('network'));
      await settle();
      assert.equal(q('#retry').hidden, false);
      assert.equal(document.activeElement, q('#retry'));
    });

    it('CR-5: a Retry that settles does not take focus away from a control the user moved to', async () => {
      await boot([]);
      app.load('retry');
      q('#add-title').focus();
      api.callsOf('list')[1].respond(ok([task({ id: 'a' })]));
      await settle();
      assert.equal(document.activeElement, q('#add-title'));
    });

    it('a second failure after Retry shows the generic message again', async () => {
      await start();
      api.callsOf('list')[0].respond(fail('http'));
      await app.ready;
      q('#retry').click();
      api.callsOf('list')[1].respond(fail('unexpected'));
      await settle();
      assert.equal(q('#retry').hidden, false);
      assert.equal(messages().alert, 'Something went wrong. Please try again.');
    });
  });

  describe('keyed reconcile (D-4, case (f) part, FR-11)', () => {
    it('a refresh reuses the row nodes by id and updates them in place', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      const before = { a: rowOf('a'), b: rowOf('b') };
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'b', title: 'B2' }), task({ id: 'a', title: 'A' })]));
      await settle();
      assert.deepEqual(ids(), ['b', 'a']);
      assert.ok(rowOf('a') === before.a, 'row a is the same node');
      assert.ok(rowOf('b') === before.b, 'row b is the same node');
      assert.equal(titles()[0], 'B2');
    });

    it('a refresh adds new tasks and removes tasks that are gone', async () => {
      await boot([task({ id: 'a' }), task({ id: 'b' })]);
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'c' }), task({ id: 'a' })]));
      await settle();
      assert.deepEqual(ids(), ['c', 'a']);
    });

    it('a pending row keeps its disabled state and "Updating..." through a refresh', async () => {
      await boot([task({ id: 'a' }), task({ id: 'b' })]);
      button('a', 'toggle').click();
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'a' }), task({ id: 'b' })]));
      await settle();
      assert.equal(button('a', 'toggle').textContent, 'Updating...');
      assert.equal(button('a', 'toggle').disabled, true);
      assert.equal(button('a', 'edit').disabled, true);
      assert.equal(button('a', 'delete').disabled, true);
      assert.equal(button('b', 'toggle').disabled, false, 'other tasks stay usable (AC-19)');
    });

    it('CR-6: the pending text is part of the accessible name, with the title, and the row is aria-busy', async () => {
      await boot([task({ id: 'a', title: 'Alpha' }), task({ id: 'b', title: 'Beta' })]);
      assert.equal(button('a', 'toggle').getAttribute('aria-label'), 'Mark as completed: Alpha');
      button('a', 'toggle').click();
      assert.equal(button('a', 'toggle').getAttribute('aria-label'), 'Updating...: Alpha');
      assert.equal(rowOf('a').getAttribute('aria-busy'), 'true');
      assert.equal(button('b', 'toggle').getAttribute('aria-label'), 'Mark as completed: Beta');
      assert.equal(rowOf('b').hasAttribute('aria-busy'), false);
      api.callsOf('status')[0].respond(ok(task({ id: 'a', title: 'Alpha', status: 'Completed' })));
      await settle();
      assert.equal(button('a', 'toggle').getAttribute('aria-label'), 'Mark as pending: Alpha');
      assert.equal(rowOf('a').hasAttribute('aria-busy'), false);
    });
  });

  describe('add (AC-4, AC-5, AC-6, AC-13, AC-14, AC-17; case (d))', () => {
    it('case (d): an empty title submitted from the title input sends nothing, alerts "Title is required", keeps focus on the title', async () => {
      await boot([]);
      q('#add-title').focus();
      q('#add-form').requestSubmit();
      assert.equal(api.callsOf('add').length, 0);
      assert.equal(q('#add-title-error').textContent, 'Title is required');
      assert.equal(q('#add-title').getAttribute('aria-invalid'), 'true');
      assert.equal(messages().alert, 'Title is required');
      assert.equal(document.activeElement, q('#add-title'));
    });

    it('AC-5: a whitespace-only title (including U+3000) is rejected', async () => {
      await boot([]);
      fillAdd('  ' + String.fromCharCode(0x3000) + '  ');
      q('#add-form').requestSubmit();
      assert.equal(api.callsOf('add').length, 0);
      assert.equal(q('#add-title-error').textContent, 'Title is required');
    });

    it('AC-6: 101-character title and 501-character description show both messages in field order; focus goes to the title', async () => {
      await boot([]);
      fillAdd('t'.repeat(101), 'd'.repeat(501));
      q('#add-form').requestSubmit();
      assert.equal(api.callsOf('add').length, 0);
      assert.equal(q('#add-title-error').textContent, 'Title must be 100 characters or fewer');
      assert.equal(q('#add-description-error').textContent, 'Description must be 500 characters or fewer');
      assert.equal(
        messages().alert,
        'Title must be 100 characters or fewer Description must be 500 characters or fewer',
      );
      assert.equal(document.activeElement, q('#add-title'));
    });

    it('a description-only error moves focus to the description', async () => {
      await boot([]);
      fillAdd('ok', 'd'.repeat(501));
      q('#add-form').requestSubmit();
      assert.equal(document.activeElement, q('#add-description'));
    });

    it('the next submit clears the earlier field errors', async () => {
      await boot([]);
      q('#add-form').requestSubmit();
      assert.equal(q('#add-title-error').textContent, 'Title is required');
      fillAdd('fine');
      q('#add-form').requestSubmit();
      assert.equal(q('#add-title-error').textContent, '');
      assert.equal(q('#add-title').hasAttribute('aria-invalid'), false);
    });

    it('AC-4 / AC-17: valid input sends the trimmed values once; the new task is first; the form is cleared; "Task added"; focus on the title', async () => {
      await boot([task({ id: 'a', title: 'Old' })]);
      fillAdd('  New task  ', '  details  ');
      q('#add-form').requestSubmit();
      assert.equal(api.callsOf('add').length, 1);
      assert.deepEqual(api.callsOf('add')[0].args, [{ title: 'New task', description: 'details' }]);
      api.callsOf('add')[0].respond(ok(task({ id: 'n', title: 'New task', description: 'details' })));
      await settle();
      assert.deepEqual(ids(), ['n', 'a']);
      assert.equal(q('#add-title').value, '');
      assert.equal(q('#add-description').value, '');
      assert.equal(messages().status, 'Task added');
      assert.equal(document.activeElement, q('#add-title'));
    });

    it('adding to an empty list removes "No tasks yet"', async () => {
      await boot([]);
      fillAdd('first');
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(ok(task({ id: 'n', title: 'first' })));
      await settle();
      assert.equal(q('#list-status').textContent, '');
      assert.deepEqual(ids(), ['n']);
    });

    it('a returned task whose id is already in the list replaces it instead of duplicating it', async () => {
      await boot([task({ id: 'n', title: 'Old' })]);
      fillAdd('x');
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(ok(task({ id: 'n', title: 'Fresh' })));
      await settle();
      assert.deepEqual(ids(), ['n']);
      assert.equal(titles()[0], 'Fresh');
    });

    it('AC-13: two submits record exactly one request (guard, not only the disabled attribute)', async () => {
      await boot([]);
      fillAdd('once');
      q('#add-form').requestSubmit();
      q('#add-form').requestSubmit();
      q('#add-submit').click();
      assert.equal(api.callsOf('add').length, 1);
    });

    it('AC-19 form pending state: inputs and button disabled, "Adding...", aria-busy', async () => {
      await boot([]);
      fillAdd('x');
      q('#add-form').requestSubmit();
      assert.equal(q('#add-submit').disabled, true);
      assert.equal(q('#add-submit').textContent, 'Adding...');
      assert.equal(q('#add-title').disabled, true);
      assert.equal(q('#add-description').disabled, true);
      assert.equal(q('#add-form').getAttribute('aria-busy'), 'true');
      api.callsOf('add')[0].respond(fail('network'));
      await settle();
      assert.equal(q('#add-submit').disabled, false);
      assert.equal(q('#add-submit').textContent, 'Add task');
      assert.equal(q('#add-title').disabled, false);
      assert.equal(q('#add-form').hasAttribute('aria-busy'), false);
    });

    for (const kind of ['network', 'timeout', 'unexpected', 'http']) {
      it(`case (e) / AC-11: a ${kind} failure keeps the list and typed text, shows the generic message, and returns focus to the field that had it`, async () => {
        await boot([task({ id: 'a' })]);
        fillAdd('keep me', 'and me');
        q('#add-description').focus();
        q('#add-form').requestSubmit();
        api.callsOf('add')[0].respond(fail(kind));
        await settle();
        assert.deepEqual(ids(), ['a']);
        assert.equal(q('#add-title').value, 'keep me');
        assert.equal(q('#add-description').value, 'and me');
        assert.equal(messages().alert, 'Something went wrong. Please try again.');
        assert.equal(document.activeElement, q('#add-description'));
        assert.deepEqual(app.getState().tasks.map((t) => t.id), ['a'], 'FR-10: tasks unchanged');
      });
    }

    it('a 404 on add is the generic message', async () => {
      await boot([]);
      fillAdd('x');
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(fail('notFound', { status: 404, code: 'TODO_NOT_FOUND', details: [] }));
      await settle();
      assert.equal(messages().alert, 'Something went wrong. Please try again.');
    });

    it('AC-15 / DR-3: a 400 VALIDATION_ERROR with a title entry shows it beside the field and in #msg-alert, and focuses the title', async () => {
      await boot([]);
      fillAdd('x');
      q('#add-description').focus();
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(
        fail('validation', { details: [{ field: 'title', message: 'Title is required' }] }),
      );
      await settle();
      assert.equal(q('#add-title-error').textContent, 'Title is required');
      assert.equal(q('#add-title').getAttribute('aria-invalid'), 'true');
      assert.equal(messages().alert, 'Title is required');
      assert.equal(document.activeElement, q('#add-title'));
      assert.equal(q('#add-title').value, 'x', 'typed text kept');
    });

    it('a 400 naming only another field shows the check-input message and keeps focus where it was', async () => {
      await boot([]);
      fillAdd('x');
      q('#add-title').focus();
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(fail('validation', { details: [{ field: 'status', message: 'bad' }] }));
      await settle();
      assert.equal(messages().alert, 'Please check your input and try again.');
      assert.equal(q('#add-title-error').textContent, '');
      assert.equal(document.activeElement, q('#add-title'));
    });
  });

  describe('status change (AC-10, AC-13, AC-19, AC-11)', () => {
    it('toggle sends one PATCH with the other status; only that row is pending; double click records one request', async () => {
      await boot([task({ id: 'a' }), task({ id: 'b' })]);
      button('a', 'toggle').click();
      button('a', 'toggle').click();
      assert.equal(api.callsOf('status').length, 1);
      assert.deepEqual(api.callsOf('status')[0].args, ['a', 'Completed']);
      assert.equal(button('a', 'toggle').disabled, true);
      assert.equal(button('a', 'toggle').textContent, 'Updating...');
      assert.equal(button('a', 'edit').disabled, true);
      assert.equal(button('a', 'delete').disabled, true);
      assert.equal(rowOf('a').getAttribute('aria-busy'), 'true');
      assert.equal(button('b', 'toggle').disabled, false);
      assert.equal(button('b', 'delete').disabled, false);
      button('b', 'toggle').click();
      assert.equal(api.callsOf('status').length, 2, 'another task can be changed at the same time');
    });

    it('success replaces the task in place, announces the matching text, and focus stays on that row\'s toggle', async () => {
      await boot([task({ id: 'a' }), task({ id: 'b' })]);
      const before = rowOf('a');
      button('a', 'toggle').focus();
      button('a', 'toggle').click();
      api.callsOf('status')[0].respond(ok(task({ id: 'a', status: 'Completed' })));
      await settle();
      assert.ok(rowOf('a') === before, 'same node');
      assert.equal(rowOf('a').querySelector('.badge').textContent, 'Completed');
      assert.equal(rowOf('a').getAttribute('data-status'), 'Completed');
      assert.equal(button('a', 'toggle').textContent, 'Mark as pending');
      assert.equal(button('a', 'toggle').disabled, false);
      assert.equal(messages().status, 'Task marked as completed');
      assert.equal(document.activeElement, button('a', 'toggle'));
      assert.deepEqual(ids(), ['a', 'b']);
    });

    it('Completed to Pending sends "Pending" and announces "Task marked as pending"', async () => {
      await boot([task({ id: 'a', status: 'Completed' })]);
      button('a', 'toggle').click();
      assert.deepEqual(api.callsOf('status')[0].args, ['a', 'Pending']);
      api.callsOf('status')[0].respond(ok(task({ id: 'a', status: 'Pending' })));
      await settle();
      assert.equal(messages().status, 'Task marked as pending');
      assert.equal(rowOf('a').querySelector('.badge').textContent, 'Pending');
    });

    for (const kind of ['network', 'timeout', 'unexpected', 'http']) {
      it(`case (e): a ${kind} failure leaves the task unchanged, shows the generic message, re-enables the row and focuses the toggle`, async () => {
        await boot([task({ id: 'a' })]);
        button('a', 'toggle').focus();
        button('a', 'toggle').click();
        api.callsOf('status')[0].respond(fail(kind));
        await settle();
        assert.equal(rowOf('a').querySelector('.badge').textContent, 'Pending');
        assert.equal(button('a', 'toggle').disabled, false);
        assert.equal(button('a', 'toggle').textContent, 'Mark as completed');
        assert.equal(messages().alert, 'Something went wrong. Please try again.');
        assert.equal(document.activeElement, button('a', 'toggle'));
        assert.equal(app.getState().tasks[0].status, 'Pending', 'FR-10');
      });
    }

    it('a 400 VALIDATION_ERROR with a status entry shows the check-input message only', async () => {
      await boot([task({ id: 'a' })]);
      button('a', 'toggle').click();
      api.callsOf('status')[0].respond(fail('validation', { details: [{ field: 'status', message: 'invalid' }] }));
      await settle();
      assert.equal(messages().alert, 'Please check your input and try again.');
      assert.equal(rowOf('a').querySelector('.badge').textContent, 'Pending');
    });
  });

  describe('ordering of load and mutation results (DR-2, case (c), FR-10)', () => {
    it('case (c): a held refresh, an add that resolves, then the held list without the new task: the new task stays and a second GET is recorded', async () => {
      await boot([task({ id: 'a', title: 'A' })]);
      app.load('refresh');
      assert.equal(api.callsOf('list').length, 2);
      fillAdd('N');
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(ok(task({ id: 'n', title: 'N' })));
      await settle();
      assert.deepEqual(ids(), ['n', 'a']);
      api.callsOf('list')[1].respond(ok([task({ id: 'a', title: 'A' })]));
      await settle();
      assert.deepEqual(ids(), ['n', 'a'], 'the stale snapshot was not applied');
      assert.equal(api.callsOf('list').length, 3, 'a second GET /todos was recorded');
      api.callsOf('list')[2].respond(ok([task({ id: 'n', title: 'N' }), task({ id: 'a', title: 'A' })]));
      await settle();
      assert.deepEqual(ids(), ['n', 'a']);
      assert.equal(app.getState().loadStatus, 'loaded');
      assert.equal(messages().status, 'Task added', 'the dirty re-run keeps the add announcement');
    });

    it('a status change that succeeds during a held refresh is not overwritten by the older list', async () => {
      await boot([task({ id: 'a' })]);
      app.load('refresh');
      button('a', 'toggle').click();
      api.callsOf('status')[0].respond(ok(task({ id: 'a', status: 'Completed' })));
      await settle();
      api.callsOf('list')[1].respond(ok([task({ id: 'a', status: 'Pending' })]));
      await settle();
      assert.equal(rowOf('a').querySelector('.badge').textContent, 'Completed');
      assert.equal(api.callsOf('list').length, 3);
    });

    it('a list response from an older generation is ignored', async () => {
      await boot([task({ id: 'a' })]);
      app.load('refresh');
      app.load('refresh');
      assert.equal(api.callsOf('list').length, 3);
      api.callsOf('list')[2].respond(ok([task({ id: 'new' })]));
      await settle();
      assert.deepEqual(ids(), ['new']);
      api.callsOf('list')[1].respond(ok([task({ id: 'stale' })]));
      await settle();
      assert.deepEqual(ids(), ['new'], 'the older response did not overwrite the newer one');
    });

    it('a status result for a task that is absent after a list was applied triggers a refresh', async () => {
      await boot([task({ id: 'a' }), task({ id: 'b' })]);
      button('a', 'toggle').click();
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'b' })]));
      await settle();
      api.callsOf('status')[0].respond(ok(task({ id: 'a', status: 'Completed' })));
      await settle();
      assert.equal(api.callsOf('list').length, 3, 'one more GET /todos');
      api.callsOf('list')[2].respond(ok([task({ id: 'a', status: 'Completed' }), task({ id: 'b' })]));
      await settle();
      assert.deepEqual(ids(), ['a', 'b']);
    });

    it('DR-13b: an initial load turned into a re-run by an add, which then fails, is announced in #msg-alert with Retry', async () => {
      await start();
      fillAdd('N');
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(ok(task({ id: 'n', title: 'N' })));
      await settle();
      api.callsOf('list')[0].respond(ok([]));
      await settle();
      assert.equal(api.callsOf('list').length, 2, 'the snapshot that may predate the add was not applied');
      api.callsOf('list')[1].respond(fail('network'));
      await settle();
      assert.equal(messages().alert, 'Something went wrong. Please try again.');
      assert.equal(q('#list-status').textContent, 'Something went wrong. Please try again.');
      assert.equal(q('#list-status').getAttribute('role'), 'status');
      assert.equal(q('#retry').hidden, false);
      assert.deepEqual(ids(), ['n'], 'the added task stays');
    });
  });

  describe('messages across a refresh (DR-1)', () => {
    it('a successful refresh keeps the current message', async () => {
      await boot([task({ id: 'a' })]);
      fillAdd('N');
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(ok(task({ id: 'n', title: 'N' })));
      await settle();
      assert.equal(messages().status, 'Task added');
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'n', title: 'N' }), task({ id: 'a' })]));
      await settle();
      assert.equal(messages().status, 'Task added');
    });

    it('a failed refresh shows the generic text and Retry in #list-status only, keeps the list and the message', async () => {
      await boot([task({ id: 'a' })]);
      fillAdd('N');
      q('#add-form').requestSubmit();
      api.callsOf('add')[0].respond(ok(task({ id: 'n', title: 'N' })));
      await settle();
      messages();
      app.load('refresh');
      api.callsOf('list')[1].respond(fail('timeout'));
      await settle();
      assert.equal(q('#list-status').textContent, 'Something went wrong. Please try again.');
      assert.equal(q('#retry').hidden, false);
      assert.deepEqual(messages(), { status: 'Task added', alert: '' });
      assert.deepEqual(ids(), ['n', 'a']);
    });
  });
});
