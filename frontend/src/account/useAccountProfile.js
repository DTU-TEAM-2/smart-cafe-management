import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../auth/AuthContext.jsx";
import { changePassword, getProfile, patchProfile } from "./accountApi.js";
import { passwordErrors, profilePatch } from "./profileValidation.js";

const initial = { status: "idle", profile: null, snapshot: null, draft: {}, errors: {}, issue: null, saving: false };

export default function useAccountProfile() {
  const { token, user, sessionGeneration, captureSession, isCurrentSession, expireSession, replaceToken, updateProfile } = useAuth();
  const [state, setState] = useState(initial);
  const active = useRef(null);
  const eligible = !user?.role || ["ADMIN", "STAFF"].includes(user.role);

  const isActive = useCallback(scope => Boolean(scope) && !scope.controller.signal.aborted
    && active.current === scope && isCurrentSession(scope.session), [isCurrentSession]);

  const handleError = useCallback((error, scope) => {
    if (!isActive(scope)) return;
    if (error.status === 401) expireSession(scope.session);
    else setState(previous => ({ ...previous, issue: error,
      errors: error.field ? { [error.field]: error.message } : {},
      ...(error.status === 403 ? { status: "forbidden", profile: null, snapshot: null, draft: {} } : {}) }));
  }, [expireSession, isActive]);

  const load = useCallback(async (scope, issue = null) => {
    if (!isActive(scope)) return;
    scope.read?.abort();
    const read = new AbortController();
    scope.read = read;
    setState({ ...initial, status: "loading", session: scope.session, issue });
    try {
      const profile = await getProfile(scope.session.token, AbortSignal.any([scope.controller.signal, read.signal]));
      if (!isActive(scope) || read.signal.aborted) return;
      updateProfile(profile, scope.session);
      setState({ ...initial, status: "ready", session: scope.session, profile, issue });
    } catch (error) {
      if (!isActive(scope) || read.signal.aborted) return;
      setState({ ...initial, status: "error", session: scope.session, issue: error });
      handleError(error, scope);
    }
  }, [handleError, isActive, updateProfile]);

  useEffect(() => {
    const scope = { session: captureSession(), controller: new AbortController(), pending: false };
    active.current = scope;
    if (token && eligible) void load(scope);
    return () => { scope.controller.abort(); scope.read?.abort(); };
  }, [token, sessionGeneration, eligible, captureSession, load]);

  function startEdit() {
    const scope = active.current;
    if (!isActive(scope) || scope.pending || !state.profile?.canUpdateProfile) return;
    setState(previous => ({ ...previous, snapshot: { ...previous.profile }, draft: {}, issue: null, errors: {} }));
  }
  function updateField(field, value) {
    if (!["name", "address", "phone"].includes(field) || active.current?.pending) return;
    setState(previous => previous.snapshot ? { ...previous, draft: { ...previous.draft, [field]: value }, errors: {} } : previous);
  }
  function cancelEdit() {
    if (active.current?.pending) return;
    setState(previous => ({ ...previous, snapshot: null, draft: {}, issue: null, errors: {} }));
  }

  async function save() {
    const scope = active.current;
    if (!isActive(scope) || scope.pending || !state.snapshot) return { ok: false };
    const { patch, errors } = profilePatch(state.snapshot, state.draft);
    if (Object.keys(errors).length) { setState(previous => ({ ...previous, errors })); return { ok: false }; }
    if (!patch) { cancelEdit(); return { ok: true, unchanged: true }; }
    scope.pending = true;
    // A pre-mutation GET must not overwrite the committed response.
    scope.read?.abort();
    setState(previous => ({ ...previous, saving: true, issue: null, errors: {} }));
    try {
      const profile = await patchProfile(scope.session.token, patch, scope.controller.signal);
      if (!isActive(scope)) return { ok: false };
      updateProfile(profile, scope.session);
      setState({ ...initial, status: "ready", session: scope.session, profile });
      return { ok: true, profile };
    } catch (error) {
      if (!isActive(scope)) return { ok: false };
      if (error.code === "PROFILE_CONFLICT") await load(scope, error);
      else handleError(error, scope);
      return { ok: false };
    } finally {
      scope.pending = false;
      if (isActive(scope)) setState(previous => ({ ...previous, saving: false }));
    }
  }

  async function changeOwnPassword(passwords) {
    const scope = active.current;
    if (!isActive(scope) || scope.pending || !eligible || state.reauthenticate) return { ok: false };
    const errors = passwordErrors(passwords);
    if (Object.keys(errors).length) { setState(previous => ({ ...previous, errors })); return { ok: false }; }
    scope.pending = true;
    scope.read?.abort();
    setState(previous => ({ ...previous, saving: true, issue: null, errors: {} }));
    try {
      const response = await changePassword(scope.session.token, passwords, scope.controller.signal);
      if (!isActive(scope)) return { ok: false };
      // Synchronous replacement invalidates every old-session callback before any refresh.
      const replacement = replaceToken(response, scope.session);
      return { ok: Boolean(replacement) };
    } catch (error) {
      if (!isActive(scope)) return { ok: false };
      if (error.uncertain || error.code === "PASSWORD_CONFLICT") {
        // Keep the explanatory outcome on this form, but require explicit authentication next.
        setState(previous => ({ ...previous, issue: error, errors: {}, reauthenticate: true }));
      } else handleError(error, scope);
      return { ok: false, clearPasswords: Boolean(error.uncertain) };
    } finally {
      scope.pending = false;
      if (isActive(scope)) setState(previous => ({ ...previous, saving: false }));
    }
  }

  const visible = state.session && isCurrentSession(state.session) ? state
    : { ...initial, status: !token ? "anonymous" : !eligible ? "forbidden" : "loading" };
  return { ...visible, passwordStatus: visible.profile ? {
    mustChangePassword: visible.profile.mustChangePassword, passwordChangedAt: visible.profile.passwordChangedAt,
    passwordExpiresAt: visible.profile.passwordExpiresAt,
  } : null, startEdit, updateField, cancelEdit, save, changeOwnPassword,
  retry: () => { if (!active.current?.pending) void load(active.current); },
  requireAuthentication: () => expireSession(captureSession()),
  clearErrors: () => setState(previous => ({ ...previous, errors: {}, issue: null })),
  unsaved: Boolean(visible.snapshot && Object.entries(visible.draft).some(([field, value]) => value !== visible.snapshot[field])),
  };
}
