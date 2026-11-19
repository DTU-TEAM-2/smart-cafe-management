const limits = { name: 160, address: 255, phone: 20 };
const labels = { name: "Họ và tên", address: "Địa chỉ", phone: "Điện thoại" };
// Character.isWhitespace OR Character.isSpaceChar in the Profile contract (not JS trim).
const blank = value => [...value].every(char => {
  const code = char.codePointAt(0);
  return (code >= 9 && code <= 13) || (code >= 28 && code <= 32) || /\p{Z}/u.test(char);
});

export function profilePatch(snapshot, draft) {
  const errors = {};
  const patch = { version: snapshot.version };
  for (const [field, limit] of Object.entries(limits)) {
    if (!Object.hasOwn(draft, field) || draft[field] === snapshot[field]) continue;
    const value = draft[field];
    if (typeof value !== "string" || blank(value)) errors[field] = `${labels[field]} không được để trống.`;
    else if ([...value].length > limit) errors[field] = `${labels[field]} tối đa ${limit} ký tự Unicode.`;
    else patch[field] = value;
  }
  return { patch: Object.keys(patch).length > 1 ? patch : null, errors };
}

export function passwordErrors({ currentPassword, newPassword, confirmNewPassword }) {
  const errors = {};
  if (!currentPassword || currentPassword.length > 256) errors.currentPassword = "Nhập mật khẩu hiện tại, tối đa 256 ký tự.";
  if (!newPassword || newPassword.length < 8 || newPassword.length > 64
    || new TextEncoder().encode(newPassword).length > 72) {
    errors.newPassword = "Mật khẩu mới phải từ 8–64 ký tự và không vượt quá 72 byte.";
  } else {
    // Java PasswordPolicy iterates UTF-16 chars, so supplementary letters do not satisfy these rules.
    const chars = newPassword.split("");
    if (!chars.some(char => /\p{Uppercase}/u.test(char))
      || !chars.some(char => /\p{Lowercase}/u.test(char))
      || !chars.some(char => /\p{Nd}/u.test(char))) errors.newPassword = "Mật khẩu phải có chữ hoa, chữ thường và chữ số.";
    else if (newPassword === currentPassword) errors.newPassword = "Mật khẩu mới phải khác mật khẩu hiện tại.";
  }
  if (!confirmNewPassword || confirmNewPassword.length > 256 || newPassword !== confirmNewPassword) {
    errors.confirmNewPassword = "Xác nhận mật khẩu phải khớp chính xác mật khẩu mới.";
  }
  return errors;
}
