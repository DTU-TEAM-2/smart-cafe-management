import { useEffect, useState } from "react";
import { useAuth } from "../auth/useAuth.js";
import { fetchFeedbackImageContent, imageUrl, readFeedback } from "./feedbackApi.js";
import "./feedback.css";

const PAGE_SIZE = 20;
function dateLabel(value) {
  return value?.split("-").reverse().join("/") || "—";
}

export default function FeedbackManagement() {
  const { token, captureSession, isCurrentSession, expireSession } = useAuth();
  const [date, setDate] = useState("");
  const [sort, setSort] = useState("newest");
  const [query, setQuery] = useState({ date: "", page: 0, sort: "newest", revision: 0 });
  const [validation, setValidation] = useState("");

  function search(event) {
    event.preventDefault();
    if (!date) { setValidation("Please enter a keyword!"); setQuery(null); return; }
    setValidation("");
    setQuery({ date, page: 0, sort, revision: Date.now() });
  }
  return <section className="feedback-management" aria-labelledby="feedback-title">
    <p className="eyebrow">ADMIN · MANAGE · FEEDBACK</p>
    <h2 id="feedback-title">Feedback Management</h2>
    <form className="feedback-toolbar" onSubmit={search} noValidate>
      <label htmlFor="feedback-date">Search by Date<input id="feedback-date" type="date" min="1000-01-01" max="9998-12-31"
        value={date} onChange={event => setDate(event.target.value)} aria-invalid={Boolean(validation)}
        aria-describedby={validation ? "feedback-date-error" : "feedback-date-help"} /></label>
      <label htmlFor="feedback-sort">Thứ tự<select id="feedback-sort" value={sort} onChange={event => {
        setSort(event.target.value);
        setQuery(current => ({ date: current?.date || "", page: 0, sort: event.target.value, revision: Date.now() }));
      }}><option value="newest">Mới nhất</option><option value="oldest">Cũ nhất</option></select></label>
      <button type="submit" className="btn-primary">Search</button>
      <button type="button" className="btn-secondary" onClick={() => {
        setDate(""); setValidation(""); setQuery({ date: "", page: 0, sort, revision: Date.now() });
      }}>Show All</button>
    </form>
    <p id="feedback-date-help" className="feedback-muted">Ngày tại quán (Asia/Ho_Chi_Minh).</p>
    {validation && <p id="feedback-date-error" role="alert" className="feedback-error">{validation}</p>}
    {query && (
      <FeedbackResults
        key={`${token}:${JSON.stringify(query)}`}
        token={token}
        query={query}
        onPage={page => setQuery({ ...query, page })}
        captureSession={captureSession}
        isCurrentSession={isCurrentSession}
        expireSession={expireSession}
      />
    )}
  </section>;
}

function FeedbackResults({ token, query, onPage, captureSession, isCurrentSession, expireSession }) {
  const [resource, setResource] = useState({ loading: true });
  const [retry, setRetry] = useState(0);
  const [selected, setSelected] = useState(null);

  const handleSessionExpired = (error, snapshot) => {
    setSelected(null);
    setResource({ error });
    if (expireSession && snapshot) {
      expireSession(snapshot);
    }
  };

  useEffect(() => {
    const snapshot = captureSession ? captureSession() : null;
    const controller = new AbortController();
    setResource({ loading: true });
    const params = new URLSearchParams({ page: String(query.page), size: String(PAGE_SIZE), sort: query.sort });
    if (query.date) params.set("date", query.date);
    readFeedback(`?${params}`, token, controller.signal).then(data => {
      if (!Array.isArray(data.content) || !Number.isSafeInteger(data.totalElements) || data.totalElements < 0) throw new Error("Phản hồi máy chủ không hợp lệ.");
      if (!controller.signal.aborted) setResource({ data });
    }).catch(error => {
      if (!controller.signal.aborted) {
        setResource({ error });
      }
    });
    return () => controller.abort();
  }, [token, query, retry]);
  return <div className="feedback-columns">
    <section className="feedback-panel" aria-label="Danh sách phản hồi" aria-busy={Boolean(resource.loading)}>
      {resource.loading && <p role="status">Đang tải phản hồi…</p>}
      {resource.error && <FeedbackError error={resource.error} retry={() => { setSelected(null); setRetry(value => value + 1); }} />}
      {resource.data && <>
        <p className="feedback-muted">{resource.data.totalElements} phản hồi · Trang {query.page + 1}</p>
        <div className="feedback-table-wrap" role="region" aria-label="Bảng phản hồi, cuộn ngang trên màn hình nhỏ" tabIndex={0}><table className="feedback-table">
          <caption className="feedback-sr-only">Danh sách Feedback — chọn ID để xem chi tiết</caption>
          <thead><tr>{["ID", "Creator", "Email", "Date", "Feedback"].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
          <tbody>{resource.data.content.map(item => <tr
            key={item.id}
            className={`feedback-row ${selected === item.id ? "is-selected" : ""}`}
            onClick={() => setSelected(item.id)}
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.target === e.currentTarget && (e.key === "Enter" || e.key === " ")) {
                e.preventDefault();
                setSelected(item.id);
              }
            }}
          >
            <th scope="row">
              <button
                type="button"
                className="feedback-select"
                aria-pressed={selected === item.id}
                aria-label={`Xem phản hồi ${item.id}`}
                onClick={(e) => {
                  e.stopPropagation();
                  setSelected(item.id);
                }}
              >
                #{item.id}
              </button>
            </th>
            <td className="feedback-cell-creator">{item.creator}</td>
            <td className="feedback-cell-email">{item.email}</td>
            <td className="feedback-cell-date">{dateLabel(item.date)}</td>
            <td className="feedback-cell-content"><span className="feedback-preview">{item.content}</span></td>
          </tr>)}</tbody>
        </table></div>
        {resource.data.content.length === 0 && <p role="status">{query.date ? "Not found!" : "Chưa có phản hồi."}</p>}
        <nav className="feedback-pagination" aria-label="Phân trang phản hồi">
          <button type="button" disabled={query.page === 0} onClick={() => onPage(query.page - 1)}>Trang trước</button>
          <button type="button" disabled={(query.page + 1) * PAGE_SIZE >= resource.data.totalElements} onClick={() => onPage(query.page + 1)}>Trang sau</button>
        </nav>
      </>}
    </section>
    <aside className="feedback-panel feedback-detail" aria-label="Chi tiết phản hồi">
      <h3>Feedback's Info</h3>
      {selected && resource.data ? (
        <FeedbackDetail
          key={selected}
          id={selected}
          token={token}
          captureSession={captureSession}
          isCurrentSession={isCurrentSession}
          onSessionExpired={handleSessionExpired}
        />
      ) : (
        <p className="feedback-muted">Chọn một phản hồi để xem thông tin.</p>
      )}
    </aside>
  </div>;
}

function FeedbackError({ error, retry }) {
  return <div role="alert" className="feedback-error"><p>{error.message}</p>
    {[401, 403].includes(error.status) ? <a href="#login">Đăng nhập lại</a> : <button type="button" onClick={retry}>Thử lại</button>}
  </div>;
}

function FeedbackDetail({ id, token, captureSession, isCurrentSession, onSessionExpired }) {
  const [resource, setResource] = useState({ loading: true });
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const snapshot = captureSession ? captureSession() : null;
    const controller = new AbortController();
    setResource({ loading: true });
    readFeedback(`/${encodeURIComponent(id)}`, token, controller.signal).then(data => {
      if (data.id !== id || !Array.isArray(data.images)) throw new Error("Phản hồi máy chủ không hợp lệ.");
      if (!controller.signal.aborted) setResource({ data });
    }).catch(error => {
      if (!controller.signal.aborted) {
        setResource({ error });
        if (error.status === 401 && isCurrentSession && isCurrentSession(snapshot)) {
          onSessionExpired?.(error, snapshot);
        }
      }
    });
    return () => controller.abort();
  }, [id, token, retry, captureSession, isCurrentSession, onSessionExpired]);
  const data = resource.data;
  return <div aria-busy={Boolean(resource.loading)}>
    {resource.loading && <p role="status">Đang tải chi tiết…</p>}
    {resource.error && <FeedbackError error={resource.error} retry={() => setRetry(value => value + 1)} />}
    {data && <><dl>{[["ID", data.id], ["Creator", data.creator], ["Email", data.email], ["Date", dateLabel(data.date)], ["Rating", `${data.rating}/5`]].map(([label, value]) =>
      <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      <h4>Content</h4><p className="feedback-content">{data.content}</p><h4>Ảnh đính kèm</h4>
      {data.images.length ? (
        <div className="feedback-images">
          {data.images.map(image => (
            <FeedbackImage
              key={image.id}
              feedbackId={id}
              image={image}
              token={token}
              captureSession={captureSession}
              isCurrentSession={isCurrentSession}
              onSessionExpired={onSessionExpired}
            />
          ))}
        </div>
      ) : (
        <p>Không có ảnh đính kèm.</p>
      )}
    </>}
  </div>;
}

function FeedbackImage({ feedbackId, image, token, captureSession, isCurrentSession, onSessionExpired }) {
  const [src, setSrc] = useState(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl = null;
    let cancelled = false;
    const snapshot = captureSession ? captureSession() : null;
    const controller = new AbortController();

    setLoading(true);
    setFailed(false);
    setSrc(null);

    if (token && feedbackId && image.id) {
      fetchFeedbackImageContent(feedbackId, image.id, token, controller.signal)
        .then(url => {
          if (!cancelled) {
            objectUrl = url;
            setSrc(url);
            setLoading(false);
          } else if (url && typeof URL.revokeObjectURL === "function") {
            URL.revokeObjectURL(url);
          }
        })
        .catch((error) => {
          if (cancelled) return;
          if (error.status === 401 && isCurrentSession && isCurrentSession(snapshot)) {
            onSessionExpired?.(error, snapshot);
          }
          const fallback = imageUrl(image.url);
          if (fallback && !fallback.includes("storage.googleapis.com")) {
            setSrc(fallback);
            setLoading(false);
          } else {
            setFailed(true);
            setLoading(false);
          }
        });
    } else {
      const fallback = imageUrl(image.url);
      if (fallback) {
        setSrc(fallback);
        setLoading(false);
      } else {
        setFailed(true);
        setLoading(false);
      }
    }

    return () => {
      cancelled = true;
      controller.abort();
      if (objectUrl && typeof URL.revokeObjectURL === "function") {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [feedbackId, image.id, image.url, token, captureSession, isCurrentSession, onSessionExpired]);

  if (loading) {
    return <p role="status">Đang tải ảnh…</p>;
  }

  if (!src || failed) {
    return <p>Ảnh chưa khả dụng hoặc chưa được cấp quyền xem.</p>;
  }

  return (
    <img
      src={src}
      alt={`Ảnh đính kèm phản hồi ${image.id}`}
      referrerPolicy="no-referrer"
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
}
