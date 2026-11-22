// T-5 tests: announcer.js (DR-13a, AC-14) and the markup contract of index.html (architecture 6.2).
// Browser, HUMAN-REPORTED. ASCII only.

import { describe, it, assert, beforeEach, afterEach } from './harness.js';
import { createManualDefer, mountSandbox } from './fakes.js';
import { createAnnouncer } from '../js/announcer.js';

function regions() {
  const statusEl = document.createElement('p');
  const alertEl = document.createElement('p');
  return { statusEl, alertEl };
}

describe('announcer: regions, repeat text, sequence number', () => {
  it('success is written to the status region and the alert region stays empty', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('success', 'Task added');
    assert.equal(statusEl.textContent, '', 'nothing is written before the deferred flush');
    manual.flush();
    assert.equal(statusEl.textContent, 'Task added');
    assert.equal(alertEl.textContent, '');
  });

  it('error is written to the alert region and the status region stays empty', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('error', 'Something went wrong. Please try again.');
    manual.flush();
    assert.equal(alertEl.textContent, 'Something went wrong. Please try again.');
    assert.equal(statusEl.textContent, '');
  });

  it('both regions are emptied first, before the deferred write', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('success', 'One');
    manual.flush();
    alertEl.textContent = 'old alert';
    announcer.announce('success', 'Two');
    assert.equal(statusEl.textContent, '');
    assert.equal(alertEl.textContent, '');
    manual.flush();
    assert.equal(statusEl.textContent, 'Two');
  });

  it('an identical repeated message is written again (the region is empty in between)', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('error', 'Same text');
    manual.flush();
    assert.equal(alertEl.textContent, 'Same text');
    announcer.announce('error', 'Same text');
    assert.equal(alertEl.textContent, '', 'emptied again before the repeat');
    manual.flush();
    assert.equal(alertEl.textContent, 'Same text');
  });

  it('DR-13a: with a queue-style defer, a newer announce makes the older queued write a no-op', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('success', 'Older');
    announcer.announce('error', 'Newer');
    assert.equal(manual.size(), 2);
    manual.flush();
    assert.equal(statusEl.textContent, '');
    assert.equal(alertEl.textContent, 'Newer');
  });

  it('DR-13a: the same holds when the newer message is a success and the older an error', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('error', 'Older');
    announcer.announce('success', 'Newer');
    manual.flush();
    assert.equal(alertEl.textContent, '');
    assert.equal(statusEl.textContent, 'Newer');
  });

  it('DR-13a: three announces in a row leave only the last message', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('success', 'A');
    announcer.announce('success', 'B');
    announcer.announce('success', 'C');
    manual.flush();
    assert.equal(statusEl.textContent, 'C');
  });

  it('clear() empties both regions and cancels a queued write', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('error', 'Queued');
    announcer.clear();
    manual.flush();
    assert.equal(alertEl.textContent, '');
    assert.equal(statusEl.textContent, '');
  });

  it('the text is set with textContent, never as markup', () => {
    const { statusEl, alertEl } = regions();
    const manual = createManualDefer();
    const announcer = createAnnouncer({ statusEl, alertEl, defer: manual.defer });
    announcer.announce('error', '<b>bold</b>');
    manual.flush();
    assert.equal(alertEl.textContent, '<b>bold</b>');
    assert.equal(alertEl.children.length, 0);
  });

  it('the default defer is a short timer: empty at once, written shortly after', async () => {
    const { statusEl, alertEl } = regions();
    const announcer = createAnnouncer({ statusEl, alertEl });
    announcer.announce('success', 'Timed');
    assert.equal(statusEl.textContent, '');
    await new Promise((resolve) => setTimeout(resolve, 300));
    assert.equal(statusEl.textContent, 'Timed');
  });
});

describe('page shell: index.html markup (architecture 6.2)', () => {
  let sandbox;
  let page;

  beforeEach(async () => {
    sandbox = await mountSandbox();
    const response = await fetch(new URL('../index.html', import.meta.url));
    page = new DOMParser().parseFromString(await response.text(), 'text/html');
  });

  afterEach(() => {
    sandbox.remove();
  });

  it('has every element id of 6.2, in the specified order', () => {
    const ids = Array.from(sandbox.root.querySelectorAll('[id]')).map((element) => element.id);
    const expectedOrder = [
      'msg-status',
      'msg-alert',
      'add-form',
      'add-title',
      'add-title-error',
      'add-description',
      'add-description-error',
      'add-submit',
      'list-heading',
      'list-status',
      'retry',
      'orphan',
      'orphan-heading',
      'orphan-title',
      'orphan-description',
      'orphan-dismiss',
      'task-list',
      'confirm-dialog',
      'confirm-title',
      'confirm-text',
      'confirm-cancel',
      'confirm-ok',
    ];
    let last = -1;
    for (const id of expectedOrder) {
      const position = ids.indexOf(id);
      assert.ok(position !== -1, `missing #${id}`);
      assert.ok(position > last, `#${id} is out of order`);
      last = position;
    }
    assert.equal(sandbox.root.id, 'app');
    assert.equal(sandbox.root.firstElementChild.tagName, 'H1');
  });

  it('the live regions have the roles of 6.2', () => {
    const status = sandbox.root.querySelector('#msg-status');
    const alert = sandbox.root.querySelector('#msg-alert');
    assert.equal(status.getAttribute('role'), 'status');
    assert.equal(status.getAttribute('aria-live'), 'polite');
    assert.equal(alert.getAttribute('role'), 'alert');
    assert.equal(sandbox.root.querySelector('#list-status').getAttribute('role'), 'status');
  });

  it('the add form is always visible, novalidate, and has no maxlength anywhere (D-6)', () => {
    const form = sandbox.root.querySelector('#add-form');
    assert.equal(form.hidden, false);
    assert.ok(form.hasAttribute('novalidate'));
    assert.equal(sandbox.root.querySelectorAll('[maxlength]').length, 0);
    assert.equal(sandbox.root.querySelector('#add-title').getAttribute('aria-describedby'), 'add-title-error');
    assert.equal(
      sandbox.root.querySelector('#add-description').getAttribute('aria-describedby'),
      'add-description-error',
    );
  });

  it('retry and the orphan panel start hidden; both focus targets have tabindex -1 (DR-14b)', () => {
    assert.equal(sandbox.root.querySelector('#retry').hidden, true);
    assert.equal(sandbox.root.querySelector('#orphan').hidden, true);
    assert.equal(sandbox.root.querySelector('#list-heading').getAttribute('tabindex'), '-1');
    assert.equal(sandbox.root.querySelector('#orphan-heading').getAttribute('tabindex'), '-1');
  });

  it('has a data: favicon, loads js/main.js as a module, and the dialog has its labels', () => {
    assert.equal(page.querySelector('link[rel="icon"]').getAttribute('href'), 'data:,');
    const script = page.querySelector('script[type="module"]');
    assert.equal(script.getAttribute('src'), 'js/main.js');
    const dialog = sandbox.root.querySelector('#confirm-dialog');
    assert.equal(dialog.tagName, 'DIALOG');
    assert.equal(dialog.getAttribute('aria-labelledby'), 'confirm-title');
    assert.equal(dialog.getAttribute('aria-describedby'), 'confirm-text');
    assert.equal(dialog.open, false);
  });

  it('has a noscript note and contains no inline script or inline event handler', () => {
    assert.ok(page.querySelector('noscript'));
    assert.equal(page.querySelectorAll('script:not([src])').length, 0);
    assert.equal(page.querySelectorAll('[onclick],[onload],[onerror],[onsubmit]').length, 0);
  });
});
