import { useEffect, useRef, useState } from 'react';
import MenuEditor from './MenuEditor.jsx';
import DeleteDialog from './DeleteDialog.jsx';
import { deleteMenu, listMenu, readMenu, saveMenu } from './menuApi.js';
import './menu.css';

const money = new Intl.NumberFormat('vi-VN');
const pageSize = 10;
const blankQuery = { field: 'name', keyword: '', categoryCode: '', page: 0 };

async function loadCategories(authorization, signal) {
  const size = 100;
  const first = await listMenu('categories', { page: 0, size }, authorization, signal);
  const remaining = Math.max(0, Math.ceil(first.totalElements / size) - 1);
  const categories = new Map(first.content.map(row => [row.code, row]));
  for (let page = 1; page <= remaining; page += 1) {
    const result = await listMenu('categories', { page, size }, authorization, signal);
    for (const row of result.content) categories.set(row.code, row);
  }
  return [...categories.values()];
}

export default function MenuAdmin({ authorization }) {
  const [kind, setKind] = useState('items');
  const [query, setQuery] = useState(blankQuery);
  const [keyword, setKeyword] = useState('');
  const [result, setResult] = useState(null);
  const [categoryResult, setCategoryResult] = useState(null);
  const [revision, setRevision] = useState(0);
  const [editor, setEditor] = useState(null);
  const [target, setTarget] = useState(null);
  const [selected, setSelected] = useState('');
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [busy, setBusy] = useState(false);
  const action = useRef(null);
  const uploadedImages = useRef(new WeakMap());
  const heading = useRef(null);
  const itemView = kind === 'items';
  const noun = itemView ? 'món' : 'nhóm món';
  const current = result?.query === query && result.kind === kind && result.revision === revision ? result : null;
  const categories = categoryResult?.revision === revision ? categoryResult.data : null;
  const rows = current?.data?.content ?? [];
  const pageCount = Math.max(1, Math.ceil((current?.data?.totalElements ?? 0) / pageSize));

  useEffect(() => {
    const controller = new AbortController();
    const params = { page: query.page, size: pageSize, keyword: query.keyword };
    if (kind === 'items') {
      params.field = query.field;
      if (query.categoryCode) params.categoryCode = query.categoryCode;
    }
    listMenu(kind, params, authorization, controller.signal).then(data => {
      if (controller.signal.aborted) return;
      const last = Math.max(0, Math.ceil(data.totalElements / pageSize) - 1);
      if (query.page > last) setQuery(current => current === query ? { ...current, page: last } : current);
      else setResult({ query, kind, revision, data });
    }).catch(error => {
      if (!controller.signal.aborted) setResult({ query, kind, revision, error: error.message });
    });
    return () => controller.abort();
  }, [authorization, kind, query, revision]);

  useEffect(() => {
    const controller = new AbortController();
    loadCategories(authorization, controller.signal).then(data => {
      if (!controller.signal.aborted) setCategoryResult({ revision, data });
    }).catch(error => {
      if (!controller.signal.aborted) setCategoryResult({ revision, error: error.message });
    });
    return () => controller.abort();
  }, [authorization, revision]);

  useEffect(() => () => action.current?.abort(), []);
  useEffect(() => { if (!editor) heading.current?.focus(); }, [editor, kind]);

  function changeQuery(next) {
    setQuery(next);
    setSelected('');
    setActionError('');
    setNotice('');
  }

  function showAll() {
    setKeyword('');
    changeQuery({ ...blankQuery });
  }

  function switchKind(next) {
    setKind(next);
    setEditor(null);
    showAll();
  }

  function refresh(message = '') {
    setSelected('');
    setActionError('');
    setNotice(message);
    setRevision(current => current + 1);
  }

  async function runAction(work) {
    if (action.current) return;
    const controller = new AbortController();
    action.current = controller;
    setBusy(true);
    try {
      return await work(controller.signal);
    } finally {
      action.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  async function openAction(row, editing) {
    setActionError('');
    if (!row) {
      setActionError(`Vui lòng chọn ${noun} trước khi ${editing ? 'sửa' : 'xóa'}.`);
      return;
    }
    try {
      await runAction(async signal => {
        const latest = await readMenu(kind, row.code, authorization, signal);
        if (signal.aborted) return;
        if (editing) setEditor({ value: latest });
        else {
          setDeleteError('');
          setTarget(latest);
        }
      });
    } catch (error) {
      if (error.name !== 'AbortError') setActionError(error.message);
    }
  }

  async function save(value) {
    await runAction(async signal => {
      await saveMenu(kind, value, Boolean(editor.value), authorization, signal, uploadedImages.current);
      if (signal.aborted) return;
      setEditor(null);
      refresh(`Đã lưu ${noun}.`);
    });
    return null;
  }

  async function confirmDelete() {
    setDeleteError('');
    try {
      await runAction(async signal => {
        await deleteMenu(kind, target, authorization, signal);
        if (signal.aborted) return;
        setTarget(null);
        refresh(`Đã ẩn ${noun}${itemView ? '' : ' và các món thuộc nhóm'}.`);
      });
    } catch (error) {
      if (error.name !== 'AbortError') setDeleteError(error.message);
    }
  }

  function submitSearch(event) {
    event.preventDefault();
    const term = keyword.trim();
    if (!term) {
      setActionError('Vui lòng nhập từ khóa tìm kiếm.');
      return;
    }
    if (itemView && query.field === 'price' && !/^\d{1,18}$/.test(term)) {
      setActionError('Giá tìm kiếm phải là số VND nguyên, tối đa 18 chữ số.');
      return;
    }
    changeQuery({ ...query, keyword: term, page: 0 });
  }

  const selectedRow = rows.find(row => row.code === selected);
  const categoryError = categoryResult?.revision === revision ? categoryResult.error : null;
  const canEdit = !busy && (!itemView || Boolean(categories));
  return <div className="menu-preview admin-shell">
    <div className="workspace">
      <header className="topbar"><a href="#admin">Về bảng điều khiển</a></header>
      <main id="main-content" aria-busy={busy}>
        <div className="page-heading"><h1 ref={heading} tabIndex={-1}>Quản lý thực đơn</h1></div>
        {editor ? <MenuEditor key={editor.value?.code ?? 'new'} kind={kind} value={editor.value}
          categories={categories ?? []} onSave={save} onCancel={() => setEditor(null)} /> : <section className="panel" aria-labelledby="menu-list-title">
          <h2 id="menu-list-title">Danh sách {noun}</h2>
          <div className="view-switch" role="group" aria-label="Loại danh sách">
            <button disabled={busy} aria-pressed={itemView} onClick={() => switchKind('items')}>Món</button>
            <button disabled={busy} aria-pressed={!itemView} onClick={() => switchKind('categories')}>Nhóm món</button>
          </div>
          <form className="list-controls" onSubmit={submitSearch}>
            {itemView ? <label><span className="sr-only">Tiêu chí tìm kiếm</span><select disabled={busy} value={query.field} onChange={event => {
              setKeyword('');
              changeQuery({ ...query, field: event.target.value, keyword: '', page: 0 });
            }}><option value="code">Mã món</option><option value="name">Tên món</option><option value="price">Đơn giá</option><option value="category">Nhóm món</option></select></label> : null}
            <label className="search"><span className="sr-only">Tìm {noun}</span><input type="search" disabled={busy} maxLength={160} value={keyword} onChange={event => setKeyword(event.target.value)} /></label>
            {itemView ? <label><span className="sr-only">Lọc nhóm món</span><select disabled={busy || !categories} value={query.categoryCode} onChange={event => changeQuery({ ...query, categoryCode: event.target.value, page: 0 })}>
              <option value="">Tất cả nhóm món</option>{categories?.map(row => <option key={row.code} value={row.code}>{row.name}</option>)}
            </select></label> : null}
            <button disabled={busy}>TÌM KIẾM</button><button type="button" disabled={busy} onClick={showAll}>HIỂN THỊ TẤT CẢ</button>
          </form>
          {itemView && categoryError ? <p role="alert">{categoryError} <button disabled={busy} onClick={() => refresh()}>Tải lại nhóm món</button></p> : null}
          {itemView && !categories && !categoryError ? <p role="status">Đang tải nhóm món…</p> : null}
          {!current ? <p role="status">Đang tải {noun}…</p> : null}
          {current?.error ? <p role="alert">{current.error} <button disabled={busy} onClick={() => refresh()}>Thử lại</button></p> : null}
          {current?.data ? <>
            <div className="selection-actions" role="group" aria-label="Thao tác với mục đã chọn">
              <button disabled={!canEdit} onClick={() => void openAction(selectedRow, true)}>SỬA ĐÃ CHỌN</button>
              <button disabled={busy} onClick={() => void openAction(selectedRow, false)}>XÓA ĐÃ CHỌN</button>
            </div>
            <div className="table-scroll" role="region" aria-label={`Bảng ${noun}`} tabIndex={0}><table>
              <caption className="sr-only">Danh sách {noun}</caption>
              <thead><tr><th scope="col">Chọn</th><th scope="col">Mã</th><th scope="col">Tên</th>
                {itemView ? <><th scope="col">Danh mục</th><th scope="col">Giá</th><th scope="col">Trạng thái</th></> : <th scope="col">Số món</th>}
                <th scope="col">Thao tác</th></tr></thead>
              <tbody>{rows.map(row => <tr key={row.code}>
                <td className="row-select"><input type="radio" name="menu-row" aria-label={`Chọn ${row.name}`} checked={selected === row.code} onChange={() => setSelected(row.code)} /></td>
                <th scope="row">{row.code}</th><td>{row.name}</td>
                {itemView ? <><td>{row.categoryName}</td><td className="price">{money.format(BigInt(row.price))}đ</td><td>{row.available ? 'Có sẵn' : 'Hết món'}</td></> : <td>{row.itemCount}</td>}
                <td><div className="row-actions">
                  <button disabled={!canEdit} aria-label={`Sửa ${row.name}`} onClick={() => void openAction(row, true)}>Sửa</button>
                  <button disabled={busy} aria-label={`Xóa ${row.name}`} onClick={() => void openAction(row, false)}>Xóa</button>
                </div></td>
              </tr>)}</tbody>
            </table></div>
            {rows.length === 0 ? <p role="status">Không tìm thấy {noun}.</p> : null}
            <div className="list-footer">
              <button className="primary" disabled={!canEdit} onClick={() => setEditor({ value: null })}>THÊM {itemView ? 'MÓN' : 'NHÓM MÓN'} MỚI</button>
              <div className="pagination"><span>{current.data.totalElements} {noun} · {query.page + 1}/{pageCount}</span>
                <button aria-label="Trang trước" disabled={busy || query.page === 0} onClick={() => changeQuery({ ...query, page: query.page - 1 })}>‹</button>
                <button aria-label="Trang sau" disabled={busy || query.page + 1 >= pageCount} onClick={() => changeQuery({ ...query, page: query.page + 1 })}>›</button>
              </div>
            </div>
          </> : null}
        </section>}
        {notice ? <p className="notice" role="status">{notice}</p> : null}
        {actionError ? <p className="error" role="alert">{actionError}</p> : null}
      </main>
    </div>
    {target ? <DeleteDialog target={target} kind={kind} itemCount={target.itemCount} preview={false} pending={busy}
      error={deleteError} onClose={() => setTarget(null)} onConfirm={() => void confirmDelete()} /> : null}
  </div>;
}
