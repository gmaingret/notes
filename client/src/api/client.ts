const BASE = ''; // same origin in production; Vite proxy handles /api in dev

type RequestOptions = Omit<RequestInit, 'headers'> & {
  headers?: Record<string, string>;
};

class ApiClient {
  private accessToken: string | null = null;
  private refreshHandler: (() => Promise<string | null>) | null = null;
  private logoutHandler: (() => void) | null = null;
  private refreshPromise: Promise<string | null> | null = null;

  setToken(token: string | null) {
    this.accessToken = token;
  }

  setRefreshHandler(handler: (() => Promise<string | null>) | null) {
    this.refreshHandler = handler;
  }

  setLogoutHandler(handler: (() => void) | null) {
    this.logoutHandler = handler;
  }

  private async handleUnauthorized<T>(
    retryFn: (token: string) => Promise<T>
  ): Promise<T> {
    if (!this.refreshHandler) throw new Error('Session expired');

    // Shared promise lock: reuse in-flight refresh to prevent concurrent refresh calls
    if (!this.refreshPromise) {
      this.refreshPromise = this.refreshHandler().finally(() => {
        this.refreshPromise = null;
      });
    }

    const newToken = await this.refreshPromise;

    if (!newToken) {
      // Refresh failed — session truly expired
      if (this.logoutHandler) this.logoutHandler();
      throw Object.assign(new Error('Session expired'), { status: 401 });
    }

    this.setToken(newToken);
    return retryFn(newToken);
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    };

    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }

    const res = await fetch(`${BASE}${path}`, {
      ...options,
      headers,
      credentials: 'include', // always include httpOnly refresh cookie
    });

    // 401 interception: attempt silent token refresh if not already a retry
    if (res.status === 401 && !(options as any)?._isRetry) {
      return this.handleUnauthorized((token) =>
        this.request<T>(path, {
          ...options,
          headers: { ...(options.headers ?? {}), Authorization: `Bearer ${token}` },
          _isRetry: true,
        } as any)
      );
    }

    if (!res.ok) {
      const error = await res.json().catch(() => ({ error: 'Request failed' }));
      throw Object.assign(new Error(error.error ?? 'Request failed'), { status: res.status, data: error });
    }

    // Handle 204 No Content
    if (res.status === 204) return undefined as T;

    return res.json() as Promise<T>;
  }

  get<T>(path: string, options?: RequestOptions) {
    return this.request<T>(path, { ...options, method: 'GET' });
  }

  post<T>(path: string, body?: unknown, options?: RequestOptions) {
    return this.request<T>(path, {
      ...options,
      method: 'POST',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  }

  patch<T>(path: string, body?: unknown, options?: RequestOptions) {
    return this.request<T>(path, {
      ...options,
      method: 'PATCH',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  }

  delete<T>(path: string, options?: RequestOptions) {
    return this.request<T>(path, { ...options, method: 'DELETE' });
  }

  async download(path: string, isRetry = false): Promise<Response> {
    const headers: Record<string, string> = {};
    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }
    const res = await fetch(`${BASE}${path}`, { headers, credentials: 'include' });

    if (res.status === 401 && !isRetry) {
      return this.handleUnauthorized(() => this.download(path, true));
    }

    return res;
  }

  async upload<T>(path: string, formData: FormData, isRetry = false): Promise<T> {
    const headers: Record<string, string> = {};
    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }
    // Do NOT set Content-Type — browser sets it with multipart boundary
    const res = await fetch(`${BASE}${path}`, {
      method: 'POST',
      headers,
      body: formData,
      credentials: 'include',
    });

    if (res.status === 401 && !isRetry) {
      return this.handleUnauthorized(() => this.upload<T>(path, formData, true));
    }

    if (!res.ok) {
      const error = await res.json().catch(() => ({ error: 'Request failed' }));
      throw Object.assign(new Error(error.error ?? 'Request failed'), { status: res.status });
    }
    return res.json() as Promise<T>;
  }
}

export const apiClient = new ApiClient();
