import { useEffect, useRef } from 'react';

export default function DeleteDialog({ target, kind, itemCount, onClose, onConfirm }) {
  const dialog = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const element = dialog.current;
    element.showModal();
    return () => {
      element.close();
      if (previous?.isConnected) previous.focus();
      else document.querySelector('h1')?.focus();
    };
  }, []);
  function keepFocus(event) {
    if (event.key !== 'Tab') return;
    const buttons = [...dialog.current.querySelectorAll('button:not(:disabled)')];
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }
  return <dialog ref={dialog} aria-labelledby="delete-title" aria-describedby="delete-description" onCancel={onClose} onKeyDown={keepFocus}>
    <h2 id="delete-title">Xác nhận xóa {kind === 'items' ? 'món' : 'nhóm món'}</h2>
    <p id="delete-description">Bạn có chắc muốn ẩn <strong>{target.name}</strong> khỏi danh sách minh họa?{kind === 'categories' ? ` ${itemCount} món đang hoạt động thuộc nhóm cũng sẽ bị ẩn. Hủy để giữ nguyên nhóm và các món.` : ''}</p>
    <div className="form-actions"><button autoFocus onClick={onClose}>HỦY</button><button className="danger" onClick={onConfirm}>XÁC NHẬN XÓA</button></div>
  </dialog>;
}
