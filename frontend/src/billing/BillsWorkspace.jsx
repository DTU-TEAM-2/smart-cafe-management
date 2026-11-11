import { useState, useEffect, useCallback } from "react";
import { fetchBills, fetchBillDetail, formatDateTime, vnd } from "./billingApi.js";
import BillDetailModal from "./BillDetailModal.jsx";
import StatisticsView from "./StatisticsView.jsx";
import "./billing.css";

export default function BillsWorkspace({ authorization }) {
  const [activeTab, setActiveTab] = useState("bills"); // 'bills' | 'statistics'

  // Bill List states
  const [searchType, setSearchType] = useState("id"); // 'id' | 'date'
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [sort] = useState("createdAt,desc");

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");
  const [searchNotice, setSearchNotice] = useState("");
  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0, page: 0 });

  const [selectedBillId, setSelectedBillId] = useState(null);
  const [detailModalBill, setDetailModalBill] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadBills = useCallback(
    async (overrideParams = {}) => {
      setLoading(true);
      setErrorMsg("");
      setSearchNotice("");
      try {
        const queryCode = overrideParams.code !== undefined ? overrideParams.code : (searchType === "id" ? keyword : undefined);
        const queryDate = overrideParams.date !== undefined ? overrideParams.date : (searchType === "date" ? keyword : undefined);
        const queryStatus = overrideParams.status !== undefined ? overrideParams.status : statusFilter;
        const queryPage = overrideParams.page !== undefined ? overrideParams.page : page;
        const querySort = overrideParams.sort !== undefined ? overrideParams.sort : sort;

        const res = await fetchBills({
          code: queryCode,
          from: queryDate,
          to: queryDate,
          status: queryStatus,
          page: queryPage,
          size: pageSize,
          sort: querySort,
          authorization,
        });

        setData(res);
        if (res.totalElements === 0 && (queryCode || queryDate)) {
          setSearchNotice("Not found!");
        }
      } catch (err) {
        if (err.message === "UNAUTHORIZED") {
          setErrorMsg("Bạn không có quyền truy cập quản lý hóa đơn (Yêu cầu quyền ADMIN).");
        } else {
          setErrorMsg(err.message || "Lỗi tải danh sách hóa đơn.");
        }
      } finally {
        setLoading(false);
      }
    },
    [searchType, keyword, statusFilter, page, pageSize, sort, authorization]
  );

  useEffect(() => {
    if (activeTab === "bills") {
      loadBills();
    }
  }, [activeTab, page, statusFilter, sort]);

  function handleSearch(e) {
    e.preventDefault();
    if (!keyword || !keyword.trim()) {
      setSearchNotice("Please enter a keyword!");
      return;
    }
    setSearchNotice("");
    setPage(0);
    loadBills({ page: 0 });
  }

  function handleShowListBill() {
    setKeyword("");
    setSearchType("id");
    setStatusFilter("ALL");
    setSearchNotice("");
    setErrorMsg("");
    setSelectedBillId(null);
    setPage(0);
    loadBills({ code: "", date: "", status: "ALL", page: 0 });
  }

  async function handleOpenDetail() {
    if (!selectedBillId) {
      setSearchNotice("Vui lòng chọn hóa đơn cụ thể!");
      return;
    }
    setSearchNotice("");
    setDetailLoading(true);
    try {
      const detail = await fetchBillDetail(selectedBillId, authorization);
      setDetailModalBill(detail);
    } catch (err) {
      if (err.message === "NOT_FOUND") {
        setErrorMsg("Không tìm thấy hóa đơn được chọn.");
      } else {
        setErrorMsg("Không thể tải chi tiết hóa đơn: " + err.message);
      }
    } finally {
      setDetailLoading(false);
    }
  }

  return (
    <main className="billing-workspace" id="main" tabIndex={-1}>
      <div className="billing-header">
        <div className="billing-title-group">
          <p className="eyebrow">LECKER • KHU VỰC QUẢN TRỊ</p>
          <h1>Quản lý hóa đơn & Thống kê (DT2-8)</h1>
        </div>

        <div className="billing-tabs" role="tablist" aria-label="Phân hệ hóa đơn và thống kê">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "bills"}
            className="billing-tab-button"
            onClick={() => setActiveTab("bills")}
          >
            Hóa đơn (MANAGE → Bill)
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "statistics"}
            className="billing-tab-button"
            onClick={() => setActiveTab("statistics")}
          >
            Biểu đồ thống kê (Statistics)
          </button>
        </div>
      </div>

      {activeTab === "statistics" ? (
        <StatisticsView authorization={authorization} />
      ) : (
        <>
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
                />

                <select
                  className="billing-status-select"
                  value={statusFilter}
                  onChange={(e) => {
                    setStatusFilter(e.target.value);
                    setPage(0);
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
                  onClick={handleOpenDetail}
                  disabled={detailLoading}
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
              >
                ✕
              </button>
            </div>
          )}

          {errorMsg && (
            <div className="billing-message-banner error" role="alert" style={{ marginBottom: "16px" }}>
              <span>{errorMsg}</span>
              <button type="button" className="billing-button secondary" onClick={() => loadBills()}>
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
                ) : data.content.length === 0 ? (
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
                        onClick={() => setSelectedBillId(bill.id)}
                        onDoubleClick={() => {
                          setSelectedBillId(bill.id);
                          handleOpenDetail();
                        }}
                        aria-selected={isSelected}
                        tabIndex={0}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" || e.key === " ") {
                            setSelectedBillId(bill.id);
                          }
                        }}
                      >
                        <td style={{ fontWeight: 600, color: "var(--gold)" }}>
                          {bill.billCode || `#${bill.id}`}
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
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
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
                  onClick={() => setPage((p) => p + 1)}
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
        </>
      )}
    </main>
  );
}
