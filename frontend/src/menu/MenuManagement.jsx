import { useEffect, useRef, useState } from 'react';
import { demoCategories, demoItems } from './demoMenu.js';
import MenuEditor from './MenuEditor.jsx';
import DeleteDialog from './DeleteDialog.jsx';

const money = new Intl.NumberFormat('vi-VN');
const normalize = value => String(value).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
const otherModules = ['▦  Danh sách nhân viên', '◈  Quản lý bàn', '✦  Phản hồi', '▥  Thống kê doanh thu', '▣  Quản lý tin tức'];

export default function MenuManagement() {
  const [items, setItems] = useState(demoItems);
  const [categories, setCategories] = useState(demoCategories);
  const [kind, setKind] = useState('items');
  const [editor, setEditor] = useState(null);
  const [target, setTarget] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState('');
  const heading = useRef(null);
  const isItem = kind === 'items';
  const noun = isItem ? 'món' : 'nhóm món';
  const categoryName = code => categories.find(category => category.code === code)?.name ?? '';
  const filtered = (isItem ? items : categories).filter(row =>
    normalize(`${row.code} ${row.name} ${isItem ? categoryName(row.categoryCode) : ''}`).includes(normalize(keyword.trim())) &&
    (!isItem || !categoryFilter || row.categoryCode === categoryFilter));
  const pageCount = Math.max(1, Math.ceil(filtered.length / 10));
  const currentPage = Math.min(page, pageCount - 1);
  const rows = filtered.slice(currentPage * 10, currentPage * 10 + 10);

  useEffect(() => { if (editor === null) heading.current?.focus(); }, [editor, kind]);
  function changeKind(next) { setKind(next); setEditor(null); setKeyword(''); setCategoryFilter(''); setPage(0); setNotice(''); }
  function save(value) {
    const collection = isItem ? items : categories;
    if (!editor.value && collection.some(row => normalize(row.code) === normalize(value.code))) return 'Mã đã tồn tại. Vui lòng nhập mã khác.';
    if (collection.some(row => row.code !== editor.value?.code && normalize(row.name) === normalize(value.name))) return 'Tên đã tồn tại. Vui lòng nhập tên khác.';
    if (isItem && (!categories.some(category => category.code === value.categoryCode) || !Number.isSafeInteger(value.price) || value.price <= 0)) return 'Kiểm tra nhóm món và đơn giá nguyên dương.';
    const update = current => editor.value ? current.map(row => row.code === editor.value.code ? value : row) : [...current, value];
    (isItem ? setItems : setCategories)(update);
    setEditor(null); setPage(0); setKeyword(''); setCategoryFilter(''); setNotice(`Đã lưu ${noun} trong bản xem thử.`);
    return '';
  }
  const title = editor ? `${editor.value ? 'Chỉnh sửa' : 'Thêm'} ${noun}` : `Quản lý ${noun}`;

  return <div className="admin-shell">
    <a className="skip-link" href="#main-content">Đến nội dung chính</a>
    <aside className="sidebar">
      <div className="brand">LECKER<small>COFFEE CAFE</small></div>
      <nav aria-label="Điều hướng quản trị">
        <span className="nav-unavailable" title="Ngoài phạm vi FE thực đơn">◉  Tổng quan quản trị</span>
        <button className={isItem && !editor ? 'active' : ''} aria-current={isItem && !editor ? 'page' : undefined} onClick={() => changeKind('items')}>▤  Quản lý món</button>
        <button className={isItem && editor ? 'active' : ''} aria-current={isItem && editor ? 'page' : undefined} onClick={() => { setKind('items'); setEditor({ value: null }); }}>▧  Thêm / sửa món</button>
        <button className={!isItem ? 'active' : ''} aria-current={!isItem ? 'page' : undefined} onClick={() => changeKind('categories')}>▤  Quản lý nhóm món</button>
        {otherModules.map(label => <span className="nav-unavailable" key={label} title="Ngoài phạm vi FE thực đơn">{label}</span>)}
      </nav>
      <small className="sidebar-footer">SMART CAFÉ MANAGEMENT</small>
    </aside>
    <div className="workspace">
      <header className="topbar"><strong>KHU VỰC QUẢN TRỊ</strong><span className="header-tools" aria-hidden="true">⌕　 ◉　 Thông báo　 Tài khoản</span></header>
      <main id="main-content">
        <div className="page-heading"><h1 ref={heading} tabIndex={-1}>{title}</h1><p>LECKER Coffee Cafe • Smart Café Management System</p></div>
        <p className="demo-note">Bản xem thử giao diện · Dữ liệu minh họa, thay đổi được đặt lại khi tải lại trang.</p>
        {editor ? <MenuEditor key={`${kind}-${editor.value?.code ?? 'new'}`} kind={kind} value={editor.value} categories={categories} onSave={save} onCancel={() => setEditor(null)} /> : <section className="panel" aria-labelledby="list-title">
          <h2 id="list-title">Quản lý nhóm món &amp; món</h2>
          <p className="panel-description">Tìm kiếm, thêm, sửa, quản lý thực đơn</p>
          <div className="list-controls">
            <div className="view-switch" role="group" aria-label="Loại danh sách"><button aria-pressed={isItem} onClick={() => changeKind('items')}>Món</button><button aria-pressed={!isItem} onClick={() => changeKind('categories')}>Nhóm món</button></div>
            <label className="search"><span className="sr-only">Tìm {noun}</span><input type="search" placeholder={`Tìm mã, tên ${noun}…`} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></label>
            {isItem ? <label><span className="sr-only">Lọc nhóm món</span><select value={categoryFilter} onChange={e => { setCategoryFilter(e.target.value); setPage(0); }}><option value="">Tất cả nhóm món</option>{categories.map(category => <option key={category.code} value={category.code}>{category.name}</option>)}</select></label> : null}
          </div>
          <p className="table-hint">Vuốt ngang bảng để xem đầy đủ thông tin và thao tác.</p>
          <div className="table-scroll" role="region" aria-label={`Bảng ${noun}`} tabIndex={0}><table>
            <caption className="sr-only">Danh sách {noun} minh họa</caption>
            <thead><tr><th scope="col">Mã {noun}</th><th scope="col">Tên {noun}</th>{isItem ? <><th scope="col">Danh mục</th><th scope="col">Giá</th><th scope="col">Trạng thái</th></> : <th scope="col">Số món</th>}<th scope="col">Thao tác</th></tr></thead>
            <tbody>{rows.map(row => <tr key={row.code}>
              <th scope="row">{row.code}</th><td>{row.name}</td>
              {isItem ? <><td>{categoryName(row.categoryCode)}</td><td className="price">{money.format(row.price)}đ</td><td>{row.available ? 'Có sẵn' : 'Hết món'}</td></> : <td>{items.filter(item => item.categoryCode === row.code).length}</td>}
              <td><div className="row-actions"><button aria-label={`Sửa ${row.name}`} onClick={() => setEditor({ value: row })}>Sửa</button><button aria-label={`Xóa ${row.name}`} onClick={() => setTarget(row)}>Xóa</button></div></td>
            </tr>)}</tbody>
          </table></div>
          {rows.length === 0 ? <div className="empty"><h3>Không tìm thấy {noun}</h3><p>Thử từ khóa khác hoặc thêm {noun} mới.</p><button onClick={() => { setKeyword(''); setCategoryFilter(''); }}>Xóa bộ lọc</button></div> : null}
          <div className="list-footer"><button className="primary" onClick={() => setEditor({ value: null })}>THÊM {isItem ? 'MÓN' : 'NHÓM MÓN'} MỚI</button><div className="pagination"><span>{filtered.length} {noun} · {currentPage + 1}/{pageCount}</span><button aria-label="Trang trước" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>‹</button><button aria-label="Trang sau" disabled={currentPage + 1 >= pageCount} onClick={() => setPage(currentPage + 1)}>›</button></div></div>
        </section>}
        <p className="notice" role="status">{notice}</p>
      </main>
    </div>
    {target ? <DeleteDialog target={target} kind={kind} blocked={!isItem && items.some(item => item.categoryCode === target.code)} onClose={() => setTarget(null)} onConfirm={() => { (isItem ? setItems : setCategories)(current => current.filter(row => row.code !== target.code)); setTarget(null); setNotice(`Đã xóa ${noun} khỏi bản xem thử.`); }} /> : null}
  </div>;
}
