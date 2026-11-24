/**
 * AuthGuard - Centralized Authentication Module
 * Provides consistent auth state management across all protected pages
 * Fixes race conditions between localStorage and page initialization
 */

(function(global) {
    'use strict';

    const AuthGuard = {
        isReady: false,
        token: null,
        role: null,
        initialized: false,

        /**
         * Initialize auth state from localStorage and verified JWT payload
         * @returns {Promise<{token: string|null, role: string|null}>}
         */
        async init(expectedRole) {
            let reqRole = expectedRole;
            if (!reqRole && typeof window !== 'undefined' && window.location) {
                const p = window.location.pathname.toLowerCase();
                if (p.includes('admin')) reqRole = 'Admin';
                else if (p.includes('responder')) reqRole = 'Responder';
                else if (p.includes('citizen')) reqRole = 'Citizen';
            }

            this.token = (window.AuthHelper && window.AuthHelper.getToken(reqRole)) || localStorage.getItem('token');
            if (this.token && this.token.includes('.')) {
                try {
                    const payload = JSON.parse(atob(this.token.split('.')[1]));
                    if (payload && payload.role) {
                        this.role = payload.role;
                    }
                } catch(e) {}
            }
            if (!this.role && reqRole) {
                this.role = sessionStorage.getItem('userRole_' + reqRole) || localStorage.getItem('userRole_' + reqRole);
            }
            if (!this.role) {
                this.role = sessionStorage.getItem('userRole') || localStorage.getItem('userRole');
            }
            this.initialized = true;
            this.isReady = true;

            console.log('[AuthGuard] Initialized - role:', this.role, 'hasToken:', !!this.token);
            return { token: this.token, role: this.role };
        },

        /**
         * Get stored token
         * @returns {string|null}
         */
        getToken() {
            return this.token;
        },

        /**
         * Get stored role
         * @returns {string|null}
         */
        getRole() {
            return this.role;
        },

        /**
         * Check if user is authenticated
         * @returns {boolean}
         */
        isAuthenticated() {
            return this.token !== null && this.token !== undefined && this.token !== 'null';
        },

        /**
         * Check if role matches expected
         * @param {string} expectedRole
         * @returns {boolean}
         */
        hasRole(expectedRole) {
            return this.role === expectedRole;
        },

        /**
         * Validate token with backend API and synchronize server ground truth
         * @returns {Promise<boolean>}
         */
        async validateWithBackend() {
            if (!this.token) {
                return false;
            }

            try {
                const response = await fetch('/api/auth/profile', {
                    method: 'POST',
                    headers: {
                        'Authorization': 'Bearer ' + this.token,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({})
                });

                if (!response.ok) {
                    console.warn('[AuthGuard] Backend validation rejected with HTTP', response.status);
                    return false;
                }

                const data = await response.json();
                if (data && (data.status === 'success' || data.email)) {
                    if (data.role) {
                        this.role = data.role;
                        localStorage.setItem('userRole', data.role);
                    }
                    if (data.email) {
                        localStorage.setItem('userEmail', data.email);
                    }
                    if (data.name) {
                        localStorage.setItem('userName', data.name);
                    }
                    return true;
                }
                return false;
            } catch (e) {
                console.error('[AuthGuard] Backend validation failed:', e);
                return false;
            }
        },

        /**
         * Require authentication, redirect to login if not authenticated
         * @param {string} expectedRole - Optional role requirement
         * @returns {Promise<boolean>}
         */
        async requireAuth(expectedRole) {
            // Hide loading screen first to prevent perpetual loading
            if (typeof RoleLoadingScreen !== 'undefined') {
                RoleLoadingScreen.hide();
            }

            await this.init(expectedRole);

            if (!this.isAuthenticated()) {
                console.log('[AuthGuard] No token, redirecting to login');
                window.location.href = '/index.html';
                return false;
            }

            // Validate with backend
            const isValid = await this.validateWithBackend();
            if (!isValid) {
                console.log('[AuthGuard] Token rejected by server');
                this.logout();
                window.location.href = '/index.html';
                return false;
            }

            // Check role if expectedRole provided
            if (expectedRole && this.role !== expectedRole) {
                console.warn('[AuthGuard] Role mismatch! DB Role:', this.role, 'Expected:', expectedRole);
                const msg = 'Access Denied: This account is ' + (this.role || 'unauthorized') + ' and cannot access ' + expectedRole + ' resources';
                if (window.showToast) {
                    window.showToast(msg, 'error');
                } else {
                    alert(msg);
                }
                setTimeout(() => {
                    this.redirectToDashboard();
                }, 500);
                return false;
            }

            console.log('[AuthGuard] Auth passed for role:', this.role);
            return true;
        },

        /**
         * Redirect to appropriate dashboard based on role
         */
        redirectToDashboard() {
            const dashboards = {
                'Admin': '/admin-dashboard.html',
                'Responder': '/responder-dashboard.html',
                'Citizen': '/citizen-dashboard.html'
            };
            const target = dashboards[this.role] || '/index.html';
            window.location.href = target;
        },

        /**
         * Clear auth data (logout)
         */
        logout() {
            localStorage.clear();
            sessionStorage.clear();
            this.token = null;
            this.role = null;
            this.initialized = false;
            console.log('[AuthGuard] Logged out');
            try {
                fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
            } catch(e) {}
            window.location.href = '/index.html';
        }
    };

    // Auto-initialize on script load
    AuthGuard.init();

    // Export to global scope
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = AuthGuard;
    } else {
        global.AuthGuard = AuthGuard;
    }

})(typeof window !== 'undefined' ? window : this);
