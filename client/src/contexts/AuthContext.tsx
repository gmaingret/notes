import { createContext, useContext, useEffect, useState, type ReactNode, useCallback } from 'react';
import { apiClient } from '../api/client';

type User = { id: string; email: string };

type AuthContextValue = {
  accessToken: string | null;
  user: User | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true); // loading until silent refresh completes

  const applyToken = useCallback((token: string, userData: User) => {
    setAccessToken(token);
    setUser(userData);
    apiClient.setToken(token);
  }, []);

  const clearAuth = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    apiClient.setToken(null);
  }, []);

  // Silent refresh on mount — restore session from httpOnly refresh cookie
  useEffect(() => {
    fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(r => r.ok ? r.json() : null)
      .then(data => {
        if (data?.accessToken) {
          setAccessToken(data.accessToken);
          apiClient.setToken(data.accessToken);
        }
      })
      .catch(() => {/* silently fail — user goes to /login */})
      .finally(() => setIsLoading(false));
  }, []);

  // Handle Google OAuth token in URL hash fragment (after OAuth redirect)
  useEffect(() => {
    const hash = window.location.hash;
    const match = hash.match(/[#&]?token=([^&]+)/);
    if (match) {
      const token = decodeURIComponent(match[1]);
      setAccessToken(token);
      apiClient.setToken(token);
      // Clean fragment from URL without triggering navigation
      window.history.replaceState(null, '', window.location.pathname);
      setIsLoading(false);
    }
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const data = await apiClient.post<{ accessToken: string; user: User }>(
      '/api/auth/login',
      { email, password }
    );
    applyToken(data.accessToken, data.user);
  }, [applyToken]);

  const register = useCallback(async (email: string, password: string) => {
    const data = await apiClient.post<{ accessToken: string; user: User }>(
      '/api/auth/register',
      { email, password }
    );
    applyToken(data.accessToken, data.user);
  }, [applyToken]);

  const logout = useCallback(async () => {
    await apiClient.post('/api/auth/logout').catch(() => {});
    clearAuth();
  }, [clearAuth]);

  return (
    <AuthContext.Provider value={{ accessToken, user, isLoading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
