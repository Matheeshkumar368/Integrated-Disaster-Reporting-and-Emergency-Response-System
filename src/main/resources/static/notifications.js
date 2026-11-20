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
            // Guard role access and prevent cross-role contamination
            if (window.AuthHelper) {
                const token = window.AuthHelper.getToken();
                if (!token) {
                    window.location.href = '/index.html';
                    return;
                }
                const actualRole = window.AuthHelper.getRole();
                if (actualRole && actualRole !== this.role) {
                    console.warn(`[NotificationCenter] Cross-role access blocked: ${actualRole} attempted accessing ${this.role} notifications. Redirecting to appropriate center...`);
                    const target = window.AuthHelper.getNotificationUrl(actualRole);
                    window.location.replace(target);
                    return;
                }
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
                    this.unreadCount = countData.unreadCount !== undefined ? countData.unreadCount : (countData.unread !== undefined ? countData.unread : 0);
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

        normalizeCategory(rawCategory, item) {
            const cat = (rawCategory || '').trim().toUpperCase();
            const relType = ((item && item.relatedIncidentType) || '').toUpperCase();
            const title = ((item && item.title) || '').toLowerCase();
            const msg = ((item && item.message) || '').toLowerCase();

            if (this.role === 'Admin') {
                if (cat === 'RESPONDERS' || cat === 'RESPONDER' || title.includes('responder') || title.includes('rescue team') || title.includes('assigned to') || title.includes('acknowledged by')) {
                    return 'RESPONDERS';
                }
                if (cat === 'DISASTERS' || cat === 'DISASTER' || relType === 'CITIZEN_REPORT' || relType === 'REPORT' || title.includes('disaster') || title.includes('distress') || title.includes('incident')) {
                    return 'DISASTERS';
                }
                if (cat === 'USERS' || cat === 'USER' || title.includes('user') || title.includes('registered')) {
                    return 'USERS';
                }
                return 'SYSTEM';
            }

            if (this.role === 'Responder') {
                if (cat === 'ASSIGNMENTS' || cat === 'ASSIGNMENT' || cat === 'TASKS' || cat === 'TASK' || relType === 'TASK' || relType === 'RESCUE_TASK' || title.includes('assignment') || title.includes('mission') || title.includes('rescue')) {
                    return 'ASSIGNMENTS';
                }
                if (cat === 'ALERTS' || cat === 'ALERT' || relType === 'ALERT' || title.includes('alert') || title.includes('warning')) {
                    return 'ALERTS';
                }
                if (cat === 'MESSAGES' || cat === 'MESSAGE' || title.includes('message') || title.includes('broadcast')) {
                    return 'MESSAGES';
                }
                return 'UPDATES';
            }

            // Citizen
            if (cat === 'ALERTS' || cat === 'ALERT' || cat === 'DISASTERS' || cat === 'DISASTER' || title.includes('alert') || title.includes('warning')) {
                return 'ALERTS';
            }
            if (cat === 'RESPONSE' || cat === 'RESPONSES' || title.includes('rescue team') || title.includes('deployed') || title.includes('en route') || title.includes('arrived') || title.includes('resolved') || title.includes('responder')) {
                return 'RESPONSE';
            }
            if (cat === 'MESSAGES' || cat === 'MESSAGE' || title.includes('message')) {
                return 'MESSAGES';
            }
            return 'UPDATES';
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

            // Group counters using normalized categories
            const counts = {};
            this.notifications.forEach(n => {
                const cat = this.normalizeCategory(n.category, n);
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
                const cat = this.normalizeCategory(n.category, n);
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
                    <div class="notif-card card-tint-${visual.color} ${isUnread ? 'unread' : ''}" data-id="${item.id}">
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
                            <i class="fa-solid fa-chevron-right chevron-icon" title="Go to section" onclick="event.stopPropagation(); window.location.href=window.__notifCenter.resolveTargetUrl(window.__notifCenter.notifications.find(n=>n.id===${item.id}))"></i>
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
            } else if (cat === 'MESSAGES' || title.includes('message')) {
                color = 'blue';
                icon = 'fa-solid fa-comments';
                titleColor = '#60a5fa';
            } else if (cat === 'RESPONDERS' || title.includes('responder')) {
                color = 'orange';
                icon = 'fa-solid fa-truck-medical';
                titleColor = '#fb923c';
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

        resolveTargetUrl(item) {
            if (!item) {
                return this.role === 'Admin' ? '/admin-dashboard.html' :
                       this.role === 'Responder' ? '/responder-dashboard.html' :
                       '/citizen-dashboard.html';
            }

            // If actionUrl already contains specific query parameters or hash, use it
            if (item.actionUrl && (item.actionUrl.includes('?') || item.actionUrl.includes('#'))) {
                return item.actionUrl;
            }

            if (this.role === 'Citizen') {
                const cat = (item.category || '').toUpperCase();
                const relType = (item.relatedIncidentType || '').toUpperCase();
                const title = (item.title || '').toLowerCase();
                const msg = (item.message || '').toLowerCase();

                // Incident/Disaster Report status updates & deployments -> My Reports
                if (
                    relType === 'CITIZEN_REPORT' ||
                    relType === 'REPORT' ||
                    relType === 'TASK' ||
                    cat === 'RESPONSE' ||
                    cat === 'UPDATES' ||
                    title.includes('report') ||
                    title.includes('incident') ||
                    title.includes('rescue team') ||
                    title.includes('mission') ||
                    title.includes('deployed') ||
                    title.includes('acknowledged') ||
                    title.includes('resolved')
                ) {
                    if (item.relatedIncidentId) {
                        return `/citizen-dashboard.html?my-reports&reportId=${item.relatedIncidentId}`;
                    }
                    return '/citizen-dashboard.html?my-reports';
                }

                // Official alerts & disaster warnings -> Alerts tab
                if (
                    relType === 'ALERT' ||
                    cat === 'ALERTS' ||
                    cat === 'ALERT' ||
                    cat === 'DISASTERS' ||
                    title.includes('alert') ||
                    title.includes('warning') ||
                    title.includes('cyclone') ||
                    title.includes('flood')
                ) {
                    return '/citizen-dashboard.html?alerts';
                }

                // Prompt to file report
                if (title.includes('submit report') || msg.includes('file a report')) {
                    return '/citizen-dashboard.html?report';
                }

                return '/citizen-dashboard.html';
            }

            if (this.role === 'Admin') {
                const cat = (item.category || '').toUpperCase();
                const relType = (item.relatedIncidentType || '').toUpperCase();
                const title = (item.title || '').toLowerCase();

                // Citizen distress reports and disaster verification
                if (
                    relType === 'CITIZEN_REPORT' ||
                    relType === 'REPORT' ||
                    cat === 'DISASTERS' ||
                    cat === 'DISASTER' ||
                    title.includes('disaster') ||
                    title.includes('distress') ||
                    title.includes('pending')
                ) {
                    if (item.relatedIncidentId) {
                        return `/admin-dashboard.html?disasters&reportId=${item.relatedIncidentId}`;
                    }
                    return '/admin-dashboard.html?disasters';
                }

                // Rescue tasks and missions
                if (
                    relType === 'TASK' ||
                    relType === 'RESCUE_TASK' ||
                    cat === 'TASKS' ||
                    cat === 'TASK' ||
                    title.includes('task') ||
                    title.includes('mission') ||
                    title.includes('assignment')
                ) {
                    if (item.relatedIncidentId) {
                        return `/admin-dashboard.html?tasks&taskId=${item.relatedIncidentId}`;
                    }
                    return '/admin-dashboard.html?tasks';
                }

                // Broadcast alerts
                if (
                    relType === 'ALERT' ||
                    relType === 'ALERT_BROADCAST' ||
                    cat === 'ALERTS' ||
                    cat === 'ALERT' ||
                    title.includes('alert') ||
                    title.includes('broadcast') ||
                    title.includes('warning')
                ) {
                    if (item.relatedIncidentId) {
                        return `/admin-dashboard.html?alerts&alertId=${item.relatedIncidentId}`;
                    }
                    return '/admin-dashboard.html?alerts';
                }

                // User management
                if (cat === 'USERS' || cat === 'USER' || title.includes('user') || title.includes('registered')) {
                    return '/admin-users.html';
                }

                return '/admin-dashboard.html';
            }

            if (this.role === 'Responder') {
                const cat = (item.category || '').toUpperCase();
                const relType = (item.relatedIncidentType || '').toUpperCase();
                const title = (item.title || '').toLowerCase();

                // Rescue task assignments
                if (
                    cat === 'ASSIGNMENTS' ||
                    cat === 'ASSIGNMENT' ||
                    cat === 'TASKS' ||
                    cat === 'TASK' ||
                    relType === 'TASK' ||
                    relType === 'RESCUE_TASK' ||
                    title.includes('assignment') ||
                    title.includes('rescue') ||
                    title.includes('mission') ||
                    title.includes('task')
                ) {
                    if (item.relatedIncidentId) {
                        return `/responder-dashboard.html?tasks&taskId=${item.relatedIncidentId}`;
                    }
                    return '/responder-dashboard.html?tasks';
                }

                // Citizen distress / SOS
                if (
                    relType === 'CITIZEN_REPORT' ||
                    relType === 'REPORT' ||
                    title.includes('distress') ||
                    title.includes('sos')
                ) {
                    if (item.relatedIncidentId) {
                        return `/responder-dashboard.html?distress&reportId=${item.relatedIncidentId}`;
                    }
                    return '/responder-dashboard.html?distress';
                }

                // Official alerts & warnings
                if (
                    cat === 'ALERTS' ||
                    cat === 'ALERT' ||
                    relType === 'ALERT' ||
                    title.includes('alert') ||
                    title.includes('warning')
                ) {
                    if (item.relatedIncidentId) {
                        return `/responder-dashboard.html?warnings&alertId=${item.relatedIncidentId}`;
                    }
                    return '/responder-dashboard.html?warnings';
                }

                if (title.includes('tracking') || title.includes('gps')) {
                    return '/responder-dashboard.html?tracking';
                }

                return '/responder-dashboard.html';
            }

            return item.actionUrl || (this.role === 'Admin' ? '/admin-dashboard.html' : '/responder-dashboard.html');
        }

        async handleCardClick(id) {
            const item = this.notifications.find(n => n.id === id);
            if (!item) return;

            // Mark as read immediately on backend
            if (!item.isRead) {
                item.isRead = true;
                this.markAsRead(id);
            }

            // Open detail modal with details and direct action button
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
                const targetUrl = this.resolveTargetUrl(item);
                let btnLabel = 'Go to Dashboard';
                if (this.role === 'Citizen') {
                    if (targetUrl.includes('my-reports')) {
                        btnLabel = '<i class="fa-solid fa-list-check"></i> Track in My Reports';
                    } else if (targetUrl.includes('alerts')) {
                        btnLabel = '<i class="fa-solid fa-triangle-exclamation"></i> View Live Warnings';
                    } else if (targetUrl.includes('report')) {
                        btnLabel = '<i class="fa-solid fa-pen-to-square"></i> Open Incident Form';
                    } else {
                        btnLabel = '<i class="fa-solid fa-gauge-high"></i> Citizen Safety Hub';
                    }
                } else if (this.role === 'Responder') {
                    if (targetUrl.includes('tasks')) {
                        btnLabel = '<i class="fa-solid fa-list-check"></i> Open Assigned Mission';
                    } else if (targetUrl.includes('distress')) {
                        btnLabel = '<i class="fa-solid fa-hand-holding-hand"></i> View Distress Call';
                    } else if (targetUrl.includes('warnings')) {
                        btnLabel = '<i class="fa-solid fa-tower-broadcast"></i> View Official Warning';
                    } else if (targetUrl.includes('tracking')) {
                        btnLabel = '<i class="fa-solid fa-location-crosshairs"></i> Open Live Tracking';
                    } else {
                        btnLabel = '<i class="fa-solid fa-gauge-high"></i> Open Operations Hub';
                    }
                } else if (this.role === 'Admin') {
                    if (targetUrl.includes('disasters')) {
                        btnLabel = '<i class="fa-solid fa-triangle-exclamation"></i> Open Disaster Management';
                    } else if (targetUrl.includes('tasks')) {
                        btnLabel = '<i class="fa-solid fa-truck-medical"></i> View Field Mission';
                    } else if (targetUrl.includes('alerts')) {
                        btnLabel = '<i class="fa-solid fa-tower-broadcast"></i> View Emergency Broadcast';
                    } else if (targetUrl.includes('admin-users')) {
                        btnLabel = '<i class="fa-solid fa-users"></i> Manage Users';
                    } else {
                        btnLabel = '<i class="fa-solid fa-gauge-high"></i> Open Command Center';
                    }
                }
                actionBtn.innerHTML = btnLabel;
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
