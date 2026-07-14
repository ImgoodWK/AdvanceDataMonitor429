// API client — wraps fetch with Bearer token auth, error handling, and JSON parsing.
import type { ApiError } from '@/types/dto';

export class ApiClientError extends Error {
  code: string;
  status: number;
  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = 'ApiClientError';
    this.code = code;
    this.status = status;
  }
}

export interface ApiClientOptions {
  getToken: () => string | null;
  getAdminToken?: () => string | null;
  onAuthFailure?: (code: string) => void;
  onAdminRequired?: () => void;
}

export class ApiClient {
  private getToken: () => string | null;
  private getAdminToken: () => string | null;
  private onAuthFailure?: (code: string) => void;
  private onAdminRequired?: () => void;

  constructor(opts: ApiClientOptions) {
    this.getToken = opts.getToken;
    this.getAdminToken = opts.getAdminToken ?? (() => null);
    this.onAuthFailure = opts.onAuthFailure;
    this.onAdminRequired = opts.onAdminRequired;
  }

  private authHeaders(): Record<string, string> {
    const token = this.getToken();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const adminToken = this.getAdminToken();
    if (adminToken) headers['X-WebAE-Admin'] = adminToken;
    return headers;
  }

  private async request<T>(
    url: string,
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' = 'GET',
    body?: unknown
  ): Promise<T> {
    const opts: RequestInit = {
      method,
      headers: this.authHeaders(),
    };
    if (body !== undefined && (method === 'POST' || method === 'PUT' || method === 'PATCH')) {
      opts.body = typeof body === 'string' ? body : JSON.stringify(body);
    }
    let resp: Response;
    try {
      resp = await fetch(url, opts);
    } catch (e) {
      throw new ApiClientError(
        (e as Error).message || 'Network error',
        'network_error',
        0
      );
    }
    if (resp.status === 401) {
      let code = 'auth_failed';
      try {
        const err = (await resp.json()) as ApiError;
        code = err.code || (err as { error?: string }).error || code;
      } catch {
        /* ignore parse error */
      }
      this.onAuthFailure?.(code);
      throw new ApiClientError('Authentication failed', code, 401);
    }
    if (!resp.ok) {
      let message = 'Request failed: ' + resp.status;
      let code = 'http_' + resp.status;
      try {
        const err = (await resp.json()) as ApiError;
        if (err.message) message = err.message;
        if (err.code) code = err.code;
        else if ((err as { error?: string }).error) code = (err as { error?: string }).error!;
      } catch {
        /* ignore */
      }
      if (code === 'webae_disabled') {
        this.onAuthFailure?.(code);
      }
      if (code === 'admin_required' || code === 'admin_elevate_denied') {
        this.onAdminRequired?.();
      }
      throw new ApiClientError(message, code, resp.status);
    }
    // Handle binary responses (PNG icons) — caller should use fetchIcon directly
    const ct = resp.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
      return (await resp.json()) as T;
    }
    return (await resp.text()) as unknown as T;
  }

  get<T>(url: string): Promise<T> {
    return this.request<T>(url, 'GET');
  }

  post<T>(url: string, body?: unknown): Promise<T> {
    return this.request<T>(url, 'POST', body);
  }

  put<T>(url: string, body?: unknown): Promise<T> {
    return this.request<T>(url, 'PUT', body);
  }

  patch<T>(url: string, body?: unknown): Promise<T> {
    return this.request<T>(url, 'PATCH', body);
  }

  delete<T>(url: string): Promise<T> {
    return this.request<T>(url, 'DELETE');
  }

  postBinary<T>(url: string, file: Blob | File): Promise<T> {
    const headers: Record<string, string> = {};
    const token = this.getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const adminToken = this.getAdminToken();
    if (adminToken) headers['X-WebAE-Admin'] = adminToken;
    return fetch(url, { method: 'POST', headers, body: file }).then((r) => {
      if (!r.ok) throw new ApiClientError('Upload failed', 'upload_failed', r.status);
      return r.json() as Promise<T>;
    });
  }

  updateOptions(opts: Partial<ApiClientOptions>): void {
    if (opts.getToken) this.getToken = opts.getToken;
    if (opts.getAdminToken !== undefined) this.getAdminToken = opts.getAdminToken;
    if (opts.onAuthFailure !== undefined) this.onAuthFailure = opts.onAuthFailure;
    if (opts.onAdminRequired !== undefined) this.onAdminRequired = opts.onAdminRequired;
  }
}

// Singleton instance — initialized by AppContext
let _client: ApiClient | null = null;

export function initApiClient(opts: ApiClientOptions) {
  _client = new ApiClient(opts);
  return _client;
}

export function updateApiClientOptions(opts: Partial<ApiClientOptions>): void {
  if (_client) _client.updateOptions(opts);
}

export function getApiClient(): ApiClient {
  if (!_client) throw new Error('ApiClient not initialized');
  return _client;
}
