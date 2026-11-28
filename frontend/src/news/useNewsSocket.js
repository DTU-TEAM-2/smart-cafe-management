import { useEffect, useRef, useState } from "react";

const readyTimeoutMs = 15000;

export default function useNewsSocket(authorization, account, onUnauthorized) {
  const [revision, setRevision] = useState(0);
  const [connection, setConnection] = useState(null);
  const retry = useRef({ failures: 0, after: 0 });
  useEffect(() => {
    if (account?.role !== "STAFF" || !authorization) return;
    let socket, timer, readyTimer, stopped = false;
    const report = status => setConnection({ account, authorization, status });
    function retire() {
      clearTimeout(readyTimer);
      if (!socket) return;
      socket.onopen = socket.onmessage = socket.onclose = null;
      if (socket.readyState < WebSocket.CLOSING) socket.close();
      socket = null;
    }
    function disconnected(code) {
      retire();
      report("offline");
      const delay = Math.min(30000, 1000 * 2 ** Math.min(retry.current.failures++, 5));
      retry.current.after = Date.now() + delay;
      // A fresh account response restarts this effect only if it still confirms STAFF.
      if (code === 1008) { onUnauthorized(); return; }
      timer = setTimeout(connect, delay);
    }
    function connect() {
      if (stopped) return;
      report("connecting");
      let current;
      try {
        current = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/api/v1/news/events`);
      } catch {
        disconnected();
        return;
      }
      socket = current;
      const ownsSocket = () => !stopped && socket === current;
      let ready = false;
      readyTimer = setTimeout(() => { if (ownsSocket()) disconnected(); }, readyTimeoutMs);
      current.onopen = () => {
        if (!ownsSocket()) return;
        try { current.send(authorization); }
        catch { disconnected(); }
      };
      current.onmessage = event => {
        if (!ownsSocket()) return;
        try {
          const { type } = JSON.parse(event.data);
          if (type === "READY" && !ready) {
            ready = true;
            clearTimeout(readyTimer);
            retry.current = { failures: 0, after: 0 };
            report("ready");
            setRevision(value => value + 1);
          } else if (type === "NEWS_CHANGED" && ready) setRevision(value => value + 1);
        } catch { /* Unknown server frames carry no UI authority. */ }
      };
      current.onclose = event => { if (ownsSocket()) disconnected(event.code); };
    }
    timer = setTimeout(connect, Math.max(0, retry.current.after - Date.now()));
    return () => { stopped = true; clearTimeout(timer); retire(); };
  }, [authorization, account, onUnauthorized]);
  return { revision, connection: connection && connection.account === account && connection.authorization === authorization
    ? connection.status : "offline" };
}
