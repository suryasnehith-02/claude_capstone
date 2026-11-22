// C-10 Message area (architecture 8.2, I-9). Imports nothing.
//
// announce(kind, text): empties both live regions, then, through the injected `defer`, writes `text` with
// textContent into #msg-status (kind 'success') or #msg-alert (kind 'error'). Emptying first makes an identical
// repeated message audible again.
//
// DR-13a: every announce() bumps a sequence number and the deferred callback writes only if its number is still the
// current one. A newer announce (or clear) therefore turns an older queued write into a no-op, whatever `defer`
// is injected (default setTimeout, or the queue used by the self-test page). The default `defer` fires also in a
// background tab; its ~50 ms delay lets the emptied region be seen by assistive technology before the text arrives.

export function createAnnouncer({ statusEl, alertEl, defer = (fn) => setTimeout(fn, 50) }) {
  let sequence = 0;

  function empty() {
    statusEl.textContent = '';
    alertEl.textContent = '';
  }

  function announce(kind, text) {
    const mine = ++sequence;
    empty();
    defer(() => {
      if (mine !== sequence) {
        return;
      }
      (kind === 'error' ? alertEl : statusEl).textContent = text;
    });
  }

  // Empties both regions now and cancels any write still queued.
  function clear() {
    sequence++;
    empty();
  }

  return { announce, clear };
}
