const TOKEN_KEY = 'rag_token';
const USER_KEY = 'rag_username';

export interface AuthUser {
  token: string;
  username: string;
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getUsername(): string | null {
  return localStorage.getItem(USER_KEY);
}

export function saveAuth(user: AuthUser): void {
  localStorage.setItem(TOKEN_KEY, user.token);
  localStorage.setItem(USER_KEY, user.username);
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function isLoggedIn(): boolean {
  return !!getToken();
}
