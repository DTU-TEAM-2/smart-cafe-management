import { useState, useEffect } from "react";
import { useCustomer, formatVnd } from "./CustomerContext.jsx";
import { fetchCategories, fetchMenuItems } from "./customerApi.js";
import ItemDetailModal from "./ItemDetailModal.jsx";

export default function CustomerMenuView() {
  const { addToCart, selectedItemForDetail, setSelectedItemForDetail } = useCustomer();
  const [categories, setCategories] = useState([]);
  const [items, setItems] = useState([]);
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [keyword, setKeyword] = useState("");
  const [availableOnly, setAvailableOnly] = useState(true);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);

  const PAGE_SIZE = 20;

  // Load categories
  useEffect(() => {
    let mounted = true;
    fetchCategories()
      .then((data) => {
        if (mounted) setCategories(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (mounted) console.warn("Failed to load categories:", err.message);
      });
    return () => {
      mounted = false;
    };
  }, []);

  // Load items with pagination support
  useEffect(() => {
    let mounted = true;
    if (page === 0) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    setError(null);

    const delay = keyword ? 150 : 0;
    const timer = setTimeout(() => {
      fetchMenuItems({
        categoryId: activeCategoryId,
        keyword: keyword.trim() || undefined,
        availableOnly,
        page,
        size: PAGE_SIZE,
      })
        .then((data) => {
          if (mounted) {
            const newContent = data.content || [];
            const total = typeof data.totalElements === "number" ? data.totalElements : newContent.length;
            setTotalElements(total);
            if (page === 0) {
              setItems(newContent);
              setLoading(false);
            } else {
              setItems((prev) => {
                const existingIds = new Set(prev.map((it) => it.id));
                const nonDuplicates = newContent.filter((it) => !existingIds.has(it.id));
                return [...prev, ...nonDuplicates];
              });
              setLoadingMore(false);
            }
          }
        })
        .catch((err) => {
          if (mounted) {
            setError(err.message || "Không thể tải thực đơn. Vui lòng thử lại.");
            setLoading(false);
            setLoadingMore(false);
          }
        });
    }, delay);

    return () => {
      mounted = false;
      clearTimeout(timer);
    };
  }, [activeCategoryId, keyword, availableOnly, page]);

  return (
    <div className="c-content">
      {/* Header Info */}
      <h1 className="c-title">Thực đơn hôm nay</h1>
      <p className="c-subtitle">Chọn món và thêm vào giỏ để gửi tới quầy bar</p>

      {/* Search Input */}
      <div className="c-search-bar">
        <svg className="c-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          type="search"
          className="c-search-input"
          placeholder="Tìm kiếm cà phê, trà, bánh..."
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value);
            setPage(0);
          }}
          aria-label="Tìm kiếm món"
        />
      </div>

      {/* Categories Row */}
      <div className="c-categories-row" role="tablist" aria-label="Danh mục món">
        <button
          type="button"
          role="tab"
          aria-selected={activeCategoryId === null}
          className={`c-category-pill ${activeCategoryId === null ? "active" : ""}`}
          onClick={() => {
            setActiveCategoryId(null);
            setPage(0);
          }}
        >
          Tất cả
        </button>
        {categories.map((cat) => (
          <button
            key={cat.id}
            type="button"
            role="tab"
            aria-selected={activeCategoryId === String(cat.id)}
            className={`c-category-pill ${activeCategoryId === String(cat.id) ? "active" : ""}`}
            onClick={() => {
              setActiveCategoryId(String(cat.id));
              setPage(0);
            }}
          >
            {cat.name} {cat.activeItemCount ? `(${cat.activeItemCount})` : ""}
          </button>
        ))}
      </div>

      {/* Available Only Filter */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "14px" }}>
        <span style={{ fontSize: "12px", color: "#b9aa98" }}>
          Hiển thị: <strong>{totalElements > 20 ? `${items.length}/${totalElements}` : items.length} món</strong>
        </span>
        <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "12px", color: "#b9aa98", cursor: "pointer" }}>
          <input
            type="checkbox"
            checked={availableOnly}
            onChange={(e) => {
              setAvailableOnly(e.target.checked);
              setPage(0);
            }}
            style={{ accentColor: "#d5a953" }}
          />
          Chỉ món có sẵn
        </label>
      </div>

      {/* Error state */}
      {error && (
        <div style={{ background: "rgba(220, 53, 69, 0.15)", border: "1px solid #dc3545", borderRadius: "10px", padding: "12px", marginBottom: "16px", color: "#ff8b8b", fontSize: "13px" }}>
          {error}
        </div>
      )}

      {/* Loading Skeletons (09-menu-loading.png) */}
      {loading && (
        <div className="c-menu-grid">
          {[1, 2, 3, 4].map((n) => (
            <div key={n} className="c-item-card" style={{ opacity: 0.6 }}>
              <div className="c-item-thumb-placeholder" style={{ animation: "pulse 1.5s infinite" }}>
                <span style={{ fontSize: "11px", color: "#8c7e70" }}>Đang tải...</span>
              </div>
              <div className="c-item-info">
                <div style={{ height: "14px", background: "#241e1a", borderRadius: "4px", marginBottom: "6px", width: "80%" }} />
                <div style={{ height: "12px", background: "#241e1a", borderRadius: "4px", width: "50%" }} />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!loading && items.length === 0 && (
        <div className="c-empty-state">
          <svg className="c-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <circle cx="12" cy="12" r="10" />
            <line x1="8" y1="12" x2="16" y2="12" />
          </svg>
          <h3 className="c-title" style={{ fontSize: "16px" }}>Không tìm thấy món</h3>
          <p className="c-subtitle">Vui lòng thử tìm kiếm với từ khóa hoặc danh mục khác.</p>
        </div>
      )}

      {/* Menu Grid */}
      {!loading && items.length > 0 && (
        <div className="c-menu-grid">
          {items.map((item) => (
            <div
              key={item.id}
              className="c-item-card"
              onClick={() => setSelectedItemForDetail(item)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  setSelectedItemForDetail(item);
                }
              }}
            >
              {item.imageUrl ? (
                <img src={item.imageUrl} alt={item.name} className="c-item-thumb" loading="lazy" />
              ) : (
                <div className="c-item-thumb-placeholder">
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                    <path d="M18 8h1a4 4 0 0 1 0 8h-1M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" />
                    <line x1="6" y1="1" x2="6" y2="4" />
                    <line x1="10" y1="1" x2="10" y2="4" />
                    <line x1="14" y1="1" x2="14" y2="4" />
                  </svg>
                </div>
              )}

              <div className="c-item-info">
                <span className="c-item-category">{item.categoryName || "Thực đơn"}</span>
                <h2 className="c-item-name">{item.name}</h2>
                <div className="c-item-bottom">
                  <span className="c-item-price">{formatVnd(item.price)}</span>
                  <button
                    type="button"
                    className="c-btn-add"
                    onClick={(e) => {
                      e.stopPropagation();
                      addToCart(item, 1);
                    }}
                    aria-label={`Thêm ${item.name} vào giỏ hàng`}
                  >
                    +
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Load More Button */}
      {!loading && items.length > 0 && items.length < totalElements && (
        <div style={{ marginTop: "20px", marginBottom: "12px", textAlign: "center" }}>
          <button
            type="button"
            className="c-btn-primary"
            style={{
              background: "transparent",
              border: "1px solid #d5a953",
              color: "#d5a953",
              padding: "12px 20px",
              fontSize: "13px",
              letterSpacing: "0.05em",
            }}
            onClick={() => setPage((p) => p + 1)}
            disabled={loadingMore}
          >
            {loadingMore ? "ĐANG TẢI THÊM MÓN..." : `XEM THÊM MÓN (${items.length}/${totalElements})`}
          </button>
        </div>
      )}

      {/* Detail Modal */}
      {selectedItemForDetail && (
        <ItemDetailModal
          item={selectedItemForDetail}
          onClose={() => setSelectedItemForDetail(null)}
        />
      )}
    </div>
  );
}
