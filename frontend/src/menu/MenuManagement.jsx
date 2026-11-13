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
  const [field, setField] = useState('name');
  const [selectedCode, setSelectedCode] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState('');
  const heading = useRef(null);
  const isItem = kind === 'items';
  const noun = isItem ? 'món' : 'nhóm món';
  const activeCategories = categories.filter(category => category.active !== false);
  const categoryName = code => categories.find(category => category.code === code)?.name ?? '';
  const term = keyword.trim();
  const filtered = (isItem ? items : categories).filter(row => {
    if (row.active === false || (isItem && categoryFilter && row.categoryCode !== categoryFilter)) return false;
    if (!term) return true;
    if (field === 'code') return row.code === term.toUpperCase();
    if (field === 'price') return /^\d{1,18}$/.test(term) && BigInt(row.price) === BigInt(term);
    if (field === 'category') return row.categoryCode === term.toUpperCase() || normalize(categoryName(row.categoryCode)).includes(normalize(term));
    return normalize(row.name).includes(normalize(term));
  });
  const pageCount = Math.max(1, Math.ceil(filtered.length / 10));
  const currentPage = Math.min(page, pageCount - 1);
  const rows = filtered.slice(currentPage * 10, currentPage * 10 + 10);

  useEffect(() => { if (editor === null) heading.current?.focus(); }, [editor, kind]);
  function resetList() { setKeyword(''); setField('name'); setCategoryFilter(''); setPage(0); setSelectedCode(''); }
  function changeKind(next) { setKind(next); setEditor(null); resetList(); setNotice(''); }
  function selectedAction(action) {
    const row = rows.find(row => row.code === selectedCode);
    if (!row) { setNotice(`Vui lòng chọn ${noun} trước khi ${action === 'edit' ? 'sửa' : 'xóa'}.`); return; }
    if (action === 'edit') setEditor({ value: row });
    else setTarget(row);
  }
  function confirmDelete() {
    if (isItem) setItems(current => current.map(row => row.code === target.code ? { ...row, active: false } : row));
    else {
      setCategories(current => current.map(row => row.code === target.code ? { ...row, active: false } : row));
      setItems(current => current.map(row => row.categoryCode === target.code ? { ...row, active: false } : row));
    }
    setTarget(null); setSelectedCode(''); setNotice(`Đã ẩn ${noun}${isItem ? '' : ' và các món thuộc nhóm'} trong bản xem thử.`);
  }
  function save(value) {
    const collection = isItem ? items : categories;
    if (!editor.value && collection.some(row => row.code === value.code)) return { code: 'Mã đã tồn tại hoặc đã bị xóa. Vui lòng nhập mã khác.' };
    if (isItem && !activeCategories.some(category => category.code === value.categoryCode)) return { categoryCode: 'Chọn nhóm món đang hoạt động.' };
    const update = current => editor.value ? current.map(row => row.code === editor.value.code ? value : row) : [...current, value];
    (isItem ? setItems : setCategories)(update);
    setEditor(null); resetList(); setNotice(`Đã lưu ${noun} trong bản xem thử.`);
    return null;
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
        {editor ? <MenuEditor key={`${kind}-${editor.value?.code ?? 'new'}`} kind={kind} value={editor.value} categories={activeCategories} onSave={save} onCancel={() => setEditor(null)} /> : <section className="panel" aria-labelledby="list-title">
          <h2 id="list-title">Quản lý nhóm món &amp; món</h2>
          <p className="panel-description">Tìm kiếm, thêm, sửa, quản lý thực đơn</p>
          <div className="view-switch" role="group" aria-label="Loại danh sách"><button aria-pressed={isItem} onClick={() => changeKind('items')}>Món</button><button aria-pressed={!isItem} onClick={() => changeKind('categories')}>Nhóm món</button></div>
          <form className="list-controls" onSubmit={event => { event.preventDefault(); setNotice(!term ? 'Vui lòng nhập từ khóa tìm kiếm.' : field === 'price' && !/^\d{1,18}$/.test(term) ? 'Giá tìm kiếm phải là số VND nguyên, tối đa 18 chữ số.' : `Tìm thấy ${filtered.length} ${noun}.`); }}>
            <label><span className="sr-only">Tiêu chí tìm kiếm</span><select value={field} onChange={e => { setField(e.target.value); setPage(0); setSelectedCode(''); setNotice(''); }}><option value="code">Mã {noun}</option><option value="name">Tên {noun}</option>{isItem ? <><option value="price">Đơn giá</option><option value="category">Nhóm món</option></> : null}</select></label>
            <label className="search"><span className="sr-only">Tìm {noun}</span><input type="search" placeholder={`Nhập từ khóa tìm ${noun}…`} maxLength={160} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); setSelectedCode(''); setNotice(''); }} /></label>
            {isItem ? <label><span className="sr-only">Lọc nhóm món</span><select value={categoryFilter} onChange={e => { setCategoryFilter(e.target.value); setPage(0); setSelectedCode(''); setNotice(''); }}><option value="">Tất cả nhóm món</option>{activeCategories.map(category => <option key={category.code} value={category.code}>{category.name}</option>)}</select></label> : null}
            <button type="submit">TÌM KIẾM</button>
            <button type="button" onClick={() => { resetList(); setNotice(`Đã hiển thị toàn bộ danh sách ${noun} đang hoạt động.`); }}>HIỂN THỊ TẤT CẢ</button>
          </form>
          <div className="selection-actions" role="group" aria-label={`Thao tác ${noun} đã chọn`}><button onClick={() => selectedAction('edit')}>SỬA ĐÃ CHỌN</button><button onClick={() => selectedAction('delete')}>XÓA ĐÃ CHỌN</button></div>
          <p className="table-hint">Vuốt ngang bảng để xem đầy đủ thông tin và thao tác.</p>
          <div className="table-scroll" role="region" aria-label={`Bảng ${noun}`} tabIndex={0}><table>
            <caption className="sr-only">Danh sách {noun} minh họa</caption>
            <thead><tr><th scope="col">Chọn</th><th scope="col">Mã {noun}</th><th scope="col">Tên {noun}</th>{isItem ? <><th scope="col">Danh mục</th><th scope="col">Giá</th><th scope="col">Trạng thái</th></> : <th scope="col">Số món</th>}<th scope="col">Thao tác</th></tr></thead>
            <tbody>{rows.map(row => <tr key={row.code}>
              <td className="row-select"><input type="radio" name="selected-menu-row" aria-label={`Chọn ${row.name}`} checked={selectedCode === row.code} onChange={() => { setSelectedCode(row.code); setNotice(''); }} /></td>
              <th scope="row">{row.code}</th><td>{row.name}</td>
              {isItem ? <><td>{categoryName(row.categoryCode)}</td><td className="price">{money.format(BigInt(row.price))}đ</td><td>{row.available ? 'Có sẵn' : 'Hết món'}</td></> : <td>{items.filter(item => item.active !== false && item.categoryCode === row.code).length}</td>}
              <td><div className="row-actions"><button aria-label={`Sửa ${row.name}`} onClick={() => setEditor({ value: row })}>Sửa</button><button aria-label={`Xóa ${row.name}`} onClick={() => setTarget(row)}>Xóa</button></div></td>
            </tr>)}</tbody>
          </table></div>
          {rows.length === 0 ? <div className="empty" role="status"><h3>Không tìm thấy {noun}</h3><p>Thử từ khóa khác hoặc thêm {noun} mới.</p><button onClick={resetList}>Xóa bộ lọc</button></div> : null}
          <div className="list-footer"><button className="primary" onClick={() => setEditor({ value: null })}>THÊM {isItem ? 'MÓN' : 'NHÓM MÓN'} MỚI</button><div className="pagination"><span>{filtered.length} {noun} · {currentPage + 1}/{pageCount}</span><button aria-label="Trang trước" disabled={currentPage === 0} onClick={() => { setPage(currentPage - 1); setSelectedCode(''); }}>‹</button><button aria-label="Trang sau" disabled={currentPage + 1 >= pageCount} onClick={() => { setPage(currentPage + 1); setSelectedCode(''); }}>›</button></div></div>
        </section>}
        <p className="notice" role="status">{notice}</p>
      </main>
    </div>
    {target ? <DeleteDialog target={target} kind={kind} itemCount={items.filter(item => item.active !== false && item.categoryCode === target.code).length} onClose={() => setTarget(null)} onConfirm={confirmDelete} /> : null}
  </div>;
}
