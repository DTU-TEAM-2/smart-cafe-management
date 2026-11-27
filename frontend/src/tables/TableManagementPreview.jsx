import { useEffect, useRef, useState } from "react";
import "./tables.css";

const normalize = value => value.trim().toLocaleLowerCase("vi");
const PAGE_SIZE = 10;

// This development-only UI has no API, authentication or database mapping.
export default function TableManagementPreview() {
  const [tables, setTables] = useState([]);
  const [selectedKey, setSelectedKey] = useState(null);
  const [adding, setAdding] = useState(false);
  const [query, setQuery] = useState("");
  const [searchField, setSearchField] = useState("displayId");
  const [filter, setFilter] = useState(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [removing, setRemoving] = useState(null);
  const editor = useRef(null);
  const addButton = useRef(null);
  const selected = tables.find(table => table.key === selectedKey) ?? null;
  const filtered = filter ? tables.filter(table => normalize(table[filter.field]).includes(filter.keyword)) : tables;
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const visible = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  function clearFeedback() { setError(""); setNotice(""); }
  function resetSelection() { setSelectedKey(null); setAdding(false); }

  function choose(table) {
    clearFeedback();
    setAdding(false);
    setSelectedKey(table.key);
  }

  function startAdd() {
    clearFeedback();
    setSelectedKey(null);
    setAdding(true);
  }

  function save(values) {
    clearFeedback();
    if (!adding && !selected) return "No table is chosen. Vui lòng chọn một bàn.";
    if (tables.some(table => table.key !== selectedKey && normalize(table.displayId) === normalize(values.displayId))) {
      return "ID đã tồn tại trong danh sách thử. Vui lòng nhập lại.";
    }
    const key = adding ? crypto.randomUUID() : selected.key;
    setTables(current => adding
      ? [...current, { key, ...values }]
      : current.map(table => table.key === key ? { ...table, state: values.state } : table));
    setSelectedKey(key);
    setAdding(false);
    setFilter(null);
    setQuery("");
    setPage(Math.floor((adding ? tables.length : tables.findIndex(table => table.key === key)) / PAGE_SIZE));
    setNotice(adding ? "Đã thêm bàn vào bản thử." : "Đã lưu thay đổi trong bản thử.");
    return null;
  }

  function search(event) {
    event.preventDefault();
    clearFeedback();
    if (!query.trim()) { setError("Vui lòng nhập từ khóa tìm kiếm."); return; }
    setFilter({ field: searchField, keyword: normalize(query) });
    setPage(0);
    resetSelection();
  }

  return <main className="table-preview" id="table-main">
    <header className="table-preview-header">
      <div><p className="table-eyebrow">MANAGE / TABLE · DT2-11</p><h1>Quản lý bàn</h1><p>Danh sách bàn và thông tin của bàn đang chọn.</p></div>
      <a href="/">Về trang chủ</a>
    </header>
    <aside className="table-preview-note" aria-label="Phạm vi bản thử">
      <strong>Bản thử giao diện độc lập</strong>
      <p>Dữ liệu chỉ nằm trong trang này và mất khi tải lại. Các thao tác không thay đổi dữ liệu của quán.</p>
      <p>ID và State đang là nội dung nhập thử; ý nghĩa ID, State/On và quy tắc xóa còn chờ xác nhận. On chưa có thao tác mở hoặc reset phiên.</p>
    </aside>
    <div className="table-preview-toolbar">
      <p><strong>{tables.length}</strong> bàn trong bản thử</p>
      <div className="table-preview-actions">
        <button ref={addButton} className="table-primary" onClick={startAdd}>Thêm bàn (Add)</button>
        <button onClick={() => {
          clearFeedback();
          if (!selected) { setError("No table is chosen. Vui lòng chọn một bàn."); return; }
          editor.current?.querySelector("input[name=state]")?.focus();
        }}>Sửa bàn (Edit)</button>
        <button className="table-danger" onClick={() => {
          clearFeedback();
          if (!selected) { setError("No table is chosen. Vui lòng chọn một bàn."); return; }
          setRemoving(selected);
        }}>Xóa bàn (Remove)</button>
      </div>
    </div>
    <p className="table-feedback" role="status">{notice}</p>
    {error && <p className="table-error" role="alert">{error}</p>}
    <div className="table-preview-grid">
      <section className="table-panel" aria-labelledby="table-list-title">
        <h2 id="table-list-title">Danh sách bàn</h2>
        <form className="table-search" onSubmit={search}>
          <label>Tìm theo<select value={searchField} onChange={event => setSearchField(event.target.value)}><option value="displayId">ID</option><option value="state">State</option></select></label>
          <label>Từ khóa<input type="search" value={query} onChange={event => setQuery(event.target.value)} /></label>
          <button type="submit">Tìm kiếm</button>
          <button type="button" onClick={() => {
            clearFeedback(); setFilter(null); setQuery(""); setPage(0); resetSelection();
            setNotice("Đã hiển thị danh sách đầy đủ.");
          }}>Show List Table</button>
        </form>
        <p className="table-list-summary" role="status">{filtered.length} kết quả{filter ? ` · Tìm theo ${filter.field === "displayId" ? "ID" : "State"}: ${filter.keyword}` : ""}</p>
        <div className="table-list-scroll">
          <table>
            <caption className="table-sr-only">Danh sách bàn nhập thử. Chọn một bàn để xem Table’s Info.</caption>
            <thead><tr><th scope="col">ID</th><th scope="col">State</th><th scope="col">On</th></tr></thead>
            <tbody>{visible.map(table => <tr key={table.key} className={table.key === selectedKey ? "table-selected" : ""}>
              <th scope="row"><button aria-label={`Chọn bàn ${table.displayId}`} aria-pressed={table.key === selectedKey} onClick={() => choose(table)}>{table.displayId}</button></th>
              <td>{table.state}</td><td><span className="table-pending">Chưa chốt</span></td>
            </tr>)}</tbody>
          </table>
        </div>
        {filtered.length === 0 && <div className="table-empty"><h3>{filter ? "Không tìm thấy bàn" : "Chưa có bàn trong bản thử"}</h3><p>{filter ? "Thử từ khóa khác hoặc chọn Show List Table." : "Chọn Thêm bàn để bắt đầu thử giao diện."}</p></div>}
        <nav className="table-pagination" aria-label="Phân trang danh sách bàn">
          <button disabled={currentPage === 0} onClick={() => { setPage(currentPage - 1); resetSelection(); clearFeedback(); }}>Trang trước</button>
          <span>Trang {currentPage + 1} / {pageCount}</span>
          <button disabled={currentPage + 1 >= pageCount} onClick={() => { setPage(currentPage + 1); resetSelection(); clearFeedback(); }}>Trang sau</button>
        </nav>
      </section>
      <section ref={editor} className="table-panel table-info" aria-labelledby="table-info-title">
        <p className="table-eyebrow">TABLE’S INFO</p><h2 id="table-info-title">{adding ? "Thêm bàn mới" : "Thông tin bàn"}</h2>
        {adding || selected ? <TableEditor key={adding ? "new" : selected.key} table={selected} onSave={save} onCancel={() => {
          resetSelection(); clearFeedback(); addButton.current?.focus();
        }} /> : <p className="table-empty-info">Chọn một bàn trong danh sách để sửa State, hoặc chọn Thêm bàn.</p>}
      </section>
    </div>
    {removing && <RemoveTableDialog table={removing} onClose={() => setRemoving(null)} onConfirm={() => {
      setTables(current => current.filter(table => table.key !== removing.key));
      resetSelection(); setRemoving(null); clearFeedback();
      setNotice(`Đã xóa bàn ${removing.displayId} khỏi bản thử.`);
    }} />}
  </main>;
}

function TableEditor({ table, onSave, onCancel }) {
  const [displayId, setDisplayId] = useState(table?.displayId ?? "");
  const [state, setState] = useState(table?.state ?? "");
  const [errors, setErrors] = useState({});
  const form = useRef(null);

  useEffect(() => { form.current?.querySelector(table ? "input[name=state]" : "input[name=displayId]")?.focus(); }, [table]);

  function submit(event) {
    event.preventDefault();
    const nextErrors = {};
    if (!displayId.trim()) nextErrors.displayId = "Vui lòng nhập ID.";
    if (!state.trim()) nextErrors.state = "Vui lòng nhập State.";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      form.current.elements.namedItem(Object.keys(nextErrors)[0])?.focus();
      return;
    }
    const message = onSave({ displayId: displayId.trim(), state: state.trim() });
    if (message) { setErrors({ displayId: message }); form.current.elements.namedItem("displayId")?.focus(); }
  }

  return <form ref={form} className="table-editor" onSubmit={submit} noValidate>
    <label htmlFor="table-display-id">ID *</label>
    <input id="table-display-id" name="displayId" value={displayId} readOnly={Boolean(table)} required
      aria-invalid={Boolean(errors.displayId)} aria-describedby={`table-id-help${errors.displayId ? " table-id-error" : ""}`}
      onChange={event => setDisplayId(event.target.value)} />
    <small id="table-id-help">{table ? "ID giữ nguyên khi sửa trong bản thử." : "Nhãn nhập thử, chưa ánh xạ sang mã hoặc số bàn trong database."}</small>
    {errors.displayId && <p id="table-id-error" className="table-error" role="alert">{errors.displayId}</p>}
    <label htmlFor="table-state">State *</label>
    <input id="table-state" name="state" value={state} required aria-invalid={Boolean(errors.state)}
      aria-describedby={`table-state-help${errors.state ? " table-state-error" : ""}`} onChange={event => setState(event.target.value)} />
    <small id="table-state-help">Nhập nội dung để thử layout; chưa chốt tập trạng thái nghiệp vụ.</small>
    {errors.state && <p id="table-state-error" className="table-error" role="alert">{errors.state}</p>}
    <div className="table-preview-actions"><button className="table-primary" type="submit">{table ? "Lưu thay đổi" : "Lưu bàn mới"}</button><button type="button" onClick={onCancel}>Hủy</button></div>
    <small>* Trường bắt buộc trong bản thử.</small>
  </form>;
}

function RemoveTableDialog({ table, onClose, onConfirm }) {
  const dialog = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const element = dialog.current;
    element.showModal();
    return () => { element.close(); if (previous?.isConnected) previous.focus(); };
  }, []);
  function keepFocus(event) {
    if (event.key !== "Tab") return;
    const buttons = dialog.current.querySelectorAll("button");
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }
  return <dialog ref={dialog} className="table-remove-dialog table-preview" aria-labelledby="table-remove-title" aria-describedby="table-remove-description" onCancel={onClose} onKeyDown={keepFocus}>
    <h2 id="table-remove-title">Xóa bàn {table.displayId}?</h2>
    <p id="table-remove-description">Chỉ xóa khỏi danh sách thử trên trang này. Quy tắc xóa bàn thật và bảo toàn lịch sử còn chờ xác nhận.</p>
    <div className="table-preview-actions"><button autoFocus onClick={onClose}>Hủy xóa</button><button className="table-danger" onClick={onConfirm}>Xác nhận xóa</button></div>
  </dialog>;
}
