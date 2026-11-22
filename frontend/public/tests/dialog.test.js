// T-5 tests: dialog.js (AC-9 dialog part, case (g), DR-11, DR-12). Browser, HUMAN-REPORTED. ASCII only.
// Escape is simulated as the browser does it: a `cancel` event, then close() with the unchanged returnValue.
// The real Escape key and the native close order are UNVERIFIED by the agent (R-5); the human's browsers cover them.

import { describe, it, assert, beforeEach, afterEach } from './harness.js';
import { mountSandbox, settle } from './fakes.js';
import { createConfirmDialog } from '../js/dialog.js';

describe('dialog: confirmDelete', () => {
  let sandbox;
  let dialogEl;
  let dialog;
  let opener;
  let showCount;

  beforeEach(async () => {
    sandbox = await mountSandbox();
    dialogEl = sandbox.root.querySelector('#confirm-dialog');
    opener = document.createElement('button');
    opener.textContent = 'opener';
    sandbox.container.appendChild(opener);
    showCount = 0;
    const nativeShow = dialogEl.showModal.bind(dialogEl);
    dialogEl.showModal = () => {
      showCount++;
      nativeShow();
    };
    dialog = createConfirmDialog(dialogEl);
  });

  afterEach(() => {
    if (dialogEl.open) {
      dialogEl.close();
    }
    sandbox.remove();
  });

  function open(id = 'a', title = 'Buy milk') {
    return dialog.confirmDelete({ id, title, returnFocus: opener });
  }

  function track(promise) {
    const seen = [];
    promise.then((value) => seen.push(value));
    return seen;
  }

  const cancelButton = () => dialogEl.querySelector('#confirm-cancel');
  const deleteButton = () => dialogEl.querySelector('#confirm-ok');

  it('opens as a modal and puts the title in the text with textContent', async () => {
    open('a', '<b>x</b>&amp;');
    assert.equal(dialogEl.open, true);
    assert.equal(dialog.isOpen(), true);
    const text = dialogEl.querySelector('#confirm-text');
    assert.equal(text.textContent, 'Delete "<b>x</b>&amp;"? This cannot be undone.');
    assert.equal(text.children.length, 0);
  });

  it('initial focus is on Cancel', () => {
    open();
    assert.equal(document.activeElement, cancelButton());
  });

  // Every wait below is on the confirmDelete promise itself (the observable result), never on a timer tick or on
  // the timing of the native `close` event. A promise that never resolves ends in the harness timeout, which names
  // the test. settle() afterwards only drains continuations so that "resolved exactly once" can be checked.

  it('Cancel resolves false once, closes the dialog and returns focus to the caller\'s element', async () => {
    const pending = open();
    const seen = track(pending);
    cancelButton().click();
    assert.equal(await pending, false);
    await settle();
    assert.deepEqual(seen, [false]);
    assert.equal(dialogEl.open, false);
    assert.equal(dialog.isOpen(), false);
    assert.equal(document.activeElement, opener);
  });

  it('Delete resolves true once', async () => {
    const pending = open();
    const seen = track(pending);
    deleteButton().click();
    assert.equal(await pending, true);
    await settle();
    assert.deepEqual(seen, [true]);
    assert.equal(dialogEl.open, false);
    assert.equal(document.activeElement, opener);
  });

  it('case (g): Escape (cancel event, then close) resolves false exactly once', async () => {
    const pending = open();
    const seen = track(pending);
    const cancelEvent = new Event('cancel', { cancelable: true });
    dialogEl.dispatchEvent(cancelEvent);
    assert.equal(cancelEvent.defaultPrevented, false, 'cancel is not prevented');
    assert.equal(seen.length, 0, 'cancel alone resolves nothing');
    dialogEl.close();
    assert.equal(await pending, false);
    await settle();
    assert.deepEqual(seen, [false]);
    assert.equal(document.activeElement, opener);
  });

  it('close without a prior cancel resolves false once', async () => {
    const pending = open();
    const seen = track(pending);
    dialogEl.close();
    assert.equal(await pending, false);
    await settle();
    assert.deepEqual(seen, [false]);
  });

  it('returnValue is reset before each opening, so an earlier confirm does not leak', async () => {
    const first = open();
    deleteButton().click();
    assert.equal(await first, true);
    const second = open();
    assert.equal(dialogEl.returnValue, '');
    dialogEl.close();
    assert.equal(await second, false);
  });

  it('showModal is called once per opening', async () => {
    const first = open();
    open();
    assert.equal(showCount, 1);
    dialogEl.close();
    assert.equal(await first, false);
    open();
    assert.equal(showCount, 2);
  });

  it('a new confirmDelete right after a close() opens a fresh confirmation, the old one resolves false', async () => {
    const first = open('a', 'Buy milk');
    dialogEl.close();
    const second = open('b', 'Other');
    assert.equal(showCount, 2);
    assert.equal(await first, false);
    assert.equal(dialogEl.querySelector('#confirm-text').textContent, 'Delete "Other"? This cannot be undone.');
    const seen = track(second);
    dialogEl.dispatchEvent(new Event('close')); // a late close event of the first opening must not decide the second
    await settle();
    assert.equal(seen.length, 0, 'the second confirmation is still pending');
    assert.equal(dialog.isOpen(), true);
    deleteButton().click();
    assert.equal(await second, true);
  });

  it('DR-12 / case (g): a second confirmDelete for the same task returns the same promise', async () => {
    const first = open('a', 'Buy milk');
    const second = open('a', 'Buy milk');
    assert.ok(first === second, 'same pending promise');
    const seen = track(first);
    deleteButton().click();
    assert.equal(await first, true);
    await settle();
    assert.deepEqual(seen, [true]);
  });

  it('DR-12: a confirmDelete for a different task resolves false at once and leaves the open dialog unchanged', async () => {
    const pending = open('a', 'Buy milk');
    const first = track(pending);
    const other = await dialog.confirmDelete({ id: 'b', title: 'Other task', returnFocus: opener });
    assert.equal(other, false);
    assert.equal(dialogEl.open, true);
    assert.equal(showCount, 1);
    assert.equal(dialogEl.querySelector('#confirm-text').textContent, 'Delete "Buy milk"? This cannot be undone.');
    assert.equal(first.length, 0, 'the first confirmation is still pending');
    deleteButton().click();
    assert.equal(await pending, true);
    await settle();
    assert.deepEqual(first, [true]);
  });

  it('Tab and Shift+Tab cycle between Cancel and Delete', () => {
    open();
    function press(shiftKey) {
      const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true });
      document.activeElement.dispatchEvent(event);
      return event;
    }
    assert.equal(document.activeElement, cancelButton());
    assert.equal(press(false).defaultPrevented, true);
    assert.equal(document.activeElement, deleteButton());
    press(false);
    assert.equal(document.activeElement, cancelButton());
    press(true);
    assert.equal(document.activeElement, deleteButton());
    press(true);
    assert.equal(document.activeElement, cancelButton());
  });

  it('Tab from outside the two buttons goes to the first, Shift+Tab to the last', () => {
    open();
    document.activeElement.blur();
    dialogEl.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
    assert.equal(document.activeElement, cancelButton());
    document.activeElement.blur();
    dialogEl.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }),
    );
    assert.equal(document.activeElement, deleteButton());
  });

  it('a missing returnFocus does not break the resolution', async () => {
    const pending = dialog.confirmDelete({ id: 'a', title: 'x' });
    const seen = track(pending);
    cancelButton().click();
    assert.equal(await pending, false);
    await settle();
    assert.deepEqual(seen, [false]);
  });

  it('if showModal throws, the promise resolves false and the dialog is not left "open"', async () => {
    dialogEl.showModal = () => {
      throw new Error('InvalidStateError');
    };
    const result = await open();
    assert.equal(result, false);
    assert.equal(dialog.isOpen(), false);
  });
});
