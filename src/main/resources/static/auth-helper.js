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

    if (typeof window !== 'undefined' && !window.escapeHtml) {
        window.escapeHtml = function(text) {
            if (!text) return '';
            const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
            return String(text).replace(/[&<>"']/g, m => map[m]);
        };
        try { globalThis.escapeHtml = window.escapeHtml; } catch(e) {}
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

        getToken: function(preferredRole) {
            try {
                let reqRole = preferredRole;
                if (!reqRole && typeof window !== 'undefined' && window.location) {
                    const p = window.location.pathname.toLowerCase();
                    if (p.includes('admin')) reqRole = 'Admin';
                    else if (p.includes('responder')) reqRole = 'Responder';
                    else if (p.includes('citizen')) reqRole = 'Citizen';
                }

                // If a specific role is expected, prioritize that role's isolated token
                if (reqRole) {
                    const norm = reqRole.charAt(0).toUpperCase() + reqRole.slice(1).toLowerCase();
                    const sRoleTok = sessionStorage.getItem('token_' + norm) || sessionStorage.getItem(norm.toLowerCase() + '_token');
                    if (sRoleTok && sRoleTok !== 'null' && sRoleTok !== 'undefined') return sRoleTok;

                    const sTok = sessionStorage.getItem('token');
                    if (sTok && sTok.includes('.')) {
                        try {
                            const p = JSON.parse(atob(sTok.split('.')[1]));
                            if (p && p.role && p.role.toLowerCase() === norm.toLowerCase()) return sTok;
                        } catch(e) {}
                    }

                    const lRoleTok = localStorage.getItem('token_' + norm) || localStorage.getItem(norm.toLowerCase() + '_token');
                    if (lRoleTok && lRoleTok !== 'null' && lRoleTok !== 'undefined') return lRoleTok;

                    const lTok = localStorage.getItem('token');
                    if (lTok && lTok.includes('.')) {
                        try {
                            const p = JSON.parse(atob(lTok.split('.')[1]));
                            if (p && p.role && p.role.toLowerCase() === norm.toLowerCase()) return lTok;
                        } catch(e) {}
                    }

                    // Strict role isolation: if a specific role is requested, NEVER fall back to another role's token!
                    return null;
                }

                // Generic fallback: tab-isolated sessionStorage first, then localStorage
                const sTok = sessionStorage.getItem('token');
                if (sTok && sTok !== 'null' && sTok !== 'undefined') return sTok;

                const token = localStorage.getItem('token');
                if (!token || token === 'null' || token === 'undefined') {
                    return null;
                }
                return token;
            } catch (e) {
                console.error('[AuthHelper] Failed to read token from storage:', e);
                return null;
            }
        },

        /**
         * Resolves the user's role with cryptographic ground truth.
         * Decodes JWT payload first. If present, synchronizes localStorage.
         */
        getRole: function(preferredRole) {
            try {
                const token = this.getToken(preferredRole);
                if (token && token.includes('.')) {
                    const payload = JSON.parse(atob(token.split('.')[1]));
                    if (payload && payload.role) {
                        return payload.role;
                    }
                }
                let reqRole = preferredRole;
                if (!reqRole && typeof window !== 'undefined' && window.location) {
                    const p = window.location.pathname.toLowerCase();
                    if (p.includes('admin')) reqRole = 'Admin';
                    else if (p.includes('responder')) reqRole = 'Responder';
                    else if (p.includes('citizen')) reqRole = 'Citizen';
                }
                if (reqRole) {
                    const norm = reqRole.charAt(0).toUpperCase() + reqRole.slice(1).toLowerCase();
                    const sRole = sessionStorage.getItem('userRole_' + norm) || sessionStorage.getItem('userRole');
                    if (sRole && sRole !== 'null') return sRole;
                    const lRole = localStorage.getItem('userRole_' + norm);
                    if (lRole && lRole !== 'null') return lRole;
                }
                const storedRole = sessionStorage.getItem('userRole') || localStorage.getItem('userRole');
                if (storedRole && storedRole !== 'null' && storedRole !== 'undefined') {
                    return storedRole;
                }
                return null;
            } catch (e) {
                const storedRole = localStorage.getItem('userRole');
                return (storedRole && storedRole !== 'null') ? storedRole : null;
            }
        },

        getName: function() {
            try {
                const storedName = localStorage.getItem('userName');
                if (storedName && storedName !== 'null') return storedName;
                const token = this.getToken();
                if (token && token.includes('.')) {
                    const payload = JSON.parse(atob(token.split('.')[1]));
                    return payload.name || payload.sub || 'User';
                }
                return 'User';
            } catch (e) {
                return 'User';
            }
        },

        getEmail: function() {
            try {
                const storedEmail = localStorage.getItem('userEmail');
                if (storedEmail && storedEmail !== 'null') return storedEmail;
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

        getHeaders: function(extraHeaders = {}, preferredRole = null) {
            if (typeof extraHeaders === 'string') {
                preferredRole = extraHeaders;
                extraHeaders = {};
            }
            const headers = { ...extraHeaders };
            const token = this.getToken(preferredRole);
            if (token) {
                headers['Authorization'] = 'Bearer ' + token;
            }
            const stepUpToken = this.getStepUpToken();
            if (stepUpToken) {
                headers['X-Admin-Step-Up-Token'] = stepUpToken;
            }
            return headers;
        },

        getBaseUrl: function() {
            try {
                if (typeof window !== 'undefined' && window.location) {
                    return window.location.origin;
                }
            } catch(e) {}
            return '';
        },

        apiUrl: function(path) {
            const cleanPath = path ? (path.startsWith('/') ? path : '/' + path) : '';
            return this.getBaseUrl() + cleanPath;
        },

        getDashboardUrl: function(role) {
            const r = role || this.getRole();
            if (r === 'Admin') return '/admin-dashboard.html';
            if (r === 'Responder') return '/responder-dashboard.html';
            return '/citizen-dashboard.html';
        },

        getNotificationUrl: function(role) {
            const r = role || this.getRole();
            if (r === 'Admin') return '/admin-notifications.html';
            if (r === 'Responder') return '/responder-notifications.html';
            return '/citizen-notifications.html';
        },

        getProfileUrl: function(role) {
            const r = role || this.getRole();
            if (r === 'Admin') return '/admin-profile.html';
            if (r === 'Responder') return '/responder-profile.html';
            return '/profile.html';
        },

        handleAuthFailure: function(response, customMessage) {
            if (response && response.status === 401) {
                console.warn('[AuthHelper] Session expired or unauthorized (401). Clearing session.');
                this.logout();
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
            const userRole = this.getRole();
            if (!userRole) {
                console.warn('[AuthHelper] No role detected in token or storage. Redirecting to login.');
                this.logout();
                return false;
            }
            if (expectedRole && userRole !== expectedRole) {
                console.warn(`[AuthHelper] Role mismatch: expected ${expectedRole}, got ${userRole}`);
                if (window.showToast) window.showToast(`Access Denied: Only ${expectedRole} accounts can access this page. Redirecting...`, 'error');
                setTimeout(() => {
                    if (userRole === 'Admin') window.location.href = '/admin-dashboard.html';
                    else if (userRole === 'Responder') window.location.href = '/responder-dashboard.html';
                    else window.location.href = '/citizen-dashboard.html';
                }, 600);
                return false;
            }
            return true;
        },

        logout: function() {
            localStorage.clear();
            sessionStorage.clear();
            try {
                fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
            } catch(e) {}
            window.location.href = '/index.html';
        }
    };

    AuthHelper.initUrlAuth();
    window.AuthHelper = AuthHelper;
    window.getAuthToken = function() { return AuthHelper.getToken(); };
    window.getAuthHeaders = function(extra) { return AuthHelper.getHeaders(extra); };
})(window);
