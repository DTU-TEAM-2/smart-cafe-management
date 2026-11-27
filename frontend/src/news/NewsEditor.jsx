import { useEffect, useRef, useState } from "react";
import { stripJavaText } from "../javaText.js";
import NewsImage from "./NewsImage.jsx";
import { audienceNames, isNewsDetail, isNewsId, isNewsVersionTag, newsError, isUncertainNewsWrite } from "./newsApi.js";

const emptyDraft = () => ({ title: "", summary: "", body: "", slug: "", status: "DRAFT", audiences: [], images: [] });
const length = value => [...value].length;
const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const sameList = (first, second) => Array.isArray(first) && first.length === second.length
  && first.every((value, index) => value === second[index]);

function canPreviewRecovery({ slug, body }) {
  return body?.slug === slug && typeof body.title === "string" && typeof body.body === "string"
    && (body.summary == null || typeof body.summary === "string")
    && (body.status === "DRAFT" || body.status === "PUBLISHED")
    && Array.isArray(body.audiences) && body.audiences.length > 0
    && body.audiences.every(audience => typeof audience === "string" && Object.hasOwn(audienceNames, audience))
    && Array.isArray(body.imageMediaIds) && body.imageMediaIds.every(isNewsId);
}

function matchesCreation(post, body) {
  return body && post.version === 0 && post.slug === body.slug && post.title === body.title
    && (post.summary || null) === (body.summary || null) && post.body === body.body && post.status === body.status
    && Array.isArray(post.audiences) && sameList(post.audiences.slice().sort(), body.audiences.slice().sort())
    && Array.isArray(post.images) && sameList(post.images.map(image => image?.mediaId), body.imageMediaIds);
}

function autoSlug(title) {
  const stem = title.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replaceAll("đ", "d")
    .replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 140).replace(/-$/, "");
  const suffix = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, "0")).join("");
  return `${stem || "news"}-${suffix}`;
}

export default function NewsEditor({ id, accountId, authorization, request, setNavigationGuard }) {
  const [draft, setDraft] = useState(emptyDraft), [original, setOriginal] = useState(() => JSON.stringify(emptyDraft()));
  const [etag, setEtag] = useState(null), [state, setState] = useState(id ? "loading" : "ready");
  const [busy, setBusy] = useState(false), [uploading, setUploading] = useState(false), [error, setError] = useState("");
  const [invalid, setInvalid] = useState({}), [attempt, setAttempt] = useState(0), [conflict, setConflict] = useState(false);
  const controller = useRef(null), errorRef = useRef(null), blocked = useRef(false);
  const storageKey = `lecker_news_pending:${accountId}`;
  const [pending, setPending] = useState(() => {
    if (id) return null;
    try {
      const saved = JSON.parse(sessionStorage.getItem(storageKey));
      if (typeof saved?.slug !== "string" || saved.slug.length > 180 || !slugPattern.test(saved.slug)) return null;
      return { slug: saved.slug, body: saved.body ?? null };
    } catch { return null; }
  });
  const [uploadUncertain, setUploadUncertain] = useState(false);
  const [editUncertain, setEditUncertain] = useState(false);
  const [found, setFound] = useState(null);
  const saving = useRef(false);
  function clearPending() {
    try { sessionStorage.removeItem(storageKey); } catch { /* A stale record can still be checked by slug. */ }
    setPending(null);
    setFound(null);
  }
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
    request(`/${id}`, { signal: abort.signal }).then(({ data, etag: tag }) => {
      if (abort.signal.aborted) return;
      if (!isNewsDetail(data, id) || !isNewsVersionTag(tag)) throw new Error("Invalid news detail response");
      const loaded = { title: data.title, summary: data.summary || "", body: data.body, slug: data.slug, status: data.status,
        audiences: data.audiences, images: data.images.map(image => ({ mediaId: image.mediaId, url: image.secureUrl })) };
      setDraft(loaded); setOriginal(JSON.stringify(loaded)); setEtag(tag); setState("ready"); setConflict(false); setInvalid({});
      setUploadUncertain(false);
    }).catch(e => { if (!abort.signal.aborted) { setState("error"); setError(newsError(e)); } });
    return () => abort.abort();
  }, [id, request, attempt]);
  function field(key, value) { setDraft(current => ({ ...current, [key]: value })); setInvalid(current => ({ ...current, [key]: null })); }
  function showError(message) { setError(message); requestAnimationFrame(() => errorRef.current?.focus()); }
  function openPost(post) {
    setOriginal(JSON.stringify(draft));
    blocked.current = false;
    setNavigationGuard?.(null);
    location.hash = `#news/${post.id}`;
  }
  async function lookupCreation(creation, signal) {
    const { data } = await request(`/by-slug/${creation.slug}`, { signal });
    if (signal.aborted) return;
    if (!isNewsDetail(data) || data.slug !== creation.slug) throw new Error("Invalid news lookup response");
    if (canPreviewRecovery(creation) && matchesCreation(data, creation.body)) {
      clearPending();
      openPost(data);
    } else {
      setError("");
      setFound(data);
    }
  }

  async function resolvePending() {
    if (saving.current || !pending) return;
    const signal = controller.current.signal;
    saving.current = true;
    setBusy(true);
    setFound(null);
    setError("");
    try {
      await lookupCreation(pending, signal);
    } catch (error) {
      if (!signal.aborted) showError(error.status === 404
        ? "Chưa tìm thấy bài qua đường dẫn cũ. Hãy đối chiếu danh sách, kể cả bài đã lưu trữ hoặc đã đổi đường dẫn."
        : newsError(error));
    } finally {
      saving.current = false;
      if (!signal.aborted) setBusy(false);
    }
  }

  function downloadRecovery() {
    const url = URL.createObjectURL(new Blob([JSON.stringify(pending, null, 2)], { type: "application/json" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "tin-tuc-can-doi-soat.json";
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function discardRecovery() {
    if (!window.confirm("Bỏ nội dung đối soát trong tab này và soạn bài khác? Nếu đã tải bản sao, hãy kiểm tra tệp trước khi xác nhận. Bài trước vẫn có thể đã được lưu trên máy chủ.")) return;
    const blank = emptyDraft();
    setDraft(blank);
    setOriginal(JSON.stringify(blank));
    setInvalid({});
    setError("");
    setUploadUncertain(false);
    clearPending();
  }
  async function submit(event) {
    event.preventDefault();
    if (state !== "ready" || saving.current || busy || uploading || conflict || pending || editUncertain) return;
    const title = stripJavaText(draft.title);
    const summary = stripJavaText(draft.summary);
    const content = stripJavaText(draft.body);
    const errors = {};
    if (!title || length(title) > 200) errors.title = "Nhập tiêu đề từ 1 đến 200 ký tự.";
    if (length(summary) > 500) errors.summary = "Tóm tắt tối đa 500 ký tự.";
    if (!content) errors.body = "Nhập nội dung bài viết.";
    if (!draft.audiences.length) errors.audiences = "Chọn ít nhất một nhóm người nhận.";
    if (Object.keys(errors).length) { setInvalid(errors); showError("Vui lòng kiểm tra các trường được đánh dấu."); return; }
    const slug = stripJavaText(draft.slug).toLowerCase();
    if (slug && (slug.length > 180 || !slugPattern.test(slug))) {
      showError("Đường dẫn chỉ gồm chữ thường, chữ số và dấu gạch ngang, tối đa 180 ký tự.");
      return;
    }
    const creationSlug = id ? null : slug || autoSlug(title);
    const body = { title, summary: summary || null, body: content,
      slug: creationSlug || slug || null, status: draft.status, audiences: draft.audiences,
      imageMediaIds: draft.images.map(image => image.mediaId) };
    if (creationSlug) {
      const creation = { slug: creationSlug, body };
      try {
        sessionStorage.setItem(storageKey, JSON.stringify(creation));
      } catch {
        showError("Trình duyệt không lưu được thông tin đối soát. Hãy giữ nội dung và kiểm tra bộ nhớ của tab trước khi đăng.");
        return;
      }
      setPending(creation);
    }
    const signal = controller.current.signal;
    saving.current = true;
    setBusy(true); setError("");
    try {
      const { data } = await request(id ? `/${id}` : "", { method: id ? "PUT" : "POST", body, etag, signal });
      if (signal.aborted) return;
      if (!isNewsId(data?.id) || (id && data.id !== id.replace(/^0+/, ""))) throw new Error("Invalid news response");
      if (creationSlug) clearPending();
      openPost(data);
    } catch (e) {
      if (signal.aborted) return;
      const rejected = !isUncertainNewsWrite(e);
      if (creationSlug && rejected) clearPending();
      if ([412, 428].includes(e.status)) setConflict(true);
      if (id && !rejected) setEditUncertain(true);
      showError(!rejected ? "Chưa xác nhận được kết quả lưu bài." : newsError(e));
    } finally {
      saving.current = false;
      if (!signal.aborted) setBusy(false);
    }
  }
  async function upload(event) {
    const files = [...event.target.files]; event.target.value = "";
    if (!files.length || state !== "ready" || uploading || busy || uploadUncertain || pending || conflict || editUncertain) return;
    if (draft.images.length + files.length > 20) { showError("Mỗi bài viết có tối đa 20 ảnh."); return; }
    if (files.some(file => !["image/png", "image/jpeg"].includes(file.type) || file.size > 5 * 1024 * 1024 || file.size === 0)) {
      showError("Chọn ảnh PNG/JPEG, mỗi ảnh lớn hơn 0 byte và không quá 5 MiB."); return;
    }
    setUploading(true); setError("");
    // Sequential uploads bound memory and preserve successful images when a later upload fails.
    try {
      for (const file of files) {
        const body = new FormData(); body.append("file", file);
        const { data } = await request("/images", { method: "POST", body, signal: controller.current.signal });
        if (controller.current.signal.aborted) return;
        if (!isNewsId(data?.mediaId)
          || data.url !== `/api/v1/news/images/${data.mediaId}`) throw new Error("Invalid upload response");
        setDraft(current => ({ ...current, images: [...current.images, { mediaId: data.mediaId, url: data.url }] }));
      }
    } catch (e) {
      if (!controller.current.signal.aborted) {
        if (isUncertainNewsWrite(e)) {
          setUploadUncertain(true);
          showError("Chưa rõ kết quả tải ảnh. Bạn có thể lưu bài với các ảnh đã hiện trong danh sách.");
        } else showError(`Chưa tải được ảnh. ${newsError(e)}`);
      }
    }
    finally { if (!controller.current.signal.aborted) setUploading(false); }
  }
  function move(index, delta) {
    setDraft(current => { const images = [...current.images]; [images[index], images[index + delta]] = [images[index + delta], images[index]]; return { ...current, images }; });
  }
  const fieldsLocked = busy || state === "loading" || Boolean(pending);
  const recoveryBody = pending && canPreviewRecovery(pending) ? pending.body : null;
  const shownDraft = recoveryBody ?? draft;
  const shownImages = recoveryBody ? recoveryBody.imageMediaIds.map(mediaId => ({
    mediaId, url: `/api/v1/news/images/${mediaId}`,
  })) : draft.images;
  const saveDisabled = state !== "ready" || fieldsLocked || uploading || conflict || editUncertain;
  async function checkEdit() {
    if (saving.current) return;
    const signal = controller.current.signal;
    saving.current = true;
    setBusy(true);
    try {
      const current = await request(`/${id}`, { signal });
      if (!signal.aborted) {
        if (!isNewsDetail(current.data, id) || !isNewsVersionTag(current.etag)) throw new Error("Invalid news detail response");
        showError(current.etag === etag
          ? "Máy chủ vẫn đang hiển thị phiên bản cũ. Kiểm tra lại nếu lần lưu còn đang xử lý."
          : "Phiên bản trên máy chủ đã thay đổi. Mở bài để đối chiếu với nội dung đang soạn.");
      }
    } catch (e) {
      if (!signal.aborted) showError(newsError(e));
    } finally {
      saving.current = false;
      if (!signal.aborted) setBusy(false);
    }
  }
  return <section className="news-page news-editor" aria-labelledby="editor-title">
    <a className="news-text-link" href={id ? `#news/${id}` : "#news"}>← {id ? "Về bài viết" : "Danh sách tin tức"}</a>
    <div className="news-heading"><h1 id="editor-title">{id ? "Chỉnh sửa tin tức" : "Một câu chuyện mới."}</h1></div>
    {error && <div className="news-error" role="alert" tabIndex={-1} ref={errorRef}><p>{error}</p>{conflict && <button type="button" className="news-button" onClick={() => {
      if (window.confirm("Tải bản mới sẽ thay nội dung bạn đang soạn. Bạn đã sao chép phần cần giữ chưa?")) setAttempt(v => v + 1);
    }}>Tải bản mới</button>}</div>}
    {pending && !busy && <div className="news-error">
      <p role="status">Chưa xác nhận được kết quả lần lưu trước. Kiểm tra bài có đường dẫn <strong>{pending.slug}</strong>.</p>
      {pending.body != null && !recoveryBody && <p>Không thể hiển thị nội dung đối soát. Bạn vẫn có thể tải bản sao JSON.</p>}
      <button type="button" className="news-button" onClick={resolvePending}>Kiểm tra bài đã lưu</button>
      <button type="button" className="news-button subtle" onClick={downloadRecovery}>Tải bản sao JSON</button>
      <button type="button" className="news-button subtle" onClick={discardRecovery}>Bỏ đối soát và soạn bài khác</button>
      {found && <p>Tìm thấy “{found.title}”. <button type="button" className="news-button" onClick={() => {
        if (window.confirm("Xác nhận bài tìm thấy là bài cần khôi phục và mở bài đó?")) {
          clearPending();
          openPost(found);
        }
      }}>Mở bài tìm thấy</button></p>}
    </div>}
    {editUncertain && <div className="news-error">
      <button type="button" className="news-button" disabled={busy} onClick={checkEdit}>Kiểm tra phiên bản máy chủ</button>{" "}
      <a href={`#news/${id}`}>Mở bài hiện tại</a>
    </div>}
    {state === "loading" && <p role="status">Đang tải nội dung biên tập…</p>}
    {state === "error" && !conflict && <button className="news-button" onClick={() => setAttempt(v => v + 1)}>Thử lại</button>}
    {(state === "ready" || etag) && <form onSubmit={submit} noValidate aria-busy={busy || uploading || state === "loading"}>
        <div className="news-editor-grid"><div className="news-editor-main"><section className="news-form-panel"><h2>Nội dung bài viết</h2>
          <label htmlFor="news-title-input">Tiêu đề <span aria-hidden="true">*</span></label>
          <input id="news-title-input" value={shownDraft.title} required disabled={fieldsLocked} aria-invalid={!!invalid.title} aria-describedby="news-title-help" onChange={e => field("title", e.target.value)} placeholder="Bạn muốn chia sẻ điều gì?" />
          <p className={invalid.title ? "news-field-error" : "news-field-help"} id="news-title-help">{invalid.title || `${length(stripJavaText(shownDraft.title))}/200 ký tự`}</p>
          <label htmlFor="news-summary">Tóm tắt</label><textarea id="news-summary" rows={3} disabled={fieldsLocked} value={shownDraft.summary ?? ""} aria-invalid={!!invalid.summary} aria-describedby="news-summary-help" onChange={e => field("summary", e.target.value)} placeholder="Một lời mở đầu để người đọc muốn khám phá thêm…" />
          <p className={invalid.summary ? "news-field-error" : "news-field-help"} id="news-summary-help">{invalid.summary || `${length(stripJavaText(shownDraft.summary ?? ""))}/500 ký tự`}</p>
          <label htmlFor="news-body">Nội dung <span aria-hidden="true">*</span></label><textarea id="news-body" className="news-body-input" rows={13} disabled={fieldsLocked} required value={shownDraft.body} aria-invalid={!!invalid.body} aria-describedby="news-body-help" onChange={e => field("body", e.target.value)} placeholder="Viết câu chuyện của bạn tại đây…" />
          <p id="news-body-help" className={invalid.body ? "news-field-error" : "news-field-help"}>{invalid.body || "Nội dung văn bản, giữ nguyên xuống dòng."}</p>
        </section>
          <section className="news-form-panel"><div className="news-panel-title"><h2>Hình ảnh</h2><span>{shownImages.length}/20</span></div>
            <p className="news-field-help" id="news-upload-help">PNG hoặc JPEG, tối đa 5 MiB/ảnh, 6000 px mỗi chiều và 12 triệu pixel. Ảnh đầu là ảnh đại diện.</p>
            {shownImages.length > 0 && <ol className="news-upload-list">{shownImages.map((image, index) => <li key={image.mediaId}>
              <NewsImage request={request} url={image.url} authorization={authorization} alt={`Ảnh ${index + 1}`} />
              <div className="news-upload-actions"><button type="button" disabled={saveDisabled || index === 0} aria-label={`Đưa ảnh ${index + 1} lên trước`} onClick={() => move(index, -1)}>←</button>
                <span>{index === 0 ? "Ảnh đại diện" : `Ảnh ${index + 1}`}</span><button type="button" disabled={saveDisabled || index === shownImages.length - 1} aria-label={`Đưa ảnh ${index + 1} ra sau`} onClick={() => move(index, 1)}>→</button>
                <button type="button" disabled={saveDisabled} aria-label={`Gỡ ảnh ${index + 1}`} onClick={() => field("images", draft.images.filter(item => item.mediaId !== image.mediaId))}>Gỡ</button></div>
            </li>)}</ol>}
            <label className={`news-upload-zone ${saveDisabled || uploadUncertain || shownImages.length >= 20 ? "is-disabled" : ""}`} htmlFor="news-images"><span aria-hidden="true">＋</span><strong>{uploading ? "Đang tải ảnh lên…" : "Chọn ảnh từ thiết bị"}</strong>
              <span>Ảnh được tải lên trước khi lưu bài.</span><input id="news-images" type="file" accept="image/png,image/jpeg" multiple disabled={saveDisabled || uploadUncertain || shownImages.length >= 20} onChange={upload} aria-describedby="news-upload-help" /></label>
            {uploading && <p role="status">Đang tải ảnh. Vui lòng đợi trước khi lưu tin.</p>}
          </section>
        </div><aside className="news-editor-sidebar"><section className="news-form-panel"><h2>Xuất bản</h2>
          <label htmlFor="news-publish-status">Trạng thái</label><select id="news-publish-status" disabled={fieldsLocked} value={shownDraft.status} onChange={e => field("status", e.target.value)}>
            <option value="DRAFT">Bản nháp</option><option value="PUBLISHED">Đã đăng</option>{id && <option value="ARCHIVED">Đã lưu trữ</option>}</select>
          <p className="news-field-help">{shownDraft.status === "PUBLISHED" ? "Tin sẽ hiển thị với nhóm người nhận đã chọn." : shownDraft.status === "ARCHIVED" ? "Tin được lưu giữ và ẩn với người đọc." : "Chỉ quản lý có thể xem bản nháp."}</p>
          <fieldset aria-describedby="news-audience-help" aria-invalid={!!invalid.audiences} disabled={fieldsLocked}><legend>Gửi đến <span aria-hidden="true">*</span></legend>
            {[["STAFF", "Nhân viên", "Thông tin nội bộ của quán"], ["CUSTOMER", "Khách hàng", "Câu chuyện dành cho khách"]].map(([value, label, hint]) => <label className="news-audience-option" key={value}>
              <input type="checkbox" checked={shownDraft.audiences.includes(value)} onChange={e => field("audiences", e.target.checked ? [...draft.audiences, value] : draft.audiences.filter(a => a !== value))} /><span><strong>{label}</strong><small>{hint}</small></span></label>)}
          </fieldset><p id="news-audience-help" className={invalid.audiences ? "news-field-error" : "news-field-help"}>{invalid.audiences || "Chọn một hoặc cả hai nhóm."}</p>
          <label htmlFor="news-slug">Đường dẫn riêng</label><input id="news-slug" disabled={fieldsLocked} value={shownDraft.slug} onChange={e => field("slug", e.target.value)} aria-describedby="news-slug-help" placeholder="Tự tạo từ tiêu đề" />
          <p className="news-field-help" id="news-slug-help">Tùy chọn. Chữ thường, chữ số và dấu gạch ngang, tối đa 180 ký tự sau chuẩn hóa.</p>
          <button type="submit" className="news-button primary news-save" disabled={saveDisabled} aria-describedby={saveDisabled ? "news-save-help" : undefined}>{busy ? "Đang lưu…" : id ? "Lưu thay đổi" : shownDraft.status === "PUBLISHED" ? "Đăng tin" : "Lưu bản nháp"}<span aria-hidden="true">↗</span></button>
          {saveDisabled && <p id="news-save-help" className="news-field-help">{pending && !busy ? "Cần kiểm tra kết quả lần lưu trước." : editUncertain ? "Cần đối chiếu phiên bản trên máy chủ." : conflict ? "Cần tải bản mới trước khi lưu tiếp." : uploading ? "Đang tải ảnh lên." : "Đang lưu tin tức."}</p>}
          <a className="news-cancel" href={id ? `#news/${id}` : "#news"}>Hủy và quay lại</a>
          <p className="news-editor-note">{dirty ? "Bạn có thay đổi chưa lưu." : "Mọi thay đổi sẽ được lưu khi bạn xác nhận."}</p>
        </section></aside></div>
      </form>}
  </section>;
}
