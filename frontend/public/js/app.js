// C-8 Controller and state (architecture 8, 8.1, 8.4, I-8; DR-1..DR-4, DR-12..DR-14).
//
// State (all in memory, the backend is the source of truth, nothing is applied before a confirmed result):
//   tasks            Task[] in backend order (add: unshift or replace by id; edit/status: replace by id;
//                    delete: remove by id)
//   loadStatus       'loading' | 'loaded' | 'failed'
//   loadGen          incremented by every load; a list response from an older generation is ignored
//   dirtyDuringLoad  a mutation succeeded while a load was in flight (the list in flight is not applied; load again)
//   pending          Map<taskId | 'add', 'add' | 'save' | 'status' | 'delete'>. Per-task (FR-11): one task's
//                    pending state never disables another. view.js derives disabled state and pending text from it.
//   editing          null | { id, draftTitle, draftDescription } (one row at a time; drafts follow every `input`)
//   editErrors       field errors of the editing row
//   orphan           null | { title, description } (AC-22)
//
// Ordering rule (FR-10, DR-2): a mutation result is applied at once. If a load is in flight, dirtyDuringLoad is set,
// so that list is not applied and the load runs again. A result that arrives after a list was applied is applied by
// id against the new tasks (edit/status refresh when the id is absent).
//
// DR-13b: a dirty re-run keeps the original reason. For `initial` and `retry` a failed re-run is therefore announced
// in #msg-alert; the success of a re-run does not clear the message (it may hold the mutation's own announcement).
//
// Guards run synchronously at the start of every handler, before any await, and `pending` is set before the request
// is sent (AC-13). Focus after a failure is set only after the controls are enabled again (architecture 8.1).
//
// createApp({ root, api, confirm?, announcer?, validation?, messages?, view?, autoStart? }): every collaborator can be
// injected (the self-test page passes fakes); the defaults are the real modules. Returns the handle T-9 uses.

import { createView } from './view.js';
import { createAnnouncer } from './announcer.js';
import { createConfirmDialog } from './dialog.js';
import * as defaultValidation from './validation.js';
import * as defaultMessages from './messages.js';

const LOADING_TEXT = 'Loading tasks...';
const EMPTY_TEXT = 'No tasks yet';
const FIELD_ORDER = ['title', 'description'];

export function createApp({
  root,
  api,
  confirm,
  announcer,
  validation = defaultValidation,
  messages = defaultMessages,
  view,
  autoStart = true,
}) {
  const ui = view || createView(root);
  const say =
    announcer ||
    createAnnouncer({ statusEl: root.querySelector('#msg-status'), alertEl: root.querySelector('#msg-alert') });
  const askConfirm = confirm || createConfirmDialog(root.querySelector('#confirm-dialog')).confirmDelete;
  const { MESSAGES } = messages;

  const state = {
    tasks: [],
    loadStatus: 'loading',
    loadGen: 0,
    dirtyDuringLoad: false,
    pending: new Map(),
    editing: null,
    editErrors: {},
    orphan: null,
  };
  const confirming = new Set();
  let shownMessage = null; // text of the message currently in the message area, or null

  // ---------- helpers ----------

  function announce(kind, text) {
    shownMessage = text;
    say.announce(kind, text);
  }

  function clearMessage() {
    shownMessage = null;
    say.clear();
  }

  function render() {
    ui.renderList(state.tasks, { pending: state.pending, editing: state.editing, editErrors: state.editErrors });
  }

  function findTask(id) {
    return state.tasks.find((task) => task.id === id);
  }

  function fieldAlertText(errors) {
    return FIELD_ORDER.filter((name) => errors[name]).map((name) => errors[name]).join(' ');
  }

  // The element that had focus inside the app, to give focus back to after a failure.
  function rememberFocus() {
    const active = ui.activeElement();
    return active && root.contains(active) ? active : null;
  }

  function restoreFocus(target, fallback) {
    if (target && target.isConnected && !target.disabled) {
      target.focus();
    } else if (fallback) {
      fallback();
    }
  }

  // The empty state is shown only for a loaded, empty list (FR-2); a failed or running load keeps its own text.
  function syncEmptyState() {
    if (state.loadStatus === 'loaded') {
      ui.setListStatus(state.tasks.length === 0 ? EMPTY_TEXT : '');
    }
  }

  // A mutation result is applied by id; a load in flight must not overwrite it (DR-2).
  function noteMutation() {
    if (state.loadStatus === 'loading') {
      state.dirtyDuringLoad = true;
    }
  }

  function applyAdded(task) {
    noteMutation();
    const index = state.tasks.findIndex((candidate) => candidate.id === task.id);
    if (index === -1) {
      state.tasks = [task, ...state.tasks];
    } else {
      state.tasks = state.tasks.map((candidate, i) => (i === index ? task : candidate));
    }
  }

  // Returns false when the id is absent (the caller then refreshes).
  function applyReplaced(task) {
    noteMutation();
    if (!findTask(task.id)) {
      return false;
    }
    state.tasks = state.tasks.map((candidate) => (candidate.id === task.id ? task : candidate));
    return true;
  }

  // ---------- load (8.1 "Load, Retry and refresh") ----------

  // reason: 'initial' | 'retry' | 'refresh'. `rerun` is true for the repeat caused by the dirty rule.
  async function load(reason, rerun = false) {
    const myGen = ++state.loadGen;
    state.dirtyDuringLoad = false;
    state.loadStatus = 'loading';
    ui.setListStatus(LOADING_TEXT);
    ui.setRetry({ visible: false, disabled: true });
    ui.setListBusy(true);

    const result = await api.listTasks();
    if (myGen !== state.loadGen) {
      return;
    }
    const userReason = reason === 'initial' || reason === 'retry';

    if (!result || !result.ok) {
      state.loadStatus = 'failed';
      ui.setListBusy(false);
      ui.setListStatus(MESSAGES.GENERIC);
      ui.setRetry({ visible: true, disabled: false });
      if (userReason) {
        announce('error', MESSAGES.GENERIC);
      }
      if (reason === 'retry' && focusLost()) {
        ui.focusRetry();
      }
      return;
    }

    if (state.dirtyDuringLoad) {
      return load(reason, true);
    }

    state.tasks = Array.isArray(result.data) ? result.data.slice() : [];
    state.loadStatus = 'loaded';
    const orphanedByRefresh = state.editing !== null && !findTask(state.editing.id);
    if (orphanedByRefresh) {
      orphanEditing();
    }
    render();
    ui.setListBusy(false);
    ui.setListStatus(state.tasks.length === 0 ? EMPTY_TEXT : '');
    ui.setRetry({ visible: false });
    if (userReason && !rerun) {
      clearMessage();
    }
    if (orphanedByRefresh) {
      if (shownMessage === null) {
        announce('error', MESSAGES.NOT_FOUND);
      }
      if (focusLost()) {
        ui.focusOrphanHeading();
      }
    } else if (reason === 'retry' && focusLost()) {
      ui.focusListHeading();
    }
  }

  // True when focus is on the body or outside the app, or still on the (now hidden) Retry button: a browser may
  // report a hidden element as active until its next rendering step (CR-5).
  function focusLost() {
    const active = ui.activeElement();
    return !active || !root.contains(active) || ui.isRetryFocused();
  }

  // The open edit's task is gone: its draft goes to the orphan panel (8.4 step 2). No message is made here.
  function orphanEditing() {
    state.orphan = { title: state.editing.draftTitle, description: state.editing.draftDescription };
    state.editing = null;
    state.editErrors = {};
    ui.showOrphan(state.orphan);
  }

  // ---------- add (8.1 "Add") ----------

  async function submitAdd() {
    if (state.pending.has('add')) {
      return;
    }
    ui.setAddErrors({});
    const checked = validation.validateTask(ui.getAddValues());
    if (!checked.ok) {
      ui.setAddErrors(checked.errors);
      announce('error', fieldAlertText(checked.errors));
      ui.focusFirstInvalidAdd(checked.errors);
      return;
    }
    const returnTarget = rememberFocus();
    state.pending.set('add', 'add');
    ui.setAddPending(true);

    const result = await api.createTask(checked.value);
    const outcome = messages.outcomeFor('add', result);
    state.pending.delete('add');

    if (outcome.kind === 'success') {
      applyAdded(result.data);
      render();
      ui.clearAddForm();
      ui.setAddPending(false);
      announce('success', outcome.message);
      syncEmptyState();
      ui.focusAddTitle();
      return;
    }
    ui.setAddPending(false);
    ui.setAddErrors(outcome.fieldErrors);
    announce('error', outcome.message);
    if (!ui.focusFirstInvalidAdd(outcome.fieldErrors)) {
      restoreFocus(returnTarget, ui.focusAddTitle);
    }
  }

  // ---------- status (8.1 "Status") ----------

  async function toggleStatus(id) {
    if (state.pending.has(id)) {
      return;
    }
    const task = findTask(id);
    if (!task) {
      return;
    }
    const next = task.status === 'Completed' ? 'Pending' : 'Completed';
    state.pending.set(id, 'status');
    render();

    const result = await api.setStatus(id, next);
    const outcome = messages.outcomeFor('status', result);
    state.pending.delete(id);

    if (outcome.kind === 'success') {
      const present = applyReplaced(result.data);
      render();
      announce('success', outcome.message);
      ui.focusControl(id, 'toggle');
      if (!present) {
        load('refresh');
      }
      return;
    }
    if (outcome.refresh) {
      return handleNotFound(id, 'status');
    }
    render();
    announce('error', outcome.message);
    ui.focusControl(id, 'toggle');
  }

  // ---------- 404 handling and the orphaned draft (8.4, DR-14) ----------

  // `draft` is the text that was being saved (edit only). The message is kept through the refresh (DR-1).
  function handleNotFound(id, operation, draft) {
    announce('error', MESSAGES.NOT_FOUND);
    if (operation === 'edit') {
      state.orphan = { title: draft.title, description: draft.description };
      if (state.editing && state.editing.id === id) {
        state.editing = null;
        state.editErrors = {};
      }
      ui.showOrphan(state.orphan);
    }
    render();
    const refreshed = load('refresh');
    if (operation === 'edit') {
      ui.focusOrphanHeading();
    } else {
      ui.focusListHeading();
    }
    return refreshed;
  }

  function dismissOrphan() {
    state.orphan = null;
    ui.hideOrphan();
    ui.focusListHeading();
  }

  // ---------- edit (8.1 "Edit") ----------

  function startEdit(id) {
    const task = findTask(id);
    if (!task || state.pending.has(id)) {
      return;
    }
    if (state.editing && state.editing.id === id) {
      ui.focusControl(id, 'title');
      return;
    }
    if (state.editing && state.pending.get(state.editing.id) === 'save') {
      return;
    }
    state.editing = { id, draftTitle: task.title, draftDescription: task.description };
    state.editErrors = {};
    render();
    ui.focusControl(id, 'title');
  }

  function cancelEdit(id) {
    if (!state.editing || state.editing.id !== id || state.pending.get(id) === 'save') {
      return;
    }
    state.editing = null;
    state.editErrors = {};
    render();
    ui.focusControl(id, 'edit');
  }

  function updateDraft(id, field, value) {
    if (!state.editing || state.editing.id !== id) {
      return;
    }
    if (field === 'title') {
      state.editing.draftTitle = value;
    } else if (field === 'description') {
      state.editing.draftDescription = value;
    }
  }

  async function saveEdit(id) {
    if (state.pending.has(id) || !state.editing || state.editing.id !== id) {
      return;
    }
    const typed = ui.getEditValues(id);
    if (typed) {
      state.editing.draftTitle = typed.title;
      state.editing.draftDescription = typed.description;
    }
    const checked = validation.validateTask({
      title: state.editing.draftTitle,
      description: state.editing.draftDescription,
    });
    if (!checked.ok) {
      state.editErrors = checked.errors;
      render();
      announce('error', fieldAlertText(checked.errors));
      ui.focusFirstInvalidEdit(id, checked.errors);
      return;
    }
    state.editErrors = {};
    const typedDraft = { title: state.editing.draftTitle, description: state.editing.draftDescription };
    const returnTarget = rememberFocus();
    state.pending.set(id, 'save');
    render();

    const result = await api.replaceTask(id, checked.value);
    const outcome = messages.outcomeFor('edit', result);
    state.pending.delete(id);
    const stillEditing = state.editing !== null && state.editing.id === id;

    if (outcome.kind === 'success') {
      const present = applyReplaced(result.data);
      if (stillEditing) {
        state.editing = null;
      }
      state.editErrors = {};
      render();
      announce('success', outcome.message);
      ui.focusControl(id, 'edit');
      if (!present) {
        load('refresh');
      }
      return;
    }
    if (outcome.refresh) {
      return handleNotFound(id, 'edit', typedDraft);
    }
    if (stillEditing) {
      state.editErrors = outcome.fieldErrors;
    }
    render();
    announce('error', outcome.message);
    if (!ui.focusFirstInvalidEdit(id, outcome.fieldErrors)) {
      restoreFocus(returnTarget, () => ui.focusControl(id, 'edit'));
    }
  }

  // ---------- delete (8.1 "Delete", DR-12, DR-14c) ----------

  async function deleteTask(id) {
    if (state.pending.has(id) || confirming.has(id)) {
      return;
    }
    const task = findTask(id);
    if (!task) {
      return;
    }
    confirming.add(id);
    let confirmed = false;
    try {
      confirmed = await askConfirm({ id, title: task.title, returnFocus: ui.control(id, 'delete') });
    } catch (error) {
      confirmed = false;
    } finally {
      confirming.delete(id);
    }
    if (!confirmed) {
      ui.focusControl(id, 'delete');
      return;
    }
    // DR-12: the world may have changed while the dialog was open.
    if (state.pending.has(id) || !findTask(id)) {
      return;
    }
    state.pending.set(id, 'delete');
    render();

    const result = await api.deleteTask(id);
    const outcome = messages.outcomeFor('delete', result);
    state.pending.delete(id);

    if (outcome.kind === 'success') {
      noteMutation();
      const order = ui.rowIds();
      const at = order.indexOf(id);
      state.tasks = state.tasks.filter((candidate) => candidate.id !== id);
      render();
      syncEmptyState();
      announce('success', outcome.message);
      const neighbour = at === -1 ? undefined : order[at + 1] !== undefined ? order[at + 1] : order[at - 1];
      if (neighbour === undefined || !ui.focusControl(neighbour, 'edit')) {
        ui.focusListHeading();
      }
      return;
    }
    if (outcome.refresh) {
      return handleNotFound(id, 'delete');
    }
    render();
    announce('error', outcome.message);
    ui.focusControl(id, 'delete');
  }

  // ---------- wiring ----------

  ui.bind({
    onAddSubmit: () => submitAdd(),
    onRetry: () => {
      if (state.loadStatus !== 'loading') {
        return load('retry');
      }
      return undefined;
    },
    onToggle: (id) => toggleStatus(id),
    onEdit: (id) => startEdit(id),
    onDelete: (id) => deleteTask(id),
    onSave: (id) => saveEdit(id),
    onCancel: (id) => cancelEdit(id),
    onEditInput: (id, field, value) => updateDraft(id, field, value),
    onDismissOrphan: () => dismissOrphan(),
  });

  const ready = autoStart ? load('initial') : Promise.resolve();

  return {
    ready,
    load,
    view: ui,
    getState: () => ({
      tasks: state.tasks.slice(),
      loadStatus: state.loadStatus,
      loadGen: state.loadGen,
      dirtyDuringLoad: state.dirtyDuringLoad,
      pending: new Map(state.pending),
      editing: state.editing && { ...state.editing },
      orphan: state.orphan && { ...state.orphan },
    }),
  };
}
