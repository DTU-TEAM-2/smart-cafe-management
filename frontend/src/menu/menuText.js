// Java 21 Character.isWhitespace, used by String.strip() in MenuModels.
// NBSP, figure space, narrow NBSP and BOM are intentionally preserved.
export function stripMenuText(value) {
  // eslint-disable-next-line no-control-regex -- Java strip explicitly includes U+0009–000D and U+001C–001F.
  return value.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu, '');
}
