import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../auth/useAuth.js";
import { getCurrentUserApi } from "../auth/authApi.js";
import NewsImage from "./NewsImage.jsx";
import NewsEditor from "./NewsEditor.jsx";
import { audienceNames, hasNewsVersion, isNewsDetail, isNewsPage, matchesNewsScope, newsDate, isUncertainNewsWrite, newsError, newsRequest, statusNames } from "./newsApi.js";
import "./news.css";

const newsPageSize = 6;

export default function NewsPage({ authorization, account, identityStatus, route, revision, connection, setNavigationGuard }) {
  const { captureSession, isCurrentSession, expireSession } = useAuth();
  const verification = useRef(null);
  const checkAccess = useCallback(async (snapshot) => {
    if (!snapshot.token || !isCurrentSession(snapshot)) return;
    if (verification.current?.generation === snapshot.generation) return;
    verification.current = snapshot;
    try {
      await getCurrentUserApi(snapshot.token, AbortSignal.timeout(10000));
    } catch (error) {
      if (error.status === 401 || error.status === 403) expireSession(snapshot);
    } finally {
      if (verification.current === snapshot) verification.current = null;
    }
  }, [isCurrentSession, expireSession]);
  const request = useCallback(async (path, options) => {
    const snapshot = captureSession();
    try {
      return await newsRequest(path, authorization, options);
    } catch (error) {
      if ([401, 403].includes(error.status) && authorization === `Bearer ${snapshot.token}`) void checkAccess(snapshot);
      throw error;
    }
  }, [authorization, captureSession, checkAccess]);

  if (identityStatus === "loading") return <section className="news-page"><div className="news-empty" role="status">Đang kiểm tra phiên đăng nhập…</div></section>;
  if (identityStatus === "error") return <section className="news-page" aria-labelledby="news-title">
    <NewsBanner title="Tin tức LECKER" />
  </section>;
  if (!account) return <section className="news-page" aria-labelledby="news-title">
    <NewsBanner title="Tin tức LECKER" description="Đăng nhập để xem những tin tức dành cho bạn.">
      <a className="news-button primary" href="#login">Đăng nhập <span aria-hidden="true">→</span></a>
    </NewsBanner>
  </section>;
  const match = /^#news\/([0-9]+)(\/edit)?$/.exec(route);
  const scope = `${account.id}:${account.role}`;
  if (route === "#news/new" || match?.[2]) {
    if (account.role !== "ADMIN") return <section className="news-page news-empty"><h1>Bạn không có quyền quản lý tin tức</h1><a href="#news">Về danh sách tin tức</a></section>;
    return <NewsEditor key={`${scope}:${route}`} id={match?.[1]} accountId={account.id} request={request} authorization={authorization} setNavigationGuard={setNavigationGuard} />;
  }
  if (match) return <NewsDetail key={`${scope}:${match[1]}`} id={match[1]} request={request} authorization={authorization} role={account.role} revision={revision} />;
  return <NewsList key={scope} request={request} authorization={authorization} role={account.role} revision={revision} connection={account.role === "STAFF" ? connection : null} />;
}

function NewsBanner({ title, description, headingRef, children }) {
  return <div className="news-banner">
    <div className="news-banner-copy">
      <h1 ref={headingRef} id="news-title" tabIndex={-1}>{title}</h1>
      {description && <p>{description}</p>}
      {children}
    </div>
    <img src="/images/news-journal.jpg" alt="" />
  </div>;
}

function NewsList({ request, authorization, role, revision, connection }) {
  const admin = role === "ADMIN";
  const [page, setPage] = useState(0), [status, setStatus] = useState(""), [sort, setSort] = useState("publishedAt,desc");
  const [attempt, setAttempt] = useState(0), [result, setResult] = useState(null);
  const heading = useRef(null);
  const query = `?page=${page}&size=${newsPageSize}&sort=${sort}${status ? `&status=${status}` : ""}`;
  const requestKey = `${query}:${attempt}:${revision}`;
  const view = result?.key === requestKey ? result : { state: "loading" };
  useEffect(() => {
    const controller = new AbortController();
    request(query, { signal: controller.signal })
      .then(({ data }) => {
        if (controller.signal.aborted) return;
        if (!isNewsPage(data, page, newsPageSize) || !data.content.every(post => matchesNewsScope(post, role, status))) {
          throw new Error("Invalid news page response");
        }
        if (!data.content.length && page > 0 && page >= data.totalPages) {
          setPage(Math.max(0, data.totalPages - 1));
          return;
        }
        setResult({ key: requestKey, state: "ready", data });
      }).catch(error => { if (!controller.signal.aborted) setResult({ key: requestKey, state: "error", error: newsError(error) }); });
    return () => controller.abort();
  }, [request, page, query, requestKey, role, status]);
  return <section className="news-page" aria-labelledby="news-title">
    {admin ? <div className="news-heading">
      <h1 ref={heading} id="news-title" tabIndex={-1}>Quản lý tin tức</h1>
      <a className="news-button primary" href="#news/new"><span aria-hidden="true">＋</span> Viết tin mới</a>
    </div> : <NewsBanner title="Chuyện từ LECKER." description="Tin mới và những câu chuyện bên tách cà phê." headingRef={heading} />}
    <div className="news-toolbar">
      <h2 className="news-section-label">{admin ? "Danh sách bài viết" : "Bảng tin"}</h2>
      {admin && <div className="news-filter"><label htmlFor="news-filter-status">Trạng thái</label><select id="news-filter-status" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Đang quản lý</option>
        <option value="PUBLISHED">Đã đăng</option><option value="DRAFT">Bản nháp</option><option value="ARCHIVED">Đã lưu trữ</option></select></div>}
      <div className="news-filter"><label htmlFor="news-filter-sort">Sắp xếp</label><select id="news-filter-sort" value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}><option value="publishedAt,desc">Mới xuất bản</option><option value="updatedAt,desc">Mới cập nhật</option><option value="createdAt,desc">Mới tạo</option></select></div>
      <button type="button" className="news-button" onClick={() => setAttempt(v => v + 1)}><span aria-hidden="true">↻</span> Làm mới</button>
    </div>
    {connection && <p className="news-connection" role="status"><span className={connection === "ready" ? "is-connected" : ""} aria-hidden="true" />
      {connection === "ready" ? "Tin tức tự cập nhật khi có thay đổi." : "Đang kết nối cập nhật tin tức. Bạn vẫn có thể làm mới danh sách."}</p>}
    <div aria-busy={view.state === "loading"}>
      {view.state === "loading" ? <div className="news-empty" role="status">Đang tải tin tức…</div>
        : view.state === "error" ? <div className="news-empty" role="alert"><h2>Chưa tải được tin tức</h2><p>{view.error}</p>
          <button className="news-button" onClick={() => setAttempt(v => v + 1)}>Thử lại</button>
          {page > 0 && <button className="news-button" onClick={() => { setPage(0); heading.current?.focus(); }}>Về trang đầu</button>}
        </div>
          : !view.data.content.length ? <div className="news-empty">
            <span className="news-empty-mark" aria-hidden="true">≡</span>
            <h2>{status === "DRAFT" ? "Chưa có bản nháp" : status ? `Chưa có tin ${statusNames[status].toLowerCase()}` : "Chưa có câu chuyện nào"}</h2>
            {admin
              ? <a className="news-button primary" href="#news/new">Tạo bài viết</a>
              : <><p>Khi LECKER đăng tin dành cho bạn, bài viết sẽ xuất hiện tại đây.</p><a className="news-button" href="#customer">Xem thực đơn <span aria-hidden="true">→</span></a></>}
          </div>
            : <><p className="news-result-count" role="status">{view.data.totalElements} tin tức</p><ul className={admin ? "news-admin-list" : "news-grid"}>
              {view.data.content.map(post => <li key={post.id} className="news-card">
                <a className="news-card-cover" href={`#news/${post.id}`} tabIndex={-1} aria-hidden="true"><NewsImage request={request} url={post.thumbnailUrl} authorization={authorization} /></a>
                <div className="news-card-copy"><div className="news-card-meta"><time dateTime={post.publishedAt || post.createdAt}>{newsDate(post.publishedAt || post.createdAt)}</time>
                  {admin && <span className={`news-badge ${post.status.toLowerCase()}`}>{statusNames[post.status]}</span>}</div>
                  <h2><a href={`#news/${post.id}`}>{post.title}</a></h2>{post.summary && <p>{post.summary}</p>}
                  <div className="news-card-bottom">{admin ? <span>{post.audiences.map(a => audienceNames[a]).join(" · ") || "Chưa chọn người nhận"}</span> : <span>{post.authorName}</span>}
                    <a href={admin ? `#news/${post.id}/edit` : `#news/${post.id}`} className="news-text-link" aria-label={`${admin ? "Chỉnh sửa" : "Đọc tin"}: ${post.title}`}>{admin ? "Chỉnh sửa" : "Đọc câu chuyện"} <span aria-hidden="true">↗</span></a></div>
                </div></li>)}</ul>
              {view.data.totalPages > 1 && <nav className="news-pagination" aria-label="Phân trang tin tức"><button className="news-button" disabled={page === 0} onClick={() => { setPage(v => v - 1); heading.current?.focus(); }}>← Trang trước</button>
                <span>Trang {page + 1} / {view.data.totalPages}</span><button className="news-button" disabled={view.data.last} onClick={() => { setPage(v => v + 1); heading.current?.focus(); }}>Trang sau →</button></nav>}</>}
    </div>
  </section>;
}

function NewsDetail({ id, request, authorization, role, revision }) {
  const admin = role === "ADMIN";
  const [view, setView] = useState({ state: "loading" }), [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [archiveNeedsReload, setArchiveNeedsReload] = useState(false);
  const archiving = useRef(false);
  const dialog = useRef(null), operation = useRef(null);
  useEffect(() => {
    const controller = new AbortController(); operation.current = controller;
    return () => controller.abort();
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setView({ state: "loading" });
    request(`/${id}`, { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      if (!isNewsDetail(result.data, id) || !matchesNewsScope(result.data, role)
        || (admin && !hasNewsVersion(result.data, result.etag))) throw new Error("Invalid news detail response");
      setView({ state: "ready", ...result }); setArchiveNeedsReload(false);
    }).catch(e => { if (!controller.signal.aborted) setView({ state: "error", error: newsError(e) }); });
    return () => controller.abort();
  }, [id, request, revision, attempt, role, admin]);
  async function archive() {
    if (archiving.current || archiveNeedsReload || operation.current.signal.aborted) return;
    const signal = operation.current.signal;
    archiving.current = true;
    setBusy(true); setError("");
    try {
      await request(`/${id}`, { method: "DELETE", etag: view.etag, signal });
      if (!signal.aborted) location.hash = "#news";
    } catch (e) {
      if (!signal.aborted) {
        const uncertain = isUncertainNewsWrite(e);
        const staleVersion = [412, 428].includes(e.status);
        setArchiveNeedsReload(uncertain || staleVersion);
        setError(uncertain ? "Chưa xác nhận được kết quả lưu trữ. Tải bản mới để kiểm tra trạng thái."
          : staleVersion ? "Phiên bản bài viết đã thay đổi. Tải bản mới trước khi lưu trữ." : newsError(e));
        dialog.current?.close();
      }
    }
    finally {
      archiving.current = false;
      if (!signal.aborted) setBusy(false);
    }
  }
  if (view.state !== "ready") return <section className="news-page news-empty"><a className="news-text-link" href="#news">← Danh sách tin tức</a>
    {view.state === "loading" ? <p role="status">Đang tải bài viết…</p> : <div role="alert"><h1>Chưa mở được bài viết</h1><p>{view.error}</p><button className="news-button" onClick={() => setAttempt(v => v + 1)}>Thử lại</button></div>}</section>;
  const post = view.data;
  return <article className="news-page news-detail"><div className="news-detail-bar"><a className="news-text-link" href="#news">← Danh sách tin tức</a>
    {admin && <div className="news-actions"><a className="news-button" href={`#news/${id}/edit`}>Chỉnh sửa</a>{post.status !== "ARCHIVED" && <button disabled={archiveNeedsReload} className="news-button danger" onClick={() => dialog.current.showModal()}>Lưu trữ tin</button>}</div>}</div>
    {error && <div className="news-error" role="alert"><p>{error}</p><button className="news-button" onClick={() => { setAttempt(v => v + 1); setError(""); }}>Tải bản mới</button></div>}
    <header className="news-article-header">{admin && <p className={`news-badge ${post.status.toLowerCase()}`}>{statusNames[post.status]}</p>}
      <h1>{post.title}</h1>{post.summary && <p className="news-article-summary">{post.summary}</p>}
      <div className="news-article-byline"><span>{post.authorName}</span><span aria-hidden="true">·</span><time dateTime={post.publishedAt || post.createdAt}>{newsDate(post.publishedAt || post.createdAt)}</time></div>
      {admin && <p className="news-recipients">Gửi đến: {post.audiences.map(a => audienceNames[a]).join(", ") || "Chưa chọn"}</p>}</header>
    {post.images.length > 0 && <div className="news-gallery">{post.images.map((image, index) => <NewsImage request={request} key={image.mediaId} url={image.secureUrl} authorization={authorization} alt={`Ảnh ${index + 1} của bài viết ${post.title}`} />)}</div>}
    <div className="news-article-body">{post.body}</div>
    <div className="news-article-end"><a className="news-button" href="#news">← Về bảng tin</a></div>
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
