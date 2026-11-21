/**
 * ==============================================================================
 * STANDALONE NOTIFICATION CENTRE CLIENT ENGINE
 * Mobile Notification Panel & Shade Experience
 * Integrated Disaster Reporting and Emergency Response Coordination System
 * 
 * Supports all 3 roles (Admin, Citizen, Responder) with strict authentication
 * and role-based dashboard navigation.
 * ==============================================================================
 */

(function(window) {
    'use strict';

    class NotificationCenter {
        constructor(config) {
            this.role = config.role; // 'Admin', 'Citizen', or 'Responder'
            this.defaultCategory = config.defaultCategory || 'ALL';
            this.activeCategory = this.defaultCategory;
            this.statusFilter = 'all'; // 'all' or 'unread'
            this.searchQuery = '';
            
            this.notifications = [];
            this.unreadCount = 0;
            this.totalCount = 0;
            this.pollTimer = null;
            this.clockTimer = null;
            this.pollIntervalMs = 6000; // 6 seconds

            this.init();
        }

        init() {
            // 1. Strict Role Guarding & Authentication Verification
            if (window.AuthHelper) {
                const token = window.AuthHelper.getToken();
                if (!token) {
                    console.warn('[NotificationCenter] Unauthenticated access blocked. Redirecting to login.');
                    window.location.replace('/index-new-login.html');
                    return;
                }

                const verifiedRole = window.AuthHelper.getRole();
                if (verifiedRole && verifiedRole !== this.role) {
                    console.warn(`[NotificationCenter] Cross-role violation: ${verifiedRole} tried accessing ${this.role} Notification Centre. Redirecting to proper portal...`);
                    const correctUrl = window.AuthHelper.getNotificationUrl(verifiedRole);
                    window.location.replace(correctUrl);
                    return;
                }

                // Verify dashboard button destination strictly routes to user's dashboard
                this.setupDashboardButton(verifiedRole || this.role);
            }

            // 2. Setup UI elements
            this.setupUserUI();
            this.startLiveClock();
            this.bindEvents();
            this.loadNotifications(false);
            this.startPolling();
        }

        setupDashboardButton(userRole) {
            const btn = document.getElementById('btnGoDashboard');
            if (btn) {
                const targetUrl = window.AuthHelper ? window.AuthHelper.getDashboardUrl(userRole) : 
                    (userRole === 'Admin' ? '/admin-dashboard.html' : 
                     userRole === 'Responder' ? '/responder-dashboard.html' : '/citizen-dashboard.html');
                btn.href = targetUrl;
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    window.location.href = targetUrl;
                });
            }
        }

        setupUserUI() {
            const userName = (window.AuthHelper && window.AuthHelper.getName) ? window.AuthHelper.getName() : (localStorage.getItem('userName') || `${this.role} User`);
            const userEmail = (window.AuthHelper && window.AuthHelper.getEmail) ? window.AuthHelper.getEmail() : (localStorage.getItem('userEmail') || `${this.role.toLowerCase()}@disaster.gov.in`);
            
            const nameEl = document.getElementById('userNameDisplay');
            if (nameEl) nameEl.textContent = userName;

            const roleEl = document.getElementById('userRoleDisplay');
            if (roleEl) roleEl.textContent = this.role;

            const avatarEl = document.getElementById('userAvatarDisplay');
            if (avatarEl) {
                const initials = userName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
                avatarEl.textContent = initials || this.role[0];
            }
        }

        startLiveClock() {
            const clockEl = document.getElementById('liveClockDisplay');
            if (!clockEl) return;

            const updateClock = () => {
                const now = new Date();
                const optionsDate = { month: 'short', day: 'numeric', year: 'numeric' };
                const dateStr = now.toLocaleDateString('en-US', optionsDate);
                let hours = now.getHours();
                const minutes = String(now.getMinutes()).padStart(2, '0');
                const ampm = hours >= 12 ? 'PM' : 'AM';
                hours = hours % 12;
                hours = hours ? hours : 12;
                const hoursStr = String(hours).padStart(2, '0');
                clockEl.textContent = `${dateStr} • ${hoursStr}:${minutes} ${ampm}`;
            };

            updateClock();
            this.clockTimer = setInterval(updateClock, 1000);
        }

        bindEvents() {
            // Category Filter Pills
            const pills = document.querySelectorAll('.shade-filter-pill');
            pills.forEach(pill => {
                pill.addEventListener('click', () => {
                    const category = pill.getAttribute('data-category') || 'ALL';
                    this.setActiveCategory(category);
                });
            });

            // Status Filter Toggle Buttons (All vs Unread Only)
            const btnStatusAll = document.getElementById('btnFilterStatusAll');
            const btnStatusUnread = document.getElementById('btnFilterStatusUnread');
            if (btnStatusAll && btnStatusUnread) {
                btnStatusAll.addEventListener('click', () => {
                    this.statusFilter = 'all';
                    btnStatusAll.classList.add('active');
                    btnStatusUnread.classList.remove('active');
                    this.renderNotificationList();
                });
                btnStatusUnread.addEventListener('click', () => {
                    this.statusFilter = 'unread';
                    btnStatusUnread.classList.add('active');
                    btnStatusAll.classList.remove('active');
                    this.renderNotificationList();
                });
            }

            // Real-time Search Input
            const searchInput = document.getElementById('notifSearchInput');
            const clearSearchBtn = document.getElementById('btnClearSearch');
            if (searchInput) {
                searchInput.addEventListener('input', (e) => {
                    this.searchQuery = (e.target.value || '').trim().toLowerCase();
                    if (clearSearchBtn) {
                        clearSearchBtn.style.display = this.searchQuery ? 'block' : 'none';
                    }
                    this.renderNotificationList();
                });
            }
            if (clearSearchBtn && searchInput) {
                clearSearchBtn.addEventListener('click', () => {
                    searchInput.value = '';
                    this.searchQuery = '';
                    clearSearchBtn.style.display = 'none';
                    this.renderNotificationList();
                });
            }

            // Refresh Button
            const refreshBtn = document.getElementById('btnRefreshNotifs');
            if (refreshBtn) {
                refreshBtn.addEventListener('click', () => {
                    this.loadNotifications(true);
                });
            }

            // Mark All as Read Button
            const markAllBtn = document.getElementById('btnMarkAllRead');
            if (markAllBtn) {
                markAllBtn.addEventListener('click', () => {
                    this.markAllAsRead();
                });
            }

            // Close Modal Events
            const closeModalBtn = document.getElementById('btnCloseModal');
            const modalBackdrop = document.getElementById('notifModalBackdrop');
            if (closeModalBtn && modalBackdrop) {
                closeModalBtn.addEventListener('click', () => {
                    modalBackdrop.classList.remove('open');
                });
                modalBackdrop.addEventListener('click', (e) => {
                    if (e.target === modalBackdrop) {
                        modalBackdrop.classList.remove('open');
                    }
                });
            }

            // Sign out
            const logoutLinks = document.querySelectorAll('.action-logout');
            logoutLinks.forEach(link => {
                link.addEventListener('click', (e) => {
                    e.preventDefault();
                    if (window.AuthHelper) {
                        window.AuthHelper.logout();
                    } else {
                        localStorage.clear();
                        sessionStorage.clear();
                        window.location.replace('/index-new-login.html');
                    }
                });
            });
        }

        setActiveCategory(category) {
            this.activeCategory = category;
            document.querySelectorAll('.shade-filter-pill').forEach(pill => {
                if (pill.getAttribute('data-category') === category) {
                    pill.classList.add('active');
                } else {
                    pill.classList.remove('active');
                }
            });
            this.renderNotificationList();
        }

        async loadNotifications(showFeedback = false) {
            try {
                const headers = window.AuthHelper ? window.AuthHelper.getHeaders() : {};
                const res = await fetch('/api/notifications', { headers });

                if (res.status === 401 || res.status === 403) {
                    if (window.AuthHelper && window.AuthHelper.handleAuthFailure) {
                        window.AuthHelper.handleAuthFailure(res);
                    } else {
                        window.location.replace('/index-new-login.html');
                    }
                    return;
                }

                if (!res.ok) {
                    throw new Error(`Failed to load notifications: HTTP ${res.status}`);
                }

                const data = await res.json();
                this.notifications = Array.isArray(data) ? data : [];
                this.totalCount = this.notifications.length;

                // Sync Unread count
                const countRes = await fetch('/api/notifications/unread-count', { headers });
                if (countRes.ok) {
                    const countData = await countRes.json();
                    this.unreadCount = countData.unreadCount !== undefined ? countData.unreadCount : 
                                      (countData.unread !== undefined ? countData.unread : 0);
                } else {
                    this.unreadCount = this.notifications.filter(n => !n.isRead).length;
                }

                this.updateCounters();
                this.renderNotificationList();

                if (showFeedback && window.showToast) {
                    window.showToast('Notifications refreshed and synchronized.', 'info');
                }
            } catch (err) {
                console.error('[NotificationCenter] Sync error:', err);
                if (showFeedback && window.showToast) {
                    window.showToast('Failed to refresh notifications: ' + err.message, 'error');
                }
            }
        }

        startPolling() {
            if (this.pollTimer) clearInterval(this.pollTimer);
            this.pollTimer = setInterval(() => {
                this.loadNotifications(false);
            }, this.pollIntervalMs);
        }

        /**
         * Normalizes raw categories into role-specific filter categories:
         * - Admin: REPORTS, ASSIGNMENTS, BROADCASTS, SYSTEM
         * - Citizen: REPORTS, RESCUE, WARNINGS, MESSAGES
         * - Responder: DISPATCH, STATUS_LOG, HQ_UPDATES, HAZARDS
         */
        normalizeCategory(rawCategory, item) {
            const cat = (rawCategory || '').trim().toUpperCase();
            const relType = ((item && item.relatedIncidentType) || '').toUpperCase();
            const title = ((item && item.title) || '').toLowerCase();
            const msg = ((item && item.message) || '').toLowerCase();

            if (this.role === 'Admin') {
                // 1. Citizen Reports
                if (cat === 'REPORTS' || cat === 'DISASTER' || cat === 'DISASTERS' || relType === 'CITIZEN_REPORT' ||
                    title.includes('report') || title.includes('disaster') || title.includes('incident #')) {
                    return 'REPORTS';
                }
                // 2. Responder Operations & Progress
                if (cat === 'ASSIGNMENTS' || cat === 'ASSIGNMENT' || cat === 'TASK' || cat === 'TASKS' || cat === 'RESPONSE' ||
                    title.includes('responder') || title.includes('dispatched') || title.includes('en route') || 
                    title.includes('arrived') || title.includes('acknowledged') || title.includes('mission') || title.includes('rescue')) {
                    return 'ASSIGNMENTS';
                }
                // 3. Emergency Broadcasts & NDMA Warnings
                if (cat === 'BROADCASTS' || cat === 'BROADCAST' || cat === 'ALERTS' || cat === 'ALERT' ||
                    title.includes('broadcast') || title.includes('ndma') || title.includes('warning') || title.includes('alert')) {
                    return 'BROADCASTS';
                }
                // 4. System & Personnel
                return 'SYSTEM';
            }

            if (this.role === 'Citizen') {
                // 1. My Report Status
                if (cat === 'REPORTS' || cat === 'REPORT' || relType === 'CITIZEN_REPORT' || 
                    title.includes('report submitted') || title.includes('report received') || 
                    title.includes('under review') || title.includes('report verified') || title.includes('report rejected')) {
                    return 'REPORTS';
                }
                // 2. Responder & Rescue Operations
                if (cat === 'RESCUE' || cat === 'RESPONSE' || cat === 'ASSIGNMENT' || 
                    title.includes('rescue team') || title.includes('responder assigned') || 
                    title.includes('acknowledged assignment') || title.includes('en route') || 
                    title.includes('arrived') || title.includes('mission completed') || title.includes('resolved') || title.includes('assistance in progress')) {
                    return 'RESCUE';
                }
                // 3. Disaster Warnings & NDMA Alerts
                if (cat === 'WARNINGS' || cat === 'ALERTS' || cat === 'ALERT' || 
                    title.includes('warning') || title.includes('flood') || title.includes('cyclone') || title.includes('evacuation') || title.includes('alert')) {
                    return 'WARNINGS';
                }
                // 4. Safety Directives & Advisories
                return 'MESSAGES';
            }

            if (this.role === 'Responder') {
                // 1. New Rescue Assignments & Dispatch
                if (cat === 'DISPATCH' || cat === 'ASSIGNMENT' || cat === 'ASSIGNMENTS' || 
                    title.includes('assignment') || title.includes('dispatch') || title.includes('coordinates') || title.includes('location & incident')) {
                    return 'DISPATCH';
                }
                // 2. Mission Progress & Status
                if (cat === 'STATUS_LOG' || cat === 'RESPONSE' || 
                    title.includes('acknowledged') || title.includes('en route') || title.includes('arrived') || title.includes('progress') || title.includes('completed')) {
                    return 'STATUS_LOG';
                }
                // 3. HQ Orders & Central Command Directives
                if (cat === 'HQ_UPDATES' || cat === 'MESSAGES' || cat === 'MESSAGE' ||
                    title.includes('hq') || title.includes('command') || title.includes('instructions') || title.includes('orders') || title.includes('advisory update')) {
                    return 'HQ_UPDATES';
                }
                // 4. Severe Hazards & Weather Warnings
                return 'HAZARDS';
            }

            return 'ALL';
        }

        updateCounters() {
            const unreadDisplay = document.getElementById('unreadCountDisplay');
            if (unreadDisplay) {
                unreadDisplay.innerHTML = `<span class="unread-highlight">${this.unreadCount} unread</span>`;
            }

            const totalDisplay = document.getElementById('totalCountDisplay');
            if (totalDisplay) {
                totalDisplay.textContent = `${this.totalCount} total`;
            }

            // Category Counters dynamically matching pills on active page
            const pills = document.querySelectorAll('.shade-filter-pill');
            const counts = { ALL: this.notifications.length };

            pills.forEach(p => {
                const cat = p.getAttribute('data-category');
                if (cat && cat !== 'ALL') counts[cat] = 0;
            });

            this.notifications.forEach(n => {
                const cat = this.normalizeCategory(n.category, n);
                if (counts[cat] !== undefined) {
                    counts[cat]++;
                }
            });

            pills.forEach(p => {
                const cat = p.getAttribute('data-category');
                const badge = p.querySelector('.filter-count-badge') || p.querySelector('.pill-count') || document.getElementById(`countPill_${cat}`);
                if (badge) badge.textContent = counts[cat] !== undefined ? counts[cat] : 0;
            });
        }

        filterNotifications() {
            return this.notifications.filter(item => {
                // Category Filter
                if (this.activeCategory !== 'ALL') {
                    const itemCat = this.normalizeCategory(item.category, item);
                    if (itemCat !== this.activeCategory) return false;
                }

                // Status Filter
                if (this.statusFilter === 'unread' && item.isRead) {
                    return false;
                }

                // Search Query Filter
                if (this.searchQuery) {
                    const titleMatch = (item.title || '').toLowerCase().includes(this.searchQuery);
                    const msgMatch = (item.message || '').toLowerCase().includes(this.searchQuery);
                    if (!titleMatch && !msgMatch) return false;
                }

                return true;
            });
        }

        renderNotificationList() {
            const container = document.getElementById('notificationsContainer');
            if (!container) return;

            const list = this.filterNotifications();

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="notif-empty-state">
                        <div class="notif-empty-icon">
                            <i class="fa-solid fa-bell-slash"></i>
                        </div>
                        <h3>No new notifications</h3>
                        <p>No disaster notifications available in this category. Real-time emergency broadcasts and operational dispatches will appear here automatically.</p>
                    </div>
                `;
                return;
            }

            let html = '';
            list.forEach(item => {
                const isUnread = !item.isRead;
                const visual = this.resolveVisuals(item);
                const timeStr = this.formatTime(item.createdAt);
                const categoryLabel = this.resolveCategoryLabel(item);

                html += `
                    <div class="notif-card card-tint-${visual.color} ${isUnread ? 'unread' : ''}" data-id="${item.id}" onclick="window.__notifCenter.handleCardClick(${item.id})">
                        <div class="notif-icon-circle ${visual.color}">
                            <i class="${visual.icon}"></i>
                        </div>
                        
                        <div class="notif-content-block">
                            <div class="notif-header-line">
                                ${isUnread ? '<span class="notif-unread-dot" title="Unread notification"></span>' : ''}
                                <span class="notif-title">${this.escapeHtml(item.title)}</span>
                                ${visual.priorityBadge}
                                <span class="notif-category-chip">${categoryLabel}</span>
                            </div>
                            
                            <div class="notif-desc">${this.escapeHtml(item.message)}</div>
                            
                            <div class="notif-footer-line">
                                <span class="notif-time"><i class="fa-regular fa-clock"></i> ${timeStr}</span>
                                
                                <div class="notif-actions-group" onclick="event.stopPropagation();">
                                    <button class="btn-card-quick btn-read-toggle" title="${isUnread ? 'Mark as Read' : 'Already Read'}" onclick="window.__notifCenter.markAsRead(${item.id})">
                                        <i class="fa-solid ${isUnread ? 'fa-check' : 'fa-check-double'}" style="${isUnread ? '' : 'color:#38bdf8;'}"></i>
                                    </button>
                                    <button class="btn-card-quick btn-delete" title="Delete notification" onclick="window.__notifCenter.deleteNotification(${item.id})">
                                        <i class="fa-solid fa-trash-can"></i>
                                    </button>
                                    <button class="btn-card-quick btn-open" title="View details" onclick="window.__notifCenter.openDetailModalById(${item.id})">
                                        <i class="fa-solid fa-arrow-up-right-from-square"></i>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            });

            container.innerHTML = html;
        }

        resolveCategoryLabel(item) {
            const cat = this.normalizeCategory(item.category, item);
            switch (cat) {
                case 'ALERTS': return 'Alert';
                case 'RESPONDER': return 'Responder Update';
                case 'MESSAGES': return 'Message';
                default: return 'Update';
            }
        }

        resolveVisuals(item) {
            const cat = this.normalizeCategory(item.category, item);
            const prio = (item.priority || '').toUpperCase();
            const title = (item.title || '').toLowerCase();

            let color = 'blue';
            let icon = 'fa-solid fa-circle-info';

            if (cat === 'ALERTS' || prio === 'CRITICAL' || prio === 'HIGH' || title.includes('alert') || title.includes('flood') || title.includes('fire')) {
                color = 'red';
                icon = 'fa-solid fa-triangle-exclamation';
            } else if (cat === 'RESPONDER') {
                color = 'orange';
                icon = 'fa-solid fa-truck-medical';
            } else if (cat === 'MESSAGES') {
                color = 'cyan';
                icon = 'fa-solid fa-comments';
            } else {
                color = 'green';
                icon = 'fa-solid fa-circle-check';
            }

            // Priority Badge
            let priorityBadge = '';
            if (prio === 'CRITICAL') {
                priorityBadge = `<span class="priority-pill critical"><i class="fa-solid fa-bolt"></i> Critical</span>`;
            } else if (prio === 'HIGH') {
                priorityBadge = `<span class="priority-pill high">High</span>`;
            } else if (prio === 'MEDIUM' || prio === 'WARNING') {
                priorityBadge = `<span class="priority-pill medium">Medium</span>`;
            } else {
                priorityBadge = `<span class="priority-pill low">Info</span>`;
            }

            return { color, icon, priorityBadge };
        }

        formatTime(dateStr) {
            if (!dateStr) return 'Recently';
            try {
                const date = new Date(dateStr);
                if (isNaN(date.getTime())) return 'Recently';

                // Format Indian DateTime: DD MMM YYYY, HH:MM AM/PM
                const options = { 
                    timeZone: 'Asia/Kolkata', 
                    day: 'numeric', 
                    month: 'short', 
                    year: 'numeric',
                    hour: '2-digit', 
                    minute: '2-digit', 
                    hour12: true 
                };
                return date.toLocaleString('en-IN', options);
            } catch (e) {
                return 'Recently';
            }
        }

        resolveTargetUrl(item) {
            if (!item) {
                return window.AuthHelper ? window.AuthHelper.getDashboardUrl(this.role) : '/index-new-login.html';
            }

            if (item.actionUrl && (item.actionUrl.includes('?') || item.actionUrl.includes('#'))) {
                return item.actionUrl;
            }

            if (this.role === 'Admin') {
                return '/admin-dashboard.html';
            }
            if (this.role === 'Responder') {
                return '/responder-dashboard.html';
            }
            return '/citizen-dashboard.html';
        }

        async handleCardClick(id) {
            const item = this.notifications.find(n => n.id === id);
            if (!item) return;

            if (!item.isRead) {
                item.isRead = true;
                this.markAsRead(id);
            }

            this.openDetailModal(item);
        }

        openDetailModalById(id) {
            const item = this.notifications.find(n => n.id === id);
            if (item) this.openDetailModal(item);
        }

        openDetailModal(item) {
            const modalBackdrop = document.getElementById('notifModalBackdrop');
            const titleEl = document.getElementById('modalNotifTitle');
            const bodyEl = document.getElementById('modalNotifBody');
            const actionBtn = document.getElementById('btnModalAction');

            if (!modalBackdrop || !titleEl || !bodyEl) return;

            titleEl.textContent = item.title;
            const categoryLabel = this.resolveCategoryLabel(item);
            const timeFormatted = this.formatTime(item.createdAt);

            bodyEl.innerHTML = `
                <div class="modal-metadata-grid">
                    <div class="meta-chip">
                        <span class="meta-chip-label">Category</span>
                        <span class="meta-chip-value">${categoryLabel}</span>
                    </div>
                    <div class="meta-chip">
                        <span class="meta-chip-label">Priority</span>
                        <span class="meta-chip-value">${this.escapeHtml(item.priority || 'Normal')}</span>
                    </div>
                    <div class="meta-chip">
                        <span class="meta-chip-label">Recipient</span>
                        <span class="meta-chip-value">${this.escapeHtml(item.recipientEmail || this.role)}</span>
                    </div>
                    <div class="meta-chip">
                        <span class="meta-chip-label">Timestamp</span>
                        <span class="meta-chip-value">${timeFormatted}</span>
                    </div>
                </div>
                <div class="modal-message-text">
                    ${this.escapeHtml(item.message)}
                </div>
            `;

            if (actionBtn) {
                const targetUrl = this.resolveTargetUrl(item);
                actionBtn.onclick = () => {
                    modalBackdrop.classList.remove('open');
                    window.location.href = targetUrl;
                };
            }

            modalBackdrop.classList.add('open');
        }

        async markAsRead(id) {
            try {
                const headers = window.AuthHelper ? window.AuthHelper.getHeaders() : {};
                await fetch(`/api/notifications/${id}/read`, {
                    method: 'PUT',
                    headers: headers
                });
                const notif = this.notifications.find(n => n.id === id);
                if (notif) notif.isRead = true;
                this.unreadCount = Math.max(0, this.unreadCount - 1);
                this.updateCounters();
                this.renderNotificationList();
            } catch (err) {
                console.error('[NotificationCenter] Mark read failed:', err);
            }
        }

        async markAllAsRead() {
            try {
                const headers = window.AuthHelper ? window.AuthHelper.getHeaders() : {};
                const res = await fetch('/api/notifications/read-all', {
                    method: 'PUT',
                    headers: headers
                });

                if (res.ok) {
                    if (window.showToast) window.showToast('All notifications marked as read.', 'success');
                    this.notifications.forEach(n => n.isRead = true);
                    this.unreadCount = 0;
                    this.updateCounters();
                    this.renderNotificationList();
                }
            } catch (err) {
                console.error('[NotificationCenter] Mark all read failed:', err);
            }
        }

        async deleteNotification(id) {
            try {
                const headers = window.AuthHelper ? window.AuthHelper.getHeaders() : {};
                const res = await fetch(`/api/notifications/${id}`, {
                    method: 'DELETE',
                    headers: headers
                });

                if (res.ok) {
                    if (window.showToast) window.showToast('Notification deleted from your feed.', 'info');
                    const wasUnread = this.notifications.some(n => n.id === id && !n.isRead);
                    this.notifications = this.notifications.filter(n => n.id !== id);
                    if (wasUnread) this.unreadCount = Math.max(0, this.unreadCount - 1);
                    this.totalCount = this.notifications.length;
                    this.updateCounters();
                    this.renderNotificationList();
                } else {
                    throw new Error('Deletion failed on server');
                }
            } catch (err) {
                console.error('[NotificationCenter] Delete failed:', err);
                if (window.showToast) window.showToast('Could not delete notification.', 'error');
            }
        }

        escapeHtml(text) {
            if (!text) return '';
            return String(text)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }
    }

    window.NotificationCenter = NotificationCenter;
})(window);
