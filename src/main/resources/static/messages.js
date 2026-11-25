/**
 * Tactical Direct Messaging Client Engine
 * Integrated Disaster Reporting and Emergency Response Coordination System
 */

(function(window) {
    'use strict';

    class MessagesApp {
        constructor(config) {
            this.role = config.role || 'Citizen';
            this.token = null;
            this.myEmail = null;
            this.currentFolder = 'inbox'; // 'inbox', 'sent', 'thread'
            this.messages = [];
            this.contacts = [];
            this.activeMessage = null;
            this.activeContact = null;
            this.unreadCount = 0;
            this.pollTimer = null;
            this.searchQuery = '';

            this.init();
        }

        init() {
            // Guard role & auth
            if (window.AuthHelper) {
                this.token = window.AuthHelper.getToken(this.role);
            }
            if (!this.token) {
                const normRole = this.role ? (this.role.charAt(0).toUpperCase() + this.role.slice(1).toLowerCase()) : '';
                this.token = sessionStorage.getItem('token_' + normRole) ||
                             localStorage.getItem('token_' + normRole) ||
                             sessionStorage.getItem('token') ||
                             localStorage.getItem('token') ||
                             sessionStorage.getItem(this.role ? this.role.toLowerCase() + '_token' : '') || '';
            }

            if (!this.token) {
                window.location.replace('/index.html');
                return;
            }

            if (window.AuthHelper) {
                const verifiedRole = window.AuthHelper.getRole();
                if (verifiedRole && verifiedRole !== this.role) {
                    const properUrl = verifiedRole === 'Admin' ? '/admin-messages.html' :
                                      verifiedRole === 'Responder' ? '/responder-messages.html' : '/citizen-messages.html';
                    window.location.replace(properUrl);
                    return;
                }
                this.myEmail = window.AuthHelper.getEmail();
            } else {
                this.myEmail = localStorage.getItem('userEmail');
            }

            this.setupUI();
            this.bindEvents();
            this.loadContacts();
            this.loadMessages();
            this.updateUnreadCount();
            this.startLiveClock();
            this.startPolling();
        }

        setupUI() {
            const name = (window.AuthHelper && window.AuthHelper.getName) ? window.AuthHelper.getName() : (localStorage.getItem('userName') || `${this.role} User`);
            const nameEl = document.getElementById('userNameDisplay');
            if (nameEl) nameEl.textContent = name;

            const roleEl = document.getElementById('userRoleDisplay');
            if (roleEl) roleEl.textContent = this.role;

            const avatarEl = document.getElementById('userAvatarDisplay');
            if (avatarEl) {
                const initials = name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
                avatarEl.textContent = initials || this.role[0];
            }

            // Dashboard return button
            const btnDash = document.getElementById('btnGoDashboard');
            if (btnDash) {
                const dashUrl = this.role === 'Admin' ? '/admin-dashboard.html' :
                                this.role === 'Responder' ? '/responder-dashboard.html' : '/citizen-dashboard.html';
                btnDash.href = dashUrl;
            }
        }

        bindEvents() {
            // Folder Navigation
            document.querySelectorAll('.folder-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    const folder = btn.getAttribute('data-folder');
                    this.switchFolder(folder);
                });
            });

            // Search
            const searchInput = document.getElementById('msgSearchInput');
            if (searchInput) {
                searchInput.addEventListener('input', (e) => {
                    this.searchQuery = e.target.value.toLowerCase().trim();
                    this.renderMessageList();
                });
                searchInput.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        const list = document.querySelectorAll('.msg-card');
                        if (list && list.length > 0) {
                            list[0].click();
                        }
                    } else if (e.key === 'Escape') {
                        this.searchQuery = '';
                        searchInput.value = '';
                        this.renderMessageList();
                        searchInput.blur();
                    }
                });
            }

            // Refresh button
            const btnRefresh = document.getElementById('btnRefreshFeed');
            if (btnRefresh) {
                btnRefresh.addEventListener('click', () => {
                    this.loadMessages(true);
                    this.updateUnreadCount();
                });
            }

            // Compose Modal Open
            const btnCompose = document.getElementById('btnComposeMessage');
            if (btnCompose) {
                btnCompose.addEventListener('click', () => this.openComposeModal());
            }

            // Close Modal
            const btnCloseModal = document.getElementById('btnCloseComposeModal');
            const btnCancelModal = document.getElementById('btnCancelCompose');
            const modalBackdrop = document.getElementById('composeModalBackdrop');
            if (btnCloseModal) btnCloseModal.addEventListener('click', () => this.closeComposeModal());
            if (btnCancelModal) btnCancelModal.addEventListener('click', () => this.closeComposeModal());
            if (modalBackdrop) {
                modalBackdrop.addEventListener('click', (e) => {
                    if (e.target === modalBackdrop) this.closeComposeModal();
                });
            }

            // Send Button in Modal
            const btnSendModal = document.getElementById('btnSendCompose');
            if (btnSendModal) {
                btnSendModal.addEventListener('click', () => this.submitCompose());
            }

            // Keyboard Shortcuts in Modal & Global
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    const modal = document.getElementById('composeModalBackdrop');
                    if (modal && modal.classList.contains('open')) {
                        this.closeComposeModal();
                    } else {
                        const layout = document.querySelector('.msg-layout');
                        if (layout && layout.classList.contains('detail-open')) {
                            this.backToFeed();
                        }
                    }
                } else if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                    const modal = document.getElementById('composeModalBackdrop');
                    if (modal && modal.classList.contains('open')) {
                        e.preventDefault();
                        this.submitCompose();
                    }
                }
            });

            // Logout
            const btnLogout = document.getElementById('btnLogout');
            if (btnLogout) {
                btnLogout.addEventListener('click', () => {
                    if (window.AuthHelper) {
                        window.AuthHelper.logout();
                    } else {
                        localStorage.clear();
                        window.location.replace('/index-new-login.html');
                    }
                });
            }
        }

        switchFolder(folder) {
            this.currentFolder = folder;
            document.querySelectorAll('.folder-btn').forEach(btn => {
                if (btn.getAttribute('data-folder') === folder) {
                    btn.classList.add('active');
                } else {
                    btn.classList.remove('active');
                }
            });

            const feedTitle = document.getElementById('feedTitleDisplay');
            if (feedTitle) {
                feedTitle.textContent = folder === 'inbox' ? 'Inbox Messages' :
                                       folder === 'sent' ? 'Sent Messages' : 'Direct Conversation';
            }

            // Reset selection
            this.activeMessage = null;
            this.loadMessages();
        }

        async loadMessages(showToast = false) {
            const endpoint = this.currentFolder === 'sent' ? '/api/messages/sent' : '/api/messages/inbox';
            try {
                const res = await fetch(endpoint, {
                    headers: { 'Authorization': 'Bearer ' + this.token }
                });

                if (res.status === 401 || res.status === 403) {
                    window.location.replace('/index-new-login.html');
                    return;
                }

                if (res.ok) {
                    this.messages = await res.json();
                    this.renderMessageList();
                    if (showToast && window.showToast) {
                        window.showToast('Messages synchronized successfully.', 'info');
                    }
                }
            } catch (err) {
                console.error('Failed to load messages:', err);
            }
        }

        async updateUnreadCount() {
            try {
                const res = await fetch('/api/messages/unread-count', {
                    headers: { 'Authorization': 'Bearer ' + this.token }
                });
                if (res.ok) {
                    const data = await res.json();
                    this.unreadCount = data.unreadCount || 0;
                    const badge = document.getElementById('inboxUnreadBadge');
                    if (badge) {
                        badge.textContent = this.unreadCount;
                        badge.className = this.unreadCount > 0 ? 'badge-count highlight' : 'badge-count';
                    }
                    const subtitle = document.getElementById('msgSubtitle');
                    if (subtitle) {
                        subtitle.innerHTML = `<span class="unread-highlight">${this.unreadCount} unread</span> • Secure tactical comms`;
                    }
                }
            } catch (err) {
                console.error('Failed to update unread count:', err);
            }
        }

        async loadContacts() {
            try {
                const res = await fetch('/api/messages/contacts', {
                    headers: { 'Authorization': 'Bearer ' + this.token }
                });
                if (res.ok) {
                    this.contacts = await res.json();
                    this.renderContactsList();
                    this.populateComposeRecipients();
                }
            } catch (err) {
                console.error('Failed to load contacts:', err);
            }
        }

        renderContactsList() {
            const listEl = document.getElementById('sidebarContactsList');
            if (!listEl) return;

            if (!this.contacts || this.contacts.length === 0) {
                listEl.innerHTML = `<div style="padding:0.75rem;font-size:0.75rem;color:#64748b;">No authorized contacts</div>`;
                return;
            }

            listEl.innerHTML = this.contacts.map(c => {
                const initial = (c.name || c.email || '?')[0].toUpperCase();
                const isActive = this.activeContact && this.activeContact.email.toLowerCase() === c.email.toLowerCase();

                return `
                    <button type="button" class="contact-item-mini ${isActive ? 'active' : ''}" onclick="window.MessagesApp.startChatWith('${escapeJs(c.email)}')">
                        <div class="contact-avatar-mini">${initial}</div>
                        <div class="contact-info-mini">
                            <div class="contact-name-mini">${escapeHtml(c.name || c.email)}</div>
                            <div class="contact-role-mini">${escapeHtml(c.badge || c.role)}</div>
                        </div>
                    </button>
                `;
            }).join('');
        }

        populateComposeRecipients(preselectEmail = null) {
            const selectEl = document.getElementById('composeRecipientSelect');
            if (!selectEl) return;

            selectEl.innerHTML = '<option value="">-- Choose Authorized Recipient --</option>' +
                this.contacts.map(c => {
                    const sel = preselectEmail && preselectEmail.toLowerCase() === c.email.toLowerCase() ? 'selected' : '';
                    return `<option value="${escapeHtml(c.email)}" ${sel}>${escapeHtml(c.name)} (${escapeHtml(c.badge || c.role)}) - ${escapeHtml(c.email)}</option>`;
                }).join('');
        }

        renderMessageList() {
            const listEl = document.getElementById('messagesListContainer');
            if (!listEl) return;

            let filtered = this.messages;
            if (this.searchQuery) {
                filtered = filtered.filter(m => {
                    const s = (m.subject || '').toLowerCase();
                    const b = (m.messageBody || m.message || '').toLowerCase();
                    const sender = (m.senderName || m.senderEmail || '').toLowerCase();
                    const recip = (m.recipientEmail || m.receiverEmail || '').toLowerCase();
                    return s.includes(this.searchQuery) || b.includes(this.searchQuery) ||
                           sender.includes(this.searchQuery) || recip.includes(this.searchQuery);
                });
            }

            if (!filtered || filtered.length === 0) {
                listEl.innerHTML = `
                    <div class="msg-detail-empty" style="padding:3rem 1rem;">
                        <i class="fa-solid fa-inbox"></i>
                        <p>No messages found in ${this.currentFolder}.</p>
                    </div>`;
                this.renderDetailEmpty();
                return;
            }

            listEl.innerHTML = filtered.map(m => {
                const isSelected = this.activeMessage && String(this.activeMessage.id) === String(m.id);
                const isUnread = this.currentFolder === 'inbox' && (!m.messageStatus || m.messageStatus !== 'Read');
                const displayName = this.currentFolder === 'sent' ? 
                    ('To: ' + (m.recipientEmail || m.receiverEmail)) : 
                    (m.senderName || m.senderEmail || 'Sender');
                const roleTag = this.currentFolder === 'sent' ? (m.recipientRole || 'User') : (m.senderRole || 'User');
                const timeStr = formatDateTime(m.createdAt || m.sentAt);

                return `
                    <div class="msg-card ${isSelected ? 'active' : ''} ${isUnread ? 'unread' : ''}" data-id="${m.id}" onclick="window.MessagesApp.selectMessage(${m.id})">
                        <div class="msg-card-top">
                            <div class="msg-sender-info">
                                <span class="msg-sender-name">${escapeHtml(displayName)}</span>
                                <span class="msg-sender-role-tag">${escapeHtml(roleTag)}</span>
                            </div>
                            <span class="msg-time">${timeStr}</span>
                        </div>
                        <div class="msg-subject-line">${escapeHtml(m.subject || 'Direct Communication')}</div>
                        <div class="msg-preview-text">${escapeHtml(m.messageBody || m.message || '')}</div>
                    </div>
                `;
            }).join('');

            // If an active message was already selected, make sure its detail is rendered
            if (this.activeMessage) {
                const refreshed = filtered.find(m => String(m.id) === String(this.activeMessage.id));
                if (refreshed) {
                    this.activeMessage = refreshed;
                    this.renderMessageDetail(refreshed);
                } else {
                    this.activeMessage = null;
                    this.renderDetailEmpty();
                }
            } else if (filtered.length > 0 && window.innerWidth > 820) {
                // Auto-select the first message on desktop so the right pane is never blank
                this.selectMessage(filtered[0].id, false);
            }
        }

        backToFeed() {
            const layout = document.querySelector('.msg-layout');
            if (layout) layout.classList.remove('detail-open');
            this.activeMessage = null;
            this.activeContact = null;
            this.renderMessageList();
            this.renderDetailEmpty();
        }

        async selectMessage(messageId, markAsRead = true) {
            const msg = this.messages.find(m => String(m.id) === String(messageId));
            if (!msg) return;

            this.activeMessage = msg;
            this.activeContact = null;
            
            const layout = document.querySelector('.msg-layout');
            if (layout) layout.classList.add('detail-open');

            // Update DOM card classes directly without triggering recursive list renders
            document.querySelectorAll('.msg-card').forEach(card => {
                if (card.getAttribute('data-id') === String(messageId)) {
                    card.classList.add('active');
                    if (markAsRead) card.classList.remove('unread');
                } else {
                    card.classList.remove('active');
                }
            });

            this.renderMessageDetail(msg);

            // Mark as read on server if unread and in inbox
            if (markAsRead && this.currentFolder === 'inbox' && msg.messageStatus !== 'Read') {
                try {
                    await fetch(`/api/messages/${msg.id}/read`, {
                        method: 'PATCH',
                        headers: { 'Authorization': 'Bearer ' + this.token }
                    });
                    msg.messageStatus = 'Read';
                    this.updateUnreadCount();
                } catch (err) {
                    console.error('Failed to mark read:', err);
                }
            }
        }

        renderMessageDetail(msg) {
            const paneEl = document.getElementById('msgDetailPane');
            if (!paneEl) return;

            const isSent = this.currentFolder === 'sent';
            const counterpartyEmail = isSent ? (msg.recipientEmail || msg.receiverEmail) : msg.senderEmail;
            const counterpartyName = isSent ? counterpartyEmail : (msg.senderName || msg.senderEmail);
            const timeStr = formatDateTime(msg.createdAt || msg.sentAt);

            paneEl.innerHTML = `
                <div class="msg-detail-content">
                    <div class="msg-detail-header">
                        <div class="msg-detail-title-row">
                            <div style="display:flex;align-items:center;gap:0.5rem;flex:1;min-width:0;">
                                <button type="button" class="btn-back-to-feed" onclick="window.MessagesApp.backToFeed()" title="Back to Message List">
                                    <i class="fa-solid fa-arrow-left"></i> Back
                                </button>
                                <h2 class="msg-detail-subject">${escapeHtml(msg.subject || 'Direct Communication')}</h2>
                            </div>
                            <div class="msg-detail-actions">
                                <button type="button" class="btn-icon-action" title="Open Conversation Thread" onclick="window.MessagesApp.startChatWith('${escapeJs(counterpartyEmail)}')">
                                    <i class="fa-solid fa-comments"></i>
                                </button>
                                <button type="button" class="btn-icon-action delete" title="Delete Message" onclick="window.MessagesApp.deleteMessage(${msg.id})">
                                    <i class="fa-solid fa-trash-can"></i>
                                </button>
                            </div>
                        </div>
                        <div class="msg-detail-meta">
                            <div class="meta-row">
                                <span><strong>From:</strong> ${escapeHtml(msg.senderName || msg.senderEmail)} (${escapeHtml(msg.senderRole || 'User')})</span>
                                <span>${timeStr}</span>
                            </div>
                            <div class="meta-row">
                                <span><strong>To:</strong> ${escapeHtml(msg.recipientEmail || msg.receiverEmail)} (${escapeHtml(msg.recipientRole || 'User')})</span>
                                <span><strong>Status:</strong> ${escapeHtml(msg.messageStatus || 'Sent')}</span>
                            </div>
                        </div>
                    </div>

                    <div class="msg-detail-body">${escapeHtml(msg.messageBody || msg.message || '')}</div>

                    <div class="msg-detail-footer">
                        <button type="button" class="btn-secondary" onclick="window.MessagesApp.startChatWith('${escapeJs(counterpartyEmail)}')">
                            <i class="fa-solid fa-clock-rotate-left"></i> View Full Thread
                        </button>
                        <button type="button" class="btn-primary" onclick="window.MessagesApp.replyToCurrent()">
                            <i class="fa-solid fa-reply"></i> Reply
                        </button>
                    </div>
                </div>
            `;
        }

        renderDetailEmpty() {
            const paneEl = document.getElementById('msgDetailPane');
            if (paneEl) {
                paneEl.innerHTML = `
                    <div class="msg-detail-empty">
                        <i class="fa-regular fa-envelope-open"></i>
                        <h3>No Message Selected</h3>
                        <p>Select a message on the left to read its complete tactical dispatch, or click Compose to initiate contact.</p>
                    </div>
                `;
            }
        }

        async deleteMessage(messageId) {
            if (!confirm('Are you sure you want to delete this message?')) return;

            try {
                const res = await fetch(`/api/messages/${messageId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + this.token }
                });

                if (res.ok) {
                    if (window.showToast) window.showToast('Message deleted successfully.', 'success');
                    this.messages = this.messages.filter(m => m.id !== messageId);
                    this.activeMessage = null;
                    this.renderMessageList();
                    this.renderDetailEmpty();
                    this.updateUnreadCount();
                } else {
                    const err = await res.json().catch(() => ({}));
                    alert(err.message || 'Failed to delete message.');
                }
            } catch (err) {
                console.error('Delete error:', err);
                alert('Network error deleting message.');
            }
        }

        replyToCurrent() {
            if (!this.activeMessage) return;
            const isSent = this.currentFolder === 'sent';
            const targetEmail = isSent ? (this.activeMessage.recipientEmail || this.activeMessage.receiverEmail) : this.activeMessage.senderEmail;
            let subject = this.activeMessage.subject || 'Direct Communication';
            if (!subject.startsWith('Re:')) subject = 'Re: ' + subject;

            this.openComposeModal(targetEmail, subject);
        }

        openComposeModal(preselectEmail = null, subject = null) {
            const modal = document.getElementById('composeModalBackdrop');
            if (!modal) return;

            this.populateComposeRecipients(preselectEmail);
            const subjInput = document.getElementById('composeSubjectInput');
            const bodyInput = document.getElementById('composeBodyInput');
            const errEl = document.getElementById('composeErrorAlert');

            if (subjInput) subjInput.value = subject || '';
            if (bodyInput) {
                bodyInput.value = '';
                setTimeout(() => bodyInput.focus(), 150);
            }
            if (errEl) errEl.style.display = 'none';

            modal.classList.add('open');
        }

        closeComposeModal() {
            const modal = document.getElementById('composeModalBackdrop');
            if (modal) modal.classList.remove('open');
        }

        async submitCompose() {
            const recipientSelect = document.getElementById('composeRecipientSelect');
            const subjInput = document.getElementById('composeSubjectInput');
            const bodyInput = document.getElementById('composeBodyInput');
            const btnSend = document.getElementById('btnSendCompose');
            const errEl = document.getElementById('composeErrorAlert');

            const recipient = recipientSelect ? recipientSelect.value.trim() : '';
            const subject = subjInput ? subjInput.value.trim() : '';
            const messageBody = bodyInput ? bodyInput.value.trim() : '';

            if (!recipient) {
                this.showComposeError('Please select an authorized recipient.');
                return;
            }
            if (!subject) {
                this.showComposeError('Please enter a message subject.');
                return;
            }
            if (!messageBody) {
                this.showComposeError('Message text cannot be empty.');
                return;
            }

            if (errEl) errEl.style.display = 'none';
            if (btnSend) {
                btnSend.disabled = true;
                btnSend.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Sending...';
            }

            try {
                const res = await fetch('/api/messages', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + this.token
                    },
                    body: JSON.stringify({
                        recipientEmail: recipient,
                        subject: subject,
                        messageBody: messageBody
                    })
                });

                if (res.ok) {
                    const saved = await res.json();
                    if (window.showToast) window.showToast('Message dispatched successfully.', 'success');
                    this.closeComposeModal();

                    // Switch to Sent folder and select newly sent message
                    this.switchFolder('sent');
                } else {
                    const err = await res.json().catch(() => ({}));
                    const msg = err.message || ('Failed to send message: HTTP ' + res.status);
                    this.showComposeError(msg);
                }
            } catch (err) {
                console.error('Failed to submit message:', err);
                this.showComposeError('Network error while dispatching message. Check server connection.');
            } finally {
                if (btnSend) {
                    btnSend.disabled = false;
                    btnSend.innerHTML = '<i class="fa-solid fa-paper-plane"></i> Send Message';
                }
            }
        }

        showComposeError(msg) {
            const errEl = document.getElementById('composeErrorAlert');
            if (errEl) {
                errEl.textContent = msg;
                errEl.style.display = 'block';
            } else {
                alert(msg);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Live Chat / Two-Way Thread Mode
        // ─────────────────────────────────────────────────────────────
        async startChatWith(email) {
            const contact = this.contacts.find(c => c.email.toLowerCase() === email.toLowerCase()) || {
                email: email,
                name: email,
                role: 'Contact',
                badge: 'Direct'
            };

            this.activeContact = contact;
            this.activeMessage = null;

            const layout = document.querySelector('.msg-layout');
            if (layout) layout.classList.add('detail-open');

            this.renderContactsList();

            const paneEl = document.getElementById('msgDetailPane');
            if (!paneEl) return;

            paneEl.innerHTML = `
                <div class="thread-container">
                    <div class="thread-header">
                        <div style="display:flex;align-items:center;gap:0.5rem;flex:1;min-width:0;">
                            <button type="button" class="btn-back-to-feed" onclick="window.MessagesApp.backToFeed()" title="Back to Message List">
                                <i class="fa-solid fa-arrow-left"></i> Back
                            </button>
                            <div>
                                <div style="font-size:0.95rem;font-weight:700;">${escapeHtml(contact.name)}</div>
                                <div style="font-size:0.75rem;color:#94a3b8;font-family:monospace;">${escapeHtml(contact.email)} • ${escapeHtml(contact.badge || contact.role)}</div>
                            </div>
                        </div>
                        <button type="button" class="btn-feed-action" onclick="window.MessagesApp.openComposeModal('${escapeJs(contact.email)}')">
                            <i class="fa-solid fa-pen-to-square"></i> New Subject
                        </button>
                    </div>

                    <div class="thread-stream" id="threadMessagesStream">
                        <div style="text-align:center;padding:2rem;color:#64748b;">
                            <i class="fa-solid fa-circle-notch fa-spin"></i> Loading conversation history...
                        </div>
                    </div>

                    <div class="thread-input-area">
                        <input type="text" id="threadInputBox" class="thread-input" placeholder="Type tactical response or update..." autocomplete="off" />
                        <button type="button" id="btnSendThread" class="btn-send-thread" onclick="window.MessagesApp.sendThreadMessage()">
                            <i class="fa-solid fa-paper-plane"></i>
                        </button>
                    </div>
                </div>
            `;

            const input = document.getElementById('threadInputBox');
            if (input) {
                input.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        this.sendThreadMessage();
                    }
                });
                input.focus();
            }

            await this.loadThreadConversation(email, true);
        }

        async loadThreadConversation(email, scrollToBottom = true) {
            const streamEl = document.getElementById('threadMessagesStream');
            if (!streamEl) return;

            try {
                const res = await fetch(`/api/messages/conversation?with=${encodeURIComponent(email)}`, {
                    headers: { 'Authorization': 'Bearer ' + this.token }
                });

                if (res.ok) {
                    const messages = await res.json();
                    if (!messages || messages.length === 0) {
                        streamEl.innerHTML = `
                            <div class="msg-detail-empty" style="padding:2rem;">
                                <i class="fa-solid fa-comments"></i>
                                <div>No previous messages with <strong>${escapeHtml(email)}</strong>.</div>
                                <div style="font-size:0.75rem;color:#94a3b8;">Type below to start this live channel.</div>
                            </div>`;
                        return;
                    }

                    const myEmail = (this.myEmail || '').toLowerCase();
                    streamEl.innerHTML = messages.map(m => {
                        const isOutgoing = m.senderEmail && m.senderEmail.toLowerCase() === myEmail;
                        const timeStr = formatDateTime(m.createdAt || m.sentAt);

                        return `
                            <div class="thread-bubble-row ${isOutgoing ? 'outgoing' : 'incoming'}">
                                <div class="thread-bubble">${escapeHtml(m.messageBody || m.message || '')}</div>
                                <div class="thread-meta">
                                    ${isOutgoing ? 'You' : escapeHtml(m.senderName || m.senderEmail)} • ${timeStr}
                                </div>
                            </div>
                        `;
                    }).join('');

                    if (scrollToBottom) {
                        streamEl.scrollTop = streamEl.scrollHeight;
                    }
                }
            } catch (err) {
                console.error('Thread load error:', err);
            }
        }

        async sendThreadMessage() {
            const input = document.getElementById('threadInputBox');
            if (!input || !this.activeContact) return;

            const text = input.value.trim();
            if (!text) return;

            input.value = '';

            try {
                const res = await fetch('/api/messages', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + this.token
                    },
                    body: JSON.stringify({
                        recipientEmail: this.activeContact.email,
                        subject: 'Direct Tactical Channel',
                        messageBody: text
                    })
                });

                if (res.ok) {
                    await this.loadThreadConversation(this.activeContact.email, true);
                } else {
                    const err = await res.json().catch(() => ({}));
                    alert(err.message || 'Failed to send message.');
                }
            } catch (err) {
                console.error('Thread send error:', err);
                alert('Network error sending message.');
            }
        }

        startPolling() {
            if (this.pollTimer) clearInterval(this.pollTimer);
            this.pollTimer = setInterval(() => {
                if (document.hidden) return;
                if (this.activeContact) {
                    this.loadThreadConversation(this.activeContact.email, false);
                } else if (this.currentFolder === 'inbox') {
                    this.loadMessages(false);
                }
                this.updateUnreadCount();
            }, 4000);
        }

        startLiveClock() {
            const clockEl = document.getElementById('liveClockDisplay');
            if (!clockEl) return;

            const tick = () => {
                const now = new Date();
                clockEl.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' IST';
            };
            tick();
            setInterval(tick, 1000);
        }
    }

    function formatDateTime(isoStr) {
        if (!isoStr) return '';
        try {
            let d;
            if (Array.isArray(isoStr)) {
                d = new Date(isoStr[0], isoStr[1] - 1, isoStr[2], isoStr[3] || 0, isoStr[4] || 0, isoStr[5] || 0);
            } else {
                d = new Date(isoStr);
            }
            if (isNaN(d.getTime())) return '';
            const now = new Date();
            if (d.toDateString() === now.toDateString()) {
                return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            }
            return d.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' +
                   d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch (e) {
            return '';
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function escapeJs(str) {
        if (!str) return '';
        return String(str).replace(/'/g, "\\'").replace(/"/g, '\\"');
    }

    window.MessagesApp = MessagesApp;
})(window);
