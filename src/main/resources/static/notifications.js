/**
 * ==============================================================================
 * DEDICATED NOTIFICATION SYSTEM CLIENT ENGINE
 * Integrated Disaster Reporting and Emergency Response Coordination System
 * Real backend-to-frontend synchronization, real-time polling, and role isolation
 * ==============================================================================
 */

(function(window) {
    'use strict';

    class NotificationCenter {
        constructor(config) {
            this.role = config.role; // 'Citizen', 'Responder', 'Admin'
            this.defaultCategory = config.defaultCategory || 'ALL';
            this.activeCategory = this.defaultCategory;
            this.notifications = [];
            this.unreadCount = 0;
            this.pollTimer = null;
            this.clockTimer = null;
            this.pollIntervalMs = 6000; // 6 seconds

            this.init();
        }

        init() {
            // Guard role access
            if (window.AuthHelper) {
                const authenticated = window.AuthHelper.requireAuth(this.role);
                if (!authenticated) return;
            }

            this.setupUserUI();
            this.startLiveClock();
            this.bindEvents();
            this.loadNotifications();
            this.startPolling();
        }

        setupUserUI() {
            const userName = localStorage.getItem('userName') || (this.role + ' User');
            const userEmail = localStorage.getItem('userEmail') || (this.role.toLowerCase() + '@disaster.gov.in');
            
            const nameEl = document.getElementById('userNameDisplay');
            if (nameEl) nameEl.textContent = userName;

            const avatarEl = document.getElementById('userAvatarDisplay');
            if (avatarEl) {
                const initials = userName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
                avatarEl.textContent = initials || this.role[0];
            }

            const roleEl = document.getElementById('userRoleDisplay');
            if (roleEl) {
                roleEl.innerHTML = `${this.role} <i class="fa-solid fa-chevron-down" style="font-size:10px; margin-left:4px;"></i>`;
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
            // Filter Pills
            const pills = document.querySelectorAll('.filter-pill');
            pills.forEach(pill => {
                pill.addEventListener('click', (e) => {
                    const targetCategory = pill.getAttribute('data-category') || 'ALL';
                    this.setActiveCategory(targetCategory);
                });
            });

            // Mark All As Read
            const markAllBtn = document.getElementById('btnMarkAllRead');
            if (markAllBtn) {
                markAllBtn.addEventListener('click', () => this.markAllAsRead());
            }

            // Refresh Button
            const refreshBtn = document.getElementById('btnRefreshNotifs');
            if (refreshBtn) {
                refreshBtn.addEventListener('click', () => {
                    this.loadNotifications(true);
                });
            }

            // Close Modal
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

            // Logout
            const logoutLinks = document.querySelectorAll('.action-logout');
            logoutLinks.forEach(link => {
                link.addEventListener('click', (e) => {
                    e.preventDefault();
                    if (window.AuthHelper) window.AuthHelper.logout();
                    else window.location.href = '/index.html';
                });
            });
        }

        setActiveCategory(category) {
            this.activeCategory = category;
            document.querySelectorAll('.filter-pill').forEach(pill => {
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
                const res = await fetch('/api/notifications', {
                    headers: headers
                });

                if (res.status === 401 || res.status === 403) {
                    if (window.AuthHelper) window.AuthHelper.handleAuthFailure(res);
                    return;
                }

                if (!res.ok) {
                    throw new Error(`Failed to load notifications: HTTP ${res.status}`);
                }

                const data = await res.json();
                this.notifications = Array.isArray(data) ? data : [];

                // Fetch unread count
                const countRes = await fetch('/api/notifications/unread-count', { headers });
                if (countRes.ok) {
                    const countData = await countRes.json();
                    this.unreadCount = countData.unreadCount || 0;
                } else {
                    this.unreadCount = this.notifications.filter(n => !n.isRead).length;
                }

                this.updateBadgeCounters();
                this.renderNotificationList();

                if (showFeedback && window.showToast) {
                    window.showToast('Notifications synchronized with command server.', 'info');
                }
            } catch (err) {
                console.error('[NotificationCenter] Sync error:', err);
            }
        }

        startPolling() {
            if (this.pollTimer) clearInterval(this.pollTimer);
            this.pollTimer = setInterval(() => {
                this.loadNotifications(false);
            }, this.pollIntervalMs);
        }

        updateBadgeCounters() {
            // Update sidebar badge
            const sidebarBadge = document.getElementById('sidebarUnreadBadge');
            if (sidebarBadge) {
                if (this.unreadCount > 0) {
                    sidebarBadge.style.display = 'inline-block';
                    sidebarBadge.textContent = this.unreadCount > 99 ? '99+' : this.unreadCount;
                } else {
                    sidebarBadge.style.display = 'none';
                }
            }

            // Update category pills count
            const allCount = this.notifications.length;
            const setPillCount = (cat, count) => {
                const el = document.getElementById(`countPill_${cat}`);
                if (el) el.textContent = count;
            };

            setPillCount('ALL', allCount);

            // Group counters
            const counts = {};
            this.notifications.forEach(n => {
                const cat = (n.category || 'UPDATES').toUpperCase();
                counts[cat] = (counts[cat] || 0) + 1;
            });

            // Set each known role category
            Object.keys(counts).forEach(k => setPillCount(k, counts[k]));
        }

        filterNotifications() {
            if (this.activeCategory === 'ALL') {
                return this.notifications;
            }
            return this.notifications.filter(n => {
                const cat = (n.category || 'UPDATES').toUpperCase();
                return cat === this.activeCategory;
            });
        }

        renderNotificationList() {
            const container = document.getElementById('notificationsContainer');
            if (!container) return;

            const list = this.filterNotifications();

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="notif-empty-state">
                        <i class="fa-solid fa-bell-slash"></i>
                        <h3>No Notifications in this Category</h3>
                        <p>All emergency events, alerts and updates for ${this.role} are currently up to date.</p>
                    </div>
                `;
                return;
            }

            let html = '';
            list.forEach(item => {
                const isUnread = !item.isRead;
                const visual = this.resolveVisuals(item);
                const timeFormatted = this.formatTime(item.createdAt);

                html += `
                    <div class="notif-card ${isUnread ? 'unread' : ''}" data-id="${item.id}">
                        <div class="notif-left" onclick="window.__notifCenter.handleCardClick(${item.id})">
                            <div class="notif-icon-circle ${visual.color}">
                                <i class="${visual.icon}"></i>
                            </div>
                            <div class="notif-content">
                                <div class="notif-title-row">
                                    <span class="notif-title" style="color: ${visual.titleColor};">${this.escapeHtml(item.title)}</span>
                                </div>
                                <div class="notif-desc">${this.escapeHtml(item.message)}</div>
                            </div>
                        </div>
                        <div class="notif-right">
                            ${visual.priorityBadge}
                            <span class="notif-time">${timeFormatted}</span>
                            <button class="btn-card-action btn-delete" title="Delete from notifications" onclick="event.stopPropagation(); window.__notifCenter.deleteNotification(${item.id})">
                                <i class="fa-solid fa-trash-can"></i>
                            </button>
                            <i class="fa-solid fa-chevron-right chevron-icon" onclick="window.__notifCenter.handleCardClick(${item.id})"></i>
                        </div>
                    </div>
                `;
            });

            container.innerHTML = html;
        }

        resolveVisuals(item) {
            const cat = (item.category || '').toUpperCase();
            const prio = (item.priority || '').toUpperCase();
            const title = (item.title || '').toLowerCase();

            let color = 'blue';
            let icon = 'fa-solid fa-circle-info';
            let titleColor = '#ffffff';

            if (cat === 'ALERTS' || cat === 'DISASTERS' || prio === 'CRITICAL' || prio === 'HIGH' || title.includes('alert') || title.includes('flood') || title.includes('fire')) {
                color = 'red';
                icon = 'fa-solid fa-triangle-exclamation';
                titleColor = '#f87171';
            } else if (cat === 'ASSIGNMENTS' || cat === 'TASKS' || title.includes('assignment') || title.includes('rescue')) {
                color = 'orange';
                icon = 'fa-solid fa-clipboard-check';
                titleColor = '#fb923c';
            } else if (cat === 'RESPONSE' || title.includes('deployed') || title.includes('completed') || title.includes('resolved')) {
                color = 'green';
                icon = 'fa-solid fa-circle-check';
                titleColor = '#4ade80';
            } else if (cat === 'SYSTEM' || title.includes('system') || title.includes('maintenance')) {
                color = 'purple';
                icon = 'fa-solid fa-gear';
                titleColor = '#c084fc';
            } else if (cat === 'USERS' || title.includes('user') || title.includes('registered')) {
                color = 'green';
                icon = 'fa-solid fa-user-plus';
                titleColor = '#4ade80';
            } else {
                color = 'blue';
                icon = 'fa-solid fa-circle-info';
                titleColor = '#60a5fa';
            }

            // Priority Badge
            let priorityBadge = '';
            if (prio === 'CRITICAL' || prio === 'HIGH') {
                priorityBadge = `<span class="priority-pill high">High Priority</span>`;
            } else if (prio === 'WARNING' || prio === 'MEDIUM') {
                priorityBadge = `<span class="priority-pill warning">Medium</span>`;
            } else if (cat === 'ASSIGNMENTS' || prio === 'NEW') {
                priorityBadge = `<span class="priority-pill new">New</span>`;
            }

            return { color, icon, titleColor, priorityBadge };
        }

        formatTime(dateStr) {
            if (!dateStr) return 'Recently';
            try {
                const date = new Date(dateStr);
                if (isNaN(date.getTime())) return 'Recently';
                
                let hours = date.getHours();
                const minutes = String(date.getMinutes()).padStart(2, '0');
                const ampm = hours >= 12 ? 'PM' : 'AM';
                hours = hours % 12;
                hours = hours ? hours : 12;
                const hoursStr = String(hours).padStart(2, '0');
                return `${hoursStr}:${minutes} ${ampm}`;
            } catch (e) {
                return 'Recently';
            }
        }

        async handleCardClick(id) {
            const item = this.notifications.find(n => n.id === id);
            if (!item) return;

            // Mark as read immediately on backend
            if (!item.isRead) {
                item.isRead = true;
                this.markAsRead(id);
            }

            // If actionUrl exists, offer navigation or navigate directly
            if (item.actionUrl) {
                window.location.href = item.actionUrl;
                return;
            }

            // Otherwise open modal with details
            this.openDetailModal(item);
        }

        openDetailModal(item) {
            const modalBackdrop = document.getElementById('notifModalBackdrop');
            const titleEl = document.getElementById('modalNotifTitle');
            const bodyEl = document.getElementById('modalNotifBody');
            const actionBtn = document.getElementById('btnModalAction');

            if (!modalBackdrop || !titleEl || !bodyEl) return;

            titleEl.textContent = item.title;
            bodyEl.innerHTML = `
                <div style="margin-bottom:12px; font-size:12px; color:#94a3b8;">
                    <strong>Category:</strong> ${this.escapeHtml(item.category || 'General')} &nbsp;|&nbsp; 
                    <strong>Priority:</strong> ${this.escapeHtml(item.priority || 'Normal')} &nbsp;|&nbsp; 
                    <strong>Received:</strong> ${this.formatTime(item.createdAt)}
                </div>
                <p style="font-size:15px; color:#e2e8f0; line-height:1.6;">${this.escapeHtml(item.message)}</p>
            `;

            if (actionBtn) {
                actionBtn.onclick = () => {
                    modalBackdrop.classList.remove('open');
                    // Route to role's dashboard
                    if (this.role === 'Admin') window.location.href = '/admin-dashboard.html';
                    else if (this.role === 'Responder') window.location.href = '/responder-dashboard.html';
                    else window.location.href = '/citizen-dashboard.html';
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
                this.loadNotifications(false);
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
                    this.loadNotifications(false);
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
                    if (window.showToast) window.showToast('Notification removed from your feed.', 'info');
                    this.notifications = this.notifications.filter(n => n.id !== id);
                    this.updateBadgeCounters();
                    this.renderNotificationList();
                } else {
                    throw new Error('Failed to delete notification');
                }
            } catch (err) {
                console.error('[NotificationCenter] Delete notification failed:', err);
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
