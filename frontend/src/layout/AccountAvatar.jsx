import { useState } from "react";

export default function AccountAvatar({ name, url }) {
  const [failed, setFailed] = useState(null);
  const safe = typeof url === "string" && (/^https:\/\//i.test(url) || /^\/(?!\/)/.test(url)) && !/[\\\r\n]/.test(url);
  return <span className="avatar" aria-hidden="true">{safe && failed !== url
    ? <img src={url} alt="" width="88" height="88" referrerPolicy="no-referrer" onError={() => setFailed(url)} />
    : name?.trim().slice(0, 1).toLocaleUpperCase("vi")}</span>;
}
