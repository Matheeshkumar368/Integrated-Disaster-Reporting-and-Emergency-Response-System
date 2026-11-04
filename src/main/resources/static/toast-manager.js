/**
 * ==============================================================================
 * TACTICAL TOAST MANAGER & CUSTOM DIALOGS
 * Disaster Management & Emergency Response System
 * 
 * Complete replacement for native browser alert(), confirm(), and prompt()
 * with responsive toast notifications, accessible modals, and visual lightbox.
 * ==============================================================================
 */

(function () {
    'use strict';

    // Auto-inject CSS stylesheet if not present
    if (!document.getElementById('ccToastStyles')) {
        const link = document.createElement('link');
        link.id = 'ccToastStyles';
        link.rel = 'stylesheet';
        link.href = '/toast-manager.css?v=2';
        document.head.appendChild(link);
    }

    // Active toast tracking for duplicate prevention
    const activeToastMap = new Map();

    /**
     * Get or create container for toasts
     */
    function getToastContainer() {
        let container = document.getElementById('ccToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'ccToastContainer';
            container.setAttribute('aria-live', 'polite');
            container.setAttribute('role', 'region');
            container.setAttribute('aria-label', 'Notifications');
            document.body.appendChild(container);
        }
        return container;
    }

    /**
     * Main Toast Notification Display
     * @param {string} message - Notification text
     * @param {'success'|'error'|'warning'|'info'} type - Toast severity
     * @param {number} duration - Auto-dismiss timeout in ms (default: 3500)
     */
    function showToast(message, type = 'info', duration = 3500) {
        if (!message || typeof message !== 'string') {
            message = String(message || '');
        }

        const validTypes = ['success', 'error', 'warning', 'info'];
        const normalizedType = validTypes.includes(type) ? type : 'info';
        const msgKey = `${normalizedType}:${message.trim()}`;

        // Duplicate prevention: if duplicate is already active, shake and refresh timer
        if (activeToastMap.has(msgKey)) {
            const existing = activeToastMap.get(msgKey);
            existing.classList.remove('shake');
            void existing.offsetWidth; // Force CSS reflow
            existing.classList.add('shake');

            if (existing._dismissTimer) clearTimeout(existing._dismissTimer);
            existing._dismissTimer = setTimeout(() => dismissToast(existing, msgKey), duration);
            return existing;
        }

        const container = getToastContainer();
        const toast = document.createElement('div');
        toast.className = `cc-toast ${normalizedType}`;
        toast.setAttribute('role', normalizedType === 'error' ? 'alert' : 'status');

        let iconClass = 'fa-circle-info';
        if (normalizedType === 'success') iconClass = 'fa-circle-check';
        else if (normalizedType === 'error') iconClass = 'fa-circle-xmark';
        else if (normalizedType === 'warning') iconClass = 'fa-triangle-exclamation';

        toast.innerHTML = `
            <i class="fas ${iconClass} toast-icon" aria-hidden="true"></i>
            <div class="toast-body">${escapeHtml(message)}</div>
            <button type="button" class="toast-close-btn" aria-label="Close notification">&times;</button>
        `;

        const closeBtn = toast.querySelector('.toast-close-btn');
        closeBtn.addEventListener('click', () => dismissToast(toast, msgKey));

        container.appendChild(toast);
        activeToastMap.set(msgKey, toast);

        // Animate entrance
        requestAnimationFrame(() => {
            toast.classList.add('active');
        });

        // Set dismissal timer
        toast._dismissTimer = setTimeout(() => {
            dismissToast(toast, msgKey);
        }, duration);

        return toast;
    }

    function dismissToast(toast, key) {
        if (!toast || toast.classList.contains('exiting')) return;
        if (toast._dismissTimer) clearTimeout(toast._dismissTimer);

        toast.classList.remove('active');
        toast.classList.add('exiting');

        setTimeout(() => {
            if (toast.parentElement) toast.parentElement.removeChild(toast);
            if (key) activeToastMap.delete(key);
        }, 260);
    }

    // Convenience helpers
    showToast.success = (msg, dur) => showToast(msg, 'success', dur);
    showToast.error = (msg, dur) => showToast(msg, 'error', dur);
    showToast.warning = (msg, dur) => showToast(msg, 'warning', dur);
    showToast.info = (msg, dur) => showToast(msg, 'info', dur);

    /**
     * Custom Confirmation Modal (Replaces native confirm())
     * @param {Object} options
     * @returns {Promise<boolean>}
     */
    function showConfirm(options = {}) {
        return new Promise((resolve) => {
            const title = options.title || 'Confirm Action';
            const message = options.message || 'Are you sure you wish to proceed?';
            const confirmText = options.confirmText || 'Confirm';
            const cancelText = options.cancelText || 'Cancel';
            const type = options.type || 'warning'; // 'warning', 'danger', 'info'

            const backdrop = document.createElement('div');
            backdrop.className = 'cc-dialog-backdrop';
            backdrop.setAttribute('role', 'dialog');
            backdrop.setAttribute('aria-modal', 'true');

            let iconClass = 'fa-triangle-exclamation';
            if (type === 'danger') iconClass = 'fa-trash-can';
            else if (type === 'info') iconClass = 'fa-circle-info';

            const confirmBtnClass = type === 'danger' ? 'cc-dialog-btn-danger' : 'cc-dialog-btn-confirm';

            backdrop.innerHTML = `
                <div class="cc-dialog-card">
                    <div class="cc-dialog-header">
                        <div class="cc-dialog-icon ${type}">
                            <i class="fas ${iconClass}"></i>
                        </div>
                        <h3 class="cc-dialog-title">${escapeHtml(title)}</h3>
                    </div>
                    <div class="cc-dialog-message">${escapeHtml(message)}</div>
                    <div class="cc-dialog-actions">
                        <button type="button" class="cc-dialog-btn cc-dialog-btn-cancel" id="ccDialogCancel">${escapeHtml(cancelText)}</button>
                        <button type="button" class="cc-dialog-btn ${confirmBtnClass}" id="ccDialogConfirm">${escapeHtml(confirmText)}</button>
                    </div>
                </div>
            `;

            document.body.appendChild(backdrop);
            requestAnimationFrame(() => backdrop.classList.add('active'));

            const cancelBtn = backdrop.querySelector('#ccDialogCancel');
            const confirmBtn = backdrop.querySelector('#ccDialogConfirm');
            confirmBtn.focus();

            function cleanup(result) {
                backdrop.classList.remove('active');
                setTimeout(() => {
                    if (backdrop.parentElement) backdrop.parentElement.removeChild(backdrop);
                    document.removeEventListener('keydown', handleKey);
                    resolve(result);
                }, 200);
            }

            function handleKey(e) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    cleanup(false);
                } else if (e.key === 'Enter') {
                    e.preventDefault();
                    cleanup(true);
                }
            }

            document.addEventListener('keydown', handleKey);
            cancelBtn.addEventListener('click', () => cleanup(false));
            confirmBtn.addEventListener('click', () => cleanup(true));
        });
    }

    /**
     * Custom Prompt Input Modal (Replaces native prompt())
     * @param {Object} options
     * @returns {Promise<string|null>}
     */
    function showPrompt(options = {}) {
        return new Promise((resolve) => {
            const title = options.title || 'Required Input';
            const message = options.message || 'Please provide details below:';
            const placeholder = options.placeholder || 'Type here...';
            const defaultValue = options.defaultValue || '';
            const confirmText = options.confirmText || 'Submit';
            const cancelText = options.cancelText || 'Cancel';
            const isPassword = options.isPassword || false;

            const backdrop = document.createElement('div');
            backdrop.className = 'cc-dialog-backdrop';
            backdrop.setAttribute('role', 'dialog');
            backdrop.setAttribute('aria-modal', 'true');

            backdrop.innerHTML = `
                <div class="cc-dialog-card">
                    <div class="cc-dialog-header">
                        <div class="cc-dialog-icon info">
                            <i class="fas fa-pen-to-square"></i>
                        </div>
                        <h3 class="cc-dialog-title">${escapeHtml(title)}</h3>
                    </div>
                    <div class="cc-dialog-message">${escapeHtml(message)}</div>
                    <input type="${isPassword ? 'password' : 'text'}" class="cc-dialog-input" id="ccDialogPromptInput" 
                           placeholder="${escapeHtml(placeholder)}" value="${escapeHtml(defaultValue)}" />
                    <div class="cc-dialog-actions">
                        <button type="button" class="cc-dialog-btn cc-dialog-btn-cancel" id="ccPromptCancel">${escapeHtml(cancelText)}</button>
                        <button type="button" class="cc-dialog-btn cc-dialog-btn-confirm" id="ccPromptConfirm">${escapeHtml(confirmText)}</button>
                    </div>
                </div>
            `;

            document.body.appendChild(backdrop);
            requestAnimationFrame(() => backdrop.classList.add('active'));

            const input = backdrop.querySelector('#ccDialogPromptInput');
            const cancelBtn = backdrop.querySelector('#ccPromptCancel');
            const confirmBtn = backdrop.querySelector('#ccPromptConfirm');
            input.focus();
            input.select();

            function cleanup(result) {
                backdrop.classList.remove('active');
                setTimeout(() => {
                    if (backdrop.parentElement) backdrop.parentElement.removeChild(backdrop);
                    document.removeEventListener('keydown', handleKey);
                    resolve(result);
                }, 200);
            }

            function handleKey(e) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    cleanup(null);
                } else if (e.key === 'Enter') {
                    e.preventDefault();
                    cleanup(input.value);
                }
            }

            document.addEventListener('keydown', handleKey);
            cancelBtn.addEventListener('click', () => cleanup(null));
            confirmBtn.addEventListener('click', () => cleanup(input.value));
        });
    }

    /**
     * Visual Media Lightbox (Click-to-Preview for Images & Videos)
     * @param {string} src - Image or Video URL
     * @param {string} caption - Media description or title
     * @param {'PHOTO'|'VIDEO'} mediaType - Media classification
     */
    function openMediaLightbox(src, caption = '', mediaType = 'PHOTO') {
        if (!src) return;

        let backdrop = document.getElementById('ccMediaLightbox');
        if (!backdrop) {
            backdrop = document.createElement('div');
            backdrop.id = 'ccMediaLightbox';
            backdrop.className = 'cc-lightbox-backdrop';
            document.body.appendChild(backdrop);
        }

        const isVideo = mediaType === 'VIDEO' || src.endsWith('.mp4') || src.endsWith('.webm');

        backdrop.innerHTML = `
            <div class="cc-lightbox-container">
                <button type="button" class="cc-lightbox-close" id="ccLightboxClose" aria-label="Close Lightbox">&times;</button>
                ${isVideo ? `
                    <video src="${src}" controls autoplay class="cc-lightbox-video" controlsList="nodownload"></video>
                ` : `
                    <img src="${src}" alt="Damage Evidence Preview" class="cc-lightbox-img" />
                `}
                ${caption ? `<div class="cc-lightbox-caption">${escapeHtml(caption)}</div>` : ''}
            </div>
        `;

        requestAnimationFrame(() => backdrop.classList.add('active'));

        function closeLightbox() {
            backdrop.classList.remove('active');
            setTimeout(() => {
                backdrop.innerHTML = '';
                document.removeEventListener('keydown', handleKey);
            }, 250);
        }

        function handleKey(e) {
            if (e.key === 'Escape') closeLightbox();
        }

        document.addEventListener('keydown', handleKey);
        backdrop.addEventListener('click', (e) => {
            if (e.target === backdrop || e.target.id === 'ccLightboxClose') {
                closeLightbox();
            }
        });
    }

    /**
     * Image Error Handler for Evidence Cards
     */
    function handleEvidenceImageError(imgEl, originalUrl) {
        if (!imgEl || !imgEl.parentElement) return;
        const parent = imgEl.parentElement;
        
        const errorContainer = document.createElement('div');
        errorContainer.className = 'evidence-error-state';
        errorContainer.innerHTML = `
            <i class="fas fa-triangle-exclamation" style="color:#f59e0b;font-size:1.4rem;margin-bottom:6px;display:block;"></i>
            <span style="font-size:0.82rem;color:#cbd5e1;display:block;">Unable to load evidence</span>
            <button type="button" class="evidence-retry-btn" onclick="window.retryEvidenceImage(this, '${escapeHtml(originalUrl)}')">
                <i class="fas fa-rotate-right"></i> Retry
            </button>
        `;

        imgEl.style.display = 'none';
        parent.appendChild(errorContainer);
    }

    function retryEvidenceImage(btnEl, url) {
        const errorContainer = btnEl.closest('.evidence-error-state');
        if (!errorContainer) return;
        const parent = errorContainer.parentElement;
        const img = parent.querySelector('img');

        if (img) {
            errorContainer.remove();
            img.style.display = '';
            // Add cache-busting timestamp on retry
            const separator = url.includes('?') ? '&' : '?';
            img.src = `${url}${separator}_t=${Date.now()}`;
        }
    }

    function escapeHtml(text) {
        if (!text) return '';
        const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        return String(text).replace(/[&<>"']/g, m => map[m]);
    }

    // Failsafe replacement for native alert()
    window.alert = function (message) {
        showToast(message, 'info');
    };

    // Attach to global window
    window.showToast = showToast;
    window.showConfirm = showConfirm;
    window.showPrompt = showPrompt;
    window.openMediaLightbox = openMediaLightbox;
    window.handleEvidenceImageError = handleEvidenceImageError;
    window.retryEvidenceImage = retryEvidenceImage;

    // Harmonize with CommandCenter singleton if loaded
    if (window.CommandCenter) {
        window.CommandCenter.showToast = showToast;
    }
})();

