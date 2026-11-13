import { useEffect, useRef, useState } from "react";
import NewsImage from "./NewsImage.jsx";
import { newsError, newsRequest } from "./newsApi.js";

const emptyDraft = () => ({ title: "", summary: "", body: "", slug: "", status: "DRAFT", audiences: [], images: [] });
const length = value => [...value].length;

export default function NewsEditor({ id, authorization, setNavigationGuard }) {
  const [draft, setDraft] = useState(emptyDraft), [original, setOriginal] = useState(() => JSON.stringify(emptyDraft()));
  const [etag, setEtag] = useState(null), [state, setState] = useState(id ? "loading" : "ready");
  const [busy, setBusy] = useState(false), [uploading, setUploading] = useState(false), [error, setError] = useState("");
  const [invalid, setInvalid] = useState({}), [attempt, setAttempt] = useState(0), [conflict, setConflict] = useState(false);
  const controller = useRef(null), errorRef = useRef(null), blocked = useRef(false);
  const dirty = JSON.stringify(draft) !== original;
  useEffect(() => {
    const abort = new AbortController(); controller.current = abort;
    return () => abort.abort();
  }, []);
  useEffect(() => {
    blocked.current = dirty || busy || uploading;
    const guard = () => !blocked.current || window.confirm("Bạn có thay đổi chưa lưu. Rời khỏi trang và bỏ các thay đổi này?");
    setNavigationGuard?.(guard);
    function beforeUnload(event) { if (blocked.current) { event.preventDefault(); event.returnValue = ""; } }
    window.addEventListener("beforeunload", beforeUnload);
    return () => { setNavigationGuard?.(null); window.removeEventListener("beforeunload", beforeUnload); };
  }, [dirty, busy, uploading, setNavigationGuard]);
  useEffect(() => {
    if (!id) return;
    const abort = new AbortController();
    setState("loading"); setError("");
    newsRequest(`/${id}`, authorization, { signal: abort.signal }).then(({ data, etag: tag }) => {
      if (abort.signal.aborted) return;
      const loaded = { title: data.title, summary: data.summary || "", body: data.body, slug: data.slug, status: data.status,
        audiences: data.audiences, images: data.images.map(image => ({ mediaId: image.mediaId, url: image.secureUrl })) };
      setDraft(loaded); setOriginal(JSON.stringify(loaded)); setEtag(tag); setState("ready"); setConflict(false); setInvalid({});
    }).catch(e => { if (!abort.signal.aborted) { setState("error"); setError(newsError(e)); } });
    return () => abort.abort();
  }, [id, authorization, attempt]);
  function field(key, value) { setDraft(current => ({ ...current, [key]: value })); setInvalid(current => ({ ...current, [key]: null })); }
  function showError(message) { setError(message); requestAnimationFrame(() => errorRef.current?.focus()); }
  async function submit(event) {
    event.preventDefault();
    if (busy || uploading || conflict) return;
    const errors = {};
    if (!draft.title.trim() || length(draft.title) > 200) errors.title = "Nhập tiêu đề từ 1 đến 200 ký tự.";
    if (length(draft.summary) > 500) errors.summary = "Tóm tắt tối đa 500 ký tự.";
    if (!draft.body.trim()) errors.body = "Nhập nội dung bài viết.";
    if (!draft.audiences.length) errors.audiences = "Chọn ít nhất một nhóm người nhận.";
    if (Object.keys(errors).length) { setInvalid(errors); showError("Vui lòng kiểm tra các trường được đánh dấu."); return; }
    setBusy(true); setError("");
    try {
      const body = { title: draft.title.trim(), summary: draft.summary.trim() || null, body: draft.body, slug: draft.slug.trim() || null,
        status: draft.status, audiences: draft.audiences, imageMediaIds: draft.images.map(image => image.mediaId) };
      const { data } = await newsRequest(id ? `/${id}` : "", authorization, { method: id ? "PUT" : "POST", body, etag, signal: controller.current.signal });
      if (controller.current.signal.aborted) return;
      setOriginal(JSON.stringify(draft)); blocked.current = false; setNavigationGuard?.(null); location.hash = `#news/${data.id}`;
    } catch (e) { if (!controller.current.signal.aborted) { if (e.status === 412) setConflict(true); showError(newsError(e)); } }
    finally { if (!controller.current.signal.aborted) setBusy(false); }
  }
  async function upload(event) {
    const files = [...event.target.files]; event.target.value = "";
    if (!files.length || uploading || busy) return;
    if (draft.images.length + files.length > 20) { showError("Mỗi bài viết có tối đa 20 ảnh."); return; }
    if (files.some(file => !["image/png", "image/jpeg"].includes(file.type) || file.size > 5 * 1024 * 1024 || file.size === 0)) {
      showError("Chọn ảnh PNG/JPEG, mỗi ảnh lớn hơn 0 byte và không quá 5 MiB."); return;
    }
    setUploading(true); setError("");
    // Sequential uploads bound memory and preserve successful images when a later upload fails.
    try {
      for (const file of files) {
        const body = new FormData(); body.append("file", file);
        const { data } = await newsRequest("/images", authorization, { method: "POST", body, signal: controller.current.signal });
        if (controller.current.signal.aborted) return;
        setDraft(current => ({ ...current, images: [...current.images, { mediaId: data.mediaId, url: data.url }] }));
      }
    } catch (e) { if (!controller.current.signal.aborted) showError(`Chưa tải được ảnh. ${newsError(e)}`); }
    finally { if (!controller.current.signal.aborted) setUploading(false); }
  }
  function move(index, delta) {
    setDraft(current => { const images = [...current.images]; [images[index], images[index + delta]] = [images[index + delta], images[index]]; return { ...current, images }; });
  }
  const saveDisabled = busy || uploading || conflict;
  return <section className="news-page news-editor" aria-labelledby="editor-title">
    <a className="news-text-link" href={id ? `#news/${id}` : "#news"}>← {id ? "Về bài viết" : "Danh sách tin tức"}</a>
    <div className="news-heading"><div><p className="eyebrow">LECKER • Biên tập</p><h1 id="editor-title">{id ? "Chỉnh sửa tin tức" : "Một câu chuyện mới."}</h1><p>Những điều bạn chia sẻ làm nên câu chuyện LECKER.</p></div></div>
    {error && <div className="news-error" role="alert" tabIndex={-1} ref={errorRef}><p>{error}</p>{conflict && <button type="button" className="news-button" onClick={() => {
      if (window.confirm("Tải bản mới sẽ thay nội dung bạn đang soạn. Bạn đã sao chép phần cần giữ chưa?")) setAttempt(v => v + 1);
    }}>Tải bản mới</button>}</div>}
    {state === "loading" ? <p role="status">Đang tải nội dung biên tập…</p> : state === "error" ? <button className="news-button" onClick={() => setAttempt(v => v + 1)}>Thử lại</button>
      : <form onSubmit={submit} noValidate aria-busy={busy || uploading}>
        <div className="news-editor-grid"><div className="news-editor-main"><section className="news-form-panel"><h2>Nội dung bài viết</h2>
          <label htmlFor="news-title-input">Tiêu đề <span aria-hidden="true">*</span></label>
          <input id="news-title-input" value={draft.title} required disabled={busy} aria-invalid={!!invalid.title} aria-describedby="news-title-help" onChange={e => field("title", e.target.value)} placeholder="Bạn muốn chia sẻ điều gì?" />
          <p className={invalid.title ? "news-field-error" : "news-field-help"} id="news-title-help">{invalid.title || `${length(draft.title)}/200 ký tự`}</p>
          <label htmlFor="news-summary">Tóm tắt</label><textarea id="news-summary" rows={3} disabled={busy} value={draft.summary} aria-invalid={!!invalid.summary} aria-describedby="news-summary-help" onChange={e => field("summary", e.target.value)} placeholder="Một lời mở đầu để người đọc muốn khám phá thêm…" />
          <p className={invalid.summary ? "news-field-error" : "news-field-help"} id="news-summary-help">{invalid.summary || `${length(draft.summary)}/500 ký tự`}</p>
          <label htmlFor="news-body">Nội dung <span aria-hidden="true">*</span></label><textarea id="news-body" className="news-body-input" rows={13} disabled={busy} required value={draft.body} aria-invalid={!!invalid.body} aria-describedby="news-body-help" onChange={e => field("body", e.target.value)} placeholder="Viết câu chuyện của bạn tại đây…" />
          <p id="news-body-help" className={invalid.body ? "news-field-error" : "news-field-help"}>{invalid.body || "Nội dung văn bản, giữ nguyên xuống dòng."}</p>
        </section>
          <section className="news-form-panel"><div className="news-panel-title"><h2>Hình ảnh</h2><span>{draft.images.length}/20</span></div>
            <p className="news-field-help" id="news-upload-help">PNG hoặc JPEG, tối đa 5 MiB/ảnh, 6000 px mỗi chiều và 12 triệu pixel. Ảnh đầu là ảnh đại diện.</p>
            {draft.images.length > 0 && <ol className="news-upload-list">{draft.images.map((image, index) => <li key={image.mediaId}>
              <NewsImage url={image.url} authorization={authorization} alt={`Ảnh ${index + 1}`} />
              <div className="news-upload-actions"><button type="button" disabled={saveDisabled || index === 0} aria-label={`Đưa ảnh ${index + 1} lên trước`} onClick={() => move(index, -1)}>←</button>
                <span>{index === 0 ? "Ảnh đại diện" : `Ảnh ${index + 1}`}</span><button type="button" disabled={saveDisabled || index === draft.images.length - 1} aria-label={`Đưa ảnh ${index + 1} ra sau`} onClick={() => move(index, 1)}>→</button>
                <button type="button" disabled={saveDisabled} aria-label={`Gỡ ảnh ${index + 1}`} onClick={() => field("images", draft.images.filter(item => item.mediaId !== image.mediaId))}>Gỡ</button></div>
            </li>)}</ol>}
            <label className={`news-upload-zone ${saveDisabled || draft.images.length >= 20 ? "is-disabled" : ""}`} htmlFor="news-images"><span aria-hidden="true">＋</span><strong>{uploading ? "Đang tải ảnh lên…" : "Chọn ảnh từ thiết bị"}</strong>
              <span>Ảnh được tải lên trước khi lưu bài.</span><input id="news-images" type="file" accept="image/png,image/jpeg" multiple disabled={saveDisabled || draft.images.length >= 20} onChange={upload} aria-describedby="news-upload-help" /></label>
            {uploading && <p role="status">Đang tải ảnh. Vui lòng đợi trước khi lưu tin.</p>}
          </section>
        </div><aside className="news-editor-sidebar"><section className="news-form-panel"><h2>Xuất bản</h2>
          <label htmlFor="news-publish-status">Trạng thái</label><select id="news-publish-status" disabled={busy} value={draft.status} onChange={e => field("status", e.target.value)}>
            <option value="DRAFT">Bản nháp</option><option value="PUBLISHED">Đã đăng</option>{id && <option value="ARCHIVED">Đã lưu trữ</option>}</select>
          <p className="news-field-help">{draft.status === "PUBLISHED" ? "Tin sẽ hiển thị với nhóm người nhận đã chọn." : draft.status === "ARCHIVED" ? "Tin được lưu giữ và ẩn với người đọc." : "Chỉ quản lý có thể xem bản nháp."}</p>
          <fieldset aria-describedby="news-audience-help" aria-invalid={!!invalid.audiences} disabled={busy}><legend>Gửi đến <span aria-hidden="true">*</span></legend>
            {[["STAFF", "Nhân viên", "Thông tin nội bộ của quán"], ["CUSTOMER", "Khách hàng", "Câu chuyện dành cho khách"]].map(([value, label, hint]) => <label className="news-audience-option" key={value}>
              <input type="checkbox" checked={draft.audiences.includes(value)} onChange={e => field("audiences", e.target.checked ? [...draft.audiences, value] : draft.audiences.filter(a => a !== value))} /><span><strong>{label}</strong><small>{hint}</small></span></label>)}
          </fieldset><p id="news-audience-help" className={invalid.audiences ? "news-field-error" : "news-field-help"}>{invalid.audiences || "Chọn một hoặc cả hai nhóm."}</p>
          <label htmlFor="news-slug">Đường dẫn riêng</label><input id="news-slug" disabled={busy} value={draft.slug} onChange={e => field("slug", e.target.value)} aria-describedby="news-slug-help" placeholder="Tự tạo từ tiêu đề" />
          <p className="news-field-help" id="news-slug-help">Tùy chọn. Chữ thường, chữ số và dấu gạch ngang, tối đa 180 ký tự sau chuẩn hóa.</p>
          <button type="submit" className="news-button primary news-save" disabled={saveDisabled} aria-describedby={saveDisabled ? "news-save-help" : undefined}>{busy ? "Đang lưu…" : id ? "Lưu thay đổi" : draft.status === "PUBLISHED" ? "Đăng tin" : "Lưu bản nháp"}<span aria-hidden="true">↗</span></button>
          {saveDisabled && <p id="news-save-help" className="news-field-help">{conflict ? "Cần tải bản mới trước khi lưu tiếp." : uploading ? "Đang tải ảnh lên." : "Đang lưu tin tức."}</p>}
          <a className="news-cancel" href={id ? `#news/${id}` : "#news"}>Hủy và quay lại</a>
          <p className="news-editor-note">{dirty ? "Bạn có thay đổi chưa lưu." : "Mọi thay đổi sẽ được lưu khi bạn xác nhận."}</p>
        </section><p className="news-editor-caption">Một câu chuyện hay bắt đầu<br />từ những điều giản dị.</p></aside></div>
      </form>}
  </section>;
}
