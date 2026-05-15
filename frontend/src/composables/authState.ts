import { reactive } from 'vue';
import type { AuthUser } from '../types';

const TOKEN_KEY = 'paper-rag.accessToken';
const USER_KEY = 'paper-rag.currentUser';

export const authState = reactive({
  accessToken: localStorage.getItem(TOKEN_KEY) || '',
  user: readStoredUser(),
});

export function getAccessToken() {
  return authState.accessToken;
}

export function setAuthSession(accessToken: string, user: AuthUser) {
  authState.accessToken = accessToken;
  authState.user = user;
  localStorage.setItem(TOKEN_KEY, accessToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function updateCurrentUser(user: AuthUser) {
  authState.user = user;
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuthSession() {
  authState.accessToken = '';
  authState.user = null;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
}