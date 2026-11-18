import { createContext, useContext, useState, useEffect, useCallback, useMemo } from "react";

const CustomerContext = createContext(null);

const CART_STORAGE_KEY = "lecker_customer_cart";
const SESSION_STORAGE_KEY = "lecker_customer_session";
const ORDERS_STORAGE_KEY = "lecker_customer_orders";

export function formatVnd(amount) {
  if (amount == null || amount === "") return "0 đ";
  const num = typeof amount === "number" ? amount : parseInt(String(amount).replace(/\D/g, ""), 10);
  if (isNaN(num)) return "0 đ";
  return new Intl.NumberFormat("vi-VN").format(num) + " đ";
}

export function CustomerProvider({ children }) {
  // Parse session from URL parameters (e.g., #customer?sessionId=1&token=abc&table=4)
  const [session, setSession] = useState(() => {
    try {
      const hash = window.location.hash;
      const qIndex = hash.indexOf("?");
      if (qIndex !== -1) {
        const params = new URLSearchParams(hash.slice(qIndex));
        const sessionId = params.get("sessionId");
        const token = params.get("token");
        const tableNumber = params.get("table") || params.get("tableNumber") || "01";
        if (sessionId && token) {
          const s = { sessionId, token, tableNumber };
          sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(s));
          return s;
        }
      }
      const saved = sessionStorage.getItem(SESSION_STORAGE_KEY);
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });

  // Cart: [{ item: { id, name, price, imageUrl, ... }, quantity: 1 }]
  const [cart, setCart] = useState(() => {
    try {
      const saved = sessionStorage.getItem(CART_STORAGE_KEY);
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  // Placed orders history: [{ batchId, sessionId, totalVnd, editableUntil, items: [...] }]
  const [orders, setOrders] = useState(() => {
    try {
      const saved = sessionStorage.getItem(ORDERS_STORAGE_KEY);
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  // Active view state
  const [currentTab, setCurrentTab] = useState("menu"); // 'menu' | 'cart' | 'orders' | 'service' | 'feedback'
  const [selectedItemForDetail, setSelectedItemForDetail] = useState(null);

  // Sync cart to sessionStorage
  useEffect(() => {
    try {
      sessionStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cart));
    } catch {
      // ignore
    }
  }, [cart]);

  // Sync orders to sessionStorage
  useEffect(() => {
    try {
      sessionStorage.setItem(ORDERS_STORAGE_KEY, JSON.stringify(orders));
    } catch {
      // ignore
    }
  }, [orders]);

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

  const updateBatchItem = useCallback((batchId, itemId, newQuantity, newVersion) => {
    setOrders((prev) =>
      prev.map((batch) => {
        if (batch.batchId !== batchId && batch.id !== batchId) return batch;
        return {
          ...batch,
          items: batch.items.map((it) => {
            if (it.id !== itemId && it.menuItemId !== itemId) return it;
            return {
              ...it,
              quantity: newQuantity,
              version: newVersion !== undefined ? newVersion : (it.version || 0) + 1,
            };
          }),
        };
      })
    );
  }, []);

  const markBatchItemCancelled = useCallback((batchId, itemId) => {
    setOrders((prev) =>
      prev.map((batch) => {
        if (batch.batchId !== batchId && batch.id !== batchId) return batch;
        return {
          ...batch,
          items: batch.items.map((it) => {
            if (it.id !== itemId && it.menuItemId !== itemId) return it;
            return {
              ...it,
              status: "CANCELLED",
            };
          }),
        };
      })
    );
  }, []);

  const value = useMemo(
    () => ({
      session,
      setSession,
      cart,
      addToCart,
      updateCartQuantity,
      removeFromCart,
      clearCart,
      cartTotal,
      cartCount,
      orders,
      addPlacedBatch,
      updateBatchItem,
      markBatchItemCancelled,
      currentTab,
      setCurrentTab,
      selectedItemForDetail,
      setSelectedItemForDetail,
    }),
    [
      session,
      cart,
      addToCart,
      updateCartQuantity,
      removeFromCart,
      clearCart,
      cartTotal,
      cartCount,
      orders,
      addPlacedBatch,
      updateBatchItem,
      markBatchItemCancelled,
      currentTab,
      selectedItemForDetail,
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
