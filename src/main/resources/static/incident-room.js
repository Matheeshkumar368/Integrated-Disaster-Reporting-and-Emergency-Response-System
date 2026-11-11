/**
 * ==============================================================================
 * INCIDENT COLLABORATION ROOM (Feature 5)
 * Disaster Management & Alert System
 * 
 * Shared real-time incident coordination, chronological timeline,
 * evidence media gallery, and secure role messaging.
 * ==============================================================================
 */

(function () {
    'use strict';

    class IncidentRoomManager {
        constructor() {
            this.activeIncidentId = null;
            this.activeIncidentType = 'CITIZEN_REPORT';
            this.pollTimer = null;
            this.currentEmail = null;

            if (document.body) {
                this.injectModal();
                this.bindEvents();
            } else if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', () => {
                    this.injectModal();
                    this.bindEvents();
                });
            }
        }

        injectModal() {
            if (document.getElementById('incident-room-modal-backdrop')) return;
            if (!document.body) return;

            const html = `
            <div id="incident-room-modal-backdrop" class="room-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="room-title-heading">
                <div class="room-modal-container">
                    <div class="room-modal-header">
                        <div class="room-modal-title" id="room-title-heading">
                            <span>💬</span> INCIDENT COLLABORATION ROOM
                            <span class="room-incident-badge" id="room-incident-id-tag">#--</span>
                        </div>
                        <button type="button" class="media-modal-close" id="btn-room-close" aria-label="Close collaboration room">&times;</button>
                    </div>

                    <!-- Chronological Timeline Stream -->
                    <div id="room-timeline-stream" class="room-timeline-stream">
                        <div style="text-align:center; color:#64748b; padding:2rem;">
                            Loading incident chronology and field evidence...
                        </div>
                    </div>

                    <!-- Action Bar -->
                    <div class="room-input-bar">
                        <button type="button" class="btn-room-media-attach" id="btn-room-attach-media" title="Capture / Attach Photo or Video Evidence">
                            📷
                        </button>
                        <input type="text" id="room-msg-input" class="room-msg-input" placeholder="Type tactical update, situation note, or message..." />
                        <button type="button" class="btn-room-send" id="btn-room-send-msg">
                            Send Note
                        </button>
                    </div>
                </div>
            </div>
            `;

            document.body.insertAdjacentHTML('beforeend', html);
        }

        bindEvents() {
            const closeBtn = document.getElementById('btn-room-close');
            const sendBtn = document.getElementById('btn-room-send-msg');
            const inputField = document.getElementById('room-msg-input');
            const attachBtn = document.getElementById('btn-room-attach-media');

            if (closeBtn) closeBtn.addEventListener('click', () => this.close());

            if (sendBtn) sendBtn.addEventListener('click', () => this.sendMessage());
            if (inputField) {
                inputField.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        this.sendMessage();
                    }
                });
            }

            if (attachBtn) {
                attachBtn.addEventListener('click', () => {
                    if (window.MediaCapture && this.activeIncidentId) {
                        window.MediaCapture.open({
                            incidentId: this.activeIncidentId,
                            incidentType: this.activeIncidentType,
                            onSuccess: () => this.fetchRoomData()
                        });
                    }
                });
            }
        }

        open(incidentId, incidentType = 'CITIZEN_REPORT', title = '') {
            this.activeIncidentId = incidentId;
            this.activeIncidentType = incidentType;

            const user = JSON.parse(localStorage.getItem('user') || '{}');
            this.currentEmail = user.email || '';
            this.currentUserRole = user.role || '';

            const backdrop = document.getElementById('incident-room-modal-backdrop');
            const idTag = document.getElementById('room-incident-id-tag');
            if (idTag) idTag.textContent = `#${incidentId} [${incidentType.replace('_', ' ')}]`;

            backdrop.classList.add('active');

            this.fetchRoomData();

            // Auto-refresh every 4 seconds
            if (this.pollTimer) clearInterval(this.pollTimer);
            this.pollTimer = setInterval(() => this.fetchRoomData(), 4000);
        }

        close() {
            if (this.pollTimer) {
                clearInterval(this.pollTimer);
                this.pollTimer = null;
            }
            const backdrop = document.getElementById('incident-room-modal-backdrop');
            if (backdrop) backdrop.classList.remove('active');
        }

        async fetchRoomData() {
            if (!this.activeIncidentId) return;

            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/incidents/${this.activeIncidentId}/room?type=${this.activeIncidentType}`, {
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {}
                });

                if (res.ok) {
                    const data = await res.json();
                    this.renderRoomContent(data);
                }
            } catch (err) {
                console.warn('Failed to load incident room data:', err);
            }
        }

        renderRoomContent(data) {
            const container = document.getElementById('room-timeline-stream');
            if (!container) return;

            const timeline = data.timeline || [];
            const mediaList = data.media || [];
            const report = data.report || null;
            const task = data.task || null;

            let html = '';

            // Initial incident banner card
            if (report) {
                html += `
                <div class="timeline-milestone-card">
                    <div class="milestone-icon-col">
                        <div class="milestone-node">🚨</div>
                    </div>
                    <div class="milestone-content-col">
                        <div class="milestone-header">
                            <span class="milestone-actor-tag">
                                <b>ORIGINAL CITIZEN REPORT</b>
                                <span class="role-badge citizen">Citizen</span>
                            </span>
                            <span class="milestone-timestamp">${report.timestamp ? new Date(report.timestamp).toLocaleString() : ''}</span>
                        </div>
                        <div class="milestone-body-text" style="font-weight:600; color:#fff;">
                            ${escapeHtml(report.message || '')}
                        </div>
                        <div style="font-size:0.75rem; color:#00e5ff; margin-top:4px;">
                            📍 Location: ${escapeHtml(report.location || 'Reported Zone')}
                        </div>
                        ${report.photoData ? `
                            <div style="margin-top:8px;">
                                <img src="${report.photoData.startsWith('data:') ? report.photoData : 'data:image/jpeg;base64,' + report.photoData}" 
                                     class="timeline-media-thumb" onclick="window.open(this.src)" alt="Incident report photo" />
                            </div>
                        ` : ''}
                    </div>
                </div>
                `;
            } else if (task) {
                html += `
                <div class="timeline-milestone-card">
                    <div class="milestone-icon-col">
                        <div class="milestone-node">📋</div>
                    </div>
                    <div class="milestone-content-col">
                        <div class="milestone-header">
                            <span class="milestone-actor-tag">
                                <b>TASK DIRECTIVE: ${escapeHtml(task.title || '')}</b>
                                <span class="role-badge admin">Admin</span>
                            </span>
                            <span class="milestone-timestamp">${task.createdAt ? new Date(task.createdAt).toLocaleString() : ''}</span>
                        </div>
                        <div class="milestone-body-text">
                            ${escapeHtml(task.description || '')}
                        </div>
                        <div style="font-size:0.75rem; color:#00e5ff; margin-top:4px;">
                            Zone: <b>${escapeHtml(task.zone || 'Emergency Sector')}</b> | Priority: <b>${escapeHtml(task.priority || 'High')}</b>
                        </div>
                    </div>
                </div>
                `;
            }

            // Timeline items
            timeline.forEach(event => {
                const isChat = event.eventType === 'MESSAGE';
                const isEnRoute = event.eventType === 'EN_ROUTE';
                const isResolved = event.eventType === 'RESOLVED' || event.eventType === 'ON_SCENE';
                const isMedia = event.eventType === 'MEDIA_UPLOADED';

                if (isChat) {
                    const isSelf = this.currentEmail && event.actorEmail &&
                        this.currentEmail.toLowerCase() === event.actorEmail.toLowerCase();
                    const roleClass = (event.actorRole || 'Citizen').toLowerCase();

                    html += `
                    <div class="timeline-chat-msg ${isSelf ? 'self' : 'other'}">
                        <div style="display:flex; justify-content:${isSelf ? 'flex-end' : 'flex-start'}; align-items:center; gap:6px; font-size:0.72rem; color:#94a3b8; padding:0 4px;">
                            <b>${escapeHtml(event.actorName || event.actorEmail || 'Responder')}</b>
                            <span class="role-badge ${roleClass}">${escapeHtml(event.actorRole || 'Citizen')}</span>
                            <span>${event.createdAt ? new Date(event.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</span>
                        </div>
                        <div class="chat-bubble" style="display:inline-flex; align-items:center; gap:8px;">
                            <span>${escapeHtml(event.message || '')}</span>
                            ${(isSelf || (this.currentUserRole && this.currentUserRole.toLowerCase() === 'admin')) ? `
                            <button type="button" onclick="window.IncidentRoom.deleteMessage(${event.id})" style="background:none; border:none; color:rgba(255,255,255,0.4); cursor:pointer; font-size:0.75rem; padding:0; line-height:1;" title="Delete this message">
                                <i class="fas fa-trash-can"></i>
                            </button>` : ''}
                        </div>
                    </div>
                    `;
                } else {
                    let icon = 'ℹ️';
                    let nodeClass = '';
                    if (isEnRoute) { icon = '🚑'; nodeClass = 'en-route'; }
                    else if (isResolved) { icon = '✅'; nodeClass = 'resolved'; }
                    else if (isMedia) { icon = '📷'; }

                    const roleClass = (event.actorRole || 'Citizen').toLowerCase();

                    html += `
                    <div class="timeline-milestone-card">
                        <div class="milestone-icon-col">
                            <div class="milestone-node ${nodeClass}">${icon}</div>
                        </div>
                        <div class="milestone-content-col">
                            <div class="milestone-header">
                                <span class="milestone-actor-tag">
                                    <b>${escapeHtml(event.actorName || event.actorEmail || 'System')}</b>
                                    <span class="role-badge ${roleClass}">${escapeHtml(event.actorRole || 'Event')}</span>
                                </span>
                                <span class="milestone-timestamp">${event.createdAt ? new Date(event.createdAt).toLocaleTimeString() : ''}</span>
                            </div>
                            <div class="milestone-body-text">
                                ${escapeHtml(event.message || '')}
                            </div>
                        </div>
                    </div>
                    `;
                }
            });

            // Media items grid
            if (mediaList.length > 0) {
                html += `
                <div style="margin-top:10px; padding:10px; background:rgba(0,229,255,0.04); border-radius:10px; border:1px solid rgba(0,229,255,0.15);">
                    <div style="font-size:0.78rem; font-weight:700; color:#00e5ff; text-transform:uppercase; letter-spacing:0.05em; margin-bottom:6px;">
                        📂 Field Evidence & Media Transmissions (${mediaList.length})
                    </div>
                    <div class="timeline-media-grid">
                `;

                mediaList.forEach(m => {
                    const downloadUrl = `/api/media/file/${m.storedFilename}`;
                    const isMediaSelf = (this.currentEmail && m.uploaderEmail && this.currentEmail.toLowerCase() === m.uploaderEmail.toLowerCase()) || 
                                        (this.currentUserRole && this.currentUserRole.toLowerCase() === 'admin');
                    html += `<div style="position:relative; display:inline-block; margin:4px;">`;
                    if (m.mediaType === 'VIDEO') {
                        html += `<video src="${downloadUrl}" controls class="timeline-video-player" preload="metadata"></video>`;
                    } else if (m.mediaType === 'PHOTO') {
                        html += `<img src="${downloadUrl}" class="timeline-media-thumb" 
                                      title="${escapeHtml(m.caption || m.originalFilename)}"
                                      onclick="window.open('${downloadUrl}', '_blank')" alt="Incident media" />`;
                    }
                    if (isMediaSelf) {
                        html += `
                        <button type="button" onclick="window.IncidentRoom.deleteMedia(${m.id})" style="position:absolute; top:4px; right:4px; background:rgba(239,68,68,0.85); color:#fff; border:none; border-radius:50%; width:22px; height:22px; font-size:11px; cursor:pointer; display:flex; align-items:center; justify-content:center; box-shadow:0 2px 4px rgba(0,0,0,0.5);" title="Delete evidence file">
                            <i class="fas fa-trash-can"></i>
                        </button>`;
                    }
                    html += `</div>`;
                });

                html += `</div></div>`;
            }

            container.innerHTML = html;
        }

        
        async deleteMessage(eventId) {
            let confirmed = confirm('Delete this message from the incident chronology?');
            if (!confirmed) return;
            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/incidents/timeline/message/${eventId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                if (res.ok) {
                    if (window.CommandCenter && window.CommandCenter.showToast) {
                        window.CommandCenter.showToast('Message deleted', 'info');
                    }
                    this.fetchRoomData();
                } else {
                    const err = await res.json().catch(() => ({}));
                    alert(err.message || 'Failed to delete message');
                }
            } catch (e) {
                alert('Network error: ' + e.message);
            }
        }

        async deleteMedia(mediaId) {
            let confirmed = confirm('Delete this uploaded evidence file?');
            if (!confirmed) return;
            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/media/${mediaId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                if (res.ok) {
                    if (window.CommandCenter && window.CommandCenter.showToast) {
                        window.CommandCenter.showToast('Evidence file deleted', 'info');
                    }
                    this.fetchRoomData();
                } else {
                    const err = await res.json().catch(() => ({}));
                    alert(err.message || 'Failed to delete evidence');
                }
            } catch (e) {
                alert('Network error: ' + e.message);
            }
        }

        async sendMessage() {
            const inputField = document.getElementById('room-msg-input');
            const text = inputField.value.trim();
            if (!text || !this.activeIncidentId) return;

            inputField.value = '';

            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/incidents/${this.activeIncidentId}/timeline/message?type=${this.activeIncidentType}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
                    },
                    body: JSON.stringify({ message: text })
                });

                if (res.ok) {
                    await this.fetchRoomData();
                    // Scroll to bottom
                    const container = document.getElementById('room-timeline-stream');
                    if (container) container.scrollTop = container.scrollHeight;
                }
            } catch (err) {
                console.error('Failed to post message:', err);
            }
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

    // Export singleton
    window.IncidentRoom = new IncidentRoomManager();
})();

