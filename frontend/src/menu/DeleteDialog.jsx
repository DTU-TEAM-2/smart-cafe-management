import { useEffect, useRef } from 'react';

export default function DeleteDialog({ target, kind, blocked, onClose, onConfirm }) {
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
    <p id="delete-description">Bạn có chắc muốn xóa <strong>{target.name}</strong> khỏi danh sách minh họa?</p>
    {blocked ? <p role="alert" className="error">Nhóm này đang có món. Hãy chuyển hoặc xóa các món minh họa trước.</p> : null}
    <div className="form-actions"><button autoFocus onClick={onClose}>HỦY</button><button className="danger" disabled={blocked} onClick={onConfirm}>XÁC NHẬN XÓA</button></div>
  </dialog>;
}
