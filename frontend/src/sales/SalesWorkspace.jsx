import { useEffect, useRef, useState } from "react";
import IncomePanel, { LoadState } from "./IncomePanel.jsx";
import { postSales, readApi, useSalesResource, vnd } from "./salesApi.js";
import "./sales.css";

export default function SalesWorkspace({ account, identityStatus, authorization, credentials, income, retryIdentity }) {
  const isAdmin = account?.role === "ADMIN";
  const allowed = isAdmin || account?.role === "STAFF";
  const [selected, setSelected] = useState(null);
  const keys = useRef(new Map());
  const tables = useSalesResource(allowed && !income ? "/api/v1/sales/tables" : null, authorization, credentials, 8000);
  const table = tables.data?.find(item => item.id === selected);
  const showOrders = !income && selected !== null;
  return <div className="sale-workspace">
    <a className="skip-link" href="#sale-main" onClick={event => { event.preventDefault(); document.getElementById("sale-main")?.focus(); }}>Bỏ qua điều hướng</a>
    <aside className="sale-sidebar">
      <a className="brand sale-brand" href="#top"><strong>LECKER</strong><span>COFFEE CAFE</span></a>
      {allowed && <nav aria-label="Nghiệp vụ bán hàng">
        <a href="#sale" aria-current={!income && !showOrders ? "page" : undefined} onClick={() => setSelected(null)}>Trạng thái bàn</a>
        {showOrders && <a href="#sale" aria-current="page">Đơn hàng</a>}
        {isAdmin && <a href="#sale-income" aria-current={income ? "page" : undefined}>Thu nhập</a>}
      </nav>}
    </aside>
    <div className="sale-main-shell">
      <header className="sale-header"><strong>{isAdmin ? "KHU VỰC QUẢN TRỊ" : "KHÔNG GIAN NHÂN VIÊN"}</strong>
        <a href={account ? "#account" : "#login"}>{account ? "Tài khoản" : "Đăng nhập"}</a></header>
      <main className="sale-content" id="sale-main" tabIndex={-1}>
        {!allowed ? <section className="sale-panel"><h1>Bán hàng theo bàn</h1>
          {identityStatus === "loading" ? <p role="status">Đang xác minh tài khoản…</p>
            : identityStatus === "error" ? <div role="alert"><p>Chưa tải được tài khoản. Kiểm tra kết nối rồi thử lại.</p><button onClick={retryIdentity}>Thử lại</button></div>
              : <p role="alert">Bạn cần đăng nhập bằng tài khoản nhân viên hoặc quản lý.</p>}
          <a href="#login">Đến trang đăng nhập</a></section>
          : income ? isAdmin ? <IncomePanel authorization={authorization} credentials={credentials} />
            : <section className="sale-panel"><h1>Thu nhập</h1><p role="alert">Chỉ quản lý được xem doanh thu.</p></section>
          : <>
            <h1>{showOrders ? "Đơn hàng" : "Trạng thái bàn"}</h1>
            <p className="sale-muted">LECKER Coffee Cafe · Smart Café Management System</p>
            <LoadState resource={tables} />
            {!showOrders && tables.data && <section className="sale-panel" aria-labelledby="table-map-title">
              <h2 id="table-map-title">Sơ đồ bàn</h2><p className="sale-muted">Trạng thái bàn phục vụ, trống và bảo trì</p>
              {tables.data.length === 0 ? <p>Chưa có bàn.</p> : <div className="sale-grid">{tables.data.map(item => {
                const state = item.operationalStatus === "OUT_OF_SERVICE" ? "maintenance" : item.occupancy === "OCCUPIED" ? "occupied" : "empty";
                return <button type="button" className={`sale-table-card ${state}`} key={item.id} onClick={() => setSelected(item.id)}>
                  <strong>{item.name}</strong><span>{{ maintenance: "Bảo trì", occupied: "Đang phục vụ", empty: "Trống" }[state]}</span>
                  {item.pendingWaiterRequests > 0 && <small className="sale-badge alert">Gọi phục vụ</small>}
                  {item.submittedOrderItems > 0 && <small className="sale-badge alert">Có món mới</small>}
                  {item.pendingBillRequests > 0 && <small className="sale-badge bill">Yêu cầu thanh toán</small>}
                </button>;
              })}</div>}
            </section>}
            {showOrders && <>
              <button type="button" className="sale-back" onClick={() => setSelected(null)}>← Về sơ đồ bàn</button>
              {table && <TableOrders key={table.id} table={table} isAdmin={isAdmin} authorization={authorization} credentials={credentials}
                keys={keys} refreshTables={tables.reload} />}
              {tables.data && !table && <p role="alert">Bàn này không còn trong danh sách.</p>}
            </>}
          </>}
      </main>
    </div>
  </div>;
}

function TableOrders({ table, isAdmin, authorization, credentials, keys, refreshTables }) {
  const path = `/api/v1/sales/tables/${encodeURIComponent(table.id)}`;
  const resource = useSalesResource(`${path}/services`, authorization, credentials, 8000);
  const data = resource.data;
  return <section className="sale-panel" aria-labelledby="table-orders-title">
    <h2 id="table-orders-title">Chi tiết đơn hàng {table.name}</h2>
    <p className="sale-muted">Các món trong đơn hiện tại</p>
    <LoadState resource={resource} />
    {data && <>
      {data.items.length ? <ItemTable items={data.items} /> : <p>Chưa có món để tính tiền.</p>}
      <p className="sale-total">Tổng tiền <strong>{vnd(data.totalAmount)}</strong></p>
      {data.sessionCode && <OrderActions key={data.sessionCode} path={path} data={data} table={table}
        isAdmin={isAdmin} authorization={authorization} credentials={credentials} keys={keys}
        refresh={() => { resource.reload(); refreshTables(); }} />}
    </>}
  </section>;
}

function ItemTable({ items }) {
  return <div className="sale-table-wrap"><table>
    <thead><tr><th scope="col">Tên món</th><th scope="col">Số lượng</th><th scope="col">Đơn giá</th><th scope="col">Thành tiền</th></tr></thead>
    <tbody>{items.map(item => <tr key={item.id ?? item.orderItemId}><th scope="row">{item.itemName}</th>
      <td>{item.quantity}</td><td>{vnd(item.unitPrice)}</td><td>{vnd(item.lineTotal)}</td></tr>)}</tbody>
  </table></div>;
}

function OrderActions({ path, data, table, isAdmin, authorization, credentials, keys, refresh }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [snapshot, setSnapshot] = useState(null);
  const [dialog, setDialog] = useState(null);
  const [method, setMethod] = useState("CASH");
  const [reference, setReference] = useState("");
  const active = useRef(null);
  useEffect(() => () => active.current?.abort(), []);
  const bill = data.currentBill;
  const currentSnapshot = snapshot?.id === bill?.id && snapshot?.sessionCode === data.sessionCode ? snapshot : null;
  function keyFor(name) {
    if (!keys.current.has(name)) keys.current.set(name, crypto.randomUUID());
    return keys.current.get(name);
  }
  async function execute(action, success) {
    if (active.current) return;
    const controller = new AbortController();
    active.current = controller; setBusy(true); setError(""); setNotice("");
    const options = { authorization, credentials, signal: controller.signal };
    try {
      const result = await action(options);
      if (!controller.signal.aborted) success(result);
    } catch (failure) {
      if (!controller.signal.aborted) { setError(failure.message); setDialog(null); if (failure.status === 409) refresh(); }
    } finally {
      active.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  const session = { expectedSessionCode: data.sessionCode };
  const paid = bill?.status === "PAID";
  const pending = data.activeRequests.filter(request => request.status === "PENDING");
  return <>
    {pending.length > 0 && <ul className="sale-request-list">{pending.map(request => <li key={request.id}>
      <span>{request.type === "BILL" ? "Yêu cầu thanh toán" : "Gọi phục vụ"}</span>
      <button type="button" disabled={busy} onClick={() => void execute(options =>
        postSales(`${path}/requests/${encodeURIComponent(request.id)}/acknowledge`, session, options), () => { setNotice("Đã tiếp nhận yêu cầu."); refresh(); })}>Tiếp nhận {request.type === "BILL" ? "yêu cầu thanh toán" : "gọi phục vụ"}</button>
    </li>)}</ul>}
    <div className="sale-actions">
      <button className="sale-primary" type="button" disabled={busy || data.items.length === 0 || Boolean(bill)}
        onClick={() => void execute(options => postSales(`${path}/bill`, { ...session, idempotencyKey: keyFor(`bill:${data.sessionCode}`) }, options), result => {
          setSnapshot(result); setNotice("Đã tạo hóa đơn chờ xác nhận thanh toán."); refresh();
        })}>Tạo hóa đơn</button>
      {isAdmin && bill && <button type="button" disabled={busy} onClick={() => void execute(options => readApi(`/api/v1/bills/${encodeURIComponent(bill.id)}`, options), setSnapshot)}>Xem hóa đơn hiện tại</button>}
      {isAdmin && paid && <button type="button" disabled={busy} onClick={() => setDialog({ type: "reset", code: data.sessionCode })}>Đặt lại bàn</button>}
    </div>
    {bill && <p className="sale-muted">{paid ? "Hóa đơn đã thanh toán." : "Phiên đã có hóa đơn chờ xử lý."}</p>}
    {busy && <p role="status">Đang xử lý…</p>}
    {error && <p role="alert" className="sale-error">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {isAdmin && currentSnapshot && <section className={`sale-invoice${currentSnapshot.status === "PAID" ? " sale-receipt" : ""}`} aria-label="Nội dung hóa đơn">
      <h3>Hóa đơn {currentSnapshot.billCode}</h3>
      <p>{currentSnapshot.tableName} · {currentSnapshot.creatorName}</p>
      <ItemTable items={currentSnapshot.items} />
      <p className="sale-total">Tổng hóa đơn <strong>{vnd(currentSnapshot.totalAmount)}</strong></p>
      {currentSnapshot.status === "PAID" ? <button className="sale-print-button" type="button" onClick={() => window.print()}>In hóa đơn</button>
        : !paid && <button className="sale-primary" type="button" disabled={busy} onClick={() => setDialog({ type: "pay", code: data.sessionCode, billId: currentSnapshot.id })}>Xác nhận thu tiền</button>}
    </section>}
    {dialog && <ConfirmDialog title={dialog.type === "reset" ? "Đặt lại bàn" : "Xác nhận thu tiền"} busy={busy} onDismiss={() => setDialog(null)}
      onConfirm={() => {
        if (dialog.code !== data.sessionCode || (dialog.type === "pay" && dialog.billId !== bill?.id)) { setDialog(null); setError("Phiên bàn hoặc hóa đơn đã thay đổi."); return; }
        if (dialog.type === "reset") void execute(options => postSales(`${path}/reset`, { expectedSessionCode: dialog.code }, options), () => { setDialog(null); setNotice("Đã đặt lại bàn."); refresh(); });
        else void execute(options => postSales(`/api/v1/bills/${encodeURIComponent(dialog.billId)}/payments`, {
          method, referenceCode: reference.trim() || null,
          idempotencyKey: keyFor(`pay:${dialog.billId}:${method}:${reference.trim()}`),
        }, options), result => { setSnapshot(result.bill); setDialog(null); setNotice("Đã xác nhận thu tiền. Có thể in hóa đơn."); refresh(); });
      }}>
      {dialog.type === "reset" ? <p>Kết thúc phiên đã thanh toán của {table.name}?</p> : <>
        <p>Chỉ xác nhận khi đã nhận đủ {vnd(currentSnapshot?.totalAmount)}.</p>
        <label>Phương thức<select value={method} onChange={event => setMethod(event.target.value)}><option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản</option></select></label>
        {method === "BANK_TRANSFER" && <label>Mã tham chiếu (nếu có)<input value={reference} maxLength={128} onChange={event => setReference(event.target.value)} /></label>}
      </>}
    </ConfirmDialog>}
  </>;
}

function ConfirmDialog({ title, busy, onDismiss, onConfirm, children }) {
  const dialogRef = useRef(null);
  useEffect(() => {
    const element = dialogRef.current;
    const trigger = document.activeElement;
    element.showModal();
    return () => { element.close(); if (trigger?.isConnected) trigger.focus(); };
  }, []);
  return <dialog className="sale-dialog" ref={dialogRef} aria-labelledby="sale-confirm-title"
    onCancel={event => { event.preventDefault(); if (!busy) onDismiss(); }}>
    <h2 id="sale-confirm-title">{title}</h2>
    <form onSubmit={event => { event.preventDefault(); if (!busy) onConfirm(); }}>
      {children}
      <div className="sale-actions"><button type="button" disabled={busy} onClick={onDismiss}>Hủy</button>
        <button type="submit" className="sale-primary" disabled={busy}>{busy ? "Đang xử lý…" : "Xác nhận"}</button></div>
    </form>
  </dialog>;
}
