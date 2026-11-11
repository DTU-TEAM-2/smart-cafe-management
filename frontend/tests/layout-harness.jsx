import { useState } from "react";
import { createRoot } from "react-dom/client";
import SiteLayout from "../src/layout/SiteLayout.jsx";
import "../src/styles.css";

const event = { id: "1", message: "Bàn thử gọi phục vụ", read: false };
const notifications = { recipientId: "staff-a", items: [event, event] };
const staff = { id: "staff-a", name: "Nhân viên thử", role: "STAFF", profileHref: "/profile" };
const admin = { id: "admin-b", name: "Quản lý thử", role: "ADMIN" };
const customer = { id: "staff-a", name: "Khách thử", role: "CUSTOMER" };

function Harness() {
  const [account, setAccount] = useState(staff);
  return <SiteLayout account={account} notifications={notifications}>
    <h1>Trang tích hợp kiểm thử</h1>
    <button onClick={() => setAccount(admin)}>Đổi quản lý</button>
    <button onClick={() => setAccount(customer)}>Đổi khách</button>
  </SiteLayout>;
}

createRoot(document.getElementById("root")).render(<Harness />);
