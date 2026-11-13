import { useEffect, useRef, useState } from 'react';
import { imageDimensions } from './imageDimensions.js';

export default function MenuEditor({ kind, value, categories, onSave, onCancel }) {
  const isItem = kind === 'items';
  const [form, setForm] = useState(value ?? { code: '', name: '', categoryCode: '', price: '', imageUrl: '', available: true });
  const [errors, setErrors] = useState({});
  const [imageError, setImageError] = useState('');
  const [reading, setReading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const firstField = useRef(null);
  const reader = useRef(null);
  const imageRequest = useRef(0);
  useEffect(() => {
    firstField.current?.focus();
    return () => { imageRequest.current += 1; reader.current?.abort(); };
  }, []);
  const update = (name, next) => {
    setForm(current => ({ ...current, [name]: next }));
    setErrors(current => ({ ...current, [name]: '' }));
  };
  const fieldError = name => errors[name] ? <span id={`menu-${name}-error`} className="error" role="alert">{errors[name]}</span> : null;
  const errorProps = name => ({ 'aria-invalid': Boolean(errors[name]), 'aria-describedby': errors[name] ? `menu-${name}-error` : undefined });
  function clearImage() {
    imageRequest.current += 1;
    reader.current?.abort();
    setReading(false); setImageError('');
    setForm(current => ({ ...current, imageUrl: '', imageFile: null, imageMediaId: null }));
  }

  async function readImage(file) {
    if (!file) return;
    const request = ++imageRequest.current;
    reader.current?.abort();
    setReading(false);
    setImageError('');
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024) {
      setImageError('Chọn ảnh JPG hoặc PNG hợp lệ, tối đa 5 MB và 20 triệu pixel.');
      return;
    }
    setReading(true);
    try {
      const buffer = await file.arrayBuffer();
      if (request !== imageRequest.current) return;
      const header = imageDimensions(buffer, file.type);
      const image = await createImageBitmap(file);
      const pixels = image.width * image.height;
      image.close();
      if (pixels !== header.width * header.height || pixels > 20_000_000) throw new Error('Invalid image dimensions');
    } catch {
      if (request === imageRequest.current) { setImageError('Không đọc được ảnh JPG/PNG hợp lệ hoặc ảnh vượt 20 triệu pixel.'); setReading(false); }
      return;
    }
    if (request !== imageRequest.current) return;
    const nextReader = new FileReader();
    reader.current = nextReader;
    nextReader.onload = () => {
      if (request === imageRequest.current) {
        setForm(current => ({ ...current, imageUrl: nextReader.result, imageFile: file }));
        setReading(false);
      }
    };
    nextReader.onerror = () => { if (request === imageRequest.current) { setImageError('Không đọc được ảnh. Vui lòng chọn lại.'); setReading(false); } };
    nextReader.readAsDataURL(file);
  }

  async function submit(event) {
    event.preventDefault();
    if (reading || imageError || saving) return;
    const fields = event.currentTarget.elements;
    const next = { ...form, code: form.code.trim().toUpperCase(), name: form.name.trim(), price: String(form.price ?? '').trim() };
    const failures = {};
    if (!(isItem ? /^[A-Z0-9_-]{4,40}$/ : /^[A-Z0-9_-]{1,32}$/).test(next.code)) failures.code = `Mã gồm ${isItem ? '4–40' : '1–32'} ký tự Latin, số, gạch ngang hoặc gạch dưới.`;
    if (next.name.length < (isItem ? 6 : 1) || next.name.length > (isItem ? 160 : 120)) failures.name = `Tên sau khi bỏ khoảng trắng phải dài ${isItem ? '6–160' : '1–120'} ký tự.`;
    if (isItem) {
      if (!categories.some(category => category.code === next.categoryCode)) failures.categoryCode = 'Chọn nhóm món đang hoạt động.';
      if (!/^\d{1,18}$/.test(next.price) || BigInt(next.price) <= 5000n) failures.price = 'Giá VND phải là số nguyên lớn hơn 5.000, tối đa 18 chữ số.';
      else next.price = BigInt(next.price).toString();
    }
    let result = failures;
    setSaveError('');
    if (!Object.keys(failures).length) {
      setSaving(true);
      try {
        result = await onSave(isItem ? next : { code: next.code, name: next.name, version: next.version });
      } catch (error) {
        if (error.name !== 'AbortError') setSaveError(error.message);
        result = error.fields;
      } finally {
        setSaving(false);
      }
    }
    if (result) {
      setErrors(result);
      fields.namedItem(Object.keys(result)[0])?.focus();
    }
  }

  return <form className="panel editor" noValidate onSubmit={submit}>
    <h2>{value ? 'Chỉnh sửa' : 'Thêm'} {isItem ? 'món' : 'nhóm món'}</h2>
    <p className="panel-description">{isItem ? 'Ảnh sản phẩm / Xem trước ảnh' : 'Thông tin nhóm món trong thực đơn'}</p>
    <fieldset className="form-grid" disabled={saving}>
      <label>Mã {isItem ? 'món' : 'nhóm món'} *
        <input ref={firstField} name="code" required minLength={isItem ? 4 : 1} maxLength={isItem ? 40 : 32} pattern="[A-Za-z0-9_-]+" title="Chữ Latin, số, dấu gạch ngang hoặc gạch dưới" readOnly={Boolean(value)} {...errorProps('code')} value={form.code} onChange={e => update('code', e.target.value)} placeholder={isItem ? 'VD: CF104' : 'VD: CF'} />
        {fieldError('code')}
      </label>
      <label>Tên {isItem ? 'món' : 'nhóm món'} *
        <input name="name" required minLength={isItem ? 6 : 1} maxLength={isItem ? 160 : 120} {...errorProps('name')} value={form.name} onChange={e => update('name', e.target.value)} placeholder={isItem ? 'VD: Mocha Latte' : 'VD: Cà phê'} />
        {fieldError('name')}
      </label>
      {isItem ? <>
        <label>Danh mục *
          <select name="categoryCode" required disabled={Boolean(value)} {...errorProps('categoryCode')} value={form.categoryCode} onChange={e => update('categoryCode', e.target.value)}>
            <option value="">Chọn nhóm món</option>
            {categories.map(category => <option key={category.code} value={category.code}>{category.name}</option>)}
          </select>
          {value ? <small>Nhóm món đã gán không thay đổi khi sửa.</small> : null}
          {fieldError('categoryCode')}
        </label>
        <label>Đơn giá (đ) *<input name="price" required inputMode="numeric" {...errorProps('price')} value={form.price} onChange={e => update('price', e.target.value)} placeholder="48000" />{fieldError('price')}</label>
        <div className="image-field">
          <label htmlFor="product-image">Ảnh sản phẩm</label>
          <div className="upload" onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); readImage(e.dataTransfer.files[0]); }}>
            <span aria-hidden="true">↑</span> Kéo thả ảnh hoặc chọn tệp
            <small>JPG / PNG • Tối đa 5 MB và 20 triệu pixel</small>
            <input id="product-image" type="file" accept="image/jpeg,image/png" aria-describedby="image-error" aria-invalid={Boolean(imageError)} onChange={e => { readImage(e.target.files[0]); e.target.value = ''; }} />
          </div>
          <p id="image-error" className="error" role="alert">{imageError}</p>
          {imageError ? <button type="button" onClick={() => setImageError('')}>Bỏ tệp không hợp lệ</button> : null}
          {reading ? <p role="status">Đang đọc ảnh…</p> : null}
          {form.imageUrl ? <div className="image-preview"><img src={form.imageUrl} alt="Xem trước ảnh món" /><button type="button" onClick={clearImage}>Bỏ ảnh</button></div> : null}
        </div>
        <label className="availability"><input type="checkbox" checked={form.available} onChange={e => update('available', e.target.checked)} />Có sẵn</label>
      </> : null}
    </fieldset>
    {saveError ? <p className="error" role="alert">{saveError}</p> : null}
    {saving ? <p role="status">Đang lưu thực đơn…</p> : null}
    <div className="form-actions"><button type="button" disabled={saving} onClick={onCancel}>HỦY</button><button className="primary" disabled={saving || reading || Boolean(imageError)}>{isItem ? 'LƯU MÓN' : 'LƯU NHÓM MÓN'}</button></div>
  </form>;
}
