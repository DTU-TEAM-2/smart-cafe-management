import { useState, useEffect, useRef } from "react";
import { fetchBills, fetchBillDetail, formatDateTime, vnd } from "./billingApi.js";
import BillDetailModal from "./BillDetailModal.jsx";
import StatisticsView from "./StatisticsView.jsx";
import "./billing.css";

export default function BillsWorkspace({ authorization, statistics }) {
  return (
    <div className="billing-workspace">
      <aside className="billing-sidebar">
        <p className="billing-admin-label">Khu vực quản trị</p>
        <nav aria-label="Quản lý hóa đơn và thống kê">
          <a href="#bills" aria-current={!statistics ? "page" : undefined}>Hóa đơn</a>
          <a href="#statistics" aria-current={statistics ? "page" : undefined}>Thống kê doanh thu</a>
        </nav>
      </aside>
      <section className="billing-content" aria-labelledby="billing-title">
        <h1 id="billing-title">{statistics ? "Thống kê doanh thu" : "Quản lý hóa đơn"}</h1>
        {statistics ? <StatisticsView authorization={authorization} /> : <BillList authorization={authorization} />}
      </section>
    </div>
  );
}

const emptyPage = { content: [], totalElements: 0, totalPages: 0, page: 0 };

function BillList({ authorization }) {
  const [searchType, setSearchType] = useState("id");
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState({ status: "ALL", page: 0 });
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [searchNotice, setSearchNotice] = useState("");
  const [data, setData] = useState(emptyPage);
  const [selectedBillId, setSelectedBillId] = useState(null);
  const [detailModalBill, setDetailModalBill] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const detailRequest = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    async function loadBills() {
      try {
        const result = await fetchBills({ ...query, authorization, signal: controller.signal });
        if (controller.signal.aborted) return;
        setData(result);
        if (result.totalElements === 0 && (query.code || query.from)) setSearchNotice("Not found!");
      } catch (error) {
        if (controller.signal.aborted) return;
        setErrorMsg(error.message === "UNAUTHORIZED"
          ? "Bạn không có quyền quản lý hóa đơn."
          : error.message);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void loadBills();
    return () => controller.abort();
  }, [query, authorization]);

  useEffect(() => () => detailRequest.current?.abort(), []);

  function load(nextQuery) {
    detailRequest.current?.abort();
    setDetailLoading(false);
    setDetailModalBill(null);
    setSelectedBillId(null);
    setSearchNotice("");
    setErrorMsg("");
    setData(emptyPage);
    setLoading(true);
    setQuery({ ...nextQuery });
  }

  function handleSearch(event) {
    event.preventDefault();
    const value = keyword.trim();
    if (!value) {
      setSearchNotice("Please enter a keyword!");
      return;
    }
    load({
      ...(searchType === "id" ? { code: value } : { from: value, to: value }),
      status: query.status,
      page: 0,
    });
  }

  function handleShowListBill() {
    setKeyword("");
    setSearchType("id");
    load({ status: "ALL", page: 0 });
  }

  async function handleOpenDetail(id) {
    if (!id) {
      setSearchNotice("Vui lòng chọn hóa đơn cụ thể!");
      return;
    }
    detailRequest.current?.abort();
    const controller = new AbortController();
    detailRequest.current = controller;
    setSearchNotice("");
    setErrorMsg("");
    setDetailLoading(true);
    try {
      const detail = await fetchBillDetail(id, authorization, controller.signal);
      if (!controller.signal.aborted) setDetailModalBill(detail);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error.message === "UNAUTHORIZED") {
        setData(emptyPage);
        setSelectedBillId(null);
        setErrorMsg("Bạn không có quyền xem hóa đơn.");
      } else {
        setSearchNotice(error.message === "NOT_FOUND" ? "Không tìm thấy hóa đơn được chọn." : error.message);
      }
    } finally {
      if (!controller.signal.aborted) setDetailLoading(false);
    }
  }

  function selectBill(id) {
    if (selectedBillId === id) return;
    detailRequest.current?.abort();
    setDetailLoading(false);
    setSelectedBillId(id);
    setSearchNotice("");
  }

  return (
    <section className="billing-list" aria-labelledby="billing-list-title">
      <h2 id="billing-list-title">Danh sách hóa đơn</h2>
      {/* Search and Action Toolbar */}
      <div className="billing-control-panel">
        <form className="billing-search-form" onSubmit={handleSearch}>
          <div className="billing-search-types" role="radiogroup" aria-label="Loại tìm kiếm">
            <label>
              <input
                type="radio"
                name="search_type"
                value="id"
                checked={searchType === "id"}
                onChange={() => {
                  setSearchType("id");
                  setKeyword("");
                  setSearchNotice("");
                }}
              />
              Search by ID
            </label>
            <label>
              <input
                type="radio"
                name="search_type"
                value="date"
                checked={searchType === "date"}
                onChange={() => {
                  setSearchType("date");
                  setKeyword("");
                  setSearchNotice("");
                }}
              />
              Search by Date
            </label>
          </div>

          <div className="billing-search-input-group">
            <input
              type={searchType === "date" ? "date" : "text"}
              className="billing-input"
              placeholder={
                searchType === "date"
                  ? "Chọn ngày tạo..."
                  : "Nhập mã hóa đơn cần tìm..."
              }
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              aria-label="Từ khóa tìm kiếm hóa đơn"
              maxLength={searchType === "id" ? 64 : undefined}
            />

            <select
              className="billing-status-select"
              value={query.status}
              onChange={(e) => {
                load({ ...query, status: e.target.value, page: 0 });
              }}
              aria-label="Lọc theo trạng thái"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="PAID">Đã thanh toán (PAID)</option>
              <option value="PENDING">Chờ thanh toán (PENDING)</option>
              <option value="VOID">Đã hủy (VOID)</option>
            </select>

            <button type="submit" className="billing-button primary">
              {searchType === "date" ? "Search by Date" : "Search by ID"}
            </button>
            <button
              type="button"
              className="billing-button secondary"
              onClick={handleShowListBill}
            >
              Show List Bill
            </button>
            <button
              type="button"
              className="billing-button secondary"
              onClick={() => handleOpenDetail(selectedBillId)}
            >
              {detailLoading ? "Đang tải…" : "Detail"}
            </button>
          </div>
        </form>
      </div>

      {searchNotice && (
        <div
          className={`billing-message-banner ${searchNotice.includes("Please") ? "error" : "alert"}`}
          role="alert"
          style={{ marginBottom: "16px" }}
        >
          <span>{searchNotice}</span>
          <button
            type="button"
            className="icon-button"
            style={{ width: "24px", height: "24px" }}
            onClick={() => setSearchNotice("")}
            aria-label="Đóng thông báo"
          >
            ✕
          </button>
        </div>
      )}

      {errorMsg && (
        <div className="billing-message-banner error" role="alert" style={{ marginBottom: "16px" }}>
          <span>{errorMsg}</span>
          <button type="button" className="billing-button secondary" onClick={() => load(query)}>
            Thử lại
          </button>
        </div>
      )}

      {/* Bill List Table */}
      <div className="billing-table-wrapper">
        <table className="billing-table" aria-label="Bảng danh sách hóa đơn">
          <thead>
            <tr>
              <th scope="col">ID (Mã HĐ)</th>
              <th scope="col">Date Created</th>
              <th scope="col">Creator</th>
              <th scope="col">Table</th>
              <th scope="col">Total</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="6" style={{ textAlign: "center", padding: "40px 0" }}>
                  Đang tải danh sách hóa đơn...
                </td>
              </tr>
            ) : errorMsg ? null : data.content.length === 0 ? (
              <tr>
                <td colSpan="6" style={{ textAlign: "center", padding: "40px 0", color: "var(--muted)" }}>
                  Không có hóa đơn nào.
                </td>
              </tr>
            ) : (
              data.content.map((bill) => {
                const isSelected = selectedBillId === bill.id;
                return (
                  <tr
                    key={bill.id}
                    className={`interactive-row ${isSelected ? "is-selected" : ""}`}
                    onClick={() => selectBill(bill.id)}
                    onDoubleClick={() => handleOpenDetail(bill.id)}
                  >
                    <td>
                      <button type="button" className="billing-row-choice"
                        aria-pressed={isSelected} onClick={() => selectBill(bill.id)}>
                        {bill.billCode || `#${bill.id}`}
                      </button>
                    </td>
                    <td>{formatDateTime(bill.createdAt)}</td>
                    <td>{bill.creatorName || "—"}</td>
                    <td>
                      {bill.tableName || "—"}{bill.tableCode ? ` (${bill.tableCode})` : ""}
                    </td>
                    <td className="total-amount-cell">{vnd(bill.totalAmount)}</td>
                    <td>
                      <span className={`billing-status-badge ${bill.status.toLowerCase()}`}>
                        {bill.status}
                      </span>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>

        {/* Pagination Controls */}
        <div className="billing-pagination-bar">
          <div>
            Hiển thị {data.content.length} / {data.totalElements} hóa đơn
            {data.totalPages > 1 && ` (Trang ${data.page + 1} của ${data.totalPages})`}
          </div>

          <div className="billing-pagination-controls">
            <button
              type="button"
              className="billing-button secondary"
              disabled={data.page <= 0 || loading}
              onClick={() => load({ ...query, page: data.page - 1 })}
              aria-label="Trang trước"
            >
              ← Trước
            </button>
            <span style={{ padding: "0 8px", fontWeight: 600 }}>
              {data.totalPages === 0 ? 0 : data.page + 1} / {data.totalPages}
            </span>
            <button
              type="button"
              className="billing-button secondary"
              disabled={data.page >= data.totalPages - 1 || loading}
              onClick={() => load({ ...query, page: data.page + 1 })}
              aria-label="Trang tiếp theo"
            >
              Sau →
            </button>
          </div>
        </div>
      </div>

      {/* Modal Detail */}
      {detailModalBill && (
        <BillDetailModal
          bill={detailModalBill}
          onClose={() => setDetailModalBill(null)}
        />
      )}
    </section>
  );
}
