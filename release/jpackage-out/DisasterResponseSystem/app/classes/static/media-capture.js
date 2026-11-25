/**
 * ==============================================================================
 * MEDIA CAPTURE & EVIDENCE PIPELINE
 * Disaster Management & Alert System - Tactical Command Center
 * 
 * Reusable Camera, Video Recording & Media Geolocation Component for
 * Citizen, Responder, and Admin Dashboards.
 * 
 * Zero-recursion state architecture: Guarantees zero memory leaks and
 * zero browser call-stack exhaustion.
 * ==============================================================================
 */

(function () {
    'use strict';

    class MediaCaptureManager {
        constructor() {
            this.activeStream = null;
            this.mediaRecorder = null;
            this.recordedChunks = [];
            this.currentMode = 'photo'; // 'photo', 'video', 'file'
            this.capturedBlob = null;
            this.capturedMediaType = null; // 'PHOTO', 'VIDEO'
            this.facingMode = 'environment'; // 'environment' (rear) or 'user' (selfie)
            this.recordingTimer = null;
            this.recordingSeconds = 0;
            this.currentGps = null;
            this.activeOptions = null;
            this.previewObjectUrl = null;
            this.isInitializingCamera = false;

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

        resolveAuthToken() {
            if (window.AuthHelper && typeof window.AuthHelper.getToken === 'function') {
                const t = window.AuthHelper.getToken();
                if (t) return t;
            }
            return sessionStorage.getItem('token_Citizen') || sessionStorage.getItem('token_Admin') || sessionStorage.getItem('token_Responder') ||
                   localStorage.getItem('token_Citizen') || localStorage.getItem('token_Admin') || localStorage.getItem('token_Responder') ||
                   sessionStorage.getItem('token') || localStorage.getItem('token') ||
                   sessionStorage.getItem('citizen_token') || sessionStorage.getItem('admin_token') || sessionStorage.getItem('responder_token') || '';
        }

        injectModal() {
            if (document.getElementById('media-capture-modal-backdrop')) return;
            if (!document.body) return;

            const html = `
            <div id="media-capture-modal-backdrop" class="media-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="media-modal-heading">
                <div class="media-modal-container">
                    <div class="media-modal-header">
                        <div class="media-modal-title" id="media-modal-heading">
                            <span>📸</span> SITUATION MEDIA CAPTURE
                        </div>
                        <button type="button" class="media-modal-close" id="btn-media-close" aria-label="Close capture modal">&times;</button>
                    </div>

                    <div class="media-modal-body">
                        <!-- Mode Tabs -->
                        <div class="media-tabs" role="tablist">
                            <button type="button" class="media-tab-btn active" data-mode="photo" role="tab" aria-selected="true">
                                📷 Take Photo
                            </button>
                            <button type="button" class="media-tab-btn" data-mode="video" role="tab" aria-selected="false">
                                🎥 Record Video
                            </button>
                            <button type="button" class="media-tab-btn" data-mode="file" role="tab" aria-selected="false">
                                📁 Choose File
                            </button>
                        </div>

                        <!-- Camera / Preview Area -->
                        <div class="camera-viewport-wrap" id="camera-viewport">
                            <video id="camera-video-preview" class="camera-preview-video" autoplay playsinline muted></video>
                            <img id="camera-image-preview" class="camera-preview-image" style="display:none;" alt="Captured preview" />
                            <video id="camera-video-replay" class="camera-preview-video" controls style="display:none;"></video>
                            
                            <!-- Recording HUD -->
                            <div id="recording-hud" class="recording-badge" style="display:none;">
                                <span>REC</span> <span id="recording-timer-text">00:00</span>
                            </div>

                            <!-- Camera Switcher -->
                            <button type="button" id="btn-camera-switch" class="camera-switch-btn" title="Switch Camera" aria-label="Switch front or back camera">
                                🔄
                            </button>

                            <!-- GPS Status Overlay -->
                            <div class="telemetry-overlay">
                                <div>
                                    <span id="gps-indicator-dot" class="gps-status-dot searching"></span>
                                    <span id="gps-coords-text">Acquiring high-accuracy GPS...</span>
                                </div>
                                <span id="gps-accuracy-text">± -- m</span>
                            </div>
                        </div>

                        <!-- Dropzone Fallback (For File Mode) -->
                        <div id="media-dropzone" class="media-dropzone" style="display:none;">
                            <div class="media-dropzone-icon">📤</div>
                            <div style="font-weight:600; margin-bottom:4px;" id="dropzone-label">Drag and drop photo/video or browse device</div>
                            <div style="font-size:0.75rem; color:#94a3b8;">Supports JPG, PNG, WEBP, MP4, WEBM (Max 50MB)</div>
                            <input type="file" id="media-native-input" accept="image/*,video/*" style="display:none;" />
                        </div>

                        <!-- Action Controls (Shutter, Retake) -->
                        <div class="capture-controls-bar">
                            <button type="button" id="btn-media-retake" class="btn-media-secondary" style="display:none;">
                                🔄 Retake
                            </button>
                            <button type="button" id="btn-shutter-action" class="btn-capture-shutter" title="Take Photo" aria-label="Capture button">
                            </button>
                        </div>

                        <!-- Caption & Details Form -->
                        <div class="media-meta-form">
                            <div class="media-input-group">
                                <label for="media-caption-input">Observation Caption / Damage Description</label>
                                <input type="text" id="media-caption-input" class="media-input-text" placeholder="e.g. Flooding reached ground floor, 3 families trapped..." />
                            </div>
                        </div>
                    </div>

                    <div class="media-modal-footer">
                        <button type="button" class="btn-media-secondary" id="btn-media-cancel">Cancel</button>
                        <button type="button" class="btn-media-upload-confirm" id="btn-media-confirm" disabled>
                            <span>⬆️</span> Confirm & Transmit Evidence
                        </button>
                    </div>
                </div>
            </div>
            `;

            document.body.insertAdjacentHTML('beforeend', html);
        }

        bindEvents() {
            const backdrop = document.getElementById('media-capture-modal-backdrop');
            const closeBtn = document.getElementById('btn-media-close');
            const cancelBtn = document.getElementById('btn-media-cancel');
            const confirmBtn = document.getElementById('btn-media-confirm');
            const shutterBtn = document.getElementById('btn-shutter-action');
            const retakeBtn = document.getElementById('btn-media-retake');
            const switchBtn = document.getElementById('btn-camera-switch');
            const dropzone = document.getElementById('media-dropzone');
            const nativeInput = document.getElementById('media-native-input');
            const tabButtons = document.querySelectorAll('.media-tab-btn');

            if (closeBtn) closeBtn.addEventListener('click', () => this.close());
            if (cancelBtn) cancelBtn.addEventListener('click', () => this.close());

            // Backdrop click closes modal
            if (backdrop) {
                backdrop.addEventListener('click', (e) => {
                    if (e.target === backdrop) this.close();
                });
            }

            // Escape key closes modal
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && backdrop && backdrop.classList.contains('active')) {
                    this.close();
                }
            });

            // Tab switching
            tabButtons.forEach(btn => {
                btn.addEventListener('click', () => {
                    tabButtons.forEach(b => b.classList.remove('active'));
                    btn.classList.add('active');
                    this.switchMode(btn.dataset.mode);
                });
            });

            // Shutter action (Photo capture or Video record start/stop)
            if (shutterBtn) {
                shutterBtn.addEventListener('click', () => {
                    if (this.currentMode === 'photo') {
                        this.takePhoto();
                    } else if (this.currentMode === 'video') {
                        if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
                            this.stopVideoRecording();
                        } else {
                            this.startVideoRecording();
                        }
                    }
                });
            }

            if (retakeBtn) retakeBtn.addEventListener('click', () => this.retake());
            if (switchBtn) switchBtn.addEventListener('click', () => this.toggleCameraFacing());

            // Dropzone click & Native input change
            if (dropzone && nativeInput) {
                dropzone.addEventListener('click', () => nativeInput.click());
                nativeInput.addEventListener('change', (e) => {
                    if (e.target.files && e.target.files[0]) {
                        this.handleFileSelected(e.target.files[0]);
                    }
                });

                // Drag and drop support
                dropzone.addEventListener('dragover', (e) => {
                    e.preventDefault();
                    dropzone.classList.add('dragover');
                });
                dropzone.addEventListener('dragleave', () => {
                    dropzone.classList.remove('dragover');
                });
                dropzone.addEventListener('drop', (e) => {
                    e.preventDefault();
                    dropzone.classList.remove('dragover');
                    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
                        this.handleFileSelected(e.dataTransfer.files[0]);
                    }
                });
            }

            // Confirm upload
            if (confirmBtn) {
                confirmBtn.addEventListener('click', () => this.uploadCapturedMedia());
            }

            // Caption Enter key triggers upload
            const captionInput = document.getElementById('media-caption-input');
            if (captionInput) {
                captionInput.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        if (confirmBtn && !confirmBtn.disabled) {
                            this.uploadCapturedMedia();
                        }
                    }
                });
            }
        }

        open(options = {}) {
            if (!document.getElementById('media-capture-modal-backdrop')) {
                this.injectModal();
                this.bindEvents();
            }

            this.activeOptions = options;
            const backdrop = document.getElementById('media-capture-modal-backdrop');
            if (!backdrop) return;
            backdrop.classList.add('active');

            // Reset all state without recursion
            this.resetCaptureState();
            const captionInput = document.getElementById('media-caption-input');
            if (captionInput) captionInput.value = '';

            // Reset tab highlights
            const tabs = document.querySelectorAll('.media-tab-btn');
            tabs.forEach(t => t.classList.toggle('active', t.dataset.mode === 'photo'));

            // Acquire high-accuracy GPS
            this.acquireLocation();

            // Open photo mode safely
            this.switchMode('photo');
        }

        close() {
            this.stopMediaStream();
            if (this.recordingTimer) {
                clearInterval(this.recordingTimer);
                this.recordingTimer = null;
            }
            if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
                try { this.mediaRecorder.stop(); } catch (e) {}
            }
            this.revokePreviewUrl();
            const backdrop = document.getElementById('media-capture-modal-backdrop');
            if (backdrop) backdrop.classList.remove('active');
        }

        revokePreviewUrl() {
            if (this.previewObjectUrl) {
                try { URL.revokeObjectURL(this.previewObjectUrl); } catch (e) {}
                this.previewObjectUrl = null;
            }
        }

        resetCaptureState() {
            this.revokePreviewUrl();
            this.capturedBlob = null;
            this.capturedMediaType = null;

            const imgPreview = document.getElementById('camera-image-preview');
            const videoPreview = document.getElementById('camera-video-preview');
            const videoReplay = document.getElementById('camera-video-replay');
            const shutterBtn = document.getElementById('btn-shutter-action');
            const retakeBtn = document.getElementById('btn-media-retake');
            const confirmBtn = document.getElementById('btn-media-confirm');
            const recHud = document.getElementById('recording-hud');

            if (imgPreview) {
                imgPreview.style.display = 'none';
                imgPreview.src = '';
            }
            if (videoReplay) {
                videoReplay.style.display = 'none';
                videoReplay.src = '';
            }
            if (videoPreview) {
                videoPreview.style.display = 'block';
            }
            if (recHud) recHud.style.display = 'none';
            if (retakeBtn) retakeBtn.style.display = 'none';
            if (confirmBtn) {
                confirmBtn.disabled = true;
                confirmBtn.innerHTML = '<span>⬆️</span> Confirm & Transmit Evidence';
            }
            if (shutterBtn) {
                shutterBtn.classList.remove('recording');
                shutterBtn.style.display = (this.currentMode === 'file') ? 'none' : 'flex';
            }
        }

        async switchMode(mode) {
            this.currentMode = mode;
            this.stopMediaStream();
            this.resetCaptureState();

            const viewport = document.getElementById('camera-viewport');
            const dropzone = document.getElementById('media-dropzone');
            const shutterBtn = document.getElementById('btn-shutter-action');
            const switchBtn = document.getElementById('btn-camera-switch');

            if (mode === 'file') {
                if (viewport) viewport.style.display = 'none';
                if (dropzone) dropzone.style.display = 'block';
                if (shutterBtn) shutterBtn.style.display = 'none';
                if (switchBtn) switchBtn.style.display = 'none';
            } else {
                if (viewport) viewport.style.display = 'flex';
                if (dropzone) dropzone.style.display = 'none';
                if (shutterBtn) shutterBtn.style.display = 'flex';
                if (switchBtn) switchBtn.style.display = 'flex';

                if (mode === 'photo') {
                    if (shutterBtn) {
                        shutterBtn.className = 'btn-capture-shutter';
                        shutterBtn.title = 'Take Photo';
                    }
                    await this.startCamera({ video: { facingMode: { ideal: this.facingMode } }, audio: false });
                } else if (mode === 'video') {
                    if (shutterBtn) {
                        shutterBtn.className = 'btn-capture-shutter';
                        shutterBtn.title = 'Start Recording Video';
                    }
                    await this.startCamera({ video: { facingMode: { ideal: this.facingMode } }, audio: true });
                }
            }
        }

        async startCamera(constraints) {
            if (this.isInitializingCamera) return;
            this.isInitializingCamera = true;

            const videoElem = document.getElementById('camera-video-preview');
            try {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    throw new Error('Camera hardware access is not available on this browser or origin.');
                }
                this.stopMediaStream();
                this.activeStream = await navigator.mediaDevices.getUserMedia(constraints);
                if (videoElem) {
                    videoElem.srcObject = this.activeStream;
                    videoElem.style.display = 'block';
                    await videoElem.play().catch(() => {});
                }
            } catch (err) {
                console.warn('Camera stream warning:', err.message);
                // Safe non-recursive fallback to Choose File mode
                this.currentMode = 'file';
                const tabs = document.querySelectorAll('.media-tab-btn');
                tabs.forEach(t => t.classList.toggle('active', t.dataset.mode === 'file'));
                const viewport = document.getElementById('camera-viewport');
                const dropzone = document.getElementById('media-dropzone');
                const shutterBtn = document.getElementById('btn-shutter-action');
                const switchBtn = document.getElementById('btn-camera-switch');
                if (viewport) viewport.style.display = 'none';
                if (dropzone) dropzone.style.display = 'block';
                if (shutterBtn) shutterBtn.style.display = 'none';
                if (switchBtn) switchBtn.style.display = 'none';
                this.toast('Camera not accessible: switched to file selection.', 'info');
            } finally {
                this.isInitializingCamera = false;
            }
        }

        stopMediaStream() {
            if (this.activeStream) {
                try {
                    this.activeStream.getTracks().forEach(track => {
                        try {
                            track.enabled = false;
                            track.stop();
                        } catch (e) {}
                    });
                } catch (e) {}
                this.activeStream = null;
            }
            const videoElem = document.getElementById('camera-video-preview');
            if (videoElem) {
                videoElem.srcObject = null;
            }
        }

        async toggleCameraFacing() {
            this.facingMode = this.facingMode === 'environment' ? 'user' : 'environment';
            if (this.currentMode !== 'file') {
                await this.switchMode(this.currentMode);
            }
        }

        takePhoto() {
            const video = document.getElementById('camera-video-preview');
            const imgPreview = document.getElementById('camera-image-preview');
            const shutterBtn = document.getElementById('btn-shutter-action');
            const retakeBtn = document.getElementById('btn-media-retake');
            const confirmBtn = document.getElementById('btn-media-confirm');

            const width = (video && video.videoWidth > 0) ? video.videoWidth : 1280;
            const height = (video && video.videoHeight > 0) ? video.videoHeight : 720;

            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');

            if (video && video.videoWidth > 0) {
                ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            } else {
                ctx.fillStyle = '#0f172a';
                ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.fillStyle = '#38bdf8';
                ctx.font = '24px sans-serif';
                ctx.fillText('DISASTER EVIDENCE CAPTURE', 40, canvas.height / 2);
            }

            // Watermark GPS & timestamp
            ctx.fillStyle = 'rgba(0, 0, 0, 0.65)';
            ctx.fillRect(0, canvas.height - 44, canvas.width, 44);
            ctx.fillStyle = '#00e5ff';
            ctx.font = '16px monospace';
            const timeStr = new Date().toISOString().replace('T', ' ').substring(0, 19) + ' UTC';
            const gpsStr = this.currentGps ? `GPS: ${this.currentGps.lat.toFixed(5)}, ${this.currentGps.lng.toFixed(5)} (±${Math.round(this.currentGps.accuracy)}m)` : 'GPS: Unavailable';
            ctx.fillText(`INCIDENT EVIDENCE [${timeStr}] | ${gpsStr}`, 15, canvas.height - 16);

            canvas.toBlob((blob) => {
                if (!blob) {
                    this.toast('Failed to capture photo frame', 'error');
                    return;
                }
                this.capturedBlob = blob;
                this.capturedMediaType = 'PHOTO';
                this.revokePreviewUrl();
                this.previewObjectUrl = URL.createObjectURL(blob);

                if (imgPreview) {
                    imgPreview.src = this.previewObjectUrl;
                    imgPreview.style.display = 'block';
                }
                if (video) video.style.display = 'none';
                if (shutterBtn) shutterBtn.style.display = 'none';
                if (retakeBtn) retakeBtn.style.display = 'block';
                if (confirmBtn) confirmBtn.disabled = false;

                // Stop camera stream to free hardware resources
                this.stopMediaStream();
            }, 'image/jpeg', 0.90);
        }

        startVideoRecording() {
            if (!this.activeStream) {
                this.toast('Camera not active for recording', 'error');
                return;
            }
            this.recordedChunks = [];

            let mimeType = 'video/webm;codecs=vp8,opus';
            if (typeof MediaRecorder.isTypeSupported === 'function') {
                if (!MediaRecorder.isTypeSupported(mimeType)) {
                    mimeType = 'video/webm';
                }
            }

            try {
                this.mediaRecorder = new MediaRecorder(this.activeStream, { mimeType: mimeType });
            } catch (e) {
                this.mediaRecorder = new MediaRecorder(this.activeStream);
            }

            this.mediaRecorder.ondataavailable = (e) => {
                if (e.data && e.data.size > 0) {
                    this.recordedChunks.push(e.data);
                }
            };

            this.mediaRecorder.onstop = () => {
                const blob = new Blob(this.recordedChunks, { type: 'video/webm' });
                this.capturedBlob = blob;
                this.capturedMediaType = 'VIDEO';
                this.revokePreviewUrl();
                this.previewObjectUrl = URL.createObjectURL(blob);

                const replayElem = document.getElementById('camera-video-replay');
                const videoElem = document.getElementById('camera-video-preview');
                if (replayElem) {
                    replayElem.src = this.previewObjectUrl;
                    replayElem.style.display = 'block';
                }
                if (videoElem) videoElem.style.display = 'none';

                const hud = document.getElementById('recording-hud');
                const shutterBtn = document.getElementById('btn-shutter-action');
                const retakeBtn = document.getElementById('btn-media-retake');
                const confirmBtn = document.getElementById('btn-media-confirm');

                if (hud) hud.style.display = 'none';
                if (shutterBtn) {
                    shutterBtn.style.display = 'none';
                    shutterBtn.classList.remove('recording');
                }
                if (retakeBtn) retakeBtn.style.display = 'block';
                if (confirmBtn) confirmBtn.disabled = false;

                this.stopMediaStream();
            };

            this.mediaRecorder.start(1000);
            this.recordingSeconds = 0;
            const hud = document.getElementById('recording-hud');
            const shutterBtn = document.getElementById('btn-shutter-action');
            if (hud) hud.style.display = 'flex';
            if (shutterBtn) shutterBtn.classList.add('recording');

            if (this.recordingTimer) clearInterval(this.recordingTimer);
            this.recordingTimer = setInterval(() => {
                this.recordingSeconds++;
                const mins = String(Math.floor(this.recordingSeconds / 60)).padStart(2, '0');
                const secs = String(this.recordingSeconds % 60).padStart(2, '0');
                const timerText = document.getElementById('recording-timer-text');
                if (timerText) timerText.textContent = `${mins}:${secs}`;

                // Max 60 seconds auto-stop for browser stability
                if (this.recordingSeconds >= 60) {
                    this.stopVideoRecording();
                }
            }, 1000);
        }

        stopVideoRecording() {
            if (this.recordingTimer) {
                clearInterval(this.recordingTimer);
                this.recordingTimer = null;
            }
            if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
                try { this.mediaRecorder.stop(); } catch (e) {}
            }
            const shutterBtn = document.getElementById('btn-shutter-action');
            if (shutterBtn) shutterBtn.classList.remove('recording');
        }

        retake() {
            // Cleanly reset preview without recursive mode switches
            this.resetCaptureState();

            if (this.currentMode === 'photo') {
                this.startCamera({ video: { facingMode: { ideal: this.facingMode } }, audio: false });
            } else if (this.currentMode === 'video') {
                this.startCamera({ video: { facingMode: { ideal: this.facingMode } }, audio: true });
            }
        }

        handleFileSelected(file) {
            if (!file) return;

            // Size validation (Max 50MB)
            const maxSize = 50 * 1024 * 1024;
            if (file.size > maxSize) {
                this.toast('File size exceeds the 50MB limit.', 'error');
                return;
            }

            this.capturedBlob = file;
            const isVideo = file.type.startsWith('video');
            this.capturedMediaType = isVideo ? 'VIDEO' : 'PHOTO';

            const viewport = document.getElementById('camera-viewport');
            const dropzone = document.getElementById('media-dropzone');
            const imgPreview = document.getElementById('camera-image-preview');
            const videoReplay = document.getElementById('camera-video-replay');
            const confirmBtn = document.getElementById('btn-media-confirm');
            const retakeBtn = document.getElementById('btn-media-retake');

            if (viewport) viewport.style.display = 'flex';
            if (dropzone) dropzone.style.display = 'none';

            this.revokePreviewUrl();
            this.previewObjectUrl = URL.createObjectURL(file);

            if (isVideo) {
                if (videoReplay) {
                    videoReplay.src = this.previewObjectUrl;
                    videoReplay.style.display = 'block';
                }
                if (imgPreview) imgPreview.style.display = 'none';
            } else {
                if (imgPreview) {
                    imgPreview.src = this.previewObjectUrl;
                    imgPreview.style.display = 'block';
                }
                if (videoReplay) videoReplay.style.display = 'none';
            }

            if (retakeBtn) retakeBtn.style.display = 'block';
            if (confirmBtn) confirmBtn.disabled = false;
        }

        acquireLocation() {
            const dot = document.getElementById('gps-indicator-dot');
            const coordsText = document.getElementById('gps-coords-text');
            const accuracyText = document.getElementById('gps-accuracy-text');

            if (!navigator.geolocation) {
                if (dot) dot.className = 'gps-status-dot';
                if (coordsText) coordsText.textContent = 'Geolocation not supported by client';
                return;
            }

            navigator.geolocation.getCurrentPosition(
                (pos) => {
                    this.currentGps = {
                        lat: pos.coords.latitude,
                        lng: pos.coords.longitude,
                        accuracy: pos.coords.accuracy
                    };
                    if (dot) dot.className = 'gps-status-dot';
                    if (coordsText) coordsText.textContent = `${this.currentGps.lat.toFixed(5)}°N, ${this.currentGps.lng.toFixed(5)}°E`;
                    if (accuracyText) accuracyText.textContent = `± ${Math.round(this.currentGps.accuracy)} m`;
                },
                (err) => {
                    console.warn('Geolocation notice:', err.message);
                    if (dot) dot.className = 'gps-status-dot searching';
                    if (coordsText) coordsText.textContent = 'GPS permission needed / default zone used';
                },
                { enableHighAccuracy: true, timeout: 8000, maximumAge: 10000 }
            );
        }

        async uploadCapturedMedia() {
            if (!this.capturedBlob) return;

            const confirmBtn = document.getElementById('btn-media-confirm');
            if (confirmBtn) {
                confirmBtn.disabled = true;
                confirmBtn.innerHTML = '<span>⏳</span> Transmitting...';
            }

            const captionInput = document.getElementById('media-caption-input');
            const caption = captionInput ? captionInput.value.trim() : '';
            const incidentId = this.activeOptions && this.activeOptions.incidentId ? this.activeOptions.incidentId : '';
            const incidentType = this.activeOptions && this.activeOptions.incidentType ? this.activeOptions.incidentType : 'CITIZEN_REPORT';

            let extension = this.capturedMediaType === 'VIDEO' ? '.webm' : '.jpg';
            if (this.capturedBlob.name) {
                const parts = this.capturedBlob.name.split('.');
                if (parts.length > 1) extension = '.' + parts.pop();
            }

            const filename = `evidence_${Date.now()}${extension}`;
            const formData = new FormData();
            formData.append('file', this.capturedBlob, filename);
            if (incidentId) formData.append('incidentId', incidentId);
            formData.append('incidentType', incidentType);
            if (caption) formData.append('caption', caption);

            if (this.currentGps) {
                formData.append('latitude', this.currentGps.lat);
                formData.append('longitude', this.currentGps.lng);
                formData.append('accuracy', this.currentGps.accuracy);
            }

            const token = this.resolveAuthToken();

            try {
                const res = await fetch('/api/media/upload', {
                    method: 'POST',
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {},
                    body: formData
                });

                const data = await res.json();
                if (res.ok && data.status === 'success') {
                    this.toast('Media evidence securely stored and indexed.', 'success');

                    if (this.activeOptions && typeof this.activeOptions.onSuccess === 'function') {
                        try { this.activeOptions.onSuccess(data); } catch (e) {}
                    }

                    this.close();
                } else {
                    throw new Error(data.message || 'Upload failed');
                }
            } catch (err) {
                console.error('Upload failed: ', err);
                this.toast(`Upload failed: ${err.message}`, 'error');
                if (confirmBtn) {
                    confirmBtn.disabled = false;
                    confirmBtn.innerHTML = '<span>⬆️</span> Retry Transmission';
                }
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

    // Export singleton
    window.MediaCapture = new MediaCaptureManager();
})();
