// C-5 API client (architecture 7.1, 7.2, D-7, I-4). Imports nothing, touches no DOM.
//
// Requests are built exactly as the 7.1 table: relative paths (same origin, so the base path defaults to ''),
// encodeURIComponent(id), JSON bodies with only the given title/description (callers pass the trimmed values
// from validateTask), a Content-Type header only when there is a body.
//
// Every call returns Promise<Result> and never rejects. Classification order (7.2):
//   step 1  fetch() or text() rejects: the 10 s abort timer fired -> {kind:'timeout'}, anything else -> {kind:'network'}
//   step 2  204 on delete -> {ok:true, data:null}; 204 on any other operation -> {kind:'unexpected'}
//   step 3  2xx: right code for the operation, JSON object with `data`, data passes the shape check -> {ok:true, data};
//           anything else in 2xx -> {kind:'unexpected'}
//   step 4  non-2xx with {"error":{"code":string}}: 400 VALIDATION_ERROR -> 'validation' (with filtered details),
//           404 TODO_NOT_FOUND -> 'notFound', any other code or status -> 'http'
//   step 5  non-2xx without a parseable envelope (HTML, text, empty body) -> {kind:'http'}
// `status` and `code` ride along on http/validation/notFound results for diagnosis only; messages.js never shows them.
//
// The abort timer is armed before fetch and cleared in `finally`, only after text() has settled, so a stalled body
// also ends as a timeout. The 10 s limit itself is NOT_RUN by H-2; only this logic is tested.

const DEFAULT_TIMEOUT_MS = 10000;
const STATUSES = ['Pending', 'Completed'];

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isTask(value) {
  return (
    isObject(value) &&
    typeof value.id === 'string' &&
    value.id !== '' &&
    typeof value.title === 'string' &&
    typeof value.description === 'string' &&
    STATUSES.includes(value.status)
  );
}

function isValidData(operation, data) {
  if (operation === 'list') {
    return Array.isArray(data) && data.every(isTask);
  }
  return isTask(data);
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch (error) {
    return undefined;
  }
}

const EXPECTED_SUCCESS_STATUS = { list: 200, add: 201, edit: 200, status: 200 };

function classifySuccess(operation, status, text) {
  if (status === 204) {
    return operation === 'delete' ? { ok: true, data: null } : { ok: false, kind: 'unexpected', status };
  }
  if (operation === 'delete' || status !== EXPECTED_SUCCESS_STATUS[operation]) {
    return { ok: false, kind: 'unexpected', status };
  }
  const body = parseJson(text);
  if (!isObject(body) || !('data' in body) || !isValidData(operation, body.data)) {
    return { ok: false, kind: 'unexpected', status };
  }
  return { ok: true, data: body.data };
}

function usableDetails(details) {
  if (!Array.isArray(details)) {
    return [];
  }
  return details
    .filter((entry) => isObject(entry) && typeof entry.field === 'string' && typeof entry.message === 'string')
    .map((entry) => ({ field: entry.field, message: entry.message }));
}

function classifyFailure(status, text) {
  const body = parseJson(text);
  if (!isObject(body) || !isObject(body.error) || typeof body.error.code !== 'string') {
    return { ok: false, kind: 'http', status, code: null, details: [] };
  }
  const code = body.error.code;
  if (status === 400 && code === 'VALIDATION_ERROR') {
    return { ok: false, kind: 'validation', status, code, details: usableDetails(body.error.details) };
  }
  if (status === 404 && code === 'TODO_NOT_FOUND') {
    return { ok: false, kind: 'notFound', status, code, details: [] };
  }
  return { ok: false, kind: 'http', status, code, details: usableDetails(body.error.details) };
}

function classify(operation, status, text) {
  if (typeof status === 'number' && status >= 200 && status < 300) {
    return classifySuccess(operation, status, text);
  }
  return classifyFailure(status, text);
}

export function createApiClient({
  fetchFn = (input, init) => globalThis.fetch(input, init),
  setTimer = (fn, ms) => globalThis.setTimeout(fn, ms),
  clearTimer = (handle) => globalThis.clearTimeout(handle),
  timeoutMs = DEFAULT_TIMEOUT_MS,
  basePath = '',
} = {}) {
  const base = String(basePath).replace(/\/+$/, '');

  async function send(operation, method, path, payload) {
    let timedOut = false;
    let handle;
    try {
      const controller = new AbortController();
      handle = setTimer(() => {
        timedOut = true;
        controller.abort();
      }, timeoutMs);

      const headers = { Accept: 'application/json' };
      const init = { method, headers, signal: controller.signal };
      if (payload !== undefined) {
        headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(payload);
      }

      let response;
      let text;
      try {
        response = await fetchFn(base + path, init);
        text = await response.text();
      } catch (error) {
        return { ok: false, kind: timedOut ? 'timeout' : 'network' };
      }
      return classify(operation, response.status, text);
    } catch (error) {
      return { ok: false, kind: 'unexpected' };
    } finally {
      clearTimer(handle);
    }
  }

  const taskPath = (id) => `/todos/${encodeURIComponent(id)}`;
  const fields = (input) => ({ title: input && input.title, description: input && input.description });

  return {
    listTasks: () => send('list', 'GET', '/todos'),
    createTask: (input) => send('add', 'POST', '/todos', fields(input)),
    replaceTask: (id, input) => send('edit', 'PUT', taskPath(id), fields(input)),
    setStatus: (id, status) => send('status', 'PATCH', `${taskPath(id)}/status`, { status }),
    deleteTask: (id) => send('delete', 'DELETE', taskPath(id)),
  };
}
