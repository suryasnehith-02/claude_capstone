// C-12 test fakes: fetch, timer, announcer deferral, sandbox mount. No dependencies.

// Waits one macrotask, so every promise continuation queued so far has run.
export function settle() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function abortError() {
  return new DOMException('The operation was aborted.', 'AbortError');
}

function lowerKeys(headers) {
  const out = {};
  for (const key of Object.keys(headers || {})) {
    out[key.toLowerCase()] = headers[key];
  }
  return out;
}

// ---------- response specs (scripted by tests) ----------

export function jsonResponse(status, value) {
  return { status, body: JSON.stringify(value), contentType: 'application/json' };
}

export function errorResponse(status, code, message = 'backend text', details) {
  const error = { code, message };
  if (details !== undefined) {
    error.details = details;
  }
  return jsonResponse(status, { error });
}

export function task(overrides = {}) {
  return {
    id: 'id-1',
    title: 'Title',
    description: '',
    status: 'Pending',
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z',
    ...overrides,
  };
}

// ---------- fake fetch ----------
// A scripted spec is consumed per call, in order. With an empty script the call is held until the test calls
// `call.respond(spec)`. Spec forms:
//   { status, body = '', contentType }     response whose text() resolves with body
//   { status, body, hangText: true }       headers arrive; text() stays pending until call.finishText(body) or abort
//   { status, textReject: error }          headers arrive; text() rejects
//   { reject: error } | { network: true }  fetch rejects (network: TypeError "Failed to fetch")
//   { hold: true }                         keep the call pending
// Aborting the request signal rejects a pending fetch and a pending text() with an AbortError.
export function createFakeFetch() {
  const calls = [];
  const script = [];

  function fakeFetch(url, init = {}) {
    const call = {
      url: String(url),
      method: init.method || 'GET',
      headers: lowerKeys(init.headers),
      body: init.body === undefined ? null : init.body,
      signal: init.signal || null,
      state: 'pending',
    };
    calls.push(call);
    return new Promise((resolve, reject) => {
      let rejectText = null;

      function makeResponse(spec) {
        const status = spec.status;
        return {
          status,
          ok: status >= 200 && status < 300,
          headers: { get: (name) => (name.toLowerCase() === 'content-type' ? spec.contentType || null : null) },
          text: () => {
            if (spec.textReject) {
              return Promise.reject(spec.textReject);
            }
            if (spec.hangText) {
              return new Promise((resolveText, rejectIt) => {
                call.finishText = (body) => {
                  call.state = 'done';
                  resolveText(body);
                };
                rejectText = rejectIt;
              });
            }
            return Promise.resolve(spec.body === undefined ? '' : spec.body);
          },
        };
      }

      call.respond = (spec) => {
        if (call.state !== 'pending') {
          throw new Error('fake fetch: call already settled');
        }
        if (spec.hold) {
          return;
        }
        call.state = 'responded';
        if (spec.network) {
          reject(new TypeError('Failed to fetch'));
        } else if (spec.reject) {
          reject(spec.reject);
        } else {
          resolve(makeResponse(spec));
          if (!spec.hangText) {
            call.state = 'done';
          }
        }
      };

      if (call.signal) {
        call.signal.addEventListener('abort', () => {
          if (call.state === 'pending') {
            call.state = 'aborted';
            reject(abortError());
          } else if (rejectText) {
            call.state = 'aborted';
            rejectText(abortError());
          }
        });
      }

      if (script.length > 0) {
        call.respond(script.shift());
      }
    });
  }

  fakeFetch.calls = calls;
  fakeFetch.enqueue = (...specs) => {
    script.push(...specs);
  };
  return fakeFetch;
}

// ---------- fake timer ----------
// setTimer / clearTimer are plain functions (they can be passed around unbound). fire() runs the oldest active
// timer, like the 10 s abort timer elapsing.
export function createFakeTimer() {
  const timers = [];
  let nextId = 1;
  return {
    timers,
    setTimer(fn, ms) {
      const timer = { id: nextId++, fn, ms, state: 'active' };
      timers.push(timer);
      return timer.id;
    },
    clearTimer(id) {
      const timer = timers.find((candidate) => candidate.id === id);
      if (timer && timer.state === 'active') {
        timer.state = 'cleared';
      }
    },
    active() {
      return timers.filter((timer) => timer.state === 'active');
    },
    fire() {
      const timer = timers.find((candidate) => candidate.state === 'active');
      if (!timer) {
        throw new Error('fake timer: no active timer to fire');
      }
      timer.state = 'fired';
      timer.fn();
    },
  };
}

// ---------- manual announcer flush ----------
// `defer` queues callbacks (it returns nothing, like a queue-style defer); flush() runs them oldest first.
export function createManualDefer() {
  const queue = [];
  return {
    defer(fn) {
      queue.push(fn);
    },
    flush() {
      while (queue.length > 0) {
        queue.shift()();
      }
    },
    size() {
      return queue.length;
    },
  };
}

// ---------- fake API (the object createApp receives as `api`) ----------
// Each call is recorded in `calls` ({ op, args, state }) and returns a pending promise. A queued result for that
// operation (api.enqueue('list', result)) answers the call at once; otherwise the call is held until the test
// calls call.respond(result). `ok(data)` and `fail(kind, extra)` build Result objects as api.js returns them.
export const ok = (data) => ({ ok: true, data });
export const fail = (kind, extra = {}) => ({ ok: false, kind, ...extra });

export function createFakeApi() {
  const calls = [];
  const queues = { list: [], add: [], edit: [], status: [], delete: [] };

  function make(op, args) {
    const call = { op, args, state: 'pending' };
    calls.push(call);
    return new Promise((resolve) => {
      call.respond = (result) => {
        if (call.state !== 'pending') {
          throw new Error('fake api: call already answered');
        }
        call.state = 'done';
        resolve(result);
      };
      if (queues[op].length > 0) {
        call.respond(queues[op].shift());
      }
    });
  }

  return {
    calls,
    callsOf: (op) => calls.filter((call) => call.op === op),
    enqueue(op, ...results) {
      queues[op].push(...results);
    },
    listTasks: () => make('list', []),
    createTask: (input) => make('add', [input]),
    replaceTask: (id, input) => make('edit', [id, input]),
    setStatus: (id, status) => make('status', [id, status]),
    deleteTask: (id) => make('delete', [id]),
  };
}

// ---------- sandbox mount ----------
// Copies <main id="app"> from the real index.html (one source of truth for the markup and the ids) into a
// container on the test page, so app tests mount against the real structure. Scripts in the copy do not run.
let indexHtmlPromise = null;

function loadIndexHtml() {
  if (!indexHtmlPromise) {
    const url = new URL('../index.html', import.meta.url);
    indexHtmlPromise = fetch(url).then((response) => {
      if (!response.ok) {
        throw new Error(`could not load ${url.pathname}: ${response.status}`);
      }
      return response.text();
    });
  }
  return indexHtmlPromise;
}

export async function mountSandbox() {
  const text = await loadIndexHtml();
  const parsed = new DOMParser().parseFromString(text, 'text/html');
  const source = parsed.getElementById('app');
  if (!source) {
    throw new Error('index.html has no #app element');
  }
  const container = document.createElement('div');
  container.setAttribute('data-sandbox', '');
  container.appendChild(document.importNode(source, true));
  document.body.appendChild(container);
  return {
    container,
    root: container.querySelector('#app'),
    remove() {
      if (document.activeElement && container.contains(document.activeElement)) {
        document.activeElement.blur();
      }
      container.remove();
    },
  };
}
