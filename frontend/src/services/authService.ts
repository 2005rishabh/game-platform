const API_BASE_URL = 'http://localhost:8080/auth/api';

export interface AuthResponse {
  token: string;
  username: string;
}

export interface LoginPayload {
  username: string;
  password?: string;
}

export interface RegisterPayload {
  username: string;
  email: string;
  password?: string;
}

export const authService = {
  async login(payload: LoginPayload): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      let errorMsg = 'Failed to login';
      try {
        const errorData = await response.json();
        if (errorData && errorData.message) {
          errorMsg = errorData.message;
        }
      } catch {
        const errorText = await response.text();
        if (errorText) errorMsg = errorText;
      }
      throw new Error(errorMsg);
    }

    const data: AuthResponse = await response.json();
    if (data.token) {
      localStorage.setItem('token', data.token);
      localStorage.setItem('username', data.username);
    }
    return data;
  },

  async register(payload: RegisterPayload): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/request`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      let errorMsg = 'Failed to register';
      try {
        const errorData = await response.json();
        if (errorData && errorData.message) {
          errorMsg = errorData.message;
        }
      } catch {
        const errorText = await response.text();
        if (errorText) errorMsg = errorText;
      }
      throw new Error(errorMsg);
    }

    const data: AuthResponse = await response.json();
    if (data.token) {
      localStorage.setItem('token', data.token);
      localStorage.setItem('username', data.username);
    }
    return data;
  },

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('jwt');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('access_token');
    localStorage.removeItem('username');
  },

  getStoredUser(): { token: string | null; username: string | null } {
    const token =
      localStorage.getItem('token') ??
      localStorage.getItem('jwt') ??
      localStorage.getItem('accessToken') ??
      localStorage.getItem('access_token');
    const username = localStorage.getItem('username');
    return { token: token ?? null, username: username ?? null };
  },
};
