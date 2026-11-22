// C-11 Confirm dialog (architecture 8.2, D-5, I-7; design review DR-11, DR-12). Imports nothing.
//
// createConfirmDialog(dialogEl) -> { confirmDelete({ id, title, returnFocus }) -> Promise<boolean>, isOpen() }
//   - Native <dialog> opened with showModal(); returnValue is reset to '' before each opening.
//   - The promise resolves exactly once, through one idempotent finish(confirmed). It does not depend on a single
//     event: "Cancel" and "Delete" call close('cancel') / close('confirm') and then finish at once (no event wait);
//     any other closing (Escape, which the browser turns into close(); a programmatic close()) is noticed by a
//     MutationObserver on the `open` attribute (a microtask, not a queued task) and, as a second path, by the
//     native `close` event. Whichever comes first wins; the others find nothing pending and do nothing.
//     confirmed is (returnValue === 'confirm'); Escape and any plain close() leave returnValue '' = decline.
//   - The `cancel` event (Escape) is neither listened to nor prevented: the browser then closes the dialog.
//   - DR-12 re-entry: a second confirmDelete while the dialog is open returns the SAME pending promise only for
//     the same task id. For a different id it resolves false at once and leaves the open dialog unchanged
//     (a task the user never confirmed is never deleted). showModal() is called once per opening. If the dialog
//     was closed but its closing has not been noticed yet, that confirmation is finished first, then a new one opens.
//   - Initial focus is on Cancel; Tab and Shift+Tab cycle between the two buttons; when the confirmation finishes,
//     focus goes to the element the caller supplied (`returnFocus`).
//   - The title is inserted with textContent only.
// Expected markup (index.html): #confirm-text, #confirm-cancel, #confirm-ok inside the <dialog>.

export function createConfirmDialog(dialogEl) {
  const textEl = dialogEl.querySelector('#confirm-text');
  const cancelBtn = dialogEl.querySelector('#confirm-cancel');
  const okBtn = dialogEl.querySelector('#confirm-ok');
  const focusOrder = [cancelBtn, okBtn];
  let current = null; // { id, promise, resolve, returnFocus } while a confirmation is open

  function finish(confirmed) {
    if (!current) {
      return;
    }
    const done = current;
    current = null;
    if (done.returnFocus && typeof done.returnFocus.focus === 'function') {
      done.returnFocus.focus();
    }
    done.resolve(confirmed);
  }

  // Called when the dialog is seen closed (observer, `close` event, button). A newer opening that is already
  // showing again means this notification is stale and must not decide that newer confirmation.
  function onClosed() {
    if (dialogEl.open) {
      return;
    }
    finish(dialogEl.returnValue === 'confirm');
  }

  function closeWith(value) {
    dialogEl.close(value);
    onClosed();
  }

  cancelBtn.addEventListener('click', () => closeWith('cancel'));
  okBtn.addEventListener('click', () => closeWith('confirm'));
  dialogEl.addEventListener('close', onClosed);
  new MutationObserver(onClosed).observe(dialogEl, { attributes: true, attributeFilter: ['open'] });

  dialogEl.addEventListener('keydown', (event) => {
    if (event.key !== 'Tab') {
      return;
    }
    event.preventDefault();
    const index = focusOrder.indexOf(dialogEl.ownerDocument.activeElement);
    const step = event.shiftKey ? -1 : 1;
    const next = index === -1 ? (step === 1 ? 0 : focusOrder.length - 1) : (index + step + focusOrder.length) % focusOrder.length;
    focusOrder[next].focus();
  });

  function confirmDelete({ id, title, returnFocus }) {
    if (current && !dialogEl.open) {
      onClosed();
    }
    if (current) {
      return current.id === id ? current.promise : Promise.resolve(false);
    }
    dialogEl.returnValue = '';
    textEl.textContent = `Delete "${title}"? This cannot be undone.`;
    let resolve;
    const promise = new Promise((resolveFn) => {
      resolve = resolveFn;
    });
    current = { id, promise, resolve, returnFocus };
    try {
      dialogEl.showModal();
    } catch (error) {
      current = null;
      return Promise.resolve(false);
    }
    cancelBtn.focus();
    return promise;
  }

  return { confirmDelete, isOpen: () => current !== null };
}
