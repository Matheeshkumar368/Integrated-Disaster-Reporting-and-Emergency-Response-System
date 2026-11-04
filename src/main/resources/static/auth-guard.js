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
         * Initialize auth state from localStorage
         * @returns {Promise<{token: string|null, role: string|null}>}
         */
        async init() {
            if (this.initialized) {
                return { token: this.token, role: this.role };
            }

            this.token = localStorage.getItem('token');
            this.role = localStorage.getItem('userRole');
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
         * Validate token with backend API
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
                return response.ok;
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

            await this.init();

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
                console.log('[AuthGuard] Wrong role:', this.role, 'expected:', expectedRole);
                const msg = 'Access Denied: This page is for ' + expectedRole + ' only';
                if (window.showToast) {
                    window.showToast(msg, 'error');
                }
                setTimeout(() => {
                    this.redirectToDashboard();
                }, 1200);
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
            localStorage.removeItem('token');
            localStorage.removeItem('userRole');
            this.token = null;
            this.role = null;
            this.initialized = false;
            console.log('[AuthGuard] Logged out');
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