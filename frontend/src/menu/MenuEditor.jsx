import { useEffect, useRef, useState } from 'react';

export default function MenuEditor({ kind, value, categories, onSave, onCancel }) {
  const isItem = kind === 'items';
  const [form, setForm] = useState(value ?? { code: '', name: '', categoryCode: '', price: '', description: '', imageUrl: '', available: true });
  const [error, setError] = useState('');
  const [imageError, setImageError] = useState('');
  const [reading, setReading] = useState(false);
  const firstField = useRef(null);
  const reader = useRef(null);
  useEffect(() => {
    firstField.current?.focus();
    return () => reader.current?.abort();
  }, []);
  const update = (name, next) => setForm(current => ({ ...current, [name]: next }));

  function readImage(file) {
    if (!file) return;
    reader.current?.abort();
    setReading(false);
    setImageError('');
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setImageError('Chọn ảnh JPG, PNG hoặc WebP, tối đa 5 MB.');
      return;
    }
    const nextReader = new FileReader();
    reader.current = nextReader;
    setReading(true);
    nextReader.onload = () => { update('imageUrl', nextReader.result); setReading(false); };
    nextReader.onerror = () => { setImageError('Không đọc được ảnh. Vui lòng chọn lại.'); setReading(false); };
    nextReader.readAsDataURL(file);
  }

  function submit(event) {
    event.preventDefault();
    const next = { ...form, code: form.code.trim(), name: form.name.trim() };
    if (!next.code || !next.name) { setError('Mã và tên không được chỉ chứa khoảng trắng.'); return; }
    const failure = onSave(isItem ? { ...next, price: Number(next.price) } : { code: next.code, name: next.name });
    if (failure) setError(failure);
  }

  return <form className="panel editor" onSubmit={submit}>
    <h2>{value ? 'Chỉnh sửa' : 'Thêm'} {isItem ? 'món' : 'nhóm món'}</h2>
    <p className="panel-description">{isItem ? 'Ảnh sản phẩm / Tải ảnh lên' : 'Thông tin nhóm món trong thực đơn'}</p>
    <div className="form-grid">
      <label>Mã {isItem ? 'món' : 'nhóm món'} *
        <input ref={firstField} name="code" required maxLength={40} pattern="[A-Za-z0-9_-]+" title="Chữ Latin, số, dấu gạch ngang hoặc gạch dưới" readOnly={Boolean(value)} value={form.code} onChange={e => update('code', e.target.value)} placeholder={isItem ? 'VD: CF104' : 'VD: CF'} />
      </label>
      <label>Tên {isItem ? 'món' : 'nhóm món'} *
        <input name="name" required maxLength={160} value={form.name} onChange={e => update('name', e.target.value)} placeholder={isItem ? 'VD: Mocha Latte' : 'VD: Cà phê'} />
      </label>
      {isItem ? <>
        <label>Danh mục *
          <select name="categoryCode" required value={form.categoryCode} onChange={e => update('categoryCode', e.target.value)}>
            <option value="">Chọn nhóm món</option>
            {categories.map(category => <option key={category.code} value={category.code}>{category.name}</option>)}
          </select>
        </label>
        <label>Đơn giá (đ) *<input name="price" required type="number" min="1" max="999999999" step="1" value={form.price} onChange={e => update('price', e.target.value)} placeholder="48.000" /></label>
        <label>Mô tả<textarea name="description" maxLength={1000} value={form.description} onChange={e => update('description', e.target.value)} placeholder="Espresso + Chocolate" /></label>
        <div className="image-field">
          <label htmlFor="product-image">Ảnh sản phẩm</label>
          <div className="upload" onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); readImage(e.dataTransfer.files[0]); }}>
            <span aria-hidden="true">↑</span> Kéo thả ảnh hoặc chọn tệp
            <small>JPG / PNG / WebP • Kích thước tối đa 5 MB</small>
            <input id="product-image" type="file" accept="image/jpeg,image/png,image/webp" aria-describedby="image-error" aria-invalid={Boolean(imageError)} onChange={e => { readImage(e.target.files[0]); e.target.value = ''; }} />
          </div>
          <p id="image-error" className="error" role="alert">{imageError}</p>
          {reading ? <p role="status">Đang đọc ảnh…</p> : null}
          {form.imageUrl ? <div className="image-preview"><img src={form.imageUrl} alt="Xem trước ảnh món" /><button type="button" onClick={() => update('imageUrl', '')}>Bỏ ảnh</button></div> : null}
        </div>
        <label className="availability"><input type="checkbox" checked={form.available} onChange={e => update('available', e.target.checked)} />Có sẵn</label>
      </> : null}
    </div>
    {error ? <p role="alert" className="error">{error}</p> : null}
    <div className="form-actions"><button type="button" onClick={onCancel}>HỦY</button><button className="primary" disabled={reading}>{isItem ? 'LƯU MÓN' : 'LƯU NHÓM MÓN'}</button></div>
  </form>;
}
