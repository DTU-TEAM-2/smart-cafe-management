import { useEffect, useRef } from "react";

export default function LeaveProfileDialog({ onCancel, onLeave }) {
  const dialog = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const element = dialog.current;
    element.showModal();
    return () => { element.close(); if (previous?.isConnected) previous.focus(); };
  }, []);
  return <dialog className="personnel-dialog" ref={dialog} aria-labelledby="leave-profile-title" aria-describedby="leave-profile-description"
    onCancel={event => { event.preventDefault(); onCancel(); }}>
    <h2 id="leave-profile-title">Rời trang đang chỉnh sửa?</h2>
    <p id="leave-profile-description">Các thay đổi đang nhập sẽ không được giữ trên màn hình này. Yêu cầu đã gửi vẫn có thể được máy chủ xử lý.</p>
    <div className="personnel-form-actions"><button className="account-secondary" autoFocus onClick={onCancel}>Tiếp tục chỉnh sửa</button>
      <button className="hero-cta" onClick={onLeave}>Rời trang</button></div>
  </dialog>;
}
