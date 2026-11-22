// T-4 tests: api.js (requests of architecture 7.1, classification of 7.2). Fake fetch and fake timer; no network.
// Browser, HUMAN-REPORTED. The 10 s limit itself is NOT_RUN by H-2: the fake timer is fired by hand.
// ASCII only.

import { describe, it, assert } from './harness.js';
import { createFakeFetch, createFakeTimer, errorResponse, jsonResponse, settle, task } from './fakes.js';
import { createApiClient } from '../js/api.js';

function setup(options = {}) {
  const fetchFn = createFakeFetch();
  const timer = createFakeTimer();
  const client = createApiClient({
    fetchFn,
    setTimer: timer.setTimer,
    clearTimer: timer.clearTimer,
    ...options,
  });
  return { fetchFn, timer, client };
}

const OPERATIONS = {
  list: (client) => client.listTasks(),
  add: (client) => client.createTask({ title: 'A', description: '' }),
  edit: (client) => client.replaceTask('a1', { title: 'A', description: '' }),
  status: (client) => client.setStatus('a1', 'Completed'),
  delete: (client) => client.deleteTask('a1'),
};

async function exec(operation, spec) {
  const { fetchFn, client } = setup();
  fetchFn.enqueue(spec);
  return OPERATIONS[operation](client);
}

const TASK = task({ id: 'a1', title: 'T', description: 'D' });

describe('api: requests match architecture 7.1', () => {
  it('list: GET /todos, Accept only, no body', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue(jsonResponse(200, { data: [] }));
    await client.listTasks();
    const call = fetchFn.calls[0];
    assert.equal(call.method, 'GET');
    assert.equal(call.url, '/todos');
    assert.deepEqual(call.headers, { accept: 'application/json' });
    assert.equal(call.body, null);
  });

  it('add: POST /todos with Content-Type and the two fields', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue(jsonResponse(201, { data: TASK }));
    await client.createTask({ title: 'A', description: 'B' });
    const call = fetchFn.calls[0];
    assert.equal(call.method, 'POST');
    assert.equal(call.url, '/todos');
    assert.deepEqual(call.headers, { accept: 'application/json', 'content-type': 'application/json' });
    assert.equal(call.body, '{"title":"A","description":"B"}');
  });

  it('add sends only title and description, even when the input has other keys', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue(jsonResponse(201, { data: TASK }));
    await client.createTask({ title: 'A', description: '', status: 'Completed', id: 'x' });
    assert.equal(fetchFn.calls[0].body, '{"title":"A","description":""}');
  });

  it('edit: PUT /todos/{id} sends both fields', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue(jsonResponse(200, { data: TASK }));
    await client.replaceTask('a1', { title: 'New', description: '' });
    const call = fetchFn.calls[0];
    assert.equal(call.method, 'PUT');
    assert.equal(call.url, '/todos/a1');
    assert.deepEqual(call.headers, { accept: 'application/json', 'content-type': 'application/json' });
    assert.equal(call.body, '{"title":"New","description":""}');
  });

  it('the id is path-encoded with encodeURIComponent', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue(jsonResponse(200, { data: TASK }), { status: 204 }, jsonResponse(200, { data: TASK }));
    await client.replaceTask('a/b c?', { title: 'A', description: '' });
    await client.deleteTask('a/b c?');
    await client.setStatus('a/b c?', 'Pending');
    assert.equal(fetchFn.calls[0].url, '/todos/a%2Fb%20c%3F');
    assert.equal(fetchFn.calls[1].url, '/todos/a%2Fb%20c%3F');
    assert.equal(fetchFn.calls[2].url, '/todos/a%2Fb%20c%3F/status');
  });

  it('status: PATCH /todos/{id}/status with exactly {"status":...}', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue(jsonResponse(200, { data: TASK }), jsonResponse(200, { data: TASK }));
    await client.setStatus('a1', 'Completed');
    await client.setStatus('a1', 'Pending');
    assert.equal(fetchFn.calls[0].method, 'PATCH');
    assert.equal(fetchFn.calls[0].url, '/todos/a1/status');
    assert.equal(fetchFn.calls[0].body, '{"status":"Completed"}');
    assert.equal(fetchFn.calls[1].body, '{"status":"Pending"}');
    assert.deepEqual(fetchFn.calls[0].headers, { accept: 'application/json', 'content-type': 'application/json' });
  });

  it('delete: DELETE /todos/{id}, no Content-Type, no body', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue({ status: 204 });
    await client.deleteTask('a1');
    const call = fetchFn.calls[0];
    assert.equal(call.method, 'DELETE');
    assert.equal(call.url, '/todos/a1');
    assert.deepEqual(call.headers, { accept: 'application/json' });
    assert.equal(call.body, null);
  });

  it('the base path defaults to same-origin and is configurable', async () => {
    const { fetchFn, client } = setup({ basePath: '/api/' });
    fetchFn.enqueue(jsonResponse(200, { data: [] }));
    await client.listTasks();
    assert.equal(fetchFn.calls[0].url, '/api/todos');
  });

  it('every request carries an abort signal', async () => {
    const { fetchFn, client } = setup();
    fetchFn.enqueue(jsonResponse(200, { data: [] }));
    await client.listTasks();
    assert.ok(fetchFn.calls[0].signal instanceof AbortSignal);
  });
});

describe('api: classification step 3, success', () => {
  it('list 200 with tasks and with an empty array', async () => {
    assert.deepEqual(await exec('list', jsonResponse(200, { data: [TASK] })), { ok: true, data: [TASK] });
    assert.deepEqual(await exec('list', jsonResponse(200, { data: [] })), { ok: true, data: [] });
  });

  it('add 201, edit 200 and status 200 return the task', async () => {
    assert.deepEqual(await exec('add', jsonResponse(201, { data: TASK })), { ok: true, data: TASK });
    assert.deepEqual(await exec('edit', jsonResponse(200, { data: TASK })), { ok: true, data: TASK });
    assert.deepEqual(await exec('status', jsonResponse(200, { data: TASK })), { ok: true, data: TASK });
  });

  it('delete 204 (step 2) gives ok with null data', async () => {
    assert.deepEqual(await exec('delete', { status: 204 }), { ok: true, data: null });
  });
});

describe('api: classification, unexpected 2xx (AC-20)', () => {
  it('204 on list, add, edit and status is unexpected', async () => {
    for (const operation of ['list', 'add', 'edit', 'status']) {
      const result = await exec(operation, { status: 204 });
      assert.equal(result.ok, false, operation);
      assert.equal(result.kind, 'unexpected', operation);
    }
  });

  it('a wrong success code for the operation is unexpected', async () => {
    assert.equal((await exec('add', jsonResponse(200, { data: TASK }))).kind, 'unexpected');
    assert.equal((await exec('list', jsonResponse(201, { data: [] }))).kind, 'unexpected');
    assert.equal((await exec('edit', jsonResponse(201, { data: TASK }))).kind, 'unexpected');
    assert.equal((await exec('status', jsonResponse(202, { data: TASK }))).kind, 'unexpected');
  });

  it('delete answered with 200 (not 204) is unexpected', async () => {
    assert.equal((await exec('delete', jsonResponse(200, { data: null }))).kind, 'unexpected');
  });

  it('a non-JSON 2xx body and an empty 2xx body are unexpected', async () => {
    assert.equal((await exec('list', { status: 200, body: '<html>hi</html>', contentType: 'text/html' })).kind, 'unexpected');
    assert.equal((await exec('list', { status: 200, body: '' })).kind, 'unexpected');
    assert.equal((await exec('add', { status: 201, body: '' })).kind, 'unexpected');
  });

  it('a wrong shape is unexpected', async () => {
    const shapes = [
      ['list', { data: TASK }],
      ['list', { data: [TASK, { id: 'x' }] }],
      ['list', { data: 'nope' }],
      ['list', {}],
      ['list', []],
      ['list', null],
      ['add', { data: [] }],
      ['add', { data: { ...TASK, status: 'Done' } }],
      ['add', { data: { ...TASK, id: '' } }],
      ['add', { data: { ...TASK, id: 5 } }],
      ['add', { data: { ...TASK, description: null } }],
      ['add', { data: { ...TASK, title: 7 } }],
      ['add', { data: null }],
      ['edit', { data: { title: 'only title' } }],
    ];
    for (const [operation, body] of shapes) {
      const status = operation === 'add' ? 201 : 200;
      const result = await exec(operation, jsonResponse(status, body));
      assert.equal(result.ok, false, `${operation} ${JSON.stringify(body)}`);
      assert.equal(result.kind, 'unexpected', `${operation} ${JSON.stringify(body)}`);
    }
  });

  it('extra fields on a task are accepted', async () => {
    const result = await exec('add', jsonResponse(201, { data: { ...TASK, extra: 1 } }));
    assert.equal(result.ok, true);
  });
});

describe('api: classification step 4 and 5, failures', () => {
  it('400 VALIDATION_ERROR keeps only details with string field and message', async () => {
    const details = [
      { field: 'title', message: 'Title is required' },
      { field: 5, message: 'bad field' },
      { field: 'description' },
      null,
      'text',
      { field: 'status', message: 'bad status' },
    ];
    const result = await exec('add', errorResponse(400, 'VALIDATION_ERROR', 'raw', details));
    assert.equal(result.ok, false);
    assert.equal(result.kind, 'validation');
    assert.deepEqual(result.details, [
      { field: 'title', message: 'Title is required' },
      { field: 'status', message: 'bad status' },
    ]);
  });

  it('400 VALIDATION_ERROR with details missing or malformed gives an empty list', async () => {
    const missing = await exec('edit', errorResponse(400, 'VALIDATION_ERROR'));
    assert.equal(missing.kind, 'validation');
    assert.deepEqual(missing.details, []);
    const malformed = await exec('edit', errorResponse(400, 'VALIDATION_ERROR', 'raw', 'oops'));
    assert.equal(malformed.kind, 'validation');
    assert.deepEqual(malformed.details, []);
  });

  it('404 TODO_NOT_FOUND is notFound', async () => {
    for (const operation of ['edit', 'status', 'delete']) {
      const result = await exec(operation, errorResponse(404, 'TODO_NOT_FOUND'));
      assert.equal(result.ok, false, operation);
      assert.equal(result.kind, 'notFound', operation);
    }
  });

  it('other codes and statuses with an envelope are http', async () => {
    const cases = [
      [404, 'NOT_FOUND'],
      [400, 'INVALID_REQUEST_BODY'],
      [400, 'INVALID_ID'],
      [405, 'METHOD_NOT_ALLOWED'],
      [413, 'PAYLOAD_TOO_LARGE'],
      [415, 'UNSUPPORTED_MEDIA_TYPE'],
      [500, 'INTERNAL_ERROR'],
      [502, 'BAD_GATEWAY'],
      [504, 'GATEWAY_TIMEOUT'],
      [404, 'VALIDATION_ERROR'],
      [500, 'TODO_NOT_FOUND'],
    ];
    for (const [status, code] of cases) {
      const result = await exec('list', errorResponse(status, code));
      assert.equal(result.ok, false, `${status} ${code}`);
      assert.equal(result.kind, 'http', `${status} ${code}`);
      assert.equal(result.status, status);
      assert.equal(result.code, code);
    }
  });

  it('a non-2xx without an envelope is http, including 400 text/html (AC-20)', async () => {
    const cases = [
      { status: 400, body: '<html><body>Bad Request</body></html>', contentType: 'text/html' },
      { status: 500, body: '' },
      { status: 502, body: 'Bad Gateway', contentType: 'text/plain' },
      { status: 400, body: '{"error":"flat"}' },
      { status: 400, body: '{"error":{"code":5}}' },
      { status: 400, body: '{"nothing":true}' },
      { status: 404, body: 'null' },
    ];
    for (const spec of cases) {
      const result = await exec('add', spec);
      assert.equal(result.ok, false, JSON.stringify(spec));
      assert.equal(result.kind, 'http', JSON.stringify(spec));
      assert.equal(result.code, null, JSON.stringify(spec));
    }
  });
});

describe('api: classification step 1, network and timeout (NFR-6 logic only)', () => {
  it('a rejected fetch is a network result', async () => {
    assert.deepEqual(await exec('list', { network: true }), { ok: false, kind: 'network' });
    assert.deepEqual(await exec('list', { reject: new Error('boom') }), { ok: false, kind: 'network' });
  });

  it('a fetch function that throws synchronously is a network result, and the promise does not reject', async () => {
    const { client } = setup({
      fetchFn: () => {
        throw new Error('sync boom');
      },
    });
    assert.deepEqual(await client.listTasks(), { ok: false, kind: 'network' });
  });

  it('a text() that rejects for another reason is a network result', async () => {
    const result = await exec('list', { status: 200, textReject: new TypeError('body failed') });
    assert.deepEqual(result, { ok: false, kind: 'network' });
  });

  it('timeout before headers: the timer fires, the request is aborted, the result is timeout', async () => {
    const { fetchFn, timer, client } = setup();
    const pending = client.listTasks();
    assert.equal(fetchFn.calls.length, 1);
    assert.equal(timer.active().length, 1);
    assert.equal(timer.active()[0].ms, 10000);
    timer.fire();
    assert.deepEqual(await pending, { ok: false, kind: 'timeout' });
    assert.equal(fetchFn.calls[0].signal.aborted, true);
    assert.equal(timer.active().length, 0);
  });

  it('timeout during text(): the timer stays armed until the body is read, then the result is timeout', async () => {
    const { fetchFn, timer, client } = setup();
    fetchFn.enqueue({ status: 200, body: '', hangText: true });
    const pending = client.listTasks();
    await settle();
    assert.equal(timer.active().length, 1, 'timer must not be cleared before text() settles');
    timer.fire();
    assert.deepEqual(await pending, { ok: false, kind: 'timeout' });
  });

  it('a body that finishes in time is classified normally and the timer is cleared afterwards', async () => {
    const { fetchFn, timer, client } = setup();
    fetchFn.enqueue({ status: 200, body: '', hangText: true });
    const pending = client.listTasks();
    await settle();
    assert.equal(timer.active().length, 1);
    fetchFn.calls[0].finishText(JSON.stringify({ data: [TASK] }));
    assert.deepEqual(await pending, { ok: true, data: [TASK] });
    assert.equal(timer.active().length, 0);
  });

  it('the timer is armed before fetch is called', async () => {
    const order = [];
    const fetchFn = createFakeFetch();
    fetchFn.enqueue(jsonResponse(200, { data: [] }));
    const timer = createFakeTimer();
    const client = createApiClient({
      fetchFn: (url, init) => {
        order.push('fetch');
        return fetchFn(url, init);
      },
      setTimer: (fn, ms) => {
        order.push('timer');
        return timer.setTimer(fn, ms);
      },
      clearTimer: timer.clearTimer,
    });
    await client.listTasks();
    assert.deepEqual(order, ['timer', 'fetch']);
  });

  it('the timeout is configurable', async () => {
    const { timer, client } = setup({ timeoutMs: 250 });
    const pending = client.listTasks();
    assert.equal(timer.active()[0].ms, 250);
    timer.fire();
    await pending;
  });

  it('the timer is cleared after success, http failure and network failure', async () => {
    for (const spec of [jsonResponse(200, { data: [] }), errorResponse(500, 'INTERNAL_ERROR'), { network: true }]) {
      const { fetchFn, timer, client } = setup();
      fetchFn.enqueue(spec);
      await client.listTasks();
      assert.equal(timer.active().length, 0);
      assert.equal(timer.timers.length, 1);
    }
  });
});
