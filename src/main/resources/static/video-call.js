/**
 * Emergency WebRTC Video Call Module
 * Integrated Disaster Reporting & Emergency Response Coordination System
 * Real-time peer-to-peer audio/video calling between Responder and Citizen on LAN/WAN.
 */
(function(window) {
    'use strict';

    const EmergencyVideoCall = {
        peerConnection: null,
        localStream: null,
        remoteStream: null,
        currentCallId: null,
        callTimerInterval: null,
        callDurationSec: 0,
        pollInterval: null,
        incomingWatcherInterval: null,
        audioContext: null,
        ringtoneOscillator: null,
        isAudioMuted: false,
        isVideoMuted: false,
        currentUserRole: 'Citizen',

        iceConfig: {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
                { urls: 'stun:stun2.l.google.com:19302' }
            ]
        },

        init: function(role) {
            this.currentUserRole = role || 'Citizen';
            this.injectModalHtml();
            this.bindEvents();

            if (this.currentUserRole.toLowerCase() === 'citizen') {
                this.startIncomingWatcher();
            }
        },

        injectModalHtml: function() {
            if (document.getElementById('emergencyVideoModal')) return;

            const modalHtml = `
            <!-- Fullscreen WebRTC Call Overlay -->
            <div id="emergencyVideoModal" class="call-modal-overlay">
                <div class="call-container">
                    <div class="call-header">
                        <div class="call-peer-info">
                            <span class="call-badge"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#ef4444;animation:pulseGlow 1s infinite;"></span> LIVE ENCRYPTED CALL</span>
                            <span id="callPeerName" class="call-peer-name">Responder Unit</span>
                        </div>
                        <div id="callTimerDisplay" class="call-timer">00:00</div>
                    </div>
                    <div class="call-stage">
                        <div id="callConnectingPlaceholder" class="stage-placeholder">
                            <div class="pulse-ring">📹</div>
                            <div>Connecting secure audio/video channel...</div>
                        </div>
                        <video id="remoteVideoFeed" class="remote-video" autoplay playsinline></video>
                        <div class="pip-video-container">
                            <video id="localVideoFeed" class="local-video" autoplay playsinline muted></video>
                            <span class="pip-badge">You</span>
                        </div>
                    </div>
                    <div class="call-controls">
                        <button id="btnToggleAudio" class="call-btn" title="Toggle Microphone">🎤</button>
                        <button id="btnToggleVideo" class="call-btn" title="Toggle Camera">📹</button>
                        <button id="btnEndVideoCall" class="call-btn btn-end" title="End Call">📵</button>
                    </div>
                </div>
            </div>

            <!-- Incoming Call Banner Dialog -->
            <div id="incomingEmergencyCallModal" class="incoming-call-modal">
                <div class="incoming-call-header">
                    <div class="incoming-call-icon">🚨</div>
                    <div>
                        <div class="incoming-call-title">Emergency Video Dispatch</div>
                        <div id="incomingCallerName" class="incoming-call-caller">NDRF Tactical Unit</div>
                        <div id="incomingCallIncident" class="incoming-call-incident">Incident #188</div>
                    </div>
                </div>
                <div class="incoming-call-actions">
                    <button id="btnAcceptIncomingCall" class="btn-call-accept">📹 Accept Call</button>
                    <button id="btnRejectIncomingCall" class="btn-call-reject">Decline</button>
                </div>
            </div>
            `;

            document.body.insertAdjacentHTML('beforeend', modalHtml);
        },

        bindEvents: function() {
            const btnEnd = document.getElementById('btnEndVideoCall');
            if (btnEnd) btnEnd.onclick = () => this.endCall();

            const btnMute = document.getElementById('btnToggleAudio');
            if (btnMute) btnMute.onclick = () => this.toggleAudio();

            const btnCam = document.getElementById('btnToggleVideo');
            if (btnCam) btnCam.onclick = () => this.toggleVideo();

            const btnAccept = document.getElementById('btnAcceptIncomingCall');
            if (btnAccept) {
                btnAccept.onclick = () => {
                    const callId = btnAccept.dataset.callId;
                    const sdpOffer = btnAccept.dataset.sdpOffer;
                    this.answerCall(callId, sdpOffer);
                };
            }

            const btnReject = document.getElementById('btnRejectIncomingCall');
            if (btnReject) {
                btnReject.onclick = () => {
                    const callId = btnReject.dataset.callId;
                    this.rejectCall(callId);
                };
            }
        },

        getAuthHeaders: function() {
            const roleKey = this.currentUserRole ? (this.currentUserRole.charAt(0).toUpperCase() + this.currentUserRole.slice(1).toLowerCase()) : '';
            let token = '';
            if (window.AuthHelper && typeof window.AuthHelper.getToken === 'function') {
                token = window.AuthHelper.getToken(roleKey || undefined);
            }
            if (!token && roleKey) {
                token = sessionStorage.getItem('token_' + roleKey) || localStorage.getItem('token_' + roleKey) ||
                        sessionStorage.getItem(roleKey.toLowerCase() + '_token') || '';
            }
            if (!token) {
                token = sessionStorage.getItem('token_Citizen') || sessionStorage.getItem('token_Admin') || sessionStorage.getItem('token_Responder') ||
                        localStorage.getItem('token_Citizen') || localStorage.getItem('token_Admin') || localStorage.getItem('token_Responder') ||
                        sessionStorage.getItem('token') || localStorage.getItem('token') || '';
            }
            return {
                'Content-Type': 'application/json',
                ...(token ? { 'Authorization': `Bearer ${token}` } : {})
            };
        },

        isLanHttpOrigin: function() {
            const h = window.location.hostname;
            return !window.isSecureContext && h !== 'localhost' && h !== '127.0.0.1';
        },

        /**
         * Responder initiates call to Citizen
         */
        startCall: async function(incidentId, citizenEmail, citizenName, incidentType) {
            try {
                this.showCallModal(citizenName || 'Citizen');
                document.getElementById('callConnectingPlaceholder').style.display = 'flex';

                await this.initLocalStream();

                this.peerConnection = new RTCPeerConnection(this.iceConfig);
                this.setupPeerListeners('RESPONDER');

                // Add local tracks to RTCPeerConnection if hardware stream is available
                if (this.localStream) {
                    this.localStream.getTracks().forEach(track => {
                        this.peerConnection.addTrack(track, this.localStream);
                    });
                }

                // Create SDP Offer
                const offer = await this.peerConnection.createOffer({
                    offerToReceiveAudio: true,
                    offerToReceiveVideo: true
                });
                await this.peerConnection.setLocalDescription(offer);

                const res = await fetch('/api/calls/initiate', {
                    method: 'POST',
                    headers: this.getAuthHeaders(),
                    body: JSON.stringify({
                        incidentId: incidentId,
                        incidentType: incidentType || null,
                        citizenEmail: citizenEmail,
                        citizenName: citizenName,
                        sdpOffer: JSON.stringify(offer)
                    })
                });

                if (!res.ok) {
                    throw new Error('Failed to initiate emergency call on server (Status ' + res.status + ')');
                }

                const data = await res.json();
                this.currentCallId = data.callId;

                // Start polling for citizen's SDP answer
                this.pollCallSession();
            } catch (err) {
                console.error('[VIDEO-CALL] Initiation error:', err);
                alert('Could not start emergency video call: ' + (err.message || err));
                this.cleanup();
            }
        },

        setupPeerListeners: function(role) {
            this.peerConnection.onicecandidate = (event) => {
                if (event.candidate && this.currentCallId) {
                    fetch(`/api/calls/${this.currentCallId}/ice-candidate`, {
                        method: 'POST',
                        headers: this.getAuthHeaders(),
                        body: JSON.stringify({
                            role: role,
                            candidate: JSON.stringify(event.candidate)
                        })
                    }).catch(e => console.warn('[VIDEO-CALL] Failed to transmit ICE candidate:', e));
                }
            };

            this.peerConnection.ontrack = (event) => {
                console.log('[VIDEO-CALL] Received remote track:', event.track.kind);
                const remoteVideo = document.getElementById('remoteVideoFeed');
                if (remoteVideo) {
                    if (!this.remoteStream) {
                        this.remoteStream = new MediaStream();
                        remoteVideo.srcObject = this.remoteStream;
                    }
                    this.remoteStream.addTrack(event.track);
                    document.getElementById('callConnectingPlaceholder').style.display = 'none';
                }
            };

            this.peerConnection.onconnectionstatechange = () => {
                console.log('[VIDEO-CALL] Connection state:', this.peerConnection.connectionState);
                if (this.peerConnection.connectionState === 'connected') {
                    document.getElementById('callConnectingPlaceholder').style.display = 'none';
                    this.startCallTimer();
                } else if (['disconnected', 'failed', 'closed'].includes(this.peerConnection.connectionState)) {
                    this.endCall();
                }
            };
        },

        initLocalStream: async function() {
            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                const msg = this.isLanHttpOrigin()
                    ? `Camera/Mic blocked on plain HTTP LAN (${window.location.origin}). Use HTTPS or enable chrome://flags/#unsafely-treat-insecure-origin-as-secure for ${window.location.origin}. Continuing in receive-only mode.`
                    : 'Camera/Microphone API is unavailable in this browser. Continuing in receive-only mode.';
                console.warn('[VIDEO-CALL]', msg);
                if (window.showToast) window.showToast(msg, 'warning');
                this.localStream = null;
                return null;
            }
            try {
                this.localStream = await navigator.mediaDevices.getUserMedia({
                    audio: true,
                    video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' }
                });
                const localVideo = document.getElementById('localVideoFeed');
                if (localVideo) localVideo.srcObject = this.localStream;
                return this.localStream;
            } catch (e) {
                console.warn('[VIDEO-CALL] Camera/mic access denied, attempting audio only:', e);
                try {
                    this.localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
                    return this.localStream;
                } catch (audioErr) {
                    console.warn('[VIDEO-CALL] Audio-only fallback also unavailable:', audioErr);
                    if (window.showToast) window.showToast('Camera/Mic permission denied. Connecting in receive-only mode.', 'warning');
                    this.localStream = null;
                    return null;
                }
            }
        },

        /**
         * Citizen incoming call detection
         */
        startIncomingWatcher: function() {
            if (this.incomingWatcherInterval) clearInterval(this.incomingWatcherInterval);
            this.incomingWatcherInterval = setInterval(async () => {
                if (document.hidden) return;
                // If already in a call, skip
                if (this.currentCallId) return;

                const headers = this.getAuthHeaders();
                if (!headers['Authorization']) return;

                try {
                    const res = await fetch('/api/calls/incoming', {
                        headers: headers
                    });
                    if (!res.ok) return;

                    const data = await res.json();
                    if (data.hasIncomingCall && data.call) {
                        this.showIncomingCallBanner(data.call);
                    } else {
                        this.hideIncomingCallBanner();
                    }
                } catch (e) {
                    // Suppress polling network error
                }
            }, 3000);
        },

        showIncomingCallBanner: function(call) {
            const modal = document.getElementById('incomingEmergencyCallModal');
            if (!modal) return;

            document.getElementById('incomingCallerName').textContent = call.responderName || call.responderEmail || 'Emergency Responder';
            document.getElementById('incomingCallIncident').textContent = `Incident #${call.incidentId} • Tactical Priority`;

            const btnAccept = document.getElementById('btnAcceptIncomingCall');
            btnAccept.dataset.callId = call.callId;
            btnAccept.dataset.sdpOffer = call.sdpOffer;

            const btnReject = document.getElementById('btnRejectIncomingCall');
            btnReject.dataset.callId = call.callId;

            modal.classList.add('active');
            this.playEmergencyRingtone();
        },

        hideIncomingCallBanner: function() {
            const modal = document.getElementById('incomingEmergencyCallModal');
            if (modal) modal.classList.remove('active');
            this.stopEmergencyRingtone();
        },

        /**
         * Citizen answers incoming call
         */
        answerCall: async function(callId, sdpOfferJson) {
            try {
                this.hideIncomingCallBanner();
                this.currentCallId = callId;

                const callerName = document.getElementById('incomingCallerName').textContent;
                this.showCallModal(callerName);
                document.getElementById('callConnectingPlaceholder').style.display = 'flex';

                await this.initLocalStream();

                this.peerConnection = new RTCPeerConnection(this.iceConfig);
                this.setupPeerListeners('CITIZEN');

                if (this.localStream) {
                    this.localStream.getTracks().forEach(track => {
                        this.peerConnection.addTrack(track, this.localStream);
                    });
                }

                // Parse and set Remote Offer
                const offerDesc = new RTCSessionDescription(JSON.parse(sdpOfferJson));
                await this.peerConnection.setRemoteDescription(offerDesc);

                // Create SDP Answer
                const answer = await this.peerConnection.createAnswer();
                await this.peerConnection.setLocalDescription(answer);

                // Send Answer to backend
                const res = await fetch(`/api/calls/${callId}/answer`, {
                    method: 'POST',
                    headers: this.getAuthHeaders(),
                    body: JSON.stringify({ sdpAnswer: JSON.stringify(answer) })
                });

                if (!res.ok) {
                    throw new Error('Failed to transmit answer to emergency dispatch.');
                }

                // Poll for caller's ICE candidates
                this.pollCallSession();
            } catch (err) {
                console.error('[VIDEO-CALL] Error answering call:', err);
                alert('Could not answer call: ' + (err.message || err));
                this.cleanup();
            }
        },

        rejectCall: async function(callId) {
            this.hideIncomingCallBanner();
            try {
                await fetch(`/api/calls/${callId}/reject`, {
                    method: 'POST',
                    headers: this.getAuthHeaders(),
                    body: JSON.stringify({ reason: 'Declined by Citizen' })
                });
            } catch (e) {
                console.warn('[VIDEO-CALL] Reject call transmission error:', e);
            }
        },

        pollCallSession: function() {
            if (this.pollInterval) clearInterval(this.pollInterval);
            let processedCandidates = new Set();

            this.pollInterval = setInterval(async () => {
                if (!this.currentCallId) {
                    clearInterval(this.pollInterval);
                    return;
                }

                try {
                    const res = await fetch(`/api/calls/${this.currentCallId}/poll`, {
                        headers: this.getAuthHeaders()
                    });
                    if (!res.ok) return;

                    const data = await res.json();
                    const call = data.call;

                    // If call was ended or rejected by the other party
                    if (['REJECTED', 'ENDED', 'MISSED'].includes(call.status)) {
                        clearInterval(this.pollInterval);
                        alert(`Call ended (${call.status.toLowerCase()}).`);
                        this.cleanup();
                        return;
                    }

                    // If we are Responder and citizen has answered
                    if (this.currentUserRole.toLowerCase() !== 'citizen' &&
                        call.sdpAnswer &&
                        this.peerConnection &&
                        !this.peerConnection.remoteDescription) {
                        const answerDesc = new RTCSessionDescription(JSON.parse(call.sdpAnswer));
                        await this.peerConnection.setRemoteDescription(answerDesc);
                        document.getElementById('callConnectingPlaceholder').style.display = 'none';
                    }

                    // Exchange ICE Candidates
                    const candidatesStr = (this.currentUserRole.toLowerCase() === 'citizen')
                        ? call.responderCandidatesJson
                        : call.citizenCandidatesJson;

                    if (candidatesStr && candidatesStr !== '[]' && this.peerConnection && this.peerConnection.remoteDescription) {
                        try {
                            const list = JSON.parse(candidatesStr);
                            for (const c of list) {
                                const candStr = JSON.stringify(c);
                                if (!processedCandidates.has(candStr)) {
                                    processedCandidates.add(candStr);
                                    await this.peerConnection.addIceCandidate(new RTCIceCandidate(c));
                                }
                            }
                        } catch (e) {
                            // Suppress candidate parsing noise
                        }
                    }
                } catch (e) {
                    console.warn('[VIDEO-CALL] Poll error:', e);
                }
            }, 1500);
        },

        endCall: async function() {
            if (this.currentCallId) {
                try {
                    await fetch(`/api/calls/${this.currentCallId}/end`, {
                        method: 'POST',
                        headers: this.getAuthHeaders()
                    });
                } catch (e) {
                    console.warn('[VIDEO-CALL] End call request failed:', e);
                }
            }
            this.cleanup();
        },

        cleanup: function() {
            if (this.pollInterval) {
                clearInterval(this.pollInterval);
                this.pollInterval = null;
            }

            if (this.callTimerInterval) {
                clearInterval(this.callTimerInterval);
                this.callTimerInterval = null;
            }

            // CRITICAL HARDWARE & MEMORY RELEASE: Stop all tracks to free webcam and mic immediately
            if (this.localStream) {
                this.localStream.getTracks().forEach(t => {
                    try { t.stop(); } catch (e) {}
                });
                this.localStream = null;
            }

            if (this.remoteStream) {
                this.remoteStream.getTracks().forEach(t => {
                    try { t.stop(); } catch (e) {}
                });
                this.remoteStream = null;
            }

            const localVid = document.getElementById('localVideoFeed');
            if (localVid) localVid.srcObject = null;

            const remoteVid = document.getElementById('remoteVideoFeed');
            if (remoteVid) remoteVid.srcObject = null;

            if (this.peerConnection) {
                try { this.peerConnection.close(); } catch (e) {}
                this.peerConnection = null;
            }

            this.currentCallId = null;
            this.callDurationSec = 0;
            this.isAudioMuted = false;
            this.isVideoMuted = false;

            const modal = document.getElementById('emergencyVideoModal');
            if (modal) modal.classList.remove('active');

            this.stopEmergencyRingtone();
        },

        toggleAudio: function() {
            if (!this.localStream) return;
            const audioTracks = this.localStream.getAudioTracks();
            if (audioTracks.length > 0) {
                this.isAudioMuted = !this.isAudioMuted;
                audioTracks[0].enabled = !this.isAudioMuted;

                const btn = document.getElementById('btnToggleAudio');
                if (btn) {
                    btn.classList.toggle('btn-active-off', this.isAudioMuted);
                    btn.textContent = this.isAudioMuted ? '🔇' : '🎤';
                }
            }
        },

        toggleVideo: function() {
            if (!this.localStream) return;
            const videoTracks = this.localStream.getVideoTracks();
            if (videoTracks.length > 0) {
                this.isVideoMuted = !this.isVideoMuted;
                videoTracks[0].enabled = !this.isVideoMuted;

                const btn = document.getElementById('btnToggleVideo');
                if (btn) {
                    btn.classList.toggle('btn-active-off', this.isVideoMuted);
                    btn.textContent = this.isVideoMuted ? '🚫' : '📹';
                }
            }
        },

        showCallModal: function(peerName) {
            const modal = document.getElementById('emergencyVideoModal');
            if (modal) modal.classList.add('active');

            const nameEl = document.getElementById('callPeerName');
            if (nameEl) nameEl.textContent = peerName || 'Emergency Dispatch';

            const timerEl = document.getElementById('callTimerDisplay');
            if (timerEl) timerEl.textContent = '00:00';
        },

        startCallTimer: function() {
            if (this.callTimerInterval) clearInterval(this.callTimerInterval);
            this.callDurationSec = 0;
            this.callTimerInterval = setInterval(() => {
                this.callDurationSec++;
                const mins = String(Math.floor(this.callDurationSec / 60)).padStart(2, '0');
                const secs = String(this.callDurationSec % 60).padStart(2, '0');
                const timerEl = document.getElementById('callTimerDisplay');
                if (timerEl) timerEl.textContent = `${mins}:${secs}`;
            }, 1000);
        },

        // Web Audio API emergency synthesizer chime
        playEmergencyRingtone: function() {
            try {
                if (!this.audioContext) {
                    this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
                }
                if (this.audioContext.state === 'suspended') {
                    this.audioContext.resume();
                }

                // Dual frequency pulsating chime (880Hz + 660Hz)
                if (this.ringtoneOscillator) return;

                const osc = this.audioContext.createOscillator();
                const gain = this.audioContext.createGain();

                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(880, this.audioContext.currentTime);

                // Pulse volume
                gain.gain.setValueAtTime(0.12, this.audioContext.currentTime);

                osc.connect(gain);
                gain.connect(this.audioContext.destination);
                osc.start();

                this.ringtoneOscillator = osc;
            } catch (e) {
                // Audio synthesis not permitted without prior user gesture
            }
        },

        stopEmergencyRingtone: function() {
            if (this.ringtoneOscillator) {
                try {
                    this.ringtoneOscillator.stop();
                    this.ringtoneOscillator.disconnect();
                } catch (e) {}
                this.ringtoneOscillator = null;
            }
        }
    };

    window.EmergencyVideoCall = EmergencyVideoCall;

})(window);
