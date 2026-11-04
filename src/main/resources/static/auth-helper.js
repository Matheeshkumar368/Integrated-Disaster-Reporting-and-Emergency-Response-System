/**
 * Unified Authentication & Connectivity Helper
 * Disaster Management & Alert System
 * Provides rock-solid, cross-page token retrieval, role-guards, and API headers.
 */
(function(window) {
    'use strict';

    // Auto-load Toast & Dialog Manager
    if (typeof document !== 'undefined' && !document.getElementById('ccToastScript')) {
        const script = document.createElement('script');
        script.id = 'ccToastScript';
        script.src = '/toast-manager.js?v=2';
        document.head.appendChild(script);
    }

    const AuthHelper = {
        initUrlAuth: function() {
            try {
                if (typeof window !== 'undefined' && window.location && window.location.search) {
                    const params = new URLSearchParams(window.location.search);
                    const demoAuth = params.get('demo_auth');
                    const token = params.get('token');
                    if (demoAuth && token) {
                        localStorage.setItem('token', token);
                        localStorage.setItem('userRole', demoAuth);
                        localStorage.setItem('userName', params.get('name') || (demoAuth + ' User'));
                    }
                }
            } catch (e) {
                console.warn('[AuthHelper] Failed to initUrlAuth:', e);
            }
        },

        getToken: function() {
            try {
                const token = localStorage.getItem('token');
                if (!token || token === 'null' || token === 'undefined') {
                    return null;
                }
                return token;
            } catch (e) {
                console.error('[AuthHelper] Failed to read token from localStorage:', e);
                return null;
            }
        },

        getRole: function() {
            try {
                const storedRole = localStorage.getItem('userRole');
                if (storedRole) return storedRole;
                const token = this.getToken();
                if (token && token.includes('.')) {
                    const payload = JSON.parse(atob(token.split('.')[1]));
                    return payload.role || null;
                }
                return null;
            } catch (e) {
                return null;
            }
        },

        getEmail: function() {
            try {
                const token = this.getToken();
                if (token && token.includes('.')) {
                    const payload = JSON.parse(atob(token.split('.')[1]));
                    return payload.sub || null;
                }
                return null;
            } catch (e) {
                return null;
            }
        },

        getStepUpToken: function() {
            try {
                return sessionStorage.getItem('adminStepUpToken') || null;
            } catch (e) {
                return null;
            }
        },

        setStepUpToken: function(stepUpToken) {
            try {
                if (stepUpToken) {
                    sessionStorage.setItem('adminStepUpToken', stepUpToken);
                } else {
                    sessionStorage.removeItem('adminStepUpToken');
                }
            } catch (e) {
                console.error('[AuthHelper] Failed to write step-up token:', e);
            }
        },

        getHeaders: function(extraHeaders = {}) {
            const headers = { ...extraHeaders };
            const token = this.getToken();
            if (token) {
                headers['Authorization'] = 'Bearer ' + token;
            }
            const stepUpToken = this.getStepUpToken();
            if (stepUpToken) {
                headers['X-Admin-Step-Up-Token'] = stepUpToken;
            }
            return headers;
        },

        handleAuthFailure: function(response, customMessage) {
            if (response && response.status === 401) {
                console.warn('[AuthHelper] Session expired or unauthorized (401). Clearing session.');
                localStorage.removeItem('token');
                localStorage.removeItem('userRole');
                sessionStorage.removeItem('adminStepUpToken');
                const msg = customMessage || 'Your session has expired or is invalid. Please log in again.';
                if (window.showToast) window.showToast(msg, 'error');
                setTimeout(() => {
                    window.location.href = '/index.html?error=session_expired';
                }, 800);
                return true;
            }
            if (response && response.status === 403) {
                if (window.showToast) window.showToast('Access Denied (403): You do not have permission to perform this action.', 'error');
                return true;
            }
            return false;
        },

        requireAuth: function(expectedRole) {
            const token = this.getToken();
            if (!token) {
                console.warn('[AuthHelper] No auth token found. Redirecting to login.');
                window.location.href = '/index.html';
                return false;
            }
            if (expectedRole) {
                const userRole = this.getRole();
                if (userRole !== expectedRole) {
                    console.warn(`[AuthHelper] Role mismatch: expected ${expectedRole}, got ${userRole}`);
                    if (window.showToast) window.showToast(`Access Denied: Only ${expectedRole} accounts can access this page.`, 'error');
                    setTimeout(() => {
                        if (userRole === 'Admin') window.location.href = '/admin-dashboard.html';
                        else if (userRole === 'Responder') window.location.href = '/responder-dashboard.html';
                        else window.location.href = '/citizen-dashboard.html';
                    }, 800);
                    return false;
                }
            }
            return true;
        },

        logout: function() {
            localStorage.removeItem('token');
            localStorage.removeItem('userRole');
            sessionStorage.removeItem('adminStepUpToken');
            fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
            window.location.href = '/index.html';
        }
    };

    AuthHelper.initUrlAuth();
    window.AuthHelper = AuthHelper;
    window.getAuthToken = function() { return AuthHelper.getToken(); };
    window.getAuthHeaders = function(extra) { return AuthHelper.getHeaders(extra); };
})(window);

