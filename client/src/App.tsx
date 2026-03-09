import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ReactNode } from 'react';
import { useAuth } from './contexts/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { AppPage } from './pages/AppPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { accessToken, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return null;
  if (!accessToken) return <Navigate to="/login" state={{ from: location }} replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<RequireAuth><AppPage /></RequireAuth>} />
      <Route path="/doc/:docId" element={<RequireAuth><AppPage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
