/**
 * Loading Screen Component
 * A visually stunning, modern loading overlay for web applications
 * 
 * Usage:
 *   LoadingScreen.show(message)     - Show with custom message
 *   LoadingScreen.show(message, options) - Show with options
 *   LoadingScreen.hide(callback)    - Hide with optional callback
 *   LoadingScreen.setTheme('light' | 'dark')
 *   LoadingScreen.setLogo(iconHTML)
 */

const LoadingScreen = (function() {
    'use strict';

    const DEFAULTS = {
        duration: 300,
        logo: null,
        theme: 'auto',
        backdropBlur: true,
        showProgress: true,
        progressType: 'spinner'
    };

    let container = null;
    let options = { ...DEFAULTS };
    let isVisible = false;
    let theme = 'dark';

    function createElements() {
        if (container) return;

        container = document.createElement('div');
        container.className = 'ls-container ls-hidden';
        container.setAttribute('aria-hidden', 'true');
        container.setAttribute('role', 'dialog');
        container.setAttribute('aria-label', 'Loading');

        container.innerHTML = `
            <div class="ls-backdrop"></div>
            <div class="ls-particles">
                <span class="ls-particle"></span>
                <span class="ls-particle"></span>
                <span class="ls-particle"></span>
                <span class="ls-particle"></span>
                <span class="ls-particle"></span>
                <span class="ls-particle"></span>
                <span class="ls-particle"></span>
                <span class="ls-particle"></span>
            </div>
            <div class="ls-glow"></div>
            <div class="ls-glow"></div>
            <div class="ls-content">
                <div class="ls-logo-wrapper">
                    <div class="ls-logo">
                        <svg viewBox="0 0 60 60" class="ls-logo-svg">
                            <defs>
                                <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                                    <stop offset="0%" style="stop-color:#6366f1"/>
                                    <stop offset="50%" style="stop-color:#8b5cf6"/>
                                    <stop offset="100%" style="stop-color:#a855f7"/>
                                </linearGradient>
                            </defs>
                            <circle cx="30" cy="30" r="25" fill="none" stroke="url(#logoGradient)" stroke-width="3"/>
                            <path d="M30 15 L30 30 L42 36" fill="none" stroke="url(#logoGradient)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                    </div>
                </div>
                <div class="ls-spinner">
                    <div class="ls-spinner-ring"></div>
                    <div class="ls-spinner-ring"></div>
                    <div class="ls-spinner-ring"></div>
                </div>
                <div class="ls-message"></div>
                <div class="ls-dots">
                    <span></span>
                    <span></span>
                    <span></span>
                </div>
            </div>
        `;

        document.body.appendChild(container);
        applyTheme();
    }

    function applyTheme() {
        const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const effectiveTheme = options.theme === 'auto' ? (systemDark ? 'dark' : 'light') : options.theme;
        theme = effectiveTheme;
        
        if (container) {
            container.classList.remove('ls-theme-light', 'ls-theme-dark');
            container.classList.add(`ls-theme-${effectiveTheme}`);
        }
    }

    function show(message = 'Please wait...', opts = {}) {
        createElements();
        
        const finalOpts = { ...options, ...opts };
        options = { ...options, ...finalOpts };
        
        applyTheme();

        const messageEl = container.querySelector('.ls-message');
        const spinnerEl = container.querySelector('.ls-spinner');
        const dotsEl = container.querySelector('.ls-dots');
        
        messageEl.textContent = message;
        messageEl.classList.toggle('ls-hidden', !finalOpts.showProgress);
        
        spinnerEl.classList.remove('ls-type-spinner', 'ls-type-dots', 'ls-type-bar');
        spinnerEl.classList.add(`ls-type-${finalOpts.progressType}`);
        spinnerEl.classList.toggle('ls-hidden', finalOpts.progressType !== 'spinner');
        
        dotsEl.classList.toggle('ls-hidden', finalOpts.progressType !== 'dots');

        if (finalOpts.backdropBlur) {
            container.classList.add('ls-blur');
        } else {
            container.classList.remove('ls-blur');
        }

        if (!isVisible) {
            container.classList.remove('ls-hidden');
            container.classList.add('ls-visible');
            container.setAttribute('aria-hidden', 'false');
            isVisible = true;
        }

        requestAnimationFrame(() => {
            container.classList.add('ls-fade-in');
        });
    }

    function hide(callback) {
        if (!container || !isVisible) {
            if (callback) callback();
            return;
        }

        container.classList.remove('ls-fade-in');
        container.classList.add('ls-fade-out');
        
        setTimeout(() => {
            container.classList.add('ls-hidden');
            container.classList.remove('ls-visible', 'ls-fade-out');
            container.setAttribute('aria-hidden', 'true');
            isVisible = false;
            
            if (callback) {
                requestAnimationFrame(callback);
            }
        }, options.duration);
    }

    function setTheme(newTheme) {
        options.theme = newTheme;
        applyTheme();
    }

    function setLogo(logoHTML) {
        options.logo = logoHTML;
        if (container) {
            const logoWrapper = container.querySelector('.ls-logo-wrapper');
            if (logoHTML) {
                logoWrapper.innerHTML = `<div class="ls-logo">${logoHTML}</div>`;
            } else {
                logoWrapper.innerHTML = `
                    <div class="ls-logo">
                        <svg viewBox="0 0 60 60" class="ls-logo-svg">
                            <defs>
                                <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                                    <stop offset="0%" style="stop-color:#6366f1"/>
                                    <stop offset="50%" style="stop-color:#8b5cf6"/>
                                    <stop offset="100%" style="stop-color:#a855f7"/>
                                </linearGradient>
                            </defs>
                            <circle cx="30" cy="30" r="25" fill="none" stroke="url(#logoGradient)" stroke-width="3"/>
                            <path d="M30 15 L30 30 L42 36" fill="none" stroke="url(#logoGradient)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                    </div>
                `;
            }
        }
    }

    function setOptions(newOptions) {
        options = { ...options, ...newOptions };
    }

    return {
        show,
        hide,
        setTheme,
        setLogo,
        setOptions,
        get isVisible() {
            return isVisible;
        }
    };
})();

if (typeof module !== 'undefined' && module.exports) {
    module.exports = LoadingScreen;
}