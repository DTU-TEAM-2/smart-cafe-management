import { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from "react";
import {
  createStorageId,
  readCustomerSession,
  readSessionCart,
  readSessionOrders,
  writeSessionCart,
  writeSessionOrders,
  clearAttempt,
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
  const [hasPendingOrder, setHasPendingOrder] = useState(() => Boolean(readOrderAttempt(session)));
  const submittingOrderRef = useRef(false);
  const orderLifecycle = useRef(null);
  const [orderModalResult, setOrderModalResult] = useState(null);

  useEffect(() => {
    const lifecycle = new AbortController();
    orderLifecycle.current = lifecycle;
    return () => lifecycle.abort();
  }, []);

  // Sync cart to sessionStorage
  useEffect(() => {
    try {
      writeSessionCart(session, cart);
    } catch {
      // ignore
    }
  }, [cart, session]);

  // Sync orders to sessionStorage
  useEffect(() => {
    try {
      writeSessionOrders(session, orders);
    } catch {
      // ignore
    }
  }, [orders, session]);

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

  const subtractFromCart = useCallback((submittedItems) => {
    if (!Array.isArray(submittedItems) || submittedItems.length === 0) return;
    setCart((prev) => {
      const subMap = new Map();
      for (const it of submittedItems) {
        const id = it.cartLineId ? `line:${it.cartLineId}` : `menu:${it.menuItemId ?? it.id}`;
        const qty = Number(it.quantity) || 0;
        subMap.set(id, (subMap.get(id) || 0) + qty);
      }

      const next = [];
      for (const entry of prev) {
        const subQty = subMap.get(`line:${entry.lineId}`) ?? subMap.get(`menu:${entry.item.id}`) ?? 0;
        if (subQty > 0) {
          const remaining = entry.quantity - subQty;
          if (remaining > 0) {
            next.push({ ...entry, quantity: remaining });
          }
        } else {
          next.push(entry);
        }
      }
      return next;
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

  const addPlacedBatch = useCallback((batch) => {
    if (!batch) return;
    setOrders((prev) => [batch, ...prev]);
  }, []);

  const replacePlacedBatch = useCallback((updatedBatch) => {
    if (!updatedBatch?.id) return;
    setOrders((prev) =>
      prev.map((batch) => (String(batch.id) === String(updatedBatch.id) ? updatedBatch : batch))
    );
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
    let attempt = readOrderAttempt(session);
    let replay = Boolean(attempt);
    try {
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
        attempt = getOrCreateOrderAttempt(session, orderPayload);
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

      addPlacedBatch({
        ...batchResult,
        createdAt: new Date().toISOString(),
      });
      subtractFromCart(attempt.payload);
      clearAttempt(session, "order");
      setHasPendingOrder(false);

      setOrderModalResult({
        status: "success",
        title: "Đặt món thành công!",
        detail: `Đơn #${batchResult.id} đã được gửi tới quầy pha chế. Bạn có thể chỉnh sửa hoặc hủy món trong vòng 3 phút tại mục Đơn món.`,
      });
    } catch (err) {
      if (signal.aborted) return;
      if (attempt && !replay && err.requestRejected) {
        clearAttempt(session, "order");
        setHasPendingOrder(false);
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
  }, [cart, session, addPlacedBatch, subtractFromCart, updateCartItemPrice]);

  const value = useMemo(
    () => ({
      session,
      cart,
      addToCart,
      updateCartQuantity,
      updateCartItemPrice,
      removeFromCart,
      clearCart,
      subtractFromCart,
      cartTotal,
      cartCount,
      orders,
      addPlacedBatch,
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
      subtractFromCart,
      cartTotal,
      cartCount,
      orders,
      addPlacedBatch,
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
