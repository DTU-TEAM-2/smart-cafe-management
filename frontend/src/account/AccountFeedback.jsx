import { useEffect, useRef } from "react";

export function ErrorSummary({ errors, prefix }) {
  const summary = useRef(null);
  useEffect(() => { if (Object.keys(errors).length) summary.current?.focus(); }, [errors]);
  if (!Object.keys(errors).length) return null;
  return <div className="account-feedback account-feedback-error" ref={summary} tabIndex={-1} role="alert">
    <strong>Vui lòng kiểm tra thông tin</strong>
    <ul>{Object.entries(errors).map(([field, message]) => <li key={field}>
      <a href={`#${prefix}-${field}`} onClick={event => { event.preventDefault(); document.getElementById(`${prefix}-${field}`)?.focus(); }}>{message}</a>
    </li>)}</ul>
  </div>;
}

export function AccountIssue({ issue }) {
  const ref = useRef(null);
  useEffect(() => { if (issue && !issue.field) ref.current?.focus(); }, [issue]);
  return issue && !issue.field ? <div ref={ref} tabIndex={-1} role="alert" className="account-feedback account-feedback-error">{issue.message}</div> : null;
}
