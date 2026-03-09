import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ReactNode } from 'react';
import { useAuth } from './contexts/AuthContext';
import { LoginPage } from './pages/LoginPage';

// Placeholder for AppPage — built in Plan 05
function AppPage() {
  return <div>App — coming in Plan 05</div>;
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { accessToken, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) return null; // Wait for silent refresh before deciding

  if (!accessToken) {
    // Redirect to /login; remember where user was going
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/*"
        element={
          <RequireAuth>
            <AppPage />
          </RequireAuth>
        }
      />
    </Routes>
  );
}
