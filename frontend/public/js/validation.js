// C-6 Validation (architecture D-6, I-5). Pure; imports nothing.
//
// A copy of the backend text rules, NOT of String.prototype.trim()/length:
//   - trim removes the 25 Unicode White_Space code points listed in
//     backend/src/main/java/com/capstone/todo/validation/UnicodeText.java:17-33
//     (U+0009..U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000..U+200A, U+2028, U+2029, U+202F, U+205F, U+3000);
//   - U+FEFF and U+001C..U+001F are NOT White_Space there (JS trim() strips U+FEFF but keeps U+0085);
//   - length counts code points (an astral emoji is 1, not 2). An unpaired surrogate counts as 1 and gets no
//     client-side rule; the backend answers INVALID_REQUEST_BODY for it (architecture 7.2 step 4).
// Keep this file and UnicodeText.java in step; tests/validation.test.js holds the shared vectors.

export const TITLE_MAX = 100;
export const DESCRIPTION_MAX = 500;

export const VALIDATION_MESSAGES = Object.freeze({
  titleRequired: 'Title is required',
  titleTooLong: 'Title must be 100 characters or fewer',
  descriptionTooLong: 'Description must be 500 characters or fewer',
});

// [from, to] inclusive ranges, in the order of UnicodeText.isWhiteSpace.
const WHITE_SPACE_RANGES = [
  [0x0009, 0x000d],
  [0x0020, 0x0020],
  [0x0085, 0x0085],
  [0x00a0, 0x00a0],
  [0x1680, 0x1680],
  [0x2000, 0x200a],
  [0x2028, 0x2028],
  [0x2029, 0x2029],
  [0x202f, 0x202f],
  [0x205f, 0x205f],
  [0x3000, 0x3000],
];

export function isWhiteSpace(codePoint) {
  for (const [from, to] of WHITE_SPACE_RANGES) {
    if (codePoint >= from && codePoint <= to) {
      return true;
    }
  }
  return false;
}

// All 25 code points, for tests that compare against the backend list.
export const WHITE_SPACE_CODE_POINTS = Object.freeze(
  WHITE_SPACE_RANGES.flatMap(([from, to]) => {
    const list = [];
    for (let cp = from; cp <= to; cp++) {
      list.push(cp);
    }
    return list;
  }),
);

function asText(value) {
  if (value === null || value === undefined) {
    return '';
  }
  return typeof value === 'string' ? value : String(value);
}

// Removes leading and trailing White_Space; internal whitespace is kept. All 25 code points are in the BMP,
// so scanning UTF-16 units is equivalent to the backend's charAt loop.
export function trimText(value) {
  const text = asText(value);
  let start = 0;
  let end = text.length;
  while (start < end && isWhiteSpace(text.charCodeAt(start))) {
    start++;
  }
  while (end > start && isWhiteSpace(text.charCodeAt(end - 1))) {
    end--;
  }
  return text.slice(start, end);
}

// Same result as Java String.codePointCount: a high surrogate followed by a low one is 1, any other unit is 1.
export function codePointLength(value) {
  const text = asText(value);
  let count = 0;
  for (let i = 0; i < text.length; i++) {
    const unit = text.charCodeAt(i);
    if (unit >= 0xd800 && unit <= 0xdbff && i + 1 < text.length) {
      const next = text.charCodeAt(i + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        i++;
      }
    }
    count++;
  }
  return count;
}

// I-5. Returns the trimmed values in `value` whether or not the input is valid; these are what is sent.
export function validateTask(input) {
  const source = input || {};
  const title = trimText(source.title);
  const description = trimText(source.description);
  const errors = {};

  const titleLength = codePointLength(title);
  if (titleLength === 0) {
    errors.title = VALIDATION_MESSAGES.titleRequired;
  } else if (titleLength > TITLE_MAX) {
    errors.title = VALIDATION_MESSAGES.titleTooLong;
  }
  if (codePointLength(description) > DESCRIPTION_MAX) {
    errors.description = VALIDATION_MESSAGES.descriptionTooLong;
  }

  return {
    ok: Object.keys(errors).length === 0,
    value: { title, description },
    errors,
  };
}
