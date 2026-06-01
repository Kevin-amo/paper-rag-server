import { computed } from 'vue';
import {
  changeDisplayName as changeDisplayNameRequest,
  changeEmail as changeEmailRequest,
  changePassword as changePasswordRequest,
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  requestChangeEmailCode as requestChangeEmailCodeRequest,
  requestRegisterEmailCode as requestRegisterEmailCodeRequest,
  uploadAvatar as uploadAvatarRequest,
} from '../api/auth';
import type { UserRole } from '../types';
import { authState, clearAuthSession, getAccessToken, setAuthSession, updateCurrentUser } from './authState';

export function useAuth() {
  const isAuthenticated = computed(() => Boolean(authState.accessToken));
  const isAdmin = computed(() => hasRole('ADMIN'));

  async function login(username: string, password: string) {
    const result = await loginRequest({ username, password });
    setAuthSession(result.accessToken, result.user);
    return result.user;
  }

  async function requestRegisterEmailCode(email: string) {
    await requestRegisterEmailCodeRequest({ email });
  }

  async function register(username: string, password: string, email: string, emailCode: string) {
    const result = await registerRequest({ username, password, email, emailCode });
    setAuthSession(result.accessToken, result.user);
    return result.user;
  }

  async function hydrateCurrentUser() {
    if (!getAccessToken()) {
      return null;
    }
    const user = await getCurrentUser();
    updateCurrentUser(user);
    return user;
  }

  async function uploadAvatar(file: File) {
    const user = await uploadAvatarRequest(file);
    updateCurrentUser(user);
    return user;
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    const user = await changePasswordRequest({ currentPassword, newPassword });
    updateCurrentUser(user);
    return user;
  }

  async function changeDisplayName(displayName: string) {
    const user = await changeDisplayNameRequest({ displayName });
    updateCurrentUser(user);
    return user;
  }

  async function requestChangeEmailCode(email: string) {
    await requestChangeEmailCodeRequest({ email });
  }

  async function changeEmail(email: string, emailCode: string) {
    const user = await changeEmailRequest({ email, emailCode });
    updateCurrentUser(user);
    return user;
  }

  async function logout() {
    try {
      if (getAccessToken()) {
        await logoutRequest();
      }
    } finally {
      clearAuthSession();
    }
  }

  function hasRole(role: UserRole) {
    return authState.user?.roles.includes(role) === true;
  }

  return {
    state: authState,
    isAuthenticated,
    isAdmin,
    login,
    requestRegisterEmailCode,
    register,
    uploadAvatar,
    changePassword,
    changeDisplayName,
    requestChangeEmailCode,
    changeEmail,
    logout,
    hydrateCurrentUser,
    hasRole,
  };
}