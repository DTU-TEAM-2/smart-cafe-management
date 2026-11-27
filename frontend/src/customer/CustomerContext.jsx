import { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from "react";
import {
  createStorageId,
  readCustomerSession,
  readSessionCart,
  readSessionOrders,
  saveCheckoutDraft,
  finishOrderAttempt,
  getOrCreateOrderAttempt,
  readOrderAttempt,
} from "./customerSession.js";
import { createOrder, fetchCsrfToken, fetchMenuItem } from "./customerApi.js";

const CustomerContext = createContext(null);

export function formatVnd(amount) {
  if (amount == null || amount === "") return "0 đ";
  const normalized = typeof amount === "bigint" ? amount : String(amount).trim();
  if (typeof normalized === "string" && !/^\d+$/.test(normalized)) return "0 đ";
  try {
    return new Intl.NumberFormat("vi-VN").format(BigInt(normalized)) + " đ";
  } catch {
    return "0 đ";
  }
}

export function CustomerProvider({ children }) {
  // The QR/session module must save the server-issued credential with
  // saveCustomerSession(). Credentials are deliberately never read from URLs.
  const [session] = useState(readCustomerSession);

  // Cart lines keep their identity across reload and while an order is pending.
  const [cart, setCart] = useState(() => {
    try {
      return readSessionCart(session).map(entry => ({ ...entry, lineId: entry.lineId ?? createStorageId() }));
    } catch {
      return [];
    }
  });

  // Placed orders use the exact CustomerOrderService.Order response contract.
  const [orders, setOrders] = useState(() => {
    try {
      return readSessionOrders(session);
    } catch {
      return [];
    }
  });

  // Active view state
  const [currentTab, setCurrentTab] = useState("menu"); // 'menu' | 'cart' | 'orders' | 'service' | 'feedback'
  const [selectedItemForDetail, setSelectedItemForDetail] = useState(null);

  // In-flight order submission state (preserves state across tab switches)
  const [isSubmittingOrder, setIsSubmittingOrder] = useState(false);
  const [hasPendingOrder, setHasPendingOrder] = useState(() => {
    try { return Boolean(readOrderAttempt(session)); } catch { return true; }
  });
  const submittingOrderRef = useRef(false);
  const orderLifecycle = useRef(null);
  const [orderModalResult, setOrderModalResult] = useState(null);

  useEffect(() => {
    const lifecycle = new AbortController();
    orderLifecycle.current = lifecycle;
    return () => lifecycle.abort();
  }, []);

  // Draft updates may stay in memory during a storage outage. Submit still
  // requires an atomic durable write before dispatch and after confirmation.
  useEffect(() => {
    try {
      saveCheckoutDraft(session, cart, orders);
    } catch {
      // Keep the in-memory draft available. Submission will report storage errors.
    }
  }, [cart, orders, session]);
  const latestOrders = useRef(orders);

  const addToCart = useCallback((item, quantity = 1) => {
    if (submittingOrderRef.current) return;
    if (!item || item.available === false || quantity <= 0) return;
    const lineId = createStorageId();
    setCart((prev) => {
      const existingIndex = prev.findIndex((entry) => entry.item.id === item.id);
      if (existingIndex > -1) {
        const next = [...prev];
        next[existingIndex] = {
          ...next[existingIndex],
          quantity: next[existingIndex].quantity + quantity,
        };
        return next;
      }
      return [...prev, { item, quantity, lineId }];
    });
  }, []);

  const updateCartQuantity = useCallback((itemId, newQuantity) => {
    if (submittingOrderRef.current) return;
    setCart((prev) => {
      if (newQuantity <= 0) {
        return prev.filter((entry) => entry.item.id !== itemId);
      }
      return prev.map((entry) =>
        entry.item.id === itemId ? { ...entry, quantity: newQuantity } : entry
      );
    });
  }, []);

  const updateCartItemPrice = useCallback((itemId, newPrice) => {
    setCart((prev) =>
      prev.map((entry) =>
        String(entry.item.id) === String(itemId)
          ? { ...entry, item: { ...entry.item, price: String(newPrice) } }
          : entry
      )
    );
  }, []);

  const removeFromCart = useCallback((itemId) => {
    if (submittingOrderRef.current) return;
    setCart((prev) => prev.filter((entry) => entry.item.id !== itemId));
  }, []);

  const clearCart = useCallback(() => {
    if (submittingOrderRef.current) return;
    setCart([]);
  }, []);

  const subtractFromCart = useCallback((currentCart, submittedItems) => {
    const subMap = new Map();
    for (const item of submittedItems) {
      const id = item.cartLineId ? `line:${item.cartLineId}` : `menu:${item.menuItemId ?? item.id}`;
      subMap.set(id, (subMap.get(id) || 0) + Number(item.quantity));
    }

    return currentCart.flatMap(entry => {
      const quantity = subMap.get(`line:${entry.lineId}`) ?? subMap.get(`menu:${entry.item.id}`) ?? 0;
      if (quantity === 0) return [entry];
      return entry.quantity > quantity ? [{ ...entry, quantity: entry.quantity - quantity }] : [];
    });
  }, []);

  const cartTotal = useMemo(() => {
    return cart.reduce((sum, entry) => {
      const rawPrice = String(entry.item.price ?? "").trim();
      const price = /^\d+$/.test(rawPrice) ? BigInt(rawPrice) : 0n;
      return sum + price * BigInt(entry.quantity);
    }, 0n);
  }, [cart]);

  const cartCount = useMemo(() => {
    return cart.reduce((count, entry) => count + entry.quantity, 0);
  }, [cart]);

  const replacePlacedBatch = useCallback((updatedBatch) => {
    if (!updatedBatch?.id) return;
    latestOrders.current = latestOrders.current.map((batch) =>
      String(batch.id) === String(updatedBatch.id) ? updatedBatch : batch);
    setOrders(latestOrders.current);
  }, []);

  const submitOrder = useCallback(async () => {
    const signal = orderLifecycle.current?.signal;
    if (!signal || signal.aborted || submittingOrderRef.current) return;

    if (!session || !session.sessionId || !session.token) {
      setOrderModalResult({
        status: "failure",
        title: "Chưa xác thực bàn",
        detail: "Vui lòng quét mã QR tại bàn để xác nhận phiên đặt món trước khi gửi đơn.",
      });
      return;
    }

    submittingOrderRef.current = true;
    setIsSubmittingOrder(true);
    let attempt;
    let replay = false;
    try {
      attempt = readOrderAttempt(session);
      replay = Boolean(attempt);
      let orderPayload = attempt?.payload;
      if (!attempt) {
        if (cart.length === 0) return;
        try {
          const freshItems = await Promise.all(cart.map((entry) => fetchMenuItem(entry.item.id)));
          if (signal.aborted) return;
          const changes = freshItems.filter((item, index) => String(item.price) !== String(cart[index].item.price));
          freshItems.forEach((item) => updateCartItemPrice(item.id, item.price));
          if (changes.length) {
            setOrderModalResult({ status: "failure", action: "reviewPrices", title: "Giá món đã thay đổi",
              detail: "Đã cập nhật giá thực đơn. Vui lòng kiểm tra tổng tiền rồi bấm gửi lại để xác nhận." });
            return;
          }
        } catch {
          if (signal.aborted) return;
          setOrderModalResult({ status: "failure", title: "Chưa xác minh được giá",
            detail: "Không thể tải giá thực đơn hiện tại. Vui lòng kiểm tra kết nối và thử lại." });
          return;
        }
        orderPayload = cart
          .map((entry) => ({
            menuItemId: String(entry.item.id),
            quantity: Number(entry.quantity),
            expectedUnitPrice: String(entry.item.price),
            cartLineId: entry.lineId,
          }))
          .sort((a, b) => BigInt(a.menuItemId) < BigInt(b.menuItemId) ? -1 : BigInt(a.menuItemId) > BigInt(b.menuItemId) ? 1 : 0);
      }

      const csrf = await fetchCsrfToken();
      if (signal.aborted) return;
      if (!csrf?.headerName || !csrf?.token) {
        throw new Error("Chưa lấy được mã xác thực yêu cầu. Vui lòng thử lại.");
      }

      if (!attempt) {
        // Persist only once the request is ready to dispatch. A saved attempt
        // may already have committed even if a later retry is rejected.
        attempt = getOrCreateOrderAttempt(session, orderPayload, cart, latestOrders.current);
        replay = attempt.isRetry;
        setHasPendingOrder(true);
      }

      const batchResult = await createOrder({
        sessionId: session.sessionId,
        token: session.token,
        items: attempt.payload,
        idempotencyKey: attempt.idempotencyKey,
        csrf,
      });
      // A new provider must reconcile the persisted attempt if this view has left.
      if (signal.aborted) return;

      const alreadyPlaced = latestOrders.current.some(batch => String(batch.id) === batchResult.id);
      const nextCart = alreadyPlaced ? cart : subtractFromCart(cart, attempt.payload);
      const nextOrders = alreadyPlaced ? latestOrders.current : [
        { ...batchResult, createdAt: new Date().toISOString() }, ...latestOrders.current,
      ];
      try {
        finishOrderAttempt(session, attempt, nextCart, nextOrders);
      } catch {
        setOrderModalResult({ status: "failure", title: "Chưa lưu được đơn trên thiết bị",
          detail: "Đơn đã được ghi nhận nhưng chưa lưu được giỏ hàng và lịch sử. Thử lại để đối soát bằng cùng mã đơn." });
        return;
      }
      latestOrders.current = nextOrders;
      setOrders(nextOrders);
      setCart(nextCart);
      setHasPendingOrder(false);

      setOrderModalResult({
        status: "success",
        title: "Đặt món thành công!",
        detail: `Đơn #${batchResult.id} đã được gửi tới quầy pha chế. Bạn có thể chỉnh sửa hoặc hủy món trong vòng 3 phút tại mục Đơn món.`,
      });
    } catch (err) {
      if (signal.aborted) return;
      if (attempt && !replay && err.requestRejected) {
        try {
          finishOrderAttempt(session, attempt, cart, latestOrders.current);
          setHasPendingOrder(false);
        } catch {
          setOrderModalResult({ status: "failure", title: "Chưa lưu được trạng thái đơn",
            detail: "Không thể cập nhật mã gửi đơn trên thiết bị. Vui lòng thử lại sau khi khôi phục bộ nhớ phiên." });
          return;
        }
      }
      setOrderModalResult({
        status: "failure",
        title: "Đặt món thất bại",
        detail: replay
          ? `${err.message} Đơn trước có thể đã được ghi nhận. Gửi lại sẽ dùng cùng mã đơn.`
          : err.message || "Không thể gửi đơn đặt món. Vui lòng kiểm tra lại kết nối.",
      });
    } finally {
      submittingOrderRef.current = false;
      if (!signal.aborted) setIsSubmittingOrder(false);
    }
  }, [cart, session, subtractFromCart, updateCartItemPrice]);

  const value = useMemo(
    () => ({
      session,
      cart,
      addToCart,
      updateCartQuantity,
      updateCartItemPrice,
      removeFromCart,
      clearCart,
      cartTotal,
      cartCount,
      orders,
      replacePlacedBatch,
      currentTab,
      setCurrentTab,
      selectedItemForDetail,
      setSelectedItemForDetail,
      isSubmittingOrder,
      hasPendingOrder,
      orderModalResult,
      setOrderModalResult,
      submitOrder,
    }),
    [
      session,
      cart,
      addToCart,
      updateCartQuantity,
      updateCartItemPrice,
      removeFromCart,
      clearCart,
      cartTotal,
      cartCount,
      orders,
      replacePlacedBatch,
      currentTab,
      selectedItemForDetail,
      isSubmittingOrder,
      hasPendingOrder,
      orderModalResult,
      submitOrder,
    ]
  );

  return <CustomerContext.Provider value={value}>{children}</CustomerContext.Provider>;
}

export function useCustomer() {
  const ctx = useContext(CustomerContext);
  if (!ctx) {
    throw new Error("useCustomer must be used within CustomerProvider");
  }
  return ctx;
}
