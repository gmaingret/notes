import { useState, FormEvent } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

type Tab = 'login' | 'register';

export function LoginPage() {
  const [tab, setTab] = useState<Tab>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string; general?: string }>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const { login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: Location })?.from?.pathname ?? '/';

  const clearErrors = () => setErrors({});

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    clearErrors();
    setIsSubmitting(true);

    try {
      if (tab === 'login') {
        await login(email, password);
      } else {
        await register(email, password);
      }
      navigate(from, { replace: true });
    } catch (err: unknown) {
      const apiErr = err as { data?: { field?: string; message?: string; errors?: Record<string, string[]> }; status?: number };

      if (apiErr.data?.field === 'email') {
        setErrors({ email: apiErr.data.message });
      } else if (apiErr.data?.errors) {
        const fieldErrors = apiErr.data.errors;
        setErrors({
          email: fieldErrors.email?.[0],
          password: fieldErrors.password?.[0],
        });
      } else {
        setErrors({ general: 'Invalid email or password' });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGoogleLogin = () => {
    window.location.href = '/api/auth/google';
  };

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h1 style={styles.title}>Notes</h1>

        {/* Tabs */}
        <div style={styles.tabs}>
          <button
            style={{ ...styles.tab, ...(tab === 'login' ? styles.tabActive : {}) }}
            onClick={() => { setTab('login'); clearErrors(); }}
          >
            Login
          </button>
          <button
            style={{ ...styles.tab, ...(tab === 'register' ? styles.tabActive : {}) }}
            onClick={() => { setTab('register'); clearErrors(); }}
          >
            Register
          </button>
        </div>

        {/* Google SSO — appears ABOVE the form with "or" divider (locked UX decision) */}
        <button onClick={handleGoogleLogin} style={styles.googleButton} type="button">
          Continue with Google
        </button>

        <div style={styles.divider}>
          <span style={styles.dividerText}>or</span>
        </div>

        {/* Email/password form */}
        <form onSubmit={handleSubmit} noValidate>
          <div style={styles.field}>
            <label style={styles.label}>Email</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              style={{ ...styles.input, ...(errors.email ? styles.inputError : {}) }}
              autoComplete="email"
              disabled={isSubmitting}
            />
            {errors.email && <span style={styles.errorText}>{errors.email}</span>}
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Password</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              style={{ ...styles.input, ...(errors.password ? styles.inputError : {}) }}
              autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
              disabled={isSubmitting}
            />
            {errors.password && <span style={styles.errorText}>{errors.password}</span>}
          </div>

          {errors.general && <p style={styles.errorText}>{errors.general}</p>}

          <button type="submit" style={styles.submitButton} disabled={isSubmitting}>
            {isSubmitting ? 'Please wait...' : tab === 'login' ? 'Login' : 'Create account'}
          </button>
        </form>
      </div>
    </div>
  );
}

// Minimal inline styles — clean and minimal (Dynalist-inspired)
const styles = {
  page: { display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: '#f5f5f5' } as const,
  card: { background: '#fff', borderRadius: 8, padding: '2rem', width: 360, boxShadow: '0 2px 8px rgba(0,0,0,0.1)' } as const,
  title: { margin: '0 0 1.5rem', fontSize: '1.5rem', fontWeight: 600, textAlign: 'center' } as const,
  tabs: { display: 'flex', marginBottom: '1.25rem', borderBottom: '1px solid #e0e0e0' } as const,
  tab: { flex: 1, padding: '0.5rem', background: 'none', border: 'none', cursor: 'pointer', fontSize: '0.875rem', color: '#666', borderBottom: '2px solid transparent' } as const,
  tabActive: { color: '#000', borderBottom: '2px solid #000' } as const,
  googleButton: { width: '100%', padding: '0.625rem', border: '1px solid #ddd', borderRadius: 4, background: '#fff', cursor: 'pointer', fontSize: '0.875rem', marginBottom: '0.75rem' } as const,
  divider: { display: 'flex', alignItems: 'center', margin: '0.75rem 0', gap: '0.75rem' } as const,
  dividerText: { color: '#999', fontSize: '0.75rem', flexShrink: 0 } as const,
  field: { marginBottom: '1rem' } as const,
  label: { display: 'block', fontSize: '0.875rem', marginBottom: '0.25rem', color: '#333' } as const,
  input: { width: '100%', padding: '0.5rem', border: '1px solid #ddd', borderRadius: 4, fontSize: '0.875rem' } as const,
  inputError: { borderColor: '#e53e3e' } as const,
  errorText: { color: '#e53e3e', fontSize: '0.75rem', marginTop: '0.25rem', display: 'block' } as const,
  submitButton: { width: '100%', padding: '0.625rem', background: '#000', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: '0.875rem', marginTop: '0.5rem' } as const,
};
