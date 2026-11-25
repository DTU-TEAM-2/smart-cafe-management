import { useState, useEffect, useRef } from "react";
import { fetchBills, fetchBillDetail, formatDateTime, vnd, billStatusLabels } from "./billingApi.js";
import BillDetailModal from "./BillDetailModal.jsx";
import StatisticsView from "./StatisticsView.jsx";
import SiteLayout from "../layout/SiteLayout.jsx";
import "./billing.css";
import useBillingAccess from "./useBillingAccess.js";

export default function BillsWorkspace({ layout, authorization, credentials, statistics, isAdmin, expiryNotice }) {
  const onDenied = useBillingAccess(authorization);
  return (
    <div className="billing-workspace">
      <aside className="billing-sidebar">
        <a className="brand billing-brand" href="#top" aria-label="LECKER Trang chủ">
          <strong>LECKER</strong><span>COFFEE CAFE</span>
        </a>
        <nav aria-label="Quản lý hóa đơn và thống kê">
          <a href={isAdmin ? "#admin" : "#staff"}>{isAdmin ? "Tổng quan quản trị" : "Tổng quan ca làm"}</a>
          <a href="#sale">Trạng thái bàn</a>
          <a href="#bills" aria-current={!statistics ? "page" : undefined}>Hóa đơn</a>
          {isAdmin && <a href="#statistics" aria-current={statistics ? "page" : undefined}>Thống kê doanh thu</a>}
        </nav>
        <small className="billing-sidebar-caption">SMART CAFÉ MANAGEMENT</small>
      </aside>
      <div className="billing-main">
        <SiteLayout {...layout} management>
          {expiryNotice}
          <section className="billing-content" aria-labelledby="billing-title">
            <div className="billing-page-heading">
              <h1 id="billing-title">{statistics ? "Thống kê doanh thu" : "Hóa đơn"}</h1>
              <p>LECKER Coffee Cafe • Smart Café Management System</p>
            </div>
            {statistics ? <StatisticsView authorization={authorization} credentials={credentials} onDenied={onDenied} />
              : <BillList authorization={authorization} credentials={credentials} isAdmin={isAdmin} onDenied={onDenied} />}
          </section>
        </SiteLayout>
      </div>
    </div>
  );
}

const emptyPage = { content: [], totalElements: 0, totalPages: 0, page: 0 };

function BillList({ authorization, credentials, isAdmin, onDenied }) {
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
  const [detailError, setDetailError] = useState("");
  const detailRequest = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    async function loadBills() {
      try {
        const fetchPage = page => fetchBills({ ...query, page, authorization, credentials, onDenied, signal: controller.signal });
        let result = await fetchPage(query.page);
        if (controller.signal.aborted) return;
        if (result.totalElements > 0 && result.page >= result.totalPages) {
          // Membership can shrink between page requests (e.g. PENDING becomes PAID).
          // Recover once; a second shrink must not create an unbounded request loop.
          result = await fetchPage(Math.max(0, result.totalPages - 1));
          if (controller.signal.aborted) return;
          if (result.totalElements > 0 && result.page >= result.totalPages) {
            throw new Error("Danh sách hóa đơn vừa thay đổi. Vui lòng thử lại.");
          }
        }
        if (result.totalElements === 0) result = emptyPage;
        setData(result);
        if (result.totalElements === 0 && (query.code || query.from || query.status !== "ALL")) setSearchNotice("Not found!");
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
  }, [query, authorization, credentials, onDenied]);

  useEffect(() => () => detailRequest.current?.abort(), []);

  function load(nextQuery) {
    detailRequest.current?.abort();
    setDetailLoading(false);
    setDetailError("");
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
    setDetailError("");
    setDetailLoading(true);
    try {
      const detail = await fetchBillDetail(id, { authorization, credentials, onDenied, signal: controller.signal });
      if (!controller.signal.aborted) setDetailModalBill(detail);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error.message === "UNAUTHORIZED") {
        setData(emptyPage);
        setSelectedBillId(null);
        setErrorMsg("Bạn không có quyền xem hóa đơn.");
      } else {
        setDetailError(error.message === "NOT_FOUND" ? "Không tìm thấy hóa đơn được chọn." : error.message);
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
    setDetailError("");
  }

  return (
    <section className="billing-list" aria-labelledby="billing-list-title">
      <h2 id="billing-list-title">Danh sách hóa đơn</h2>
      <p className="billing-list-description">Tra cứu dữ liệu nghiệp vụ theo quyền</p>
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
      {detailError && (
        <div className="billing-message-banner error" role="alert" style={{ marginBottom: "16px" }}>
          <span>{detailError}</span>
          <button type="button" className="billing-button secondary" onClick={() => void handleOpenDetail(selectedBillId)}>
            Thử lại chi tiết
          </button>
        </div>
      )}

      {/* Bill List Table */}
      <div className="billing-table-wrapper">
        <div className="billing-table-scroll" role="region" aria-label="Danh sách hóa đơn" tabIndex={0}>
        <table className="billing-table" aria-label="Bảng danh sách hóa đơn">
          <thead>
            <tr>
              <th scope="col">Mã hóa đơn</th>
              <th scope="col">Ngày tạo</th>
              <th scope="col">Người lập</th>
              <th scope="col">Bàn</th>
              <th scope="col">Tổng tiền</th>
              <th scope="col">Trạng thái</th>
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
                        {billStatusLabels[bill.status] ?? bill.status}
                      </span>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
        </div>

        {/* Pagination Controls */}
        <div className="billing-pagination-bar">
          <button type="button" className="billing-button primary billing-detail-button"
            onClick={() => handleOpenDetail(selectedBillId)}>
            {detailLoading ? "Đang tải…" : "Xem chi tiết"}
          </button>
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
          canPrint={isAdmin}
          onClose={() => setDetailModalBill(null)}
        />
      )}
    </section>
  );
}
