import { useState, useEffect } from "react";
import { uploadAvatar } from "./staffApi.js";

const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });

export default function StaffFormModal({ staff, onClose, onSave, isSaving, authorization, csrf }) {
  const isEdit = Boolean(staff?.id);

  const [formData, setFormData] = useState({
    username: "",
    fullName: "",
    email: "",
    phone: "",
    address: "",
    gender: "MALE",
    birthday: "",
    role: "STAFF",
    salary: 6000000,
    avatarMediaId: null,
    avatarUrl: "",
  });

  const [error, setError] = useState("");
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    if (staff) {
      setFormData({
        username: staff.username || "",
        fullName: staff.fullName || "",
        email: staff.email || "",
        phone: staff.phone || "",
        address: staff.address || "",
        gender: staff.gender || "MALE",
        birthday: staff.birthday || "",
        role: staff.role || "STAFF",
        salary: staff.salary ? Number(staff.salary) : 6000000,
        avatarMediaId: staff.avatarMediaId || null,
        avatarUrl: staff.avatarUrl || "",
      });
    }
  }, [staff]);

  function handleChange(e) {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === "salary" ? Number(value) : value,
    }));
  }

  async function handleFileChange(e) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      setError("Ảnh đại diện không được vượt quá 5 MB.");
      return;
    }

    try {
      setUploading(true);
      setError("");
      const result = await uploadAvatar(file, authorization, csrf);
      setFormData((prev) => ({
        ...prev,
        avatarMediaId: result.mediaId,
        avatarUrl: result.secureUrl,
      }));
    } catch (err) {
      setError(err.message || "Tải ảnh lên Cloudinary thất bại.");
    } finally {
      setUploading(false);
    }
  }

  function validate() {
    if (!isEdit) {
      const u = formData.username.trim();
      if (!u) return "Tên đăng nhập không được để trống.";
      if (u.length <= 6) return "Tên đăng nhập phải dài hơn 6 ký tự.";
      if (/^\d/.test(u)) return "Tên đăng nhập không được bắt đầu bằng chữ số.";
      const lower = u.toLowerCase();
      if (lower === "admin" || lower === "root") return "Tên đăng nhập không được là admin hoặc root.";
      if (!/^[a-zA-Z][a-zA-Z0-9_]{6,}$/.test(u)) {
        return "Tên đăng nhập chỉ chứa chữ cái, chữ số hoặc gạch dưới.";
      }
    }

    if (!formData.fullName.trim()) return "Họ và tên không được để trống.";
    if (!formData.phone.trim()) return "Số điện thoại không được để trống.";
    if (!formData.address.trim()) return "Địa chỉ không được để trống.";

    const sal = Number(formData.salary);
    if (isNaN(sal) || sal <= 0) return "Lương phải lớn hơn 0.";
    if (sal % 100000 !== 0) return "Lương phải là bội số của 100.000 VNĐ.";

    return null;
  }

  function handleSubmit(e) {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError("");
    onSave(formData);
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="staff-form-title">
      <div className="modal-dialog">
        <div className="modal-header">
          <h2 id="staff-form-title">{isEdit ? "Cập nhật thông tin nhân viên" : "Thêm mới nhân viên"}</h2>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Đóng">&times;</button>
        </div>

        {error && <div className="form-error-alert" role="alert">{error}</div>}

        <form onSubmit={handleSubmit}>
          {/* Avatar upload */}
          <div className="avatar-upload-section">
            <div className="avatar-preview-box">
              {formData.avatarUrl ? (
                <img src={formData.avatarUrl} alt="Ảnh đại diện" />
              ) : (
                <span style={{ fontSize: "24px", color: "var(--gold)" }}>👤</span>
              )}
            </div>
            <div>
              <label htmlFor="staff-avatar-input" className="btn-secondary" style={{ display: "inline-block", cursor: "pointer", padding: "6px 12px", fontSize: "13px" }}>
                {uploading ? "Đang tải ảnh…" : "Chọn ảnh đại diện (Cloudinary)"}
              </label>
              <input
                id="staff-avatar-input"
                type="file"
                accept="image/png, image/jpeg, image/jpg"
                style={{ display: "none" }}
                onChange={handleFileChange}
                disabled={uploading || isSaving}
              />
              <div className="form-hint" style={{ marginTop: "4px" }}>Định dạng PNG/JPG, tối đa 5 MB.</div>
            </div>
          </div>

          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="staff-username">Tên đăng nhập (Username) *</label>
              <input
                id="staff-username"
                name="username"
                type="text"
                value={formData.username}
                onChange={handleChange}
                disabled={isEdit || isSaving}
                readOnly={isEdit}
                placeholder="Ví dụ: staff_lan01"
                required
              />
              {isEdit ? (
                <span className="form-hint" style={{ color: "#fc8181" }}>* Tên đăng nhập là cố định (chỉ đọc)</span>
              ) : (
                <span className="form-hint">&gt; 6 ký tự, không bắt đầu bằng số</span>
              )}
            </div>

            <div className="form-group">
              <label htmlFor="staff-fullname">Họ và tên *</label>
              <input
                id="staff-fullname"
                name="fullName"
                type="text"
                value={formData.fullName}
                onChange={handleChange}
                disabled={isSaving}
                placeholder="Nguyễn Văn A"
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="staff-email">Email</label>
              <input
                id="staff-email"
                name="email"
                type="email"
                value={formData.email}
                onChange={handleChange}
                disabled={isSaving}
                placeholder="nhanvien@smartcafe.vn"
              />
            </div>

            <div className="form-group">
              <label htmlFor="staff-phone">Số điện thoại *</label>
              <input
                id="staff-phone"
                name="phone"
                type="tel"
                value={formData.phone}
                onChange={handleChange}
                disabled={isSaving}
                placeholder="0987654321"
                required
              />
            </div>

            <div className="form-group form-group-full">
              <label htmlFor="staff-address">Địa chỉ *</label>
              <input
                id="staff-address"
                name="address"
                type="text"
                value={formData.address}
                onChange={handleChange}
                disabled={isSaving}
                placeholder="123 Lê Duẩn, Đà Nẵng"
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="staff-role">Vai trò (Role) *</label>
              <select
                id="staff-role"
                name="role"
                value={formData.role}
                onChange={handleChange}
                disabled={isSaving}
              >
                <option value="STAFF">Nhân viên (Staff)</option>
                <option value="ADMIN">Quản trị viên (Admin)</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="staff-gender">Giới tính</label>
              <select
                id="staff-gender"
                name="gender"
                value={formData.gender}
                onChange={handleChange}
                disabled={isSaving}
              >
                <option value="MALE">Nam</option>
                <option value="FEMALE">Nữ</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="staff-birthday">Ngày sinh</label>
              <input
                id="staff-birthday"
                name="birthday"
                type="date"
                value={formData.birthday}
                onChange={handleChange}
                disabled={isSaving}
              />
            </div>

            <div className="form-group">
              <label htmlFor="staff-salary">Mức lương (VNĐ) *</label>
              <input
                id="staff-salary"
                name="salary"
                type="number"
                step="100000"
                min="100000"
                value={formData.salary}
                onChange={handleChange}
                disabled={isSaving}
                required
              />
              <span className="form-hint">
                {formData.salary ? money.format(formData.salary) : "Bội số của 100.000 VNĐ"}
              </span>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isSaving}>
              Quay lại (Back)
            </button>
            <button type="submit" className="btn-primary" disabled={isSaving || uploading}>
              {isSaving ? "Đang lưu…" : isEdit ? "Cập nhật" : "Thêm mới"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
