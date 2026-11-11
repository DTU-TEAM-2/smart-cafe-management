import { useState, useEffect, useCallback } from "react";
import { fetchStaffList, createStaff, updateStaff, deleteStaff, fetchCsrf } from "./staffApi.js";
import StaffFormModal from "./StaffFormModal.jsx";
import DeleteStaffModal from "./DeleteStaffModal.jsx";
import "./staff.css";

const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });

export default function StaffListPage({ authorization = "" }) {
  const [staffList, setStaffList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [csrf, setCsrf] = useState(null);

  // Search & Pagination state
  const [keyword, setKeyword] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Modal states
  const [modalMode, setModalMode] = useState(null); // 'create' | 'edit' | null
  const [selectedStaff, setSelectedStaff] = useState(null);
  const [deletingStaff, setDeletingStaff] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  // Load CSRF token on mount
  useEffect(() => {
    fetchCsrf(authorization).then(setCsrf);
  }, [authorization]);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const result = await fetchStaffList({
        keyword: searchQuery,
        page,
        size: pageSize,
        authorization,
      });
      setStaffList(result.content || []);
      setTotalPages(result.totalPages || 0);
      setTotalElements(result.totalElements || 0);
    } catch (err) {
      setError(err.message || "Lỗi khi tải danh sách nhân viên.");
    } finally {
      setLoading(false);
    }
  }, [searchQuery, page, pageSize, authorization]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  function handleSearchSubmit(e) {
    e.preventDefault();
    setPage(0);
    setSearchQuery(keyword);
  }

  function handleClearSearch() {
    setKeyword("");
    setSearchQuery("");
    setPage(0);
  }

  async function handleSaveStaff(data) {
    try {
      setIsSaving(true);
      setError("");
      if (modalMode === "edit" && selectedStaff?.id) {
        await updateStaff(selectedStaff.id, data, authorization, csrf);
      } else {
        await createStaff(data, authorization, csrf);
      }
      setModalMode(null);
      setSelectedStaff(null);
      await loadData();
    } catch (err) {
      alert(err.message || "Lỗi khi lưu thông tin nhân viên.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDeleteConfirm(id) {
    try {
      setIsDeleting(true);
      setError("");
      await deleteStaff(id, authorization, csrf);
      setDeletingStaff(null);
      await loadData();
    } catch (err) {
      alert(err.message || "Lỗi khi xóa nhân viên.");
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="staff-workspace">
      <div className="staff-header">
        <div className="staff-title-group">
          <h1>Quản lý nhân viên</h1>
          <p>Danh sách, thêm mới, sửa thông tin và phân quyền nhân viên hệ thống Smart Cafe</p>
        </div>
        <div>
          <button
            type="button"
            className="btn-primary"
            onClick={() => {
              setSelectedStaff(null);
              setModalMode("create");
            }}
          >
            <span>+</span> Thêm mới nhân viên
          </button>
        </div>
      </div>

      {error && <div className="form-error-alert" role="alert">{error}</div>}

      <div className="staff-toolbar">
        <form className="staff-search-form" onSubmit={handleSearchSubmit}>
          <input
            type="text"
            className="staff-search-input"
            placeholder="Tìm kiếm theo Username, Tên, Số điện thoại (OR)…"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <button type="submit" className="btn-primary" style={{ padding: "10px 14px" }}>
            Tìm kiếm
          </button>
          {searchQuery && (
            <button type="button" className="btn-secondary" onClick={handleClearSearch}>
              Xóa tìm
            </button>
          )}
        </form>
        <div style={{ color: "var(--muted)", fontSize: "14px" }}>
          Tổng cộng: <strong>{totalElements}</strong> nhân viên
        </div>
      </div>

      <div className="staff-table-container">
        {loading ? (
          <div className="staff-empty-state">Đang tải danh sách nhân viên…</div>
        ) : staffList.length === 0 ? (
          <div className="staff-empty-state">
            <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>Not found</h3>
            <p>Không tìm thấy nhân viên nào phù hợp với điều kiện tìm kiếm.</p>
          </div>
        ) : (
          <table className="staff-table">
            <thead>
              <tr>
                <th>Ảnh</th>
                <th>Username</th>
                <th>Họ và tên</th>
                <th>Số điện thoại</th>
                <th>Địa chỉ</th>
                <th>Vai trò</th>
                <th>Giới tính</th>
                <th>Ngày sinh</th>
                <th>Mức lương</th>
                <th>Trạng thái</th>
                <th style={{ textAlign: "right" }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {staffList.map((item) => (
                <tr key={item.id}>
                  <td>
                    {item.avatarUrl ? (
                      <img src={item.avatarUrl} alt={item.fullName} className="staff-avatar-img" />
                    ) : (
                      <div className="avatar">{item.fullName?.slice(0, 1).toUpperCase()}</div>
                    )}
                  </td>
                  <td><strong>{item.username}</strong></td>
                  <td>{item.fullName}</td>
                  <td>{item.phone || "—"}</td>
                  <td>{item.address || "—"}</td>
                  <td>
                    <span className={`badge ${item.role === "ADMIN" ? "badge-admin" : "badge-staff"}`}>
                      {item.role === "ADMIN" ? "Quản trị" : "Nhân viên"}
                    </span>
                  </td>
                  <td>{item.gender === "MALE" ? "Nam" : item.gender === "FEMALE" ? "Nữ" : "—"}</td>
                  <td>{item.birthday ? item.birthday.split("-").reverse().join("/") : "—"}</td>
                  <td style={{ color: "var(--gold)", fontWeight: "600" }}>
                    {item.salary ? money.format(item.salary) : "—"}
                  </td>
                  <td>
                    <span className={`badge ${item.status === "ACTIVE" ? "badge-active" : "badge-inactive"}`}>
                      {item.status === "ACTIVE" ? "Hoạt động" : "Ngừng HĐ"}
                    </span>
                  </td>
                  <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    <button
                      type="button"
                      className="btn-edit"
                      onClick={() => {
                        setSelectedStaff(item);
                        setModalMode("edit");
                      }}
                    >
                      Sửa
                    </button>
                    <button
                      type="button"
                      className="btn-danger"
                      onClick={() => setDeletingStaff(item)}
                    >
                      Xóa
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {totalPages > 1 && (
        <div className="staff-pagination">
          <div style={{ color: "var(--muted)", fontSize: "13px" }}>
            Trang {page + 1} / {totalPages} (10 bản ghi/trang)
          </div>
          <div className="pagination-controls">
            <button
              type="button"
              className="btn-secondary"
              disabled={page <= 0 || loading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Trang trước
            </button>
            <button
              type="button"
              className="btn-secondary"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage((p) => p + 1)}
            >
              Trang sau →
            </button>
          </div>
        </div>
      )}

      {/* Form Modal (Create or Edit) */}
      {modalMode && (
        <StaffFormModal
          staff={selectedStaff}
          onClose={() => {
            setModalMode(null);
            setSelectedStaff(null);
          }}
          onSave={handleSaveStaff}
          isSaving={isSaving}
          authorization={authorization}
          csrf={csrf}
        />
      )}

      {/* Delete Confirmation Modal */}
      {deletingStaff && (
        <DeleteStaffModal
          staff={deletingStaff}
          onClose={() => setDeletingStaff(null)}
          onConfirm={handleDeleteConfirm}
          isDeleting={isDeleting}
        />
      )}
    </div>
  );
}
