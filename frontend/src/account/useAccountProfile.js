import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../auth/AuthContext.jsx";
import { accountError, changePassword, getProfile, patchProfile } from "./accountApi.js";
import { passwordErrors, profilePatch } from "./profileValidation.js";

const initial = { status: "idle", profile: null, snapshot: null, draft: {}, errors: {}, issue: null, saving: false };
const profileScopeKey = profile => profile ? `${profile.id}:${profile.role}` : null;

export default function useAccountProfile({ scopeKey, verified, getProfileScope, nextDisplaySequence, onProfileLoaded }) {
  const { token, sessionGeneration, captureSession, isCurrentSession, expireSession, replaceToken, updateProfile } = useAuth();
  const [state, setState] = useState(initial);
  const active = useRef(null);

  const ownsScope = useCallback(scope => Boolean(scope) && !scope.controller.signal.aborted
    && active.current === scope && isCurrentSession(scope.session), [isCurrentSession]);
  const isActive = useCallback(scope => ownsScope(scope) && profileScopeKey(getProfileScope()) === scope.key,
    [getProfileScope, ownsScope]);
  const publishProfile = useCallback((profile, session, sequence) => {
    if (onProfileLoaded(profile, sequence)) updateProfile(profile, session);
  }, [onProfileLoaded, updateProfile]);

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
    const sequence = nextDisplaySequence();
    setState({ ...initial, status: "loading", scope, issue });
    try {
      const profile = await getProfile(scope.session.token, AbortSignal.any([scope.controller.signal, read.signal]));
      if (!isActive(scope) || read.signal.aborted) return;
      if (profileScopeKey(profile) !== scope.key) throw accountError("PROFILE_SCOPE_MISMATCH");
      publishProfile(profile, scope.session, sequence);
      setState({ ...initial, status: "ready", scope, profile, issue });
    } catch (error) {
      if (!isActive(scope) || read.signal.aborted) return;
      setState({ ...initial, status: "error", scope, issue: error });
      handleError(error, scope);
    }
  }, [handleError, isActive, nextDisplaySequence, publishProfile]);

  useEffect(() => {
    if (!token || !scopeKey) {
      setState(initial);
      return;
    }
    const scope = { session: captureSession(), key: scopeKey, controller: new AbortController(), pending: false };
    active.current = scope;
    return () => {
      scope.controller.abort();
      scope.read?.abort();
      active.current = null;
    };
  }, [token, sessionGeneration, scopeKey, captureSession]);

  const finishSave = useCallback(async (scope, result) => {
    if (!ownsScope(scope)) return { ok: false };
    if (result.error?.status === 401) {
      expireSession(scope.session);
      return { ok: false };
    }
    if (!isActive(scope)) {
      scope.deferredSave = result;
      return { ok: false };
    }
    if (result.profile) {
      publishProfile(result.profile, scope.session, result.sequence);
      setState({ ...initial, status: "ready", scope, profile: result.profile });
      return { ok: true, profile: result.profile };
    }
    const { error } = result;
    if (error.code === "PROFILE_CONFLICT") await load(scope, error);
    else if (["PROFILE_OUTCOME_UNKNOWN", "NETWORK_ERROR"].includes(error.code)) {
      setState(previous => ({ ...previous, writeUncertain: true, issue: accountError("PROFILE_OUTCOME_UNKNOWN") }));
    } else handleError(error, scope);
    return { ok: false };
  }, [expireSession, handleError, isActive, load, ownsScope, publishProfile]);

  useEffect(() => {
    const scope = active.current;
    if (!verified || !isActive(scope)) return;
    if (scope.deferredSave) {
      const result = scope.deferredSave;
      scope.deferredSave = null;
      void finishSave(scope, result);
      setState(previous => ({ ...previous, saving: false }));
    } else if (!scope.pending) void load(scope);
    return () => scope.read?.abort();
  }, [verified, scopeKey, token, sessionGeneration, finishSave, isActive, load]);

  function startEdit() {
    const scope = active.current;
    if (!isActive(scope) || scope.pending || state.scope !== scope || !state.profile?.canUpdateProfile) return;
    setState(previous => ({ ...previous, snapshot: { ...previous.profile }, draft: {}, issue: null, errors: {} }));
  }
  function updateField(field, value) {
    if (!["name", "address", "phone"].includes(field) || !isActive(active.current) || active.current.pending || state.writeUncertain) return;
    setState(previous => previous.snapshot ? { ...previous, draft: { ...previous.draft, [field]: value }, errors: {} } : previous);
  }
  function cancelEdit() {
    if (active.current?.pending || state.writeUncertain) return;
    setState(previous => ({ ...previous, snapshot: null, draft: {}, issue: null, errors: {} }));
  }

  async function save() {
    const scope = active.current;
    if (!isActive(scope) || scope.pending || state.scope !== scope || !state.snapshot || state.writeUncertain) return { ok: false };
    const { patch, errors } = profilePatch(state.snapshot, state.draft);
    if (Object.keys(errors).length) { setState(previous => ({ ...previous, errors })); return { ok: false }; }
    if (!patch) { cancelEdit(); return { ok: true, unchanged: true }; }
    scope.pending = true;
    // A pre-mutation GET must not overwrite the committed response.
    scope.read?.abort();
    setState(previous => ({ ...previous, saving: true, issue: null, errors: {} }));
    try {
      const profile = await patchProfile(scope.session.token, patch, scope.controller.signal);
      if (profileScopeKey(profile) !== scope.key) throw accountError("PROFILE_OUTCOME_UNKNOWN");
      return await finishSave(scope, { profile, sequence: nextDisplaySequence() });
    } catch (error) {
      return await finishSave(scope, { error });
    } finally {
      scope.pending = false;
      if (isActive(scope)) setState(previous => ({ ...previous, saving: false }));
    }
  }

  async function changeOwnPassword(passwords) {
    const scope = active.current;
    if (!isActive(scope) || scope.pending || state.scope !== scope || !state.profile || state.reauthenticate) return { ok: false };
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

  const visible = verified && state.scope?.key === scopeKey && state.scope === active.current && isCurrentSession(state.scope.session)
    ? state : { ...initial, status: token ? "loading" : "anonymous" };
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
