import { useEffect, useRef, useState } from "react";
import NewsImage from "./NewsImage.jsx";
import NewsEditor from "./NewsEditor.jsx";
import { audienceNames, newsDate, newsError, newsRequest, statusNames } from "./newsApi.js";
import "./news.css";

export default function NewsPage({ authorization, account, identityStatus, route, revision, connection, setNavigationGuard }) {
  if (identityStatus === "loading") return <section className="news-page"><p role="status">Đang kiểm tra phiên đăng nhập…</p></section>;
  if (!account) return <section className="news-page news-empty"><p className="eyebrow">LECKER • Tin tức</p><h1>Câu chuyện chờ bạn khám phá.</h1>
    <p>Đăng nhập để xem những tin tức dành cho bạn.</p><a className="news-button primary" href="#login">Đăng nhập</a></section>;
  const match = /^#news\/([0-9]+)(\/edit)?$/.exec(route);
  if (route === "#news/new" || match?.[2]) {
    if (account.role !== "ADMIN") return <section className="news-page news-empty"><h1>Bạn không có quyền quản lý tin tức</h1><a href="#news">Về danh sách tin tức</a></section>;
    return <NewsEditor key={route} id={match?.[1]} authorization={authorization} setNavigationGuard={setNavigationGuard} />;
  }
  if (match) return <NewsDetail key={match[1]} id={match[1]} authorization={authorization} admin={account.role === "ADMIN"} revision={revision} />;
  return <NewsList authorization={authorization} admin={account.role === "ADMIN"} revision={revision} connection={account.role === "STAFF" ? connection : null} />;
}

function NewsList({ authorization, admin, revision, connection }) {
  const [page, setPage] = useState(0), [status, setStatus] = useState(""), [sort, setSort] = useState("publishedAt,desc");
  const [attempt, setAttempt] = useState(0), [view, setView] = useState({ state: "loading", data: null });
  const heading = useRef(null);
  useEffect(() => {
    const controller = new AbortController();
    setView({ state: "loading", data: null });
    newsRequest(`?page=${page}&size=6&sort=${sort}${status ? `&status=${status}` : ""}`, authorization, { signal: controller.signal })
      .then(({ data }) => {
        if (controller.signal.aborted) return;
        if (page > 0 && !data.content.length) { setPage(Math.max(0, data.totalPages - 1)); return; }
        setView({ state: "ready", data });
      }).catch(error => { if (!controller.signal.aborted) setView({ state: "error", error: newsError(error), data: null }); });
    return () => controller.abort();
  }, [authorization, page, status, sort, attempt, revision]);
  return <section className="news-page" aria-labelledby="news-title">
    <div className="news-heading"><div><p className="eyebrow">LECKER • Bảng tin</p><h1 ref={heading} id="news-title" tabIndex={-1}>{admin ? "Quản lý tin tức" : "Chuyện từ LECKER."}</h1>
      <p>{admin ? "Chia sẻ câu chuyện, gửi đúng người đọc." : "Những điều mới, những câu chuyện nhỏ bên tách cà phê."}</p></div>
      {admin && <a className="news-button primary" href="#news/new"><span aria-hidden="true">＋</span> Viết tin mới</a>}
      {!admin && <span className="news-heading-mark" aria-hidden="true">L.</span>}
    </div>
    <div className="news-toolbar">
      {admin ? <div className="news-filter"><label htmlFor="news-filter-status">Trạng thái</label><select id="news-filter-status" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Đang quản lý</option>
        <option value="PUBLISHED">Đã đăng</option><option value="DRAFT">Bản nháp</option><option value="ARCHIVED">Đã lưu trữ</option></select></div>
        : <p className="news-section-label">Dành cho bạn</p>}
      <div className="news-filter"><label htmlFor="news-filter-sort">Sắp xếp</label><select id="news-filter-sort" value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}><option value="publishedAt,desc">Mới xuất bản</option><option value="updatedAt,desc">Mới cập nhật</option><option value="createdAt,desc">Mới tạo</option></select></div>
      <button type="button" className="news-button subtle" onClick={() => setAttempt(v => v + 1)}>Làm mới</button>
    </div>
    {connection && <p className="news-connection" role="status"><span className={connection === "ready" ? "is-connected" : ""} aria-hidden="true" />
      {connection === "ready" ? "Tin tức tự cập nhật khi có thay đổi." : "Đang kết nối cập nhật tin tức. Bạn vẫn có thể làm mới danh sách."}</p>}
    <div aria-busy={view.state === "loading"}>
      {view.state === "loading" ? <div className="news-empty" role="status">Đang tải tin tức…</div>
        : view.state === "error" ? <div className="news-empty" role="alert"><h2>Chưa tải được tin tức</h2><p>{view.error}</p><button className="news-button" onClick={() => setAttempt(v => v + 1)}>Thử lại</button></div>
          : !view.data.content.length ? <div className="news-empty"><span className="news-empty-mark" aria-hidden="true">✦</span><h2>{status === "ARCHIVED" ? "Chưa có tin đã lưu trữ" : "Chưa có câu chuyện nào"}</h2><p>{admin ? "Bắt đầu bằng một tin mới dành cho nhân viên hoặc khách hàng." : "Những tin tức dành cho bạn sẽ xuất hiện tại đây."}</p></div>
            : <><p className="news-result-count" role="status">{view.data.totalElements} tin tức</p><ul className={admin ? "news-admin-list" : "news-grid"}>
              {view.data.content.map(post => <li key={post.id} className="news-card">
                <a className="news-card-cover" href={`#news/${post.id}`} tabIndex={-1} aria-hidden="true"><NewsImage url={post.thumbnailUrl} authorization={authorization} /></a>
                <div className="news-card-copy"><div className="news-card-meta"><time dateTime={post.publishedAt || post.createdAt}>{newsDate(post.publishedAt || post.createdAt)}</time>
                  {admin && <span className={`news-badge ${post.status.toLowerCase()}`}>{statusNames[post.status]}</span>}</div>
                  <h2><a href={`#news/${post.id}`}>{post.title}</a></h2>{post.summary && <p>{post.summary}</p>}
                  <div className="news-card-bottom">{admin ? <span>{post.audiences.map(a => audienceNames[a]).join(" · ") || "Chưa chọn người nhận"}</span> : <span>{post.authorName}</span>}
                    <a href={admin ? `#news/${post.id}/edit` : `#news/${post.id}`} className="news-text-link" aria-label={`${admin ? "Chỉnh sửa" : "Đọc tin"}: ${post.title}`}>{admin ? "Chỉnh sửa" : "Đọc câu chuyện"} <span aria-hidden="true">↗</span></a></div>
                </div></li>)}</ul>
              <nav className="news-pagination" aria-label="Phân trang tin tức"><button className="news-button" disabled={page === 0} onClick={() => { setPage(v => v - 1); heading.current?.focus(); }}>← Trang trước</button>
                <span>Trang {page + 1} / {view.data.totalPages}</span><button className="news-button" disabled={view.data.last} onClick={() => { setPage(v => v + 1); heading.current?.focus(); }}>Trang sau →</button></nav></>}
    </div>
  </section>;
}

function NewsDetail({ id, authorization, admin, revision }) {
  const [view, setView] = useState({ state: "loading" }), [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const dialog = useRef(null), operation = useRef(null);
  useEffect(() => {
    const controller = new AbortController(); operation.current = controller;
    return () => controller.abort();
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setView({ state: "loading" });
    newsRequest(`/${id}`, authorization, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setView({ state: "ready", ...data });
    }).catch(e => { if (!controller.signal.aborted) setView({ state: "error", error: newsError(e) }); });
    return () => controller.abort();
  }, [id, authorization, revision, attempt]);
  async function archive() {
    if (busy || operation.current.signal.aborted) return;
    const signal = operation.current.signal;
    setBusy(true); setError("");
    try {
      await newsRequest(`/${id}`, authorization, { method: "DELETE", etag: view.etag, signal });
      if (!signal.aborted) location.hash = "#news";
    } catch (e) { if (!signal.aborted) { setError(newsError(e)); dialog.current?.close(); } }
    finally { if (!signal.aborted) setBusy(false); }
  }
  if (view.state !== "ready") return <section className="news-page news-empty"><a className="news-text-link" href="#news">← Danh sách tin tức</a>
    {view.state === "loading" ? <p role="status">Đang tải bài viết…</p> : <div role="alert"><h1>Chưa mở được bài viết</h1><p>{view.error}</p><button className="news-button" onClick={() => setAttempt(v => v + 1)}>Thử lại</button></div>}</section>;
  const post = view.data;
  return <article className="news-page news-detail"><div className="news-detail-bar"><a className="news-text-link" href="#news">← Danh sách tin tức</a>
    {admin && <div className="news-actions"><a className="news-button" href={`#news/${id}/edit`}>Chỉnh sửa</a>{post.status !== "ARCHIVED" && <button className="news-button danger" onClick={() => dialog.current.showModal()}>Lưu trữ tin</button>}</div>}</div>
    {error && <div className="news-error" role="alert"><p>{error}</p><button className="news-button" onClick={() => { setAttempt(v => v + 1); setError(""); }}>Tải bản mới</button></div>}
    <header className="news-article-header"><p className="eyebrow">LECKER • Câu chuyện</p>{admin && <p className={`news-badge ${post.status.toLowerCase()}`}>{statusNames[post.status]}</p>}
      <h1>{post.title}</h1>{post.summary && <p className="news-article-summary">{post.summary}</p>}
      <div className="news-article-byline"><span>{post.authorName}</span><span aria-hidden="true">·</span><time dateTime={post.publishedAt || post.createdAt}>{newsDate(post.publishedAt || post.createdAt)}</time></div>
      {admin && <p className="news-recipients">Gửi đến: {post.audiences.map(a => audienceNames[a]).join(", ") || "Chưa chọn"}</p>}</header>
    {post.images.length > 0 && <div className="news-gallery">{post.images.map((image, index) => <NewsImage key={image.mediaId} url={image.secureUrl} authorization={authorization} alt={`Ảnh ${index + 1} của bài viết ${post.title}`} />)}</div>}
    <div className="news-article-body">{post.body}</div>
    <div className="news-article-end"><span aria-hidden="true">✦</span><p>LECKER Coffee Cafe</p><a href="#news">Khám phá thêm câu chuyện →</a></div>
    <dialog className="news-dialog" ref={dialog} aria-labelledby="archive-title" onCancel={e => { if (busy) e.preventDefault(); }} onKeyDown={event => {
      if (event.key !== "Tab") return;
      const buttons = [...event.currentTarget.querySelectorAll("button:not(:disabled)")];
      if (!buttons.length) { event.preventDefault(); return; }
      if (event.shiftKey && document.activeElement === buttons[0]) { event.preventDefault(); buttons.at(-1).focus(); }
      else if (!event.shiftKey && document.activeElement === buttons.at(-1)) { event.preventDefault(); buttons[0].focus(); }
    }}>
      <h2 id="archive-title">Lưu trữ tin tức?</h2><p>“{post.title}” sẽ được ẩn với người đọc. Bạn có thể khôi phục bằng cách chỉnh sửa tin đã lưu trữ.</p>
      <div className="news-actions"><button autoFocus className="news-button" disabled={busy} onClick={() => dialog.current.close()}>Giữ lại</button><button className="news-button danger" disabled={busy} onClick={archive}>{busy ? "Đang lưu trữ…" : "Xác nhận lưu trữ"}</button></div>
    </dialog>
  </article>;
}
