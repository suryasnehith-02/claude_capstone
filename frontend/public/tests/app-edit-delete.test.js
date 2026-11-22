// T-8 tests: app.js part B (edit, delete, 404 handling, orphaned draft). Browser, HUMAN-REPORTED. ASCII only.
// Named cases: architecture 10.1 row A (a), (b), (e) edit/delete part, (f), (g) extension; DR-12 (re-check after the
// confirm dialog), DR-13 (via part A), DR-14a/b/c; AC-7, AC-8, AC-9, AC-11, AC-12, AC-13, AC-22.
// None of this has been executed by an agent (no JS engine available); the human's run is the evidence.

import { describe, it, assert, afterEach } from './harness.js';
import { mountSandbox, settle, createFakeApi, createManualDefer, task, ok, fail } from './fakes.js';
import { createApp } from '../js/app.js';
import { createAnnouncer } from '../js/announcer.js';

const GENERIC = 'Something went wrong. Please try again.';
const NOT_FOUND = 'This task no longer exists';
const notFound = () => fail('notFound', { status: 404, code: 'TODO_NOT_FOUND', details: [] });

// A confirm whose answer the test gives: confirm.calls[i].resolve(true | false).
function createFakeConfirm() {
  const calls = [];
  const confirm = (options) => {
    const call = { options };
    calls.push(call);
    return new Promise((resolve) => {
      call.resolve = resolve;
    });
  };
  confirm.calls = calls;
  return confirm;
}

describe('app part B: edit, delete, 404, orphan', () => {
  let sandbox;
  let api;
  let manual;
  let app;

  const q = (selector) => sandbox.root.querySelector(selector);
  const rows = () => Array.from(sandbox.root.querySelectorAll('#task-list > li'));
  const rowOf = (id) => sandbox.root.querySelector(`#task-list > li[data-id="${id}"]`);
  const button = (id, action) => rowOf(id).querySelector(`button[data-action="${action}"]`);
  const titleInput = (id) => rowOf(id).querySelector('input[data-field="title"]');
  const descInput = (id) => rowOf(id).querySelector('textarea[data-field="description"]');
  const ids = () => rows().map((li) => li.getAttribute('data-id'));

  function messages() {
    manual.flush();
    return { status: q('#msg-status').textContent, alert: q('#msg-alert').textContent };
  }

  function type(input, value) {
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  async function start(confirm) {
    sandbox = await mountSandbox();
    api = createFakeApi();
    manual = createManualDefer();
    const announcer = createAnnouncer({
      statusEl: sandbox.root.querySelector('#msg-status'),
      alertEl: sandbox.root.querySelector('#msg-alert'),
      defer: manual.defer,
    });
    app = createApp({ root: sandbox.root, api, announcer, confirm });
  }

  async function boot(tasks, confirm) {
    await start(confirm);
    api.callsOf('list')[0].respond(ok(tasks));
    await app.ready;
  }

  // Opens the edit form of `id`, changes the title and presses Save; the PUT stays held.
  function saveWith(id, title, description) {
    button(id, 'edit').click();
    type(titleInput(id), title);
    if (description !== undefined) {
      type(descInput(id), description);
    }
    rowOf(id).querySelector('form').requestSubmit();
  }

  afterEach(() => {
    sandbox.remove();
  });

  describe('edit (AC-7, AC-8, AC-13, AC-11)', () => {
    it('Edit pre-fills the form from the task and focuses the title; the row has Save and Cancel', async () => {
      await boot([task({ id: 'a', title: 'Old', description: 'Desc' })]);
      button('a', 'edit').click();
      assert.equal(titleInput('a').value, 'Old');
      assert.equal(descInput('a').value, 'Desc');
      assert.equal(document.activeElement, titleInput('a'));
      assert.equal(button('a', 'save').textContent, 'Save');
      assert.equal(button('a', 'cancel').textContent, 'Cancel');
      assert.equal(api.callsOf('edit').length, 0);
    });

    it('only one row is in edit mode: Edit on another row closes the first without a request', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      button('a', 'edit').click();
      type(titleInput('a'), 'changed');
      button('b', 'edit').click();
      assert.equal(rowOf('a').querySelector('form'), null);
      assert.equal(rowOf('a').querySelector('h3').textContent, 'A');
      assert.ok(rowOf('b').querySelector('form') !== null);
      assert.equal(api.callsOf('edit').length, 0);
    });

    it('AC-8: Cancel sends no request, leaves the task unchanged and focuses that row\'s Edit button', async () => {
      await boot([task({ id: 'a', title: 'Keep', description: 'Same' })]);
      button('a', 'edit').click();
      type(titleInput('a'), 'Typed but cancelled');
      button('a', 'cancel').click();
      assert.equal(api.callsOf('edit').length, 0);
      assert.equal(rowOf('a').querySelector('h3').textContent, 'Keep');
      assert.equal(rowOf('a').querySelector('.desc').textContent, 'Same');
      assert.equal(document.activeElement, button('a', 'edit'));
      assert.equal(app.getState().editing, null);
    });

    it('AC-8: Escape inside the form acts as Cancel (no request, focus on Edit)', async () => {
      await boot([task({ id: 'a', title: 'Keep' })]);
      button('a', 'edit').click();
      type(titleInput('a'), 'x');
      titleInput('a').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      assert.equal(api.callsOf('edit').length, 0);
      assert.equal(rowOf('a').querySelector('form'), null);
      assert.equal(rowOf('a').querySelector('h3').textContent, 'Keep');
      assert.equal(document.activeElement, button('a', 'edit'));
    });

    it('every input event updates the draft in state', async () => {
      await boot([task({ id: 'a', title: 'T', description: 'D' })]);
      button('a', 'edit').click();
      type(titleInput('a'), 'T2');
      type(descInput('a'), 'D2');
      assert.deepEqual(app.getState().editing, { id: 'a', draftTitle: 'T2', draftDescription: 'D2' });
    });

    it('Save sends both trimmed fields once; pending state; other rows\' Edit disabled; double submit is one request', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      saveWith('a', '  New title  ', '  New desc  ');
      rowOf('a').querySelector('form').requestSubmit();
      assert.equal(api.callsOf('edit').length, 1);
      assert.deepEqual(api.callsOf('edit')[0].args, ['a', { title: 'New title', description: 'New desc' }]);
      assert.equal(button('a', 'save').textContent, 'Saving...');
      assert.equal(button('a', 'save').disabled, true);
      assert.equal(titleInput('a').disabled, true);
      assert.equal(rowOf('a').getAttribute('aria-busy'), 'true');
      assert.equal(button('b', 'edit').disabled, true, 'other rows cannot start an edit while a save is pending');
      assert.equal(button('b', 'toggle').disabled, false, 'other tasks stay usable (AC-19)');
    });

    it('success shows the returned values, leaves edit mode, announces "Task updated" and focuses that row\'s Edit', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      saveWith('a', 'New title', 'New desc');
      api.callsOf('edit')[0].respond(ok(task({ id: 'a', title: 'Server title', description: 'Server desc' })));
      await settle();
      assert.equal(rowOf('a').querySelector('form'), null);
      assert.equal(rowOf('a').querySelector('h3').textContent, 'Server title');
      assert.equal(rowOf('a').querySelector('.desc').textContent, 'Server desc');
      assert.equal(messages().status, 'Task updated');
      assert.equal(document.activeElement, button('a', 'edit'));
      assert.equal(button('b', 'edit').disabled, false);
      assert.deepEqual(ids(), ['a', 'b']);
    });

    it('invalid input sends nothing, shows the field error and its alert text, and focuses the first invalid field', async () => {
      await boot([task({ id: 'a', title: 'A' })]);
      button('a', 'edit').click();
      type(titleInput('a'), '   ');
      rowOf('a').querySelector('form').requestSubmit();
      assert.equal(api.callsOf('edit').length, 0);
      assert.equal(rowOf('a').querySelector('.field-error').textContent, 'Title is required');
      assert.equal(titleInput('a').getAttribute('aria-invalid'), 'true');
      assert.equal(messages().alert, 'Title is required');
      assert.equal(document.activeElement, titleInput('a'));
      assert.ok(rowOf('a').querySelector('form') !== null, 'still in edit mode');
    });

    for (const kind of ['network', 'timeout', 'unexpected', 'http']) {
      it(`case (e): a ${kind} failure on save keeps the form and typed text, shows the generic message, and returns focus`, async () => {
        await boot([task({ id: 'a', title: 'A' })]);
        button('a', 'edit').click();
        type(titleInput('a'), 'typed title');
        type(descInput('a'), 'typed desc');
        descInput('a').focus();
        rowOf('a').querySelector('form').requestSubmit();
        api.callsOf('edit')[0].respond(fail(kind));
        await settle();
        assert.equal(titleInput('a').value, 'typed title');
        assert.equal(descInput('a').value, 'typed desc');
        assert.equal(button('a', 'save').disabled, false);
        assert.equal(button('a', 'save').textContent, 'Save');
        assert.equal(messages().alert, GENERIC);
        assert.equal(document.activeElement, descInput('a'));
        assert.equal(app.getState().tasks[0].title, 'A', 'FR-10: tasks unchanged');
      });
    }

    it('a 400 VALIDATION_ERROR with a title entry and a status entry: field message beside the field, general text in the alert, focus on the title', async () => {
      await boot([task({ id: 'a', title: 'A' })]);
      saveWith('a', 'x');
      api.callsOf('edit')[0].respond(
        fail('validation', {
          details: [
            { field: 'title', message: 'Title is required' },
            { field: 'status', message: 'bad' },
          ],
        }),
      );
      await settle();
      assert.equal(rowOf('a').querySelector('.field-error').textContent, 'Title is required');
      assert.equal(messages().alert, 'Title is required Please check your input and try again.');
      assert.equal(document.activeElement, titleInput('a'));
      assert.equal(titleInput('a').value, 'x');
    });
  });

  describe('404 handling and orphan draft (AC-12, AC-22; cases (a), (b); DR-1, DR-14)', () => {
    it('case (a): a 404 on edit, then the refresh succeeds: the message stays and the orphan panel shows the draft; focus on its heading', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      saveWith('a', 'My typed title', 'My typed description');
      api.callsOf('edit')[0].respond(notFound());
      await settle();
      assert.equal(api.callsOf('list').length, 2, 'a refresh was started');
      assert.equal(q('#orphan').hidden, false);
      assert.equal(q('#orphan-heading').textContent, 'Your unsaved text');
      assert.equal(q('#orphan-title').textContent, 'My typed title');
      assert.equal(q('#orphan-description').textContent, 'My typed description');
      assert.equal(document.activeElement, q('#orphan-heading'));
      api.callsOf('list')[1].respond(ok([task({ id: 'b', title: 'B' })]));
      await settle();
      assert.deepEqual(ids(), ['b']);
      assert.equal(messages().alert, NOT_FOUND, 'DR-1: the message survives its own refresh');
      assert.equal(q('#orphan').hidden, false);
      assert.equal(q('#list-status').textContent, '');
    });

    it('case (b): a 404, then the refresh fails: the message stays and #list-status shows the generic text with Retry', async () => {
      await boot([task({ id: 'a', title: 'A' })]);
      saveWith('a', 'typed');
      api.callsOf('edit')[0].respond(notFound());
      await settle();
      api.callsOf('list')[1].respond(fail('timeout'));
      await settle();
      assert.equal(messages().alert, NOT_FOUND, 'the generic text is not written to the alert');
      assert.equal(q('#list-status').textContent, GENERIC);
      assert.equal(q('#retry').hidden, false);
      assert.equal(q('#orphan-title').textContent, 'typed');
      assert.deepEqual(ids(), ['a'], 'the list already on screen stays');
    });

    it('a 404 on status: message, refresh, focus on #list-heading, no orphan panel', async () => {
      await boot([task({ id: 'a' }), task({ id: 'b' })]);
      button('a', 'toggle').click();
      api.callsOf('status')[0].respond(notFound());
      await settle();
      assert.equal(messages().alert, NOT_FOUND);
      assert.equal(api.callsOf('list').length, 2);
      assert.equal(document.activeElement, q('#list-heading'));
      assert.equal(q('#orphan').hidden, true);
      api.callsOf('list')[1].respond(ok([task({ id: 'b' })]));
      await settle();
      assert.deepEqual(ids(), ['b']);
      assert.equal(messages().alert, NOT_FOUND);
    });

    it('a 404 on delete: message, refresh, focus on #list-heading', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a' }), task({ id: 'b' })], confirm);
      button('a', 'delete').click();
      confirm.calls[0].resolve(true);
      await settle();
      api.callsOf('delete')[0].respond(notFound());
      await settle();
      assert.equal(messages().alert, NOT_FOUND);
      assert.equal(api.callsOf('list').length, 2);
      assert.equal(document.activeElement, q('#list-heading'));
    });

    it('Dismiss hides the orphan panel and moves focus to #list-heading', async () => {
      await boot([task({ id: 'a' })]);
      saveWith('a', 'typed');
      api.callsOf('edit')[0].respond(notFound());
      await settle();
      api.callsOf('list')[1].respond(ok([]));
      await settle();
      q('#orphan-dismiss').click();
      assert.equal(q('#orphan').hidden, true);
      assert.equal(q('#orphan-title').textContent, '');
      assert.equal(document.activeElement, q('#list-heading'));
      assert.equal(app.getState().orphan, null);
    });

    it('DR-14a: a refresh that drops the task being edited moves the draft to the orphan panel and announces the message when none is showing', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      button('a', 'edit').click();
      type(titleInput('a'), 'half typed');
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'b', title: 'B' })]));
      await settle();
      assert.deepEqual(ids(), ['b']);
      assert.equal(q('#orphan').hidden, false);
      assert.equal(q('#orphan-title').textContent, 'half typed');
      assert.equal(messages().alert, NOT_FOUND);
      assert.equal(document.activeElement, q('#orphan-heading'), 'DR-14b: focus lands on the orphan heading');
      assert.equal(app.getState().editing, null);
    });

    it('DR-14a: a message that is already showing is not replaced', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      button('b', 'toggle').click();
      api.callsOf('status')[0].respond(ok(task({ id: 'b', status: 'Completed' })));
      await settle();
      assert.equal(messages().status, 'Task marked as completed');
      button('a', 'edit').click();
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'b', status: 'Completed' })]));
      await settle();
      assert.equal(q('#orphan').hidden, false);
      assert.deepEqual(messages(), { status: 'Task marked as completed', alert: '' });
    });

    it('a 404 on a save that was in flight when a refresh already orphaned the draft leaves one panel with the typed text', async () => {
      await boot([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B' })]);
      saveWith('a', 'typed while saving');
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'b', title: 'B' })]));
      await settle();
      assert.equal(q('#orphan-title').textContent, 'typed while saving');
      api.callsOf('edit')[0].respond(notFound());
      await settle();
      assert.equal(q('#orphan-title').textContent, 'typed while saving');
      assert.equal(q('#orphan').hidden, false);
      assert.equal(messages().alert, NOT_FOUND);
      assert.equal(app.getState().editing, null);
    });

    it('case (f) / AC-22: a refresh while a row is in edit mode keeps the same input element, its value, focus and caret', async () => {
      await boot([task({ id: 'b', title: 'B' }), task({ id: 'a', title: 'A' })]);
      button('a', 'edit').click();
      type(titleInput('a'), 'hello world');
      const input = titleInput('a');
      input.focus();
      input.setSelectionRange(2, 5);
      app.load('refresh');
      // the edit row moves to the top: moving a focused node may blur it, so focus and caret must be restored
      api.callsOf('list')[1].respond(ok([task({ id: 'a', title: 'A' }), task({ id: 'b', title: 'B2' })]));
      await settle();
      assert.deepEqual(ids(), ['a', 'b'], 'backend order is followed');
      assert.ok(titleInput('a') === input, 'the very same input element');
      assert.equal(input.value, 'hello world');
      assert.equal(document.activeElement, input);
      assert.equal(input.selectionStart, 2);
      assert.equal(input.selectionEnd, 5);
      assert.deepEqual(app.getState().editing, { id: 'a', draftTitle: 'hello world', draftDescription: '' });
      assert.equal(rowOf('b').querySelector('h3').textContent, 'B2');
    });

    it('a refresh without any change keeps the edit row and its typed text as well', async () => {
      await boot([task({ id: 'a', title: 'A', description: 'D' })]);
      button('a', 'edit').click();
      type(descInput('a'), 'typed\nlines');
      const area = descInput('a');
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'a', title: 'A', description: 'D' })]));
      await settle();
      assert.ok(descInput('a') === area);
      assert.equal(area.value, 'typed\nlines');
    });
  });

  describe('delete (AC-9, AC-13, AC-11; DR-12, DR-14c)', () => {
    it('opens the confirmation with the task id, title and the row\'s Delete button as focus target', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a', title: 'Buy milk' })], confirm);
      button('a', 'delete').click();
      assert.equal(confirm.calls.length, 1);
      assert.equal(confirm.calls[0].options.id, 'a');
      assert.equal(confirm.calls[0].options.title, 'Buy milk');
      assert.ok(confirm.calls[0].options.returnFocus === button('a', 'delete'));
      assert.equal(api.callsOf('delete').length, 0, 'nothing is sent before the answer');
    });

    it('declining sends no request, changes nothing and focuses that row\'s Delete', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a' }), task({ id: 'b' })], confirm);
      button('b', 'delete').click();
      confirm.calls[0].resolve(false);
      await settle();
      assert.equal(api.callsOf('delete').length, 0);
      assert.deepEqual(ids(), ['a', 'b']);
      assert.equal(document.activeElement, button('b', 'delete'));
    });

    it('confirming sends one DELETE; the row stays (as "Deleting...") until 204; then it is removed and "Task deleted" is announced', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a' }), task({ id: 'b' })], confirm);
      button('a', 'delete').click();
      confirm.calls[0].resolve(true);
      await settle();
      assert.equal(api.callsOf('delete').length, 1);
      assert.deepEqual(api.callsOf('delete')[0].args, ['a']);
      assert.equal(button('a', 'delete').textContent, 'Deleting...');
      assert.equal(button('a', 'delete').getAttribute('aria-label'), 'Deleting...: Title');
      assert.equal(rowOf('a').getAttribute('aria-busy'), 'true');
      assert.equal(button('a', 'delete').disabled, true);
      assert.deepEqual(ids(), ['a', 'b']);
      api.callsOf('delete')[0].respond(ok(null));
      await settle();
      assert.deepEqual(ids(), ['b']);
      assert.equal(messages().status, 'Task deleted');
    });

    it('DR-14c: focus goes to the next row\'s Edit, else the previous row\'s, else #list-heading', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a' }), task({ id: 'b' }), task({ id: 'c' })], confirm);
      async function remove(id) {
        button(id, 'delete').click();
        confirm.calls[confirm.calls.length - 1].resolve(true);
        await settle();
        api.callsOf('delete')[api.callsOf('delete').length - 1].respond(ok(null));
        await settle();
      }
      await remove('b');
      assert.equal(document.activeElement, button('c', 'edit'), 'next row');
      await remove('c');
      assert.equal(document.activeElement, button('a', 'edit'), 'previous row');
      await remove('a');
      assert.equal(document.activeElement, q('#list-heading'), 'no row left');
      assert.equal(q('#list-status').textContent, 'No tasks yet');
    });

    it('DR-14c: if a refresh already removed the row while the DELETE was in flight, focus goes to #list-heading', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a' }), task({ id: 'b' })], confirm);
      button('a', 'delete').click();
      confirm.calls[0].resolve(true);
      await settle();
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'b' })]));
      await settle();
      assert.deepEqual(ids(), ['b']);
      api.callsOf('delete')[0].respond(ok(null));
      await settle();
      assert.equal(document.activeElement, q('#list-heading'));
      assert.deepEqual(ids(), ['b']);
    });

    for (const kind of ['network', 'timeout', 'unexpected', 'http']) {
      it(`case (e): a ${kind} failure on delete keeps the row, enables it, shows the generic message and focuses Delete`, async () => {
        const confirm = createFakeConfirm();
        await boot([task({ id: 'a' }), task({ id: 'b' })], confirm);
        button('a', 'delete').click();
        confirm.calls[0].resolve(true);
        await settle();
        api.callsOf('delete')[0].respond(fail(kind));
        await settle();
        assert.deepEqual(ids(), ['a', 'b']);
        assert.equal(button('a', 'delete').disabled, false);
        assert.equal(button('a', 'delete').textContent, 'Delete');
        assert.equal(messages().alert, GENERIC);
        assert.equal(document.activeElement, button('a', 'delete'));
        assert.equal(app.getState().tasks.length, 2, 'FR-10: removed only on 204');
      });
    }

    it('AC-13 / DR-12: a second Delete click while the dialog is open is ignored (one confirmation, one DELETE)', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a' })], confirm);
      button('a', 'delete').click();
      button('a', 'delete').click();
      assert.equal(confirm.calls.length, 1);
      confirm.calls[0].resolve(true);
      await settle();
      button('a', 'delete').click();
      assert.equal(api.callsOf('delete').length, 1);
      assert.equal(confirm.calls.length, 1, 'the pending row cannot be confirmed again');
    });

    it('DR-12: the guard is re-checked after the dialog: a task removed by a refresh while it was open is not deleted', async () => {
      const confirm = createFakeConfirm();
      await boot([task({ id: 'a' }), task({ id: 'b' })], confirm);
      button('a', 'delete').click();
      app.load('refresh');
      api.callsOf('list')[1].respond(ok([task({ id: 'b' })]));
      await settle();
      confirm.calls[0].resolve(true);
      await settle();
      assert.equal(api.callsOf('delete').length, 0, 'no DELETE for a task that is no longer in the list');
      assert.deepEqual(ids(), ['b']);
    });

    it('a confirm that rejects is treated as a decline', async () => {
      const confirm = () => Promise.reject(new Error('boom'));
      await boot([task({ id: 'a' })], confirm);
      button('a', 'delete').click();
      await settle();
      assert.equal(api.callsOf('delete').length, 0);
      assert.equal(app.getState().tasks.length, 1);
    });
  });

  describe('delete with the real dialog (AC-9, case (g) extension)', () => {
    const dialogEl = () => q('#confirm-dialog');

    it('Delete in the dialog sends exactly one DELETE, even if the row\'s Delete was clicked twice', async () => {
      await boot([task({ id: 'a', title: 'Buy milk' })]);
      button('a', 'delete').click();
      button('a', 'delete').click();
      assert.equal(dialogEl().open, true);
      assert.equal(q('#confirm-text').textContent, 'Delete "Buy milk"? This cannot be undone.');
      // The dialog resolves confirmDelete inside the click (no wait for the native close event); settle() only
      // drains the controller's promise continuations.
      q('#confirm-ok').click();
      await settle();
      assert.equal(api.callsOf('delete').length, 1);
      api.callsOf('delete')[0].respond(ok(null));
      await settle();
      assert.deepEqual(ids(), []);
    });

    it('Cancel sends no request and returns focus to that row\'s Delete', async () => {
      await boot([task({ id: 'a' })]);
      button('a', 'delete').click();
      q('#confirm-cancel').click();
      await settle();
      assert.equal(dialogEl().open, false);
      assert.equal(api.callsOf('delete').length, 0);
      assert.equal(document.activeElement, button('a', 'delete'));
    });

    it('Escape (cancel event, then close) sends no request', async () => {
      await boot([task({ id: 'a' })]);
      button('a', 'delete').click();
      dialogEl().dispatchEvent(new Event('cancel', { cancelable: true }));
      dialogEl().close();
      await settle();
      assert.equal(api.callsOf('delete').length, 0);
      assert.deepEqual(ids(), ['a']);
      assert.equal(document.activeElement, button('a', 'delete'));
    });
  });
});
