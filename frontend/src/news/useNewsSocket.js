import { useEffect, useRef, useState } from "react";

const readyTimeoutMs = 15000;

export default function useNewsSocket(authorization, account, onUnauthorized) {
  const [revision, setRevision] = useState(0);
  const [connection, setConnection] = useState("offline");
  const retry = useRef({ failures: 0, after: 0 });
  useEffect(() => {
    if (account?.role !== "STAFF" || !authorization) return;
    let socket, timer, readyTimer;
    function retire() {
      clearTimeout(readyTimer);
      if (!socket) return;
      socket.onopen = socket.onmessage = socket.onclose = null;
      if (socket.readyState < WebSocket.CLOSING) socket.close();
      socket = null;
    }
    function disconnected(code) {
      retire();
      setConnection("offline");
      const delay = Math.min(30000, 1000 * 2 ** Math.min(retry.current.failures++, 5));
      retry.current.after = Date.now() + delay;
      // A fresh account response restarts this effect only if it still confirms STAFF.
      if (code === 1008) { onUnauthorized(); return; }
      timer = setTimeout(connect, delay);
    }
    function connect() {
      setConnection("connecting");
      const current = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/api/v1/news/events`);
      socket = current;
      let ready = false;
      readyTimer = setTimeout(() => disconnected(), readyTimeoutMs);
      current.onopen = () => current.send(authorization);
      current.onmessage = event => {
        try {
          const { type } = JSON.parse(event.data);
          if (type === "READY" && !ready) {
            ready = true;
            clearTimeout(readyTimer);
            retry.current = { failures: 0, after: 0 };
            setConnection("ready");
            setRevision(value => value + 1);
          } else if (type === "NEWS_CHANGED" && ready) setRevision(value => value + 1);
        } catch { /* Unknown server frames carry no UI authority. */ }
      };
      current.onclose = event => disconnected(event.code);
    }
    timer = setTimeout(connect, Math.max(0, retry.current.after - Date.now()));
    return () => { clearTimeout(timer); retire(); };
  }, [authorization, account, onUnauthorized]);
  return { revision, connection };
}
