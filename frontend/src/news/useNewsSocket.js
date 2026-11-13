import { useEffect, useRef, useState } from "react";

export default function useNewsSocket(authorization, account, onUnauthorized) {
  const [revision, setRevision] = useState(0);
  const [connection, setConnection] = useState("offline");
  const retry = useRef({ failures: 0, after: 0 });
  useEffect(() => {
    if (account?.role !== "STAFF" || !authorization) return;
    let socket, timer, stopped = false;
    function connect() {
      if (stopped) return;
      setConnection("connecting");
      socket = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/api/v1/news/events`);
      socket.onopen = () => { if (!stopped) socket.send(authorization); };
      socket.onmessage = event => {
        if (stopped) return;
        try {
          const { type } = JSON.parse(event.data);
          if (type === "READY") { retry.current = { failures: 0, after: 0 }; setConnection("ready"); setRevision(value => value + 1); }
          if (type === "NEWS_CHANGED") setRevision(value => value + 1);
        } catch { /* Unknown server frames carry no UI authority. */ }
      };
      socket.onclose = event => {
        if (stopped) return;
        setConnection("offline");
        const delay = Math.min(30000, 1000 * 2 ** Math.min(retry.current.failures++, 5));
        retry.current.after = Date.now() + delay;
        // A fresh account response restarts this effect only if it still confirms STAFF.
        // Keep backoff across those rechecks, so repeated policy closes cannot cause a hot loop.
        if (event.code === 1008) { onUnauthorized(); return; }
        timer = setTimeout(connect, delay);
      };
      socket.onerror = () => { /* onclose schedules reconnect. */ };
    }
    timer = setTimeout(connect, Math.max(0, retry.current.after - Date.now()));
    return () => { stopped = true; clearTimeout(timer); if (socket?.readyState === WebSocket.CONNECTING) socket.onopen = () => socket.close(); else socket?.close(); };
  }, [authorization, account, onUnauthorized]);
  return { revision, connection };
}
