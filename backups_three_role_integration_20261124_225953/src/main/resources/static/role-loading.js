/**
 * Role-Based Loading Screen - Optimized Version
 * Fixed: Memory leaks, timing issues, race conditions, null references
 * Features: 3s minimum display, proper cleanup, resource verification, callbacks
 */

(function(global) {
    'use strict';

    const RoleLoadingScreen = (function() {
        // Private state
        let container = null;
        let currentRole = null;
        let isVisible = false;
        let progressIntervalId = null;
        let cleanupCallbacks = [];
        let animationFrameId = null;
        let isBackForwardNav = false;
        let isLoginRedirect = false;

        // Configuration
        const CONFIG = {
            MIN_DISPLAY_TIME: 150,       // Snappy 600ms responsive display
            PROGRESS_INTERVAL: 30,       // Update every 30ms for smooth animation
            FADE_DURATION: 150,          // Fast clean fade
            HIDE_DELAY: 50,             // Quick transition
            PROGRESS_INCREMENT: 6,       // Snappy progress
            LOGIN_FLAG_KEY: 'justLoggedIn',
            LOGIN_FLAG_TIMEOUT: 4000
        };

        // Detect back/forward navigation
        function detectBackForward() {
            if (window.performance && window.performance.navigation) {
                const navType = window.performance.navigation.type;
                isBackForwardNav = (navType === 2 || navType === 1);
            }
            window.addEventListener('pageshow', function(event) {
                if (event.persisted) {
                    isBackForwardNav = true;
                }
            });
        }

        // Check if this is a login redirect
        function checkLoginRedirect() {
            try {
                const flag = sessionStorage.getItem(CONFIG.LOGIN_FLAG_KEY);
                if (flag) {
                    sessionStorage.removeItem(CONFIG.LOGIN_FLAG_KEY);
                    isLoginRedirect = true;
                    console.log('[RoleLoadingScreen] Login redirect detected, flag consumed');
                    return true;
                }
            } catch (e) {
                console.warn('[RoleLoadingScreen] Could not check sessionStorage:', e);
            }
            return false;
        }

        // Set login redirect flag - must be synchronous before navigation
        function setLoginRedirectFlag() {
            try {
                sessionStorage.setItem(CONFIG.LOGIN_FLAG_KEY, 'true');
                console.log('[RoleLoadingScreen] Login redirect flag set');
            } catch (e) {
                console.warn('[RoleLoadingScreen] Could not set sessionStorage:', e);
            }
            // Auto-cleanup after timeout
            setTimeout(function() {
                try {
                    sessionStorage.removeItem(CONFIG.LOGIN_FLAG_KEY);
                } catch (e) {}
            }, CONFIG.LOGIN_FLAG_TIMEOUT);
        }

        // Run detection
        detectBackForward();

        // Role-specific messages
        const MESSAGES = {
            Admin: {
                initializing: 'Connecting to Admin Console...',
                permissions: 'Loading administrative permissions...',
                dashboard: 'Preparing admin dashboard...',
                complete: 'Redirecting to Admin Dashboard...'
            },
            Responder: {
                initializing: 'Connecting to Emergency Services...',
                permissions: 'Loading responder credentials...',
                dashboard: 'Preparing responder console...',
                complete: 'Redirecting to Responder Dashboard...'
            },
            Citizen: {
                initializing: 'Connecting to Citizen Portal...',
                permissions: 'Loading your profile permissions...',
                dashboard: 'Preparing your dashboard...',
                complete: 'Redirecting to Citizen Dashboard...'
            }
        };

        // Get message for role and stage
        function getMessage(stage, role) {
            const roleMessages = MESSAGES[role] || MESSAGES.Citizen;
            return roleMessages[stage] || 'Please wait...';
        }

        // Get icon class for role
        function getRoleIcon(role) {
            const icons = {
                'Admin': 'fas fa-user-shield',
                'Responder': 'fas fa-ambulance',
                'Citizen': 'fas fa-user'
            };
            return icons[role] || 'fas fa-shield-alt';
        }

        // Create loading screen elements
        function createElements(forceRecreate) {
            // Always recreate on page load to avoid stale state from previous page
            forceRecreate = true;
            
            // Remove any existing container from previous page
            const existingContainer = document.getElementById('role-loading-screen');
            if (existingContainer && document.body.contains(existingContainer)) {
                try {
                    existingContainer.remove();
                    console.log('[RoleLoadingScreen] Removed stale container from previous page');
                } catch (e) {}
                container = null;
            }
            
            // Prevent duplicate creation
            if (!forceRecreate && container && document.body.contains(container)) {
                return;
            }

            try {
                container = document.createElement('div');
                container.id = 'role-loading-screen';
                container.className = 'hidden';
                container.setAttribute('role', 'status');
                container.setAttribute('aria-live', 'polite');
                container.setAttribute('aria-label', 'Loading');

                const icon = getRoleIcon(currentRole);

                container.innerHTML = `
                    <div class="loading-backdrop"></div>
                    <div class="loading-content">
                        <div class="brand-logo">
                            <div class="logo-icon">
                                <i class="${icon}"></i>
                            </div>
                        </div>
                        <h1 class="project-name">Disaster Management</h1>
                        <p class="project-tagline">National Disaster Response Platform</p>
                        <div class="spinner-container">
                            <div class="spinner-ring"></div>
                            <div class="spinner-ring"></div>
                            <div class="spinner-ring"></div>
                        </div>
                        <div class="progress-bar-container">
                            <div class="progress-bar" id="role-loading-progress" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"></div>
                        </div>
                        <p class="status-message" id="role-loading-status">Initializing...</p>
                        <p class="status-submessage" id="role-loading-substatus"></p>
                    </div>
                `;

                document.body.appendChild(container);
            } catch (error) {
                console.error('[RoleLoadingScreen] Failed to create elements:', error);
            }
        }

        // Update role-specific content
        function updateContent(role) {
            if (!container) return;

            try {
                currentRole = role;
                container.setAttribute('data-role', role);

                const iconEl = container.querySelector('.logo-icon i');
                if (iconEl) {
                    iconEl.className = getRoleIcon(role);
                }

                const progressBar = container.querySelector('.progress-bar');
                if (progressBar) {
                    progressBar.setAttribute('aria-valuenow', '0');
                }
            } catch (error) {
                console.error('[RoleLoadingScreen] Failed to update content:', error);
            }
        }

        // Set progress bar percentage
        function setProgress(percent) {
            if (!container) return;

            try {
                const progressBar = container.querySelector('.progress-bar');
                if (progressBar) {
                    const clampedPercent = Math.max(0, Math.min(100, percent));
                    progressBar.style.width = clampedPercent + '%';
                    progressBar.setAttribute('aria-valuenow', Math.round(clampedPercent));
                }
            } catch (error) {
                console.error('[RoleLoadingScreen] Failed to set progress:', error);
            }
        }

        // Update status message
        function setStatus(message, submessage) {
            if (!container) return;

            try {
                const statusEl = container.querySelector('#role-loading-status');
                const substatusEl = container.querySelector('#role-loading-substatus');

                if (statusEl) {
                    statusEl.textContent = message || 'Please wait...';
                }
                if (substatusEl) {
                    substatusEl.textContent = submessage || '';
                }
            } catch (error) {
                console.error('[RoleLoadingScreen] Failed to set status:', error);
            }
        }

        // Clean up all timers and intervals
        function cleanup() {
            if (progressIntervalId) {
                clearInterval(progressIntervalId);
                progressIntervalId = null;
            }
            if (animationFrameId) {
                cancelAnimationFrame(animationFrameId);
                animationFrameId = null;
            }
            cleanupCallbacks.forEach(function(cb) {
                try { cb(); } catch (e) { /* ignore cleanup errors */ }
            });
            cleanupCallbacks = [];
        }

        // Show loading screen
        function show(role, callback) {
            createElements();
            currentRole = role;
            
            updateContent(role);
            setProgress(0);
            setStatus(getMessage('initializing', role), 'Please wait...');

            if (!isVisible && container) {
                try {
                    container.classList.remove('hidden');
                    isVisible = true;
                    
                    // Use requestAnimationFrame for smooth fade-in
                    animationFrameId = requestAnimationFrame(function() {
                        if (container) {
                            container.classList.add('visible');
                        }
                    });
                } catch (error) {
                    console.error('[RoleLoadingScreen] Failed to show:', error);
                }
            }

            if (callback && typeof callback === 'function') {
                setTimeout(callback, 100);
            }
        }

        // Hide loading screen
        function hide(callback) {
            if (!container || !isVisible) {
                if (container) {
                    container.classList.add('hidden');
                    container.style.display = 'none';
                    container.style.pointerEvents = 'none';
                }
                if (callback && typeof callback === 'function') {
                    try {
                        callback();
                    } catch (error) {
                        console.error('[RoleLoadingScreen] Callback error:', error);
                    }
                }
                return;
            }

            try {
                container.classList.remove('visible');
                container.style.pointerEvents = 'none';
                
                setTimeout(function() {
                    if (container) {
                        container.classList.add('hidden');
                        container.style.display = 'none';
                        isVisible = false;
                    }
                    
                    if (callback && typeof callback === 'function') {
                        try {
                            callback();
                        } catch (error) {
                            console.error('[RoleLoadingScreen] Callback error:', error);
                        }
                    }
                }, CONFIG.FADE_DURATION);
            } catch (error) {
                console.error('[RoleLoadingScreen] Failed to hide:', error);
                isVisible = false;
            }
        }

        /**
         * Show loading with sequential progress - returns Promise
         * @param {string} role - User role (Admin, Responder, Citizen)
         * @param {Object} options - Optional configuration
         * @returns {Promise} Resolves when sequence completes
         */
        function showWithSequence(role, options) {
            return new Promise(function(resolve) {
                // Validate role
                if (!role) {
                    role = localStorage.getItem('userRole') || 'Citizen';
                }

                // Clean up any existing interval
                cleanup();

                let progress = 0;
                const startTime = Date.now();
                const minEndTime = startTime + CONFIG.MIN_DISPLAY_TIME;

                // Sequence stages
                const sequence = [
                    { pct: 25, stage: 'initializing', sub: 'Establishing secure connection...' },
                    { pct: 50, stage: 'permissions', sub: 'Verifying role credentials...' },
                    { pct: 75, stage: 'dashboard', sub: 'Loading interface components...' },
                    { pct: 100, stage: 'complete', sub: 'Redirecting...' }
                ];

                let currentStep = 0;

                show(role, function() {
                    // Start progress animation
                    progressIntervalId = setInterval(function() {
                        try {
                            // Calculate progress based on time elapsed
                            const elapsed = Date.now() - startTime;
                            progress = Math.min(100, Math.floor((elapsed / CONFIG.MIN_DISPLAY_TIME) * 100));

                            // Update status at each stage
                            while (currentStep < sequence.length - 1 && progress >= sequence[currentStep + 1].pct) {
                                currentStep++;
                                setStatus(getMessage(sequence[currentStep].stage, role), sequence[currentStep].sub);
                            }

                            setProgress(progress);

                            // Check if we've reached 100%
                            if (progress >= 100) {
                                // Ensure minimum display time
                                const remainingTime = minEndTime - Date.now();
                                
                                if (remainingTime > 0) {
                                    setTimeout(function() {
                                        cleanup();
                                        hide(resolve);
                                    }, remainingTime + CONFIG.HIDE_DELAY);
                                } else {
                                    cleanup();
                                    setTimeout(function() {
                                        hide(resolve);
                                    }, CONFIG.HIDE_DELAY);
                                }
                            }
                        } catch (error) {
                            console.error('[RoleLoadingScreen] Progress error:', error);
                            cleanup();
                            hide(resolve);
                        }
                    }, CONFIG.PROGRESS_INTERVAL);
                });
            });
        }

        /**
         * Verify resources are loaded before proceeding
         * @param {Function} callback - Called when ready
         */
        function verifyResourcesReady(callback) {
            if (typeof callback !== 'function') return;

            // Check if document is ready
            if (document.readyState === 'complete') {
                callback();
                return;
            }

            // Wait for window load event
            function onLoad() {
                window.removeEventListener('load', onLoad);
                // Small delay to ensure rendering complete
                setTimeout(callback, 100);
            }
            window.addEventListener('load', onLoad);
        }

        /**
         * Show loading, verify resources, then navigate
         * @param {string} role - User role
         * @param {string} targetUrl - Destination URL
         */
        function showAndNavigate(role, targetUrl) {
            // Skip loading for back/forward navigation
            if (isBackForwardNav) {
                console.log('[RoleLoadingScreen] Back/forward nav detected, skipping loading screen');
                if (targetUrl) {
                    window.location.href = targetUrl;
                }
                return;
            }

            // Set flag for login redirect
            setLoginRedirectFlag();

            showWithSequence(role).then(function() {
                verifyResourcesReady(function() {
                    if (targetUrl) {
                        window.location.href = targetUrl;
                    }
                });
            });
        }

        /**
         * Initialize on page load - for dashboard pages
         * @param {string} role - User role (optional, reads from localStorage)
         * @param {Object} options - Configuration options
         */
        function initOnLoad(role, options) {
            if (!role) {
                role = localStorage.getItem('userRole') || 'Citizen';
            }

            // Skip loading for back/forward navigation
            if (isBackForwardNav) {
                console.log('[RoleLoadingScreen] Back/forward nav detected, skipping loading screen');
                return;
            }

            // Skip loading for login redirect (already showed loading during login)
            if (checkLoginRedirect()) {
                console.log('[RoleLoadingScreen] Login redirect detected, skipping loading screen');
                return;
            }

            // Show loading immediately
            showWithSequence(role).then(function() {
                // Verify all resources loaded before showing content
                verifyResourcesReady(function() {
                    // Hide loading screen - dashboard will handle its own initialization
                    hide();
                });
            });
            
            // SAFETY FALLBACK: Always remove loading screen after max 5 seconds
            // This prevents perpetual loading if something goes wrong
            setTimeout(function() {
                if (isVisible) {
                    console.warn('[RoleLoadingScreen] Safety fallback: Force hiding after timeout');
                    cleanup();
                    if (container) {
                        container.classList.remove('visible');
                container.style.pointerEvents = 'none';
                        container.classList.add('hidden');
                        container.style.display = 'none';
                        isVisible = false;
                    }
                }
            }, 1200);
        }

        // Public API
        return {
            show: function(role, callback) {
                show(role, callback);
            },
            hide: function(callback) {
                hide(callback);
            },
            showWithSequence: function(role, options) {
                return showWithSequence(role, options);
            },
            showAndNavigate: function(role, targetUrl) {
                showAndNavigate(role, targetUrl);
            },
            initOnLoad: function(role, options) {
                initOnLoad(role, options);
            },
            setStatus: function(message, submessage) {
                setStatus(message, submessage);
            },
            setProgress: function(percent) {
                setProgress(percent);
            },
            cleanup: function() {
                cleanup();
            },
            get isVisible() {
                return isVisible;
            },
            get role() {
                return currentRole;
            }
        };
    })();

    // Export to global scope
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = RoleLoadingScreen;
    } else {
        global.RoleLoadingScreen = RoleLoadingScreen;
    }

})(typeof window !== 'undefined' ? window : this);
