import { useEffect, useState } from "react";

const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
const specials = [
  { title: "Đồ uống nổi bật", description: "Sáng tạo hương vị", image: "/images/iced-coffee.jpg" },
  { title: "Không gian quán", description: "Ấm áp và thư giãn", image: "/images/cafe-interior.jpg" },
  { title: "Đội ngũ Barista", description: "Chuyên tâm từng tách", image: "/images/coffee-cup.jpg" },
];

function validList(items, amount) {
  return Array.isArray(items) && items.length <= 5 && items.every((item) =>
    typeof item?.id === "string" && /^[1-9]\d{0,18}$/.test(item.id) && typeof item.name === "string" && item.name.trim()
    && typeof item[amount] === "string" && /^[1-9]\d{0,29}$/.test(item[amount]));
}

function Rankings({ status, data, retry }) {
  const max = Number(data?.mostOrdered[0]?.orderedQuantity ?? 1);
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
          value={(item) => <meter className="rank-meter" min="0" max={max} value={Number(item.orderedQuantity)}
            aria-label={`${item.orderedQuantity} món đã gọi`} />} />
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
      <MenuThumbnail url={item.imageUrl} />
      <span className="rank-name">{item.name}</span>
      {value(item)}
    </li>)}
  </ol>;
}

function MenuThumbnail({ url }) {
  const [failed, setFailed] = useState(null);
  const safe = typeof url === "string" && (/^https:\/\//i.test(url) || /^\/(?!\/)/.test(url)) && !/[\\\r\n]/.test(url);
  return safe && failed !== url
    ? <img className="rank-image" src={url} width="52" height="52" alt="" loading="lazy" onError={() => setFailed(url)} />
    : <span className="rank-image missing-image" role="img" aria-label="Chưa có ảnh món">—</span>;
}

export default function HomePage() {
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
      <div className="hero-art" aria-hidden="true">
        <img src="/images/latte.jpg" alt="" width="700" height="1050" fetchPriority="high" />
        <span className="hero-image-caption">Một chút cà phê, một chút thảnh thơi.</span>
      </div>
      <div className="hero-copy">
        <p className="eyebrow">Bản sắc cà phê</p>
        <h1 id="hero-title"><span>High Quality</span> COFFEE</h1>
        <p className="hero-subtitle">Mỗi tách cà phê là một câu chuyện</p>
        <a className="hero-cta" href="#newest">Khám phá menu <span aria-hidden="true">→</span></a>
        <span className="hero-note">Pha bằng cả sự tận tâm.</span>
      </div>
    </section>

    <section id="about" className="specials" aria-labelledby="specials-title">
      <h2 id="specials-title">Our Specials — Câu chuyện Lecker</h2>
      <div className="special-grid">
        {specials.map(({ title, description, image }) => <article className="special" key={title}>
          <img className="special-tile" src={image} alt="" width="116" height="116" loading="lazy" />
          <div><h3>{title}</h3><p>{description}</p></div>
        </article>)}
      </div>
    </section>

    <Rankings status={result.status} data={result.data} retry={() => {
      setResult({ status: "loading", data: null });
      setAttempt((value) => value + 1);
    }} />
  </div>;
}
