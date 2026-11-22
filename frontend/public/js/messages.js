// C-7 Outcome mapping (architecture 7.2 table, I-6; requirements 7.3). Pure; imports nothing.
//
// outcomeFor(operation, result) -> { kind: 'success' | 'error', message, fieldErrors: {title?, description?}, refresh }
//   operation: 'list' | 'add' | 'edit' | 'status' | 'delete'
//   result:    the Result produced by api.js (never rejects)
// Codes, status numbers and the raw backend error.message are never part of a message. The only backend text that
// is shown is the `message` of a 400 VALIDATION_ERROR detail for title or description, beside its field.
// `message` of a success is the announcement text ('' for a list: the controller decides whether to clear).
// `message` of a field-error outcome is the alert text of 7.2: the field messages in field order (title, then
// description) joined by a space, plus CHECK_INPUT when a detail names another field.

export const MESSAGES = Object.freeze({
  GENERIC: 'Something went wrong. Please try again.',
  CHECK_INPUT: 'Please check your input and try again.',
  NOT_FOUND: 'This task no longer exists',
  ADDED: 'Task added',
  UPDATED: 'Task updated',
  MARKED_COMPLETED: 'Task marked as completed',
  MARKED_PENDING: 'Task marked as pending',
  DELETED: 'Task deleted',
});

const FIELD_ORDER = ['title', 'description'];

function success(message) {
  return { kind: 'success', message, fieldErrors: {}, refresh: false };
}

function failure(message, extra) {
  return { kind: 'error', message, fieldErrors: {}, refresh: false, ...extra };
}

function successFor(operation, result) {
  switch (operation) {
    case 'add':
      return success(MESSAGES.ADDED);
    case 'edit':
      return success(MESSAGES.UPDATED);
    case 'status':
      return success(
        result.data && result.data.status === 'Completed' ? MESSAGES.MARKED_COMPLETED : MESSAGES.MARKED_PENDING,
      );
    case 'delete':
      return success(MESSAGES.DELETED);
    default:
      return success('');
  }
}

function validationFor(operation, result) {
  if (operation === 'status') {
    return failure(MESSAGES.CHECK_INPUT);
  }
  if (operation !== 'add' && operation !== 'edit') {
    return failure(MESSAGES.GENERIC);
  }
  const details = Array.isArray(result.details) ? result.details : [];
  const fieldErrors = {};
  let namesOtherField = false;
  for (const entry of details) {
    if (!entry || typeof entry.field !== 'string' || typeof entry.message !== 'string') {
      continue;
    }
    if (FIELD_ORDER.includes(entry.field)) {
      if (entry.message.trim() !== '' && fieldErrors[entry.field] === undefined) {
        fieldErrors[entry.field] = entry.message;
      }
    } else {
      namesOtherField = true;
    }
  }
  const parts = FIELD_ORDER.filter((field) => fieldErrors[field] !== undefined).map((field) => fieldErrors[field]);
  if (parts.length === 0 || namesOtherField) {
    parts.push(MESSAGES.CHECK_INPUT);
  }
  return failure(parts.join(' '), { fieldErrors });
}

export function outcomeFor(operation, result) {
  if (!result || typeof result.ok !== 'boolean') {
    return failure(MESSAGES.GENERIC);
  }
  if (result.ok) {
    return successFor(operation, result);
  }
  switch (result.kind) {
    case 'validation':
      return validationFor(operation, result);
    case 'notFound':
      if (operation === 'edit' || operation === 'status' || operation === 'delete') {
        return failure(MESSAGES.NOT_FOUND, { refresh: true });
      }
      return failure(MESSAGES.GENERIC);
    default:
      // http, network, timeout, unexpected and anything unknown
      return failure(MESSAGES.GENERIC);
  }
}
