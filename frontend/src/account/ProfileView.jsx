import AccountAvatar from "../layout/AccountAvatar.jsx";

const roles = { ADMIN: "Quản trị viên", STAFF: "Nhân viên phục vụ" };
const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
const labels = { name: "Họ và tên", address: "Địa chỉ", phone: "Điện thoại" };

export function ProfileIdentity({ profile }) {
  return <aside className="personnel-identity" aria-label="Tài khoản hiện tại">
    <div className="personnel-avatar"><AccountAvatar name={profile.name} url={profile.avatarUrl} /></div>
    <span className="personnel-role">{roles[profile.role]}</span>
    <h2>{profile.name}</h2>
    <p className="personnel-username">@{profile.username}</p>
    <div className="personnel-identity-note"><span className="personnel-seal" aria-hidden="true">L</span>
      <p>Một đội ngũ.<br /><strong>Cùng tạo nên LECKER.</strong></p></div>
  </aside>;
}

export default function ProfileView({ profile, snapshot, draft, errors, saving, onChange }) {
  const salary = typeof profile.salary === "string" && /^\d+$/.test(profile.salary) ? money.format(BigInt(profile.salary)) : null;
  const fields = [
    ["username", "Tên đăng nhập", profile.username], ["name", labels.name, profile.name],
    ["address", labels.address, profile.address], ["phone", labels.phone, profile.phone],
    ["role", "Vai trò", roles[profile.role]], ["birthday", "Ngày sinh", profile.birthday?.split("-").reverse().join("/")],
    ["gender", "Giới tính", { MALE: "Nam", FEMALE: "Nữ" }[profile.gender]], ["salary", "Lương", salary],
  ];
  return <dl className="personnel-fields">{fields.map(([field, label, value]) => {
    const editing = snapshot && Object.hasOwn(labels, field);
    return <div key={field} className={field === "address" ? "personnel-address" : undefined}>
      <dt>{editing ? <><label htmlFor={`profile-${field}`}>{label}</label><span aria-hidden="true">*</span></> : label}
        {!Object.hasOwn(labels, field) && <span className="personnel-readonly">Chỉ đọc</span>}</dt>
      <dd>{editing ? <>
        <input id={`profile-${field}`} name={field} value={draft[field] ?? snapshot[field] ?? ""} required
          type={field === "phone" ? "tel" : "text"} autoComplete={{ name: "name", address: "street-address", phone: "tel" }[field]}
          readOnly={saving} onChange={event => onChange(field, event.target.value)}
          aria-invalid={Boolean(errors[field])} aria-describedby={errors[field] ? `profile-${field}-error` : undefined} />
        {errors[field] && <span className="account-field-error" id={`profile-${field}-error`}>{errors[field]}</span>}
      </> : <span className={value === null || value === undefined || value === "" ? "personnel-empty" : undefined}>{value || "Chưa cập nhật"}</span>}</dd>
    </div>;
  })}</dl>;
}
