import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { type ReactNode } from 'react';
import { useAuth } from './contexts/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { AppPage } from './pages/AppPage';
import { useGlobalKeyboard } from './hooks/useUndo';

function RequireAuth({ children }: { children: ReactNode }) {
  const { accessToken, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return null;
  if (!accessToken) return <Navigate to="/login" state={{ from: location }} replace />;
  return <>{children}</>;
}

// Thin component that mounts the global keyboard handler — renders nothing.
// Placed inside RequireAuth so shortcuts only fire when authenticated.
function GlobalKeyboard() {
  useGlobalKeyboard();
  return null;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={
        <RequireAuth>
          <GlobalKeyboard />
          <AppPage />
        </RequireAuth>
      } />
      <Route path="/doc/:docId" element={
        <RequireAuth>
          <GlobalKeyboard />
          <AppPage />
        </RequireAuth>
      } />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
