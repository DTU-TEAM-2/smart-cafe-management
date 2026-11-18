import { useEffect, useState } from "react";
import { safeNewsImage } from "./newsApi.js";

export default function NewsImage(props) {
  // Discard prior private blobs immediately on any source/account change.
  return <ScopedImage key={`${props.authorization}:${props.url}`} {...props} />;
}

function ScopedImage({ url, authorization, alt = "", className = "" }) {
  const safe = safeNewsImage(url);
  const privateImage = safe?.startsWith("/api/");
  const [image, setImage] = useState(privateImage ? null : safe);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!privateImage) return;
    const controller = new AbortController();
    let blobUrl;
    async function load() {
      try {
        const response = await fetch(safe, { headers: { Authorization: authorization }, cache: "no-store", credentials: "same-origin",
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]) });
        if (!response.ok || !["image/png", "image/jpeg"].includes(response.headers.get("Content-Type")?.split(";")[0])) throw new Error("Image unavailable");
        const blob = await response.blob();
        if (controller.signal.aborted) return;
        blobUrl = URL.createObjectURL(blob);
        setImage(blobUrl);
      } catch { if (!controller.signal.aborted) setFailed(true); }
    }
    void load();
    return () => { controller.abort(); if (blobUrl) URL.revokeObjectURL(blobUrl); };
  }, [authorization, privateImage, safe]);
  if (!image || failed) return <div className={`news-image-placeholder ${className}`} role={alt ? "img" : undefined} aria-label={alt || undefined}>
    <span aria-hidden="true">L.</span>{failed && <small>Ảnh chưa khả dụng</small>}</div>;
  return <img src={image} alt={alt} className={className} loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} />;
}
