// C-9 View (architecture 6.2, 8.1 "Load" step 3, 8.3, D-4; DR-4, DR-14). Imports nothing; owns no state.
//
// Keyed-reconcile rule (D-4): rows are matched by data-id. A row that is still wanted is updated in place (never
// rebuilt while its mode is unchanged), rows are moved only when their position is wrong, and rows that are gone are
// removed. A row in edit mode keeps its input elements, their values, focus and caret through every reconcile; only
// its disabled state, pending text and field errors are refreshed. If moving a node still blurs the focused element,
// focus and the selection range are restored. Backend order is kept; there is no sorting.
//
// All text goes through textContent / setAttribute / disabled / hidden (NFR-2, AC-21). No HTML string is ever parsed.
//
// ctx given to renderList: { pending: Map<id|'add', 'save'|'status'|'delete'|'add'>,
//                           editing: null | { id, draftTitle, draftDescription },
//                           editErrors: { title?, description? } }  (errors belong to the editing row)
// Pending texts derive from ctx.pending only: "Saving..." (edit Save), "Updating..." (toggle), "Deleting..."
// (Delete), "Adding..." (add form). While any save is pending, the Edit buttons of the other rows are disabled.
//
// Markup contract with styles.css: li.task[data-id][data-status][data-mode], h3 title, p.desc (only when not empty),
// span.badge ("Pending" | "Completed"), div.actions with buttons carrying data-action edit | toggle | delete; the
// edit row has form.edit-form with [data-field="title"|"description"], data-action save | cancel.

export function createView(root) {
  const doc = root.ownerDocument;
  const q = (selector) => root.querySelector(selector);

  const addForm = q('#add-form');
  const addTitle = q('#add-title');
  const addDescription = q('#add-description');
  const addTitleError = q('#add-title-error');
  const addDescriptionError = q('#add-description-error');
  const addSubmit = q('#add-submit');
  const listEl = q('#task-list');
  const listStatus = q('#list-status');
  const listHeading = q('#list-heading');
  const retryBtn = q('#retry');
  const orphanEl = q('#orphan');
  const orphanHeading = q('#orphan-heading');
  const orphanTitle = q('#orphan-title');
  const orphanDescription = q('#orphan-description');
  const orphanDismiss = q('#orphan-dismiss');

  const refs = new WeakMap();
  let uid = 0;

  function el(tag, className) {
    const node = doc.createElement(tag);
    if (className) {
      node.className = className;
    }
    return node;
  }

  function button(text, action, type = 'button') {
    const node = el('button');
    node.setAttribute('type', type);
    node.setAttribute('data-action', action);
    node.textContent = text;
    return node;
  }

  function setText(node, text) {
    if (node.textContent !== text) {
      node.textContent = text;
    }
  }

  // ---------- row, view mode ----------

  function buildViewRow(li) {
    const h3 = el('h3');
    const badge = el('span', 'badge');
    const actions = el('div', 'actions');
    const edit = button('Edit', 'edit');
    const toggle = button('Mark as completed', 'toggle');
    const del = button('Delete', 'delete');
    actions.append(edit, toggle, del);
    li.replaceChildren(h3, badge, actions);
    li.setAttribute('data-mode', 'view');
    refs.set(li, { mode: 'view', h3, desc: null, badge, edit, toggle, del });
  }

  function syncViewRow(li, task, kind, editSaving) {
    const r = refs.get(li);
    li.setAttribute('data-status', task.status);
    setText(r.h3, task.title);
    if (task.description !== '') {
      if (!r.desc) {
        r.desc = el('p', 'desc');
        li.insertBefore(r.desc, r.badge);
      }
      setText(r.desc, task.description);
    } else if (r.desc) {
      r.desc.remove();
      r.desc = null;
    }
    setText(r.badge, task.status);

    const completed = task.status === 'Completed';
    const toggleText = completed ? 'Mark as pending' : 'Mark as completed';
    // The accessible name is the visible text plus the title, so the pending text ("Updating...", "Deleting...")
    // is exposed to assistive technology too (CR-6; WCAG 2.5.3 label in name).
    const toggleLabel = kind === 'status' ? 'Updating...' : toggleText;
    const delLabel = kind === 'delete' ? 'Deleting...' : 'Delete';
    r.edit.setAttribute('aria-label', `Edit: ${task.title}`);
    r.toggle.setAttribute('aria-label', `${toggleLabel}: ${task.title}`);
    r.del.setAttribute('aria-label', `${delLabel}: ${task.title}`);
    setText(r.edit, 'Edit');
    setText(r.toggle, toggleLabel);
    setText(r.del, delLabel);

    const busy = kind !== undefined;
    r.edit.disabled = busy || editSaving;
    r.toggle.disabled = busy;
    r.del.disabled = busy;
    if (busy) {
      li.setAttribute('aria-busy', 'true');
    } else {
      li.removeAttribute('aria-busy');
    }
  }

  // ---------- row, edit mode ----------

  function field(labelText, control, id) {
    const wrap = el('div', 'field');
    const label = el('label');
    label.setAttribute('for', id);
    label.textContent = labelText;
    const error = el('p', 'field-error');
    error.setAttribute('id', `${id}-error`);
    control.setAttribute('id', id);
    control.setAttribute('aria-describedby', `${id}-error`);
    wrap.append(label, control, error);
    return { wrap, error };
  }

  function buildEditRow(li, task, editing) {
    const n = ++uid;
    const form = el('form', 'edit-form');
    form.setAttribute('novalidate', '');
    form.setAttribute('aria-label', `Edit task: ${task.title}`);

    const title = el('input');
    title.setAttribute('type', 'text');
    title.setAttribute('autocomplete', 'off');
    title.setAttribute('data-field', 'title');
    title.value = editing.draftTitle;
    const titleField = field('Title', title, `edit-title-${n}`);

    const description = el('textarea');
    description.setAttribute('rows', '3');
    description.setAttribute('data-field', 'description');
    description.value = editing.draftDescription;
    const descriptionField = field('Description (optional)', description, `edit-description-${n}`);

    const actions = el('div', 'actions');
    const save = button('Save', 'save', 'submit');
    const cancel = button('Cancel', 'cancel');
    actions.append(save, cancel);

    form.append(titleField.wrap, descriptionField.wrap, actions);
    li.replaceChildren(form);
    li.setAttribute('data-mode', 'edit');
    li.setAttribute('data-status', task.status);
    li.removeAttribute('aria-busy');
    refs.set(li, {
      mode: 'edit',
      form,
      title,
      description,
      titleError: titleField.error,
      descriptionError: descriptionField.error,
      save,
      cancel,
    });
  }

  function applyFieldError(input, errorEl, message) {
    setText(errorEl, message || '');
    if (message) {
      input.setAttribute('aria-invalid', 'true');
    } else {
      input.removeAttribute('aria-invalid');
    }
  }

  function syncEditRow(li, kind, errors) {
    const r = refs.get(li);
    const busy = kind === 'save';
    r.title.disabled = busy;
    r.description.disabled = busy;
    r.save.disabled = busy;
    r.cancel.disabled = busy;
    setText(r.save, busy ? 'Saving...' : 'Save');
    if (busy) {
      li.setAttribute('aria-busy', 'true');
    } else {
      li.removeAttribute('aria-busy');
    }
    applyFieldError(r.title, r.titleError, errors.title);
    applyFieldError(r.description, r.descriptionError, errors.description);
  }

  function syncRow(li, task, ctx) {
    const editing = ctx.editing && ctx.editing.id === task.id ? ctx.editing : null;
    const desiredMode = editing ? 'edit' : 'view';
    const current = refs.get(li);
    if (!current || current.mode !== desiredMode) {
      if (editing) {
        buildEditRow(li, task, editing);
      } else {
        buildViewRow(li);
      }
    }
    const kind = ctx.pending.get(task.id);
    if (editing) {
      syncEditRow(li, kind, ctx.editErrors || {});
    } else {
      let editSaving = false;
      for (const value of ctx.pending.values()) {
        if (value === 'save') {
          editSaving = true;
        }
      }
      syncViewRow(li, task, kind, editSaving);
    }
  }

  // ---------- keyed reconcile ----------

  function captureFocus() {
    const active = doc.activeElement;
    if (!active || !listEl.contains(active)) {
      return null;
    }
    const saved = { element: active };
    if (typeof active.selectionStart === 'number') {
      saved.start = active.selectionStart;
      saved.end = active.selectionEnd;
      saved.direction = active.selectionDirection;
    }
    return saved;
  }

  function restoreFocus(saved) {
    if (!saved || !saved.element.isConnected || doc.activeElement === saved.element) {
      return;
    }
    saved.element.focus();
    if (typeof saved.start === 'number') {
      try {
        saved.element.setSelectionRange(saved.start, saved.end, saved.direction);
      } catch (error) {
        // an element that cannot hold a selection keeps its default caret
      }
    }
  }

  function newRow(task) {
    const li = el('li', 'task');
    li.setAttribute('data-id', task.id);
    return li;
  }

  function renderList(tasks, ctx) {
    const saved = captureFocus();
    const wanted = new Set(tasks.map((task) => task.id));
    const existing = new Map();
    for (const li of Array.from(listEl.children)) {
      const id = li.getAttribute('data-id');
      if (wanted.has(id) && !existing.has(id)) {
        existing.set(id, li);
      } else {
        li.remove();
      }
    }

    if (existing.size === 0) {
      const fragment = doc.createDocumentFragment();
      for (const task of tasks) {
        const li = newRow(task);
        syncRow(li, task, ctx);
        fragment.appendChild(li);
      }
      listEl.appendChild(fragment);
      return;
    }

    tasks.forEach((task, index) => {
      const li = existing.get(task.id) || newRow(task);
      syncRow(li, task, ctx);
      const at = listEl.children[index];
      if (at !== li) {
        listEl.insertBefore(li, at || null);
      }
    });
    restoreFocus(saved);
  }

  // ---------- list status, retry, orphan ----------

  function setListStatus(text) {
    setText(listStatus, text || '');
  }

  function setRetry({ visible, disabled = false }) {
    retryBtn.hidden = !visible;
    retryBtn.disabled = disabled;
  }

  function setListBusy(busy) {
    if (busy) {
      listEl.setAttribute('aria-busy', 'true');
    } else {
      listEl.removeAttribute('aria-busy');
    }
  }

  function showOrphan({ title, description }) {
    orphanTitle.textContent = title;
    orphanDescription.textContent = description;
    orphanDescription.hidden = description === '';
    orphanEl.hidden = false;
  }

  function hideOrphan() {
    orphanEl.hidden = true;
    orphanTitle.textContent = '';
    orphanDescription.textContent = '';
  }

  // ---------- add form ----------

  function setAddPending(pending) {
    addTitle.disabled = pending;
    addDescription.disabled = pending;
    addSubmit.disabled = pending;
    addSubmit.textContent = pending ? 'Adding...' : 'Add task';
    if (pending) {
      addForm.setAttribute('aria-busy', 'true');
    } else {
      addForm.removeAttribute('aria-busy');
    }
  }

  function setAddErrors(errors) {
    applyFieldError(addTitle, addTitleError, errors && errors.title);
    applyFieldError(addDescription, addDescriptionError, errors && errors.description);
  }

  function focusFirstInvalid(inputs, errors) {
    for (const [name, input] of inputs) {
      if (errors && errors[name]) {
        input.focus();
        return true;
      }
    }
    return false;
  }

  // ---------- row access for the controller ----------

  function rowFor(id) {
    for (const li of listEl.children) {
      if (li.getAttribute('data-id') === id) {
        return li;
      }
    }
    return null;
  }

  function control(id, name) {
    const li = rowFor(id);
    const r = li && refs.get(li);
    if (!r) {
      return null;
    }
    const map =
      r.mode === 'view'
        ? { edit: r.edit, toggle: r.toggle, delete: r.del }
        : { title: r.title, description: r.description, save: r.save, cancel: r.cancel };
    return map[name] || null;
  }

  function focusControl(id, name) {
    const node = control(id, name);
    if (!node) {
      return false;
    }
    node.focus();
    return true;
  }

  // ---------- events (delegation) ----------

  function rowIdOf(node) {
    const li = node && node.closest ? node.closest('li.task') : null;
    return li && listEl.contains(li) ? li.getAttribute('data-id') : null;
  }

  function bind(handlers) {
    addForm.addEventListener('submit', (event) => {
      event.preventDefault();
      handlers.onAddSubmit();
    });
    retryBtn.addEventListener('click', () => handlers.onRetry());
    orphanDismiss.addEventListener('click', () => handlers.onDismissOrphan());

    listEl.addEventListener('click', (event) => {
      const target = event.target.closest ? event.target.closest('button[data-action]') : null;
      const id = rowIdOf(target);
      if (!target || id === null || target.disabled) {
        return;
      }
      const action = target.getAttribute('data-action');
      if (action === 'edit') {
        handlers.onEdit(id);
      } else if (action === 'toggle') {
        handlers.onToggle(id);
      } else if (action === 'delete') {
        handlers.onDelete(id);
      } else if (action === 'cancel') {
        handlers.onCancel(id);
      }
    });
    listEl.addEventListener('submit', (event) => {
      event.preventDefault();
      const id = rowIdOf(event.target);
      if (id !== null) {
        handlers.onSave(id);
      }
    });
    listEl.addEventListener('input', (event) => {
      const id = rowIdOf(event.target);
      const name = event.target.getAttribute ? event.target.getAttribute('data-field') : null;
      if (id !== null && name) {
        handlers.onEditInput(id, name, event.target.value);
      }
    });
    listEl.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') {
        return;
      }
      const id = rowIdOf(event.target);
      if (id !== null && event.target.getAttribute && event.target.getAttribute('data-field')) {
        event.preventDefault();
        handlers.onCancel(id);
      }
    });
  }

  return {
    bind,
    renderList,
    setListStatus,
    setRetry,
    setListBusy,
    showOrphan,
    hideOrphan,
    focusOrphanHeading: () => orphanHeading.focus(),
    focusListHeading: () => listHeading.focus(),
    focusRetry: () => retryBtn.focus(),
    isRetryFocused: () => doc.activeElement === retryBtn,
    // add form
    getAddValues: () => ({ title: addTitle.value, description: addDescription.value }),
    setAddPending,
    setAddErrors,
    clearAddForm() {
      addTitle.value = '';
      addDescription.value = '';
    },
    focusAddTitle: () => addTitle.focus(),
    focusAddSubmit: () => addSubmit.focus(),
    focusFirstInvalidAdd: (errors) =>
      focusFirstInvalid([['title', addTitle], ['description', addDescription]], errors),
    // rows
    rowIds: () => Array.from(listEl.children, (li) => li.getAttribute('data-id')),
    hasRow: (id) => rowFor(id) !== null,
    control,
    focusControl,
    getEditValues(id) {
      const li = rowFor(id);
      const r = li && refs.get(li);
      return r && r.mode === 'edit' ? { title: r.title.value, description: r.description.value } : null;
    },
    focusFirstInvalidEdit(id, errors) {
      const li = rowFor(id);
      const r = li && refs.get(li);
      if (!r || r.mode !== 'edit') {
        return false;
      }
      return focusFirstInvalid([['title', r.title], ['description', r.description]], errors);
    },
    activeElement: () => doc.activeElement,
  };
}
