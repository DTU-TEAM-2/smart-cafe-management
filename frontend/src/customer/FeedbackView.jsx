import { useState, useRef } from "react";
import { submitFeedback, fetchCsrfToken } from "./customerApi.js";
import OrderResultModal from "./OrderResultModal.jsx";

export default function FeedbackView({ onNavigateMenu }) {
  const [rating, setRating] = useState(5);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [content, setContent] = useState("");
  const [images, setImages] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [modalResult, setModalResult] = useState(null);
  const fileInputRef = useRef(null);

  function handleImageChange(e) {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;

    const validImages = [];
    for (const f of files) {
      if (!["image/jpeg", "image/png"].includes(f.type)) {
        setModalResult({
          status: "failure",
          title: "Định dạng không hỗ trợ",
          detail: `File ${f.name} không phải JPEG hoặc PNG.`,
        });
        return;
      }
      if (f.size > 5 * 1024 * 1024) {
        setModalResult({
          status: "failure",
          title: "Ảnh vượt quá 5MB",
          detail: `Ảnh ${f.name} có dung lượng vượt quá 5MB.`,
        });
        return;
      }
      validImages.push(f);
    }

    setImages((prev) => {
      const merged = [...prev, ...validImages].slice(0, 3);
      return merged;
    });

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }

  function removeImage(index) {
    setImages((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!name.trim() || !email.trim() || !content.trim()) {
      setModalResult({
        status: "failure",
        title: "Thiếu thông tin",
        detail: "Vui lòng nhập họ tên, email và nội dung đánh giá của bạn.",
      });
      return;
    }

    setSubmitting(true);
    try {
      const csrf = await fetchCsrfToken("/api/v1/customer/feedback/csrf");
      await submitFeedback({
        name,
        email,
        content,
        rating,
        images,
        csrf,
      });

      setModalResult({
        status: "success",
        title: "Cảm ơn bạn!",
        detail: "Phản hồi của bạn đã được gửi thành công tới ban quản lý Smart Cafe LECKER.",
      });

      // Clear form
      setContent("");
      setName("");
      setEmail("");
      setImages([]);
      setRating(5);
    } catch (err) {
      setModalResult({
        status: "failure",
        title: "Gửi phản hồi thất bại",
        detail: err.message || "Không thể gửi phản hồi. Vui lòng thử lại sau.",
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="c-content">
      <h1 className="c-title">Đánh giá trải nghiệm</h1>
      <p className="c-subtitle">Ý kiến của bạn giúp LECKER không ngừng hoàn thiện chất lượng phục vụ</p>

      {/* 05-feedback.png */}
      <form onSubmit={handleSubmit} className="c-feedback-form">
        {/* Star Rating */}
        <div style={{ textAlign: "center" }}>
          <span className="c-label">Bạn cảm thấy trải nghiệm hôm nay thế nào?</span>
          <div className="c-star-rating" role="radiogroup" aria-label="Đánh giá sao">
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={star}
                type="button"
                role="radio"
                aria-checked={rating === star}
                className={`c-star-btn ${rating >= star ? "active" : ""}`}
                onClick={() => setRating(star)}
                aria-label={`${star} sao`}
              >
                <svg width="32" height="32" viewBox="0 0 24 24" fill={rating >= star ? "currentColor" : "none"} stroke="currentColor" strokeWidth="1.5">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                </svg>
              </button>
            ))}
          </div>
        </div>

        {/* Name (Required by the backend contract) */}
        <div className="c-input-group">
          <label className="c-label" htmlFor="feedback-name">Họ và tên <span style={{ color: "#d5a953" }}>*</span></label>
          <input
            id="feedback-name"
            type="text"
            className="c-input"
            placeholder="Ví dụ: Nguyễn Văn A"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={160}
            required
          />
        </div>

        {/* Email (Required by the backend contract) */}
        <div className="c-input-group">
          <label className="c-label" htmlFor="feedback-email">Email liên hệ <span style={{ color: "#d5a953" }}>*</span></label>
          <input
            id="feedback-email"
            type="email"
            className="c-input"
            placeholder="customer@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            maxLength={255}
            required
          />
        </div>

        {/* Content (Required) */}
        <div className="c-input-group">
          <label className="c-label" htmlFor="feedback-content">
            Nội dung nhận xét <span style={{ color: "#d5a953" }}>*</span>
          </label>
          <textarea
            id="feedback-content"
            className="c-textarea"
            placeholder="Chia sẻ về hương vị đồ uống, không gian, thái độ phục vụ..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
            required
            rows={4}
          />
        </div>

        {/* Image Attachment (Optional, max 3) */}
        <div className="c-input-group">
          <label className="c-label">Đính kèm hình ảnh (Tùy chọn, tối đa 3 ảnh)</label>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png"
            multiple
            style={{ display: "none" }}
            onChange={handleImageChange}
          />
          <button
            type="button"
            className="c-btn-icon"
            style={{
              width: "100%",
              height: "46px",
              border: "1px dashed rgba(213, 169, 83, 0.4)",
              borderRadius: "10px",
              fontSize: "13px",
              color: "#d5a953",
              gap: "8px",
            }}
            onClick={() => fileInputRef.current?.click()}
            disabled={images.length >= 3}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <polyline points="21 15 16 10 5 21" />
            </svg>
            {images.length >= 3 ? "Đã chọn tối đa 3 ảnh" : "Chọn ảnh từ thiết bị (JPEG/PNG ≤ 5MB)"}
          </button>

          {images.length > 0 && (
            <div className="c-image-upload-preview">
              {images.map((file, idx) => {
                const url = URL.createObjectURL(file);
                return (
                  <div key={idx} className="c-image-thumb-box">
                    <img src={url} alt={`Ảnh ${idx + 1}`} onLoad={() => URL.revokeObjectURL(url)} />
                    <button
                      type="button"
                      className="c-btn-remove-img"
                      onClick={() => removeImage(idx)}
                      aria-label="Xóa ảnh"
                    >
                      ×
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Submit Button */}
        <div style={{ marginTop: "12px" }}>
          <button
            type="submit"
            className="c-btn-primary"
            disabled={submitting || !name.trim() || !email.trim() || !content.trim()}
          >
            {submitting ? "ĐANG GỬI PHẢN HỒI..." : "GỬI PHẢN HỒI NGAY"}
          </button>
        </div>
      </form>

      {/* Result Modal */}
      {modalResult && (
        <OrderResultModal
          status={modalResult.status}
          title={modalResult.title}
          detail={modalResult.detail}
          primaryText="VỀ THỰC ĐƠN"
          onPrimaryAction={onNavigateMenu}
          onClose={() => setModalResult(null)}
        />
      )}
    </div>
  );
}
