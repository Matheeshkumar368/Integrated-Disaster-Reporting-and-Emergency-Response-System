/**
 * ==============================================================================
 * INCIDENT COLLABORATION ROOM
 * Disaster Management & Alert System - Tactical Command Center
 * 
 * Shared real-time incident coordination, chronological timeline,
 * evidence media gallery, live chat, direct file attachments, and GPS location sharing.
 * 
 * Performance & Memory Guard:
 * - Smart delta-rendering (only updates DOM when data changes)
 * - Safe bounded polling with automatic cleanup on room exit
 * - Zero orphaned timers or unbounded DOM recreations
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
            this.currentUserRole = null;
            this.lastDataFingerprint = null;
            this.isFetching = false;

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
                        <!-- Direct File Attachment -->
                        <button type="button" class="btn-room-file-attach" id="btn-room-attach-file" title="Attach Document / Photo / Video directly">
                            📎
                        </button>
                        <input type="file" id="room-direct-file-input" accept="image/*,video/*" style="display:none;" />

                        <!-- Situation Media Capture Modal (Camera / Recording) -->
                        <button type="button" class="btn-room-media-attach" id="btn-room-attach-media" title="Open Situation Media Camera & Evidence Capture">
                            📷
                        </button>

                        <!-- Share Live Location -->
                        <button type="button" class="btn-room-loc-attach" id="btn-room-share-loc" title="Transmit Current Live GPS Coordinates to Room">
                            📍
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
            const backdrop = document.getElementById('incident-room-modal-backdrop');
            const closeBtn = document.getElementById('btn-room-close');
            const sendBtn = document.getElementById('btn-room-send-msg');
            const inputField = document.getElementById('room-msg-input');
            const attachMediaBtn = document.getElementById('btn-room-attach-media');
            const attachFileBtn = document.getElementById('btn-room-attach-file');
            const directFileInput = document.getElementById('room-direct-file-input');
            const shareLocBtn = document.getElementById('btn-room-share-loc');

            if (closeBtn) closeBtn.addEventListener('click', () => this.close());

            // Backdrop click closes room
            if (backdrop) {
                backdrop.addEventListener('click', (e) => {
                    if (e.target === backdrop) this.close();
                });
            }

            // Escape key closes room
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && backdrop && backdrop.classList.contains('active')) {
                    this.close();
                }
            });

            // Send message
            if (sendBtn) sendBtn.addEventListener('click', () => this.sendMessage());
            if (inputField) {
                inputField.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        this.sendMessage();
                    }
                });
            }

            // Open Media Capture Modal
            if (attachMediaBtn) {
                attachMediaBtn.addEventListener('click', () => {
                    if (window.MediaCapture && this.activeIncidentId) {
                        window.MediaCapture.open({
                            incidentId: this.activeIncidentId,
                            incidentType: this.activeIncidentType,
                            onSuccess: () => this.fetchRoomData(true)
                        });
                    }
                });
            }

            // Direct File Attachment
            if (attachFileBtn && directFileInput) {
                attachFileBtn.addEventListener('click', () => directFileInput.click());
                directFileInput.addEventListener('change', (e) => {
                    if (e.target.files && e.target.files[0]) {
                        this.uploadDirectAttachment(e.target.files[0]);
                        directFileInput.value = '';
                    }
                });
            }

            // Share Live GPS Location in Room
            if (shareLocBtn) {
                shareLocBtn.addEventListener('click', () => this.shareCurrentLocation());
            }
        }

        open(incidentId, incidentType = 'CITIZEN_REPORT', title = '') {
            this.activeIncidentId = incidentId;
            this.activeIncidentType = incidentType;
            this.lastDataFingerprint = null;

            const user = JSON.parse(localStorage.getItem('user') || '{}');
            this.currentEmail = user.email || '';
            this.currentUserRole = user.role || '';

            const backdrop = document.getElementById('incident-room-modal-backdrop');
            const idTag = document.getElementById('room-incident-id-tag');
            if (idTag) idTag.textContent = `#${incidentId} [${incidentType.replace('_', ' ')}]`;

            if (backdrop) backdrop.classList.add('active');

            // Initial fetch
            this.fetchRoomData(true);

            // Auto-refresh every 5 seconds without memory churn
            if (this.pollTimer) clearInterval(this.pollTimer);
            this.pollTimer = setInterval(() => this.fetchRoomData(false), 5000);
        }

        close() {
            if (this.pollTimer) {
                clearInterval(this.pollTimer);
                this.pollTimer = null;
            }
            this.isFetching = false;
            this.lastDataFingerprint = null;
            const backdrop = document.getElementById('incident-room-modal-backdrop');
            if (backdrop) backdrop.classList.remove('active');
        }

        async fetchRoomData(forceRerender = false) {
            if (!this.activeIncidentId || this.isFetching) return;
            this.isFetching = true;

            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/incidents/${this.activeIncidentId}/room?type=${this.activeIncidentType}`, {
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {}
                });

                if (res.ok) {
                    const data = await res.json();
                    const timeline = data.timeline || [];
                    const media = data.media || [];

                    // Fast fingerprint comparison to avoid unnecessary DOM tear-down and rebuild
                    const fingerprint = `${timeline.length}_${media.length}_${timeline.length > 0 ? timeline[timeline.length - 1].id : 0}_${media.length > 0 ? media[media.length - 1].id : 0}`;

                    if (forceRerender || fingerprint !== this.lastDataFingerprint) {
                        this.lastDataFingerprint = fingerprint;
                        this.renderRoomContent(data);
                    }
                }
            } catch (err) {
                console.warn('Incident room poll notice:', err.message);
            } finally {
                this.isFetching = false;
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

            // 1. Initial Incident Directive Card
            if (report) {
                const isResolved = (report.status && report.status.toLowerCase().includes('resolve'));
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
                            ${escapeHtml(report.message || report.title || '')}
                        </div>
                        <div style="font-size:0.75rem; color:#00e5ff; margin-top:4px; display:flex; align-items:center; gap:8px;">
                            <span>📍 Location: ${escapeHtml(report.location || 'Reported Zone')}</span>
                            ${report.latitude && report.longitude ? `
                            <a href="https://www.google.com/maps?q=${report.latitude},${report.longitude}" target="_blank" style="color:#38bdf8; text-decoration:none; font-weight:600;">[View Map]</a>
                            ` : ''}
                        </div>
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

            // 2. Chronological Timeline & Messages
            timeline.forEach(event => {
                const isChat = event.eventType === 'MESSAGE';
                const isEnRoute = event.eventType === 'EN_ROUTE';
                const isResolved = event.eventType === 'RESOLVED' || event.eventType === 'ON_SCENE';
                const isMedia = event.eventType === 'MEDIA_UPLOADED';

                if (isChat) {
                    const isSelf = this.currentEmail && event.actorEmail &&
                        this.currentEmail.toLowerCase() === event.actorEmail.toLowerCase();
                    const roleClass = (event.actorRole || 'Citizen').toLowerCase();

                    // Check if message is a GPS location transmission
                    const isLocationMsg = event.message && event.message.includes('📍 LIVE LOCATION:');
                    let bubbleContent = escapeHtml(event.message || '');

                    if (isLocationMsg) {
                        const match = event.message.match(/📍 LIVE LOCATION:\s*([\d.-]+)[°N,\s]+([\d.-]+)/);
                        if (match) {
                            const lat = match[1];
                            const lng = match[2];
                            bubbleContent = `
                            <div class="chat-location-card">
                                <span>📍 <b>Live Coordinates:</b> ${lat}°N, ${lng}°E</span>
                                <a href="https://www.google.com/maps?q=${lat},${lng}" target="_blank">View on Maps</a>
                            </div>
                            `;
                        }
                    }

                    html += `
                    <div class="timeline-chat-msg ${isSelf ? 'self' : 'other'}">
                        <div style="display:flex; justify-content:${isSelf ? 'flex-end' : 'flex-start'}; align-items:center; gap:6px; font-size:0.72rem; color:#94a3b8; padding:0 4px;">
                            <b>${escapeHtml(event.actorName || event.actorEmail || 'Responder')}</b>
                            <span class="role-badge ${roleClass}">${escapeHtml(event.actorRole || 'Citizen')}</span>
                            <span>${event.createdAt ? new Date(event.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</span>
                        </div>
                        <div class="chat-bubble" style="display:inline-flex; align-items:center; gap:8px;">
                            <span>${bubbleContent}</span>
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

            // 3. Media Evidence Gallery
            if (mediaList.length > 0) {
                html += `
                <div style="margin-top:10px; padding:10px; background:rgba(0,229,255,0.04); border-radius:10px; border:1px solid rgba(0,229,255,0.15);">
                    <div style="font-size:0.78rem; font-weight:700; color:#00e5ff; text-transform:uppercase; letter-spacing:0.05em; margin-bottom:6px;">
                        📂 Field Evidence & Media Transmissions (${mediaList.length})
                    </div>
                    <div class="timeline-media-grid">
                `;

                mediaList.forEach(m => {
                    const fileUrl = `/api/media/file/${encodeURIComponent(m.storedFilename)}`;
                    const isMediaSelf = (this.currentEmail && m.uploaderEmail && this.currentEmail.toLowerCase() === m.uploaderEmail.toLowerCase()) || 
                                        (this.currentUserRole && this.currentUserRole.toLowerCase() === 'admin');
                    html += `<div style="position:relative; display:inline-block; margin:4px;">`;
                    if (m.mediaType === 'VIDEO') {
                        html += `<video src="${fileUrl}" controls class="timeline-video-player" preload="metadata"></video>`;
                    } else {
                        html += `<img src="${fileUrl}" class="timeline-media-thumb" 
                                      title="${escapeHtml(m.caption || m.originalFilename)}"
                                      onclick="window.open('${fileUrl}', '_blank')" alt="Incident media" />`;
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

            const wasAtBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 100;
            container.innerHTML = html;

            // Auto-scroll to bottom if user was already at bottom
            if (wasAtBottom) {
                container.scrollTop = container.scrollHeight;
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
                    await this.fetchRoomData(true);
                    const container = document.getElementById('room-timeline-stream');
                    if (container) container.scrollTop = container.scrollHeight;
                } else {
                    const err = await res.json().catch(() => ({}));
                    this.toast(err.message || 'Failed to post message', 'danger');
                }
            } catch (err) {
                console.error('Failed to post message:', err);
            }
        }

        async shareCurrentLocation() {
            if (!navigator.geolocation) {
                this.toast('Geolocation is not supported by your browser', 'error');
                return;
            }

            this.toast('Acquiring high-accuracy GPS coordinates...', 'info');

            navigator.geolocation.getCurrentPosition(
                async (pos) => {
                    const lat = pos.coords.latitude;
                    const lng = pos.coords.longitude;
                    const acc = pos.coords.accuracy;

                    const confirmed = confirm(`Transmit live GPS coordinates to this Incident Room?\n\nCoordinates: ${lat.toFixed(5)}°N, ${lng.toFixed(5)}°E\nAccuracy: ±${Math.round(acc)} meters`);
                    if (!confirmed) return;

                    const locMsg = `📍 LIVE LOCATION: ${lat.toFixed(5)}°N, ${lng.toFixed(5)}°E (Accuracy: ±${Math.round(acc)}m)`;

                    const token = localStorage.getItem('token');
                    try {
                        const res = await fetch(`/api/incidents/${this.activeIncidentId}/timeline/message?type=${this.activeIncidentType}`, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                ...(token ? { 'Authorization': `Bearer ${token}` } : {})
                            },
                            body: JSON.stringify({ message: locMsg })
                        });

                        if (res.ok) {
                            this.toast('Live GPS coordinates shared in room.', 'success');
                            await this.fetchRoomData(true);
                        }
                    } catch (e) {
                        this.toast('Failed to share location: ' + e.message, 'error');
                    }
                },
                (err) => {
                    this.toast('GPS Access Error: ' + err.message, 'error');
                },
                { enableHighAccuracy: true, timeout: 8000, maximumAge: 10000 }
            );
        }

        async uploadDirectAttachment(file) {
            if (!file || !this.activeIncidentId) return;

            const maxSize = 50 * 1024 * 1024;
            if (file.size > maxSize) {
                this.toast('File size exceeds the 50MB limit.', 'error');
                return;
            }

            this.toast('Uploading attachment...', 'info');

            const isVideo = file.type.startsWith('video');
            const extension = isVideo ? '.webm' : '.jpg';
            const filename = `chat_attach_${Date.now()}${extension}`;

            const formData = new FormData();
            formData.append('file', file, filename);
            formData.append('incidentId', this.activeIncidentId);
            formData.append('incidentType', this.activeIncidentType);
            formData.append('caption', 'Direct chat attachment: ' + file.name);

            const token = localStorage.getItem('token');

            try {
                const res = await fetch('/api/media/upload', {
                    method: 'POST',
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {},
                    body: formData
                });

                if (res.ok) {
                    this.toast('Attachment uploaded successfully.', 'success');
                    await this.fetchRoomData(true);
                } else {
                    const err = await res.json().catch(() => ({}));
                    this.toast(err.message || 'Upload failed', 'error');
                }
            } catch (e) {
                this.toast('Attachment upload error: ' + e.message, 'error');
            }
        }

        async deleteMessage(eventId) {
            const confirmed = confirm('Delete this message from the incident chronology?');
            if (!confirmed) return;
            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/incidents/timeline/message/${eventId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                if (res.ok) {
                    this.toast('Message deleted', 'info');
                    this.fetchRoomData(true);
                } else {
                    const err = await res.json().catch(() => ({}));
                    this.toast(err.message || 'Failed to delete message', 'error');
                }
            } catch (e) {
                this.toast('Network error: ' + e.message, 'error');
            }
        }

        async deleteMedia(mediaId) {
            const confirmed = confirm('Delete this uploaded evidence file?');
            if (!confirmed) return;
            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/media/${mediaId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                if (res.ok) {
                    this.toast('Evidence file deleted', 'info');
                    this.fetchRoomData(true);
                } else {
                    const err = await res.json().catch(() => ({}));
                    this.toast(err.message || 'Failed to delete evidence', 'error');
                }
            } catch (e) {
                this.toast('Network error: ' + e.message, 'error');
            }
        }

        toast(msg, type = 'info') {
            if (window.showToast) {
                window.showToast(msg, type);
            } else if (window.CommandCenter && window.CommandCenter.showToast) {
                window.CommandCenter.showToast(msg, type);
            } else {
                console.log(`[${type.toUpperCase()}] ${msg}`);
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
