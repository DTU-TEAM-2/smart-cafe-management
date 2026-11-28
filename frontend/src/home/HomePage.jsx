import { useEffect, useState } from "react";
import HomeStories from "./HomeStories.jsx";

const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
const quantity = new Intl.NumberFormat("vi-VN");

function validList(items, amount) {
  return Array.isArray(items) && items.length <= 5 && items.every((item) =>
    typeof item?.id === "string" && /^[1-9]\d{0,18}$/.test(item.id)
    && (item.id.length < 19 || item.id <= "9223372036854775807")
    && typeof item.name === "string" && item.name.trim()
    && typeof item[amount] === "string" && /^[1-9]\d{0,29}$/.test(item[amount]))
    && new Set(items.map(item => item.id)).size === items.length;
}

function Rankings({ status, data, retry }) {
  return <>
    {status === "error" && <div className="error-banner" role="alert">
      <span>Chưa tải được danh sách món.</span><button type="button" onClick={retry}>Thử lại</button>
    </div>}
    <div className="rankings" aria-busy={status === "loading"}>
      <section id="newest" aria-labelledby="newest-title">
        <h2 id="newest-title">5 món mới nhất</h2>
        <RankingList status={status} items={data?.newest} empty="Chưa có món mới."
          value={(item) => <strong>{currency.format(BigInt(item.price))}</strong>} />
      </section>
      <section id="most-ordered" aria-labelledby="most-ordered-title">
        <h2 id="most-ordered-title">5 món được gọi nhiều</h2>
        <RankingList status={status} items={data?.mostOrdered} empty="Chưa có lượt gọi món."
          value={(item) => <strong>{quantity.format(BigInt(item.orderedQuantity))} món</strong>} />
      </section>
    </div>
  </>;
}

function RankingList({ status, items = [], empty, value }) {
  if (status === "loading") return <p className="ranking-panel ranking-message" role="status">Đang tải danh sách món…</p>;
  if (status === "error") return <p className="ranking-panel ranking-message">Dữ liệu hiện chưa khả dụng.</p>;
  if (!items.length) return <p className="ranking-panel ranking-message">{empty}</p>;
  return <ol className="ranking-panel ranking-list">
    {items.map((item, index) => <li key={item.id}>
      <span className="rank-number" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
      <span className="rank-name">{item.name}</span>
      {value(item)}
    </li>)}
  </ol>;
}

export default function HomePage({ tableMenuHref } = {}) {
  const [result, setResult] = useState({ status: "loading", data: null });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const timeout = AbortSignal.timeout(10000);
    const signal = AbortSignal.any([controller.signal, timeout]);
    async function load() {
      try {
        const response = await fetch("/api/v1/home/highlights", { signal });
        if (!response.ok) throw new Error("Highlights request failed");
        const data = await response.json();
        if (!validList(data?.newest, "price") || !validList(data?.mostOrdered, "orderedQuantity")) {
          throw new Error("Invalid highlights");
        }
        if (!controller.signal.aborted) setResult({ status: "ready", data });
      } catch {
        if (!controller.signal.aborted) setResult({ status: "error", data: null });
      }
    }
    void load();
    return () => controller.abort();
  }, [attempt]);

  return <div className="home">
    <section className="hero" aria-labelledby="hero-title">
      <div className="hero-art">
        <img src="/images/home-coffee-hero.jpg" alt="" width="1942" height="809" fetchPriority="high" />
      </div>
      <div className="hero-copy">
        <p className="eyebrow">Bản sắc cà phê</p>
        <h1 id="hero-title"><span>High Quality</span> COFFEE</h1>
        <p className="hero-subtitle">Mỗi tách cà phê là một câu chuyện</p>
        <a className="hero-cta" href={tableMenuHref ?? "#customer"}>{tableMenuHref ? "Về thực đơn tại bàn" : "Khám phá thực đơn"} <span aria-hidden="true">→</span></a>
      </div>
    </section>

    <HomeStories />

    <Rankings status={result.status} data={result.data} retry={() => {
      setResult({ status: "loading", data: null });
      setAttempt((value) => value + 1);
    }} />
  </div>;
}
