import { createContext, useContext, useState, useEffect, useCallback, useMemo } from "react";
import {
  readCustomerSession,
  readSessionCart,
  readSessionOrders,
  writeSessionCart,
  writeSessionOrders,
  clearAttempt,
  getOrCreateOrderAttempt,
  readOrderAttempt,
} from "./customerSession.js";
import { createOrder, fetchCsrfToken } from "./customerApi.js";

const CustomerContext = createContext(null);

export function formatVnd(amount) {
  if (amount == null || amount === "") return "0 đ";
  const num = typeof amount === "number" ? amount : parseInt(String(amount).replace(/\D/g, ""), 10);
  if (isNaN(num)) return "0 đ";
  return new Intl.NumberFormat("vi-VN").format(num) + " đ";
}

export function CustomerProvider({ children }) {
  // The QR/session module must save the server-issued credential with
  // saveCustomerSession(). Credentials are deliberately never read from URLs.
  const [session] = useState(readCustomerSession);

  // Cart: [{ item: { id, name, price, imageUrl, ... }, quantity: 1 }]
  const [cart, setCart] = useState(() => {
    try {
      return readSessionCart(session);
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
  const [pendingOrderSnapshot, setPendingOrderSnapshot] = useState(null);
  const [orderModalResult, setOrderModalResult] = useState(null);

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
    if (!item || quantity <= 0) return;
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
      return [...prev, { item, quantity }];
    });
  }, []);

  const updateCartQuantity = useCallback((itemId, newQuantity) => {
    setCart((prev) => {
      if (newQuantity <= 0) {
        return prev.filter((entry) => entry.item.id !== itemId);
      }
      return prev.map((entry) =>
        entry.item.id === itemId ? { ...entry, quantity: newQuantity } : entry
      );
    });
  }, []);

  const removeFromCart = useCallback((itemId) => {
    setCart((prev) => prev.filter((entry) => entry.item.id !== itemId));
  }, []);

  const clearCart = useCallback(() => {
    setCart([]);
  }, []);

  const subtractFromCart = useCallback((submittedItems) => {
    if (!Array.isArray(submittedItems) || submittedItems.length === 0) return;
    setCart((prev) => {
      const subMap = new Map();
      for (const it of submittedItems) {
        const id = String(it.menuItemId ?? it.id);
        const qty = Number(it.quantity) || 0;
        subMap.set(id, (subMap.get(id) || 0) + qty);
      }

      const next = [];
      for (const entry of prev) {
        const id = String(entry.item.id);
        const subQty = subMap.get(id) || 0;
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
      const price = parseInt(String(entry.item.price).replace(/\D/g, ""), 10) || 0;
      return sum + price * entry.quantity;
    }, 0);
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
    if (isSubmittingOrder) return;

    if (!session || !session.sessionId || !session.token) {
      setOrderModalResult({
        status: "failure",
        title: "Chưa xác thực bàn",
        detail: "Vui lòng quét mã QR tại bàn để xác nhận phiên đặt món trước khi gửi đơn.",
      });
      return;
    }

    // Reuse existing unconfirmed attempt (payload + idempotencyKey) if prior request failed/dropped
    const existingAttempt = readOrderAttempt(session);
    let orderPayload;
    let idempotencyKey;

    if (existingAttempt?.idempotencyKey && Array.isArray(existingAttempt.payload) && existingAttempt.payload.length > 0) {
      orderPayload = existingAttempt.payload;
      idempotencyKey = existingAttempt.idempotencyKey;
    } else {
      if (cart.length === 0) return;
      const initialPayload = cart
        .map((entry) => ({
          menuItemId: Number(entry.item.id),
          quantity: Number(entry.quantity),
        }))
        .sort((a, b) => a.menuItemId - b.menuItemId);
      const attempt = getOrCreateOrderAttempt(session, initialPayload);
      orderPayload = attempt.payload;
      idempotencyKey = attempt.idempotencyKey;
    }

    setIsSubmittingOrder(true);
    setPendingOrderSnapshot(orderPayload);

    try {
      const csrf = await fetchCsrfToken();

      const batchResult = await createOrder({
        sessionId: session.sessionId,
        token: session.token,
        items: orderPayload,
        idempotencyKey,
        csrf,
      });

      addPlacedBatch({
        ...batchResult,
        createdAt: new Date().toISOString(),
      });
      subtractFromCart(orderPayload);
      clearAttempt(session, "order");

      setOrderModalResult({
        status: "success",
        title: "Đặt món thành công!",
        detail: `Đơn #${batchResult.id} đã được gửi tới quầy pha chế. Bạn có thể chỉnh sửa hoặc hủy món trong vòng 3 phút tại mục Đơn món.`,
      });
    } catch (err) {
      setOrderModalResult({
        status: "failure",
        title: "Đặt món thất bại",
        detail: err.message || "Không thể gửi đơn đặt món. Vui lòng kiểm tra lại kết nối.",
      });
    } finally {
      setIsSubmittingOrder(false);
      setPendingOrderSnapshot(null);
    }
  }, [cart, isSubmittingOrder, session, addPlacedBatch, subtractFromCart]);

  const value = useMemo(
    () => ({
      session,
      cart,
      addToCart,
      updateCartQuantity,
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
      pendingOrderSnapshot,
      orderModalResult,
      setOrderModalResult,
      submitOrder,
    }),
    [
      session,
      cart,
      addToCart,
      updateCartQuantity,
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
      pendingOrderSnapshot,
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
