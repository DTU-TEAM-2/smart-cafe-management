import { useId, useRef } from "react";

const stories = [
  {
    title: "Đồ uống nổi bật",
    description: "Từ cà phê đen đậm vị đến latte êm dịu, chọn ly hợp gu của bạn.",
    image: "/images/story-drinks.jpg",
    paragraphs: [
      "Có những buổi sáng chỉ cần một ly cà phê đen, với vị đắng rõ và dư vị đọng lại sau ngụm đầu tiên. Khi muốn một ly dịu hơn, bạc xỉu là cách để thưởng thức hương cà phê qua vị sữa ngọt nhẹ.",
      "Latte và cappuccino đều kết hợp espresso với sữa. Latte cho cảm giác mềm và dễ uống, còn lớp bọt dày hơn của cappuccino khiến mỗi ngụm có kết cấu khác. Cùng một nền espresso vẫn có thể cho hai trải nghiệm riêng.",
      "Nếu chưa biết bắt đầu từ đâu, hãy thử chọn giữa vị cà phê đậm và vị sữa dịu. Khi muốn đổi vị, bạn có thể quay lại thực đơn và thử một ly theo cách còn lại.",
    ],
  },
  {
    title: "Không gian quán",
    description: "Một chỗ dừng để đọc vài trang sách hoặc ngồi lâu hơn với bạn bè.",
    image: "/images/story-space.jpg",
    paragraphs: [
      "Có hôm ghé quán chỉ để uống một ly rồi đi. Có hôm bạn mang theo một cuốn sách, mở trang đang đọc dở và chợt nhận ra đã ngồi lâu hơn dự định. LECKER muốn dành chỗ cho cả hai khoảnh khắc ấy.",
      "Cảm giác mà quán hướng đến là sự ấm áp và dễ chịu. Đó có thể là một buổi sáng ngồi một mình, vừa nhấp cà phê vừa sắp xếp công việc. Cũng có thể là buổi gặp cuối ngày, khi câu chuyện với bạn bè kéo dài đến lúc ly đã vơi.",
      "Nếu chỉ có ít phút, một ly cà phê cũng đủ cho một khoảng nghỉ. Khi có thêm thời gian, cứ chọn một chỗ ngồi và thưởng thức ly cà phê theo nhịp của mình.",
    ],
  },
  {
    title: "Đội ngũ Barista",
    description: "Từ xay hạt đến rót sữa, mỗi bước ảnh hưởng thế nào đến vị cà phê?",
    image: "/images/story-barista.jpg",
    paragraphs: [
      "Trước khi một ly espresso đến tay bạn, người pha phải để ý đến độ mịn của bột và cách nước chảy qua cà phê. Nếu nước đi qua quá nhanh, ly có thể nhạt và chua gắt. Nếu chảy quá chậm, vị đắng có thể lấn át các hương vị khác.",
      "Với latte hay cappuccino, sữa cũng cần được đánh để có độ mịn phù hợp. Khi rót, người pha vừa kết hợp sữa với espresso, vừa giữ được lớp bọt trên mặt. Hình lá hay trái tim bạn nhìn thấy là phần cuối của quá trình đó.",
      "Nếu còn phân vân giữa các món, bạn có thể nói mình thích cà phê đậm hay nhẹ, nóng hay đá. Đó là những điều đơn giản để barista gợi ý một ly dễ hợp khẩu vị hơn.",
    ],
  },
];

function isBackdropEvent({ target, currentTarget, clientX, clientY }) {
  if (target !== currentTarget) return false;
  const { left, right, top, bottom } = currentTarget.getBoundingClientRect();
  return clientX < left || clientX >= right || clientY < top || clientY >= bottom;
}

function StoryCard({ story }) {
  const titleId = useId();
  const dialog = useRef(null);
  const trigger = useRef(null);
  const pressedBackdrop = useRef(false);

  return <article className="special">
    <img className="special-tile" src={story.image} alt="" width="116" height="117" loading="lazy" />
    <div className="special-copy">
      <h3>{story.title}</h3>
      <p>{story.description}</p>
      <button ref={trigger} className="special-read" type="button" aria-haspopup="dialog"
        aria-label={`Đọc câu chuyện ${story.title}`} onClick={() => dialog.current.showModal()}>
        Đọc câu chuyện <span aria-hidden="true">→</span>
      </button>
    </div>
    <dialog ref={dialog} className="special-dialog" aria-labelledby={titleId}
      onPointerDown={event => { pressedBackdrop.current = isBackdropEvent(event); }}
      onPointerCancel={() => { pressedBackdrop.current = false; }}
      onClick={event => {
        if (pressedBackdrop.current && isBackdropEvent(event)) event.currentTarget.close();
        pressedBackdrop.current = false;
      }}
      onClose={() => trigger.current.focus({ preventScroll: true })}>
      <header className="special-dialog-header">
        <h2 id={titleId}>{story.title}</h2>
        <form method="dialog">
          <button className="special-close" type="submit" aria-label="Đóng câu chuyện">×</button>
        </form>
      </header>
      <div className="special-dialog-body" tabIndex={0} role="region" aria-label="Nội dung câu chuyện">
        <img className="special-dialog-photo" src={story.image} alt="" loading="lazy" />
        {story.paragraphs.map(paragraph => <p key={paragraph}>{paragraph}</p>)}
      </div>
    </dialog>
  </article>;
}

export default function HomeStories() {
  return <section id="about" className="specials" aria-labelledby="specials-title">
    <h2 id="specials-title">Our Specials — Câu chuyện Lecker</h2>
    <div className="special-grid">
      {stories.map(story => <StoryCard key={story.title} story={story} />)}
    </div>
  </section>;
}
