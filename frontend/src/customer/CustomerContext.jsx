import { createContext, useContext, useState, useEffect, useCallback, useMemo } from "react";
import {
  readCustomerSession,
  readSessionCart,
  readSessionOrders,
  writeSessionCart,
  writeSessionOrders,
} from "./customerSession.js";

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

  const value = useMemo(
    () => ({
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
      replacePlacedBatch,
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
      replacePlacedBatch,
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
