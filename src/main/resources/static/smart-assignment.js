/**
 * ==============================================================================
 * SMART RESPONDER ASSIGNMENT MODAL (Feature 6)
 * Disaster Management & Alert System
 * 
 * Algorithmic dispatch matching: ranks field units by geographic proximity,
 * real-time workload, classification skills, and local zone alignment.
 * ==============================================================================
 */

(function () {
    'use strict';

    class SmartAssignmentManager {
        constructor() {
            this.activeTaskId = null;
            this.onAssignCallback = null;

            this.injectModal();
            this.bindEvents();
        }

        injectModal() {
            if (document.getElementById('smart-assign-backdrop')) return;

            const html = `
            <div id="smart-assign-backdrop" class="smart-assign-backdrop" role="dialog" aria-modal="true" aria-labelledby="smart-assign-heading">
                <div class="smart-assign-container">
                    <div class="smart-assign-header">
                        <div class="smart-assign-title" id="smart-assign-heading">
                            <span>🧠</span> SMART RESPONDER DISPATCH & MATCHING
                        </div>
                        <button type="button" class="media-modal-close" id="btn-smart-assign-close" aria-label="Close dispatch matching">&times;</button>
                    </div>

                    <div id="smart-assign-body" class="smart-assign-body">
                        <div style="text-align:center; padding:2rem; color:#64748b;">
                            Evaluating nearest responders, active workloads, and specializations...
                        </div>
                    </div>
                </div>
            </div>
            `;

            document.body.insertAdjacentHTML('beforeend', html);
        }

        bindEvents() {
            const closeBtn = document.getElementById('btn-smart-assign-close');
            if (closeBtn) closeBtn.addEventListener('click', () => this.close());
        }

        open(taskId, onAssignCallback) {
            this.activeTaskId = taskId;
            this.onAssignCallback = onAssignCallback;

            const backdrop = document.getElementById('smart-assign-backdrop');
            backdrop.classList.add('active');

            this.fetchRecommendations(taskId);
        }

        close() {
            const backdrop = document.getElementById('smart-assign-backdrop');
            if (backdrop) backdrop.classList.remove('active');
        }

        async fetchRecommendations(taskId) {
            const container = document.getElementById('smart-assign-body');
            const token = localStorage.getItem('token');

            try {
                const res = await fetch(`/api/admin/assignments/recommendations/${taskId}`, {
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {}
                });

                if (res.ok) {
                    const data = await res.json();
                    this.renderRecommendations(data.recommendations || []);
                } else {
                    const err = await res.json();
                    container.innerHTML = `
                        <div style="padding:1.5rem; color:#ff3366; text-align:center;">
                            <b>Matching Error:</b> ${escapeHtml(err.message || 'Failed to calculate recommendations')}
                        </div>
                    `;
                }
            } catch (err) {
                container.innerHTML = `
                    <div style="padding:1.5rem; color:#ff3366; text-align:center;">
                        Failed to connect to Dispatch Service: ${escapeHtml(err.message)}
                    </div>
                `;
            }
        }

        renderRecommendations(list) {
            const container = document.getElementById('smart-assign-body');
            if (!container) return;

            if (list.length === 0) {
                container.innerHTML = `
                    <div style="text-align:center; padding:2rem; color:#94a3b8;">
                        No registered field responders found.
                    </div>
                `;
                return;
            }

            let html = '';
            list.forEach((rec, idx) => {
                const isTop = idx === 0;
                const badges = rec.badges || [];

                html += `
                <div class="responder-card-recommendation ${isTop ? 'top-rank' : ''}">
                    <div class="rec-rank-badge">#${rec.rank}</div>
                    
                    <div class="rec-info-col">
                        <div class="rec-responder-name">
                            ${escapeHtml(rec.responderName || rec.responderEmail)}
                            <span style="font-size:0.75rem; color:#94a3b8; font-weight:normal;">(${escapeHtml(rec.classification || 'Rescuer')})</span>
                        </div>
                        
                        <div class="rec-metrics-row">
                            <span>📍 ${rec.distanceKm ? rec.distanceKm + ' km away' : (rec.assignedZone ? 'Zone ' + rec.assignedZone : 'Location pending')}</span>
                            <span>⚡ Active Tasks: <b>${rec.activeTasks}</b></span>
                            <span>📞 ${escapeHtml(rec.phone || 'N/A')}</span>
                        </div>

                        <div class="rec-badges-row">
                            ${badges.includes('AVAILABLE_NOW') ? '<span class="rec-pill available">⚡ Available Now</span>' : ''}
                            ${badges.includes('PROXIMITY_ALERT') ? '<span class="rec-pill proximity">📍 High Proximity</span>' : ''}
                            ${badges.includes('LOCAL_ZONE') ? '<span class="rec-pill zone">🏢 Local Sector</span>' : ''}
                            ${badges.includes('SPECIALIST_MATCH') ? '<span class="rec-pill specialist">⭐ Specialist Match</span>' : ''}
                        </div>
                    </div>

                    <div class="rec-score-box">
                        <div class="rec-score-num">${rec.matchScore}%</div>
                        <div class="rec-score-label">Match Score</div>
                    </div>

                    <button type="button" class="btn-assign-deploy" 
                            data-email="${escapeHtml(rec.responderEmail)}"
                            data-name="${escapeHtml(rec.responderName || rec.responderEmail)}">
                        Deploy Unit
                    </button>
                </div>
                `;
            });

            container.innerHTML = html;

            // Bind deploy buttons
            container.querySelectorAll('.btn-assign-deploy').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    const email = btn.dataset.email;
                    const name = btn.dataset.name;
                    this.deployResponder(email, name);
                });
            });
        }

        async deployResponder(email, name) {
            if (!this.activeTaskId) return;

            const token = localStorage.getItem('token');
            try {
                const res = await fetch(`/api/tasks/${this.activeTaskId}/assign?responderEmail=${encodeURIComponent(email)}`, {
                    method: 'PUT',
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {}
                });

                const data = await res.json();
                if (res.ok && data.status === 'success') {
                    if (window.showToast) {
                        window.showToast(`Task #${this.activeTaskId} successfully dispatched to ${name}!`, 'success');
                    } else if (window.CommandCenter && window.CommandCenter.showToast) {
                        window.CommandCenter.showToast(`Task #${this.activeTaskId} successfully dispatched to ${name}!`, 'success');
                    }

                    if (typeof this.onAssignCallback === 'function') {
                        this.onAssignCallback(data);
                    }

                    this.close();
                } else {
                    const errMsg = data.message || 'Error assigning task';
                    if (window.showToast) {
                        window.showToast(`Assignment failed: ${errMsg}`, 'error');
                    } else {
                        console.error('Assignment failed:', errMsg);
                    }
                }
            } catch (err) {
                if (window.showToast) {
                    window.showToast(`Network error: ${err.message}`, 'error');
                } else {
                    console.error('Network error:', err);
                }
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

    window.SmartAssignment = new SmartAssignmentManager();
})();

