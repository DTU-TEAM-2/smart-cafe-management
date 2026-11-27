import { useCallback, useRef } from "react";
import { useAuth } from "../auth/AuthContext.jsx";
import { getCurrentUserApi } from "../auth/authApi.js";

export default function useBillingAccess(authorization) {
  const { captureSession, isCurrentSession, expireSession } = useAuth();
  const verifying = useRef(null);
  return useCallback(() => {
    const snapshot = captureSession();
    if (!snapshot.token || authorization !== `Bearer ${snapshot.token}`) return;
    if (verifying.current?.generation === snapshot.generation) return;
    verifying.current = snapshot;
    void (async () => {
      try {
        await getCurrentUserApi(snapshot.token, AbortSignal.timeout(10000));
      } catch (error) {
        if ([401, 403].includes(error.status) && isCurrentSession(snapshot)) expireSession(snapshot);
      } finally {
        if (verifying.current === snapshot) verifying.current = null;
      }
    })();
  }, [authorization, captureSession, isCurrentSession, expireSession]);
}
