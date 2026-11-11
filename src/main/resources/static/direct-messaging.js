/**
 * Direct In-App Tactical Messaging Module
 * Integrates Citizen, Responder, and Admin communication.
 */
(function() {
    'use strict';

    class DirectMessagingController {
        constructor() {
            this.modalEl = null;
            this.contacts = [];
            this.activeContact = null;
            this.pollTimer = null;
            this.myEmail = null;
            this.myRole = null;
        }

        init() {
            this.myRole = localStorage.getItem('role') || 'Citizen';
            this.myEmail = localStorage.getItem('userEmail') || '';
        }

        async open(targetEmail = null, targetName = null) {
            this.init();
            this.ensureModalDom();
            this.modalEl.style.display = 'flex';
            document.body.style.overflow = 'hidden';

            await this.loadContacts();

            if (targetEmail) {
                // If preselected contact is provided, find or select
                let existing = this.contacts.find(c => c.email.toLowerCase() === targetEmail.toLowerCase());
                if (!existing) {
                    existing = {
                        email: targetEmail,
                        name: targetName || targetEmail,
                        role: 'Direct',
                        badge: 'Operational Contact'
                    };
                    this.contacts.unshift(existing);
                    this.renderContacts();
                }
                this.selectContact(existing);
            } else if (this.contacts.length > 0) {
                this.selectContact(this.contacts[0]);
            } else {
                this.renderEmptyChat();
            }

            // Start polling active conversation every 4 seconds
            this.startPolling();
        }

        close() {
            if (this.modalEl) {
                this.modalEl.style.display = 'none';
            }
            document.body.style.overflow = '';
            this.stopPolling();
        }

        startPolling() {
            this.stopPolling();
            this.pollTimer = setInterval(() => {
                if (this.activeContact && this.modalEl && this.modalEl.style.display === 'flex') {
                    this.loadConversation(this.activeContact.email, false);
                }
            }, 4000);
        }

        stopPolling() {
            if (this.pollTimer) {
                clearInterval(this.pollTimer);
                this.pollTimer = null;
            }
        }

        ensureModalDom() {
            if (this.modalEl) return;

            const modal = document.createElement('div');
            modal.className = 'dm-modal-backdrop';
            modal.id = 'dm-modal-root';
            modal.style.display = 'none';

            modal.innerHTML = `
                <div class="dm-modal-container" onclick="event.stopPropagation()">
                    <div class="dm-modal-header">
                        <div class="dm-modal-title">
                            <i class="fas fa-tower-broadcast"></i> Tactical Comms & Direct Dispatch
                        </div>
                        <button class="dm-close-btn" onclick="window.DirectMessaging.close()" title="Close">&times;</button>
                    </div>

                    <div class="dm-modal-body">
                        <!-- Left Contacts Sidebar -->
                        <div class="dm-contacts-sidebar" id="dm-contacts-panel">
                            <div class="dm-contacts-header">
                                <span><i class="fas fa-users-viewfinder"></i> Authorized Contacts</span>
                                <button type="button" style="background:transparent;border:none;color:#00e5ff;cursor:pointer;font-size:0.75rem;" onclick="window.DirectMessaging.loadContacts()">
                                    <i class="fas fa-rotate"></i>
                                </button>
                            </div>
                            <div class="dm-contacts-list" id="dm-contacts-list">
                                <div style="padding:1.5rem;text-align:center;color:#64748b;font-size:0.8rem;">
                                    Loading contacts...
                                </div>
                            </div>
                        </div>

                        <!-- Right Chat Pane -->
                        <div class="dm-chat-pane" id="dm-chat-panel">
                            <div class="dm-chat-header" id="dm-active-header" style="display:none;">
                                <div class="dm-chat-target-info">
                                    <div class="dm-contact-avatar" id="dm-header-avatar">?</div>
                                    <div>
                                        <div class="dm-chat-target-name" id="dm-header-name">Loading...</div>
                                        <div style="font-size:0.72rem;color:#94a3b8;font-family:monospace;" id="dm-header-email"></div>
                                    </div>
                                </div>
                                <span class="dm-chat-target-role" id="dm-header-role">Role</span>
                            </div>

                            <div class="dm-messages-stream" id="dm-messages-stream">
                                <div class="dm-empty-state">
                                    <i class="fas fa-comments"></i>
                                    <div>Select an authorized contact on the left to start direct communications.</div>
                                </div>
                            </div>

                            <div class="dm-input-area" id="dm-input-container" style="display:none;">
                                <input type="text" id="dm-message-input" class="dm-text-input" placeholder="Type tactical message or SitRep..." autocomplete="off" />
                                <button type="button" class="dm-send-btn" id="dm-send-btn" onclick="window.DirectMessaging.sendMessage()">
                                    <i class="fas fa-paper-plane"></i> Send
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            modal.addEventListener('click', (e) => {
                if (e.target === modal) this.close();
            });

            document.body.appendChild(modal);
            this.modalEl = modal;

            // Enter key binding for input
            setTimeout(() => {
                const input = document.getElementById('dm-message-input');
                if (input) {
                    input.addEventListener('keydown', (e) => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            this.sendMessage();
                        }
                    });
                }
            }, 100);
        }

        async loadContacts() {
            const token = localStorage.getItem('token');
            if (!token) return;

            try {
                const res = await fetch('/api/messages/contacts', {
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                if (res.ok) {
                    this.contacts = await res.json();
                    this.renderContacts();
                } else {
                    console.warn('Failed to load contacts:', res.status);
                }
            } catch (err) {
                console.error('Error fetching contacts:', err);
            }
        }

        renderContacts() {
            const listEl = document.getElementById('dm-contacts-list');
            if (!listEl) return;

            if (!this.contacts || this.contacts.length === 0) {
                listEl.innerHTML = `
                    <div style="padding:1.5rem;text-align:center;color:#64748b;font-size:0.8rem;">
                        No authorized contacts currently available for your profile.
                    </div>`;
                return;
            }

            listEl.innerHTML = this.contacts.map(c => {
                const roleLower = (c.role || 'citizen').toLowerCase();
                const initial = (c.name || c.email || '?')[0].toUpperCase();
                const isActive = this.activeContact && this.activeContact.email.toLowerCase() === c.email.toLowerCase();

                return `
                    <div class="dm-contact-item ${isActive ? 'active' : ''}" onclick="window.DirectMessaging.selectContactByEmail('${escapeJs(c.email)}')">
                        <div class="dm-contact-avatar ${roleLower}">${initial}</div>
                        <div class="dm-contact-info">
                            <div class="dm-contact-name">${escapeHtml(c.name)}</div>
                            <div class="dm-contact-badge">${escapeHtml(c.badge || c.role)}</div>
                        </div>
                    </div>
                `;
            }).join('');
        }

        selectContactByEmail(email) {
            const contact = this.contacts.find(c => c.email.toLowerCase() === email.toLowerCase());
            if (contact) {
                this.selectContact(contact);
            }
        }

        async selectContact(contact) {
            this.activeContact = contact;
            this.renderContacts(); // update active class

            // Update Header
            const headerEl = document.getElementById('dm-active-header');
            const inputContainer = document.getElementById('dm-input-container');
            if (headerEl) headerEl.style.display = 'flex';
            if (inputContainer) inputContainer.style.display = 'flex';

            const nameEl = document.getElementById('dm-header-name');
            const emailEl = document.getElementById('dm-header-email');
            const roleEl = document.getElementById('dm-header-role');
            const avatarEl = document.getElementById('dm-header-avatar');

            if (nameEl) nameEl.textContent = contact.name || contact.email;
            if (emailEl) emailEl.textContent = contact.email;
            if (roleEl) {
                roleEl.textContent = contact.role;
                roleEl.className = 'dm-chat-target-role dm-role-' + (contact.role || 'citizen').toLowerCase();
            }
            if (avatarEl) {
                avatarEl.textContent = (contact.name || contact.email)[0].toUpperCase();
                avatarEl.className = 'dm-contact-avatar ' + (contact.role || 'citizen').toLowerCase();
            }

            // Focus message input
            const input = document.getElementById('dm-message-input');
            if (input) input.focus();

            await this.loadConversation(contact.email, true);
        }

        async loadConversation(email, scrollToBottom = true) {
            const token = localStorage.getItem('token');
            if (!token || !email) return;

            const streamEl = document.getElementById('dm-messages-stream');
            if (!streamEl) return;

            try {
                const res = await fetch(`/api/messages/conversation?with=${encodeURIComponent(email)}`, {
                    headers: { 'Authorization': 'Bearer ' + token }
                });

                if (res.ok) {
                    const messages = await res.json();
                    this.renderMessages(messages, scrollToBottom);
                } else if (res.status === 403) {
                    streamEl.innerHTML = `
                        <div class="dm-empty-state">
                            <i class="fas fa-lock" style="color:#ef4444;"></i>
                            <div style="color:#ef4444;font-weight:600;">Access Denied (403 Forbidden)</div>
                            <div style="font-size:0.8rem;color:#94a3b8;">You do not have permission to view or participate in this conversation.</div>
                        </div>`;
                }
            } catch (err) {
                console.error('Failed to load conversation:', err);
            }
        }

        renderMessages(messages, scrollToBottom = true) {
            const streamEl = document.getElementById('dm-messages-stream');
            if (!streamEl) return;

            if (!messages || messages.length === 0) {
                streamEl.innerHTML = `
                    <div class="dm-empty-state">
                        <i class="fas fa-comment-dots"></i>
                        <div>No messages yet with <strong>${escapeHtml(this.activeContact.name)}</strong>.</div>
                        <div style="font-size:0.8rem;color:#94a3b8;">Send a message below to start this channel.</div>
                    </div>`;
                return;
            }

            const myEmail = (this.myEmail || '').toLowerCase();

            streamEl.innerHTML = messages.map(m => {
                const isOutgoing = m.senderEmail && m.senderEmail.toLowerCase() === myEmail;
                const timeStr = formatMsgTime(m.sentAt);

                return `
                    <div class="dm-bubble-row ${isOutgoing ? 'outgoing' : 'incoming'}">
                        <div class="dm-bubble">${escapeHtml(m.message)}</div>
                        <div class="dm-bubble-meta">
                            ${isOutgoing ? 'You' : escapeHtml(m.senderName || m.senderRole || 'User')} • ${timeStr}
                        </div>
                    </div>
                `;
            }).join('');

            if (scrollToBottom) {
                streamEl.scrollTop = streamEl.scrollHeight;
            }
        }

        renderEmptyChat() {
            const streamEl = document.getElementById('dm-messages-stream');
            if (streamEl) {
                streamEl.innerHTML = `
                    <div class="dm-empty-state">
                        <i class="fas fa-inbox"></i>
                        <div>No messages yet.</div>
                    </div>`;
            }
        }

        async sendMessage() {
            const input = document.getElementById('dm-message-input');
            if (!input || !this.activeContact) return;

            const text = input.value.trim();
            if (!text) return;

            const token = localStorage.getItem('token');
            if (!token) {
                alert('Authentication required.');
                return;
            }

            input.value = '';

            try {
                const res = await fetch('/api/messages', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + token
                    },
                    body: JSON.stringify({
                        receiverEmail: this.activeContact.email,
                        message: text
                    })
                });

                if (res.ok) {
                    await this.loadConversation(this.activeContact.email, true);
                } else {
                    const err = await res.json().catch(() => ({}));
                    const errMsg = err.message || ('Failed to send message: ' + res.status);
                    if (window.CommandCenter && window.CommandCenter.showToast) {
                        window.CommandCenter.showToast(errMsg, 'danger');
                    } else {
                        alert(errMsg);
                    }
                }
            } catch (err) {
                console.error('Failed to dispatch message:', err);
                alert('Network error while dispatching message.');
            }
        }
    }

    function formatMsgTime(isoStr) {
        if (!isoStr) return '';
        try {
            const d = new Date(isoStr);
            return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
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

    window.DirectMessaging = new DirectMessagingController();
})();
