import { useState, type FormEvent } from 'react';
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
    <div className="login-page">
      <div className="login-card">
        <h1 className="login-title" style={{ margin: '0 0 1.5rem', fontSize: '1.5rem', fontWeight: 600 }}>Notes</h1>

        {/* Tabs */}
        <div className="login-tabs">
          <button
            className={`login-tab${tab === 'login' ? ' login-tab--active' : ''}`}
            onClick={() => { setTab('login'); clearErrors(); }}
          >
            Login
          </button>
          <button
            className={`login-tab${tab === 'register' ? ' login-tab--active' : ''}`}
            onClick={() => { setTab('register'); clearErrors(); }}
          >
            Register
          </button>
        </div>

        {/* Google SSO — appears ABOVE the form with "or" divider (locked UX decision) */}
        <button onClick={handleGoogleLogin} className="login-google-btn" style={{ marginBottom: '0.75rem' }} type="button">
          Continue with Google
        </button>

        <div className="login-divider">
          <span>or</span>
        </div>

        {/* Email/password form */}
        <form onSubmit={handleSubmit} noValidate>
          <div className="login-field">
            <label className="login-label">Email</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              className={`login-input${errors.email ? ' login-input--error' : ''}`}
              autoComplete="email"
              disabled={isSubmitting}
            />
            {errors.email && <span className="login-error">{errors.email}</span>}
          </div>

          <div className="login-field">
            <label className="login-label">Password</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              className={`login-input${errors.password ? ' login-input--error' : ''}`}
              autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
              disabled={isSubmitting}
            />
            {errors.password && <span className="login-error">{errors.password}</span>}
          </div>

          {errors.general && <p className="login-error">{errors.general}</p>}

          <button type="submit" className="login-submit" disabled={isSubmitting}>
            {isSubmitting ? 'Please wait...' : tab === 'login' ? 'Login' : 'Create account'}
          </button>
        </form>
      </div>
    </div>
  );
}
