/**
 * ==============================================================================
 * MEDIA CAPTURE & EVIDENCE PIPELINE (Feature 1 & Feature 2)
 * Disaster Management & Alert System
 * 
 * Reusable Camera, Video Recording & Media Geolocation Component for
 * Citizen, Responder, and Admin Dashboards.
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
            this.capturedMediaType = null; // 'PHOTO', 'VIDEO', 'DOCUMENT'
            this.facingMode = 'environment'; // 'environment' (rear) or 'user' (selfie)
            this.recordingTimer = null;
            this.recordingSeconds = 0;
            this.currentGps = null;
            this.activeOptions = null;

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
                            <div style="font-weight:600; margin-bottom:4px;">Drag and drop photo/video or browse device</div>
                            <div style="font-size:0.75rem; color:#94a3b8;">Supports JPG, PNG, WEBP, MP4, WEBM (Max 50MB)</div>
                            <input type="file" id="media-native-input" accept="image/*,video/*" style="display:none;" />
                        </div>

                        <!-- Action Controls (Shutter, Retake, Record) -->
                        <div class="capture-controls-bar">
                            <button type="button" id="btn-media-retake" class="btn-media-secondary" style="display:none;">
                                🔄 Retake
                            </button>
                            <button type="button" id="btn-shutter-action" class="btn-capture-shutter" title="Capture" aria-label="Capture button">
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

            closeBtn.addEventListener('click', () => this.close());
            cancelBtn.addEventListener('click', () => this.close());

            // Tab switching
            tabButtons.forEach(btn => {
                btn.addEventListener('click', () => {
                    tabButtons.forEach(b => b.classList.remove('active'));
                    btn.classList.add('active');
                    this.switchMode(btn.dataset.mode);
                });
            });

            // Shutter action (Photo capture or Video record start/stop)
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

            retakeBtn.addEventListener('click', () => this.retake());
            switchBtn.addEventListener('click', () => this.toggleCameraFacing());

            // Dropzone click
            dropzone.addEventListener('click', () => nativeInput.click());
            nativeInput.addEventListener('change', (e) => {
                if (e.target.files && e.target.files[0]) {
                    this.handleFileSelected(e.target.files[0]);
                }
            });

            // Confirm upload
            confirmBtn.addEventListener('click', () => this.uploadCapturedMedia());
        }

        open(options = {}) {
            this.activeOptions = options;
            const backdrop = document.getElementById('media-capture-modal-backdrop');
            backdrop.classList.add('active');

            // Reset states
            this.capturedBlob = null;
            this.capturedMediaType = null;
            document.getElementById('media-caption-input').value = '';
            document.getElementById('btn-media-confirm').disabled = true;
            this.retake();

            // Acquire high-accuracy GPS
            this.acquireLocation();

            // Start camera
            this.switchMode('photo');
        }

        close() {
            this.stopMediaStream();
            if (this.recordingTimer) {
                clearInterval(this.recordingTimer);
                this.recordingTimer = null;
            }
            const backdrop = document.getElementById('media-capture-modal-backdrop');
            backdrop.classList.remove('active');
        }

        async switchMode(mode) {
            this.currentMode = mode;
            this.stopMediaStream();
            this.retake();

            const viewport = document.getElementById('camera-viewport');
            const dropzone = document.getElementById('media-dropzone');
            const shutterBtn = document.getElementById('btn-shutter-action');
            const switchBtn = document.getElementById('btn-camera-switch');

            if (mode === 'file') {
                viewport.style.display = 'none';
                dropzone.style.display = 'block';
                shutterBtn.style.display = 'none';
                switchBtn.style.display = 'none';
            } else {
                viewport.style.display = 'flex';
                dropzone.style.display = 'none';
                shutterBtn.style.display = 'flex';
                switchBtn.style.display = 'flex';

                if (mode === 'photo') {
                    shutterBtn.className = 'btn-capture-shutter';
                    shutterBtn.title = 'Take Photo';
                    await this.startCamera({ video: { facingMode: { ideal: this.facingMode } }, audio: false });
                } else if (mode === 'video') {
                    shutterBtn.className = 'btn-capture-shutter';
                    shutterBtn.title = 'Start Recording Video';
                    await this.startCamera({ video: { facingMode: { ideal: this.facingMode } }, audio: true });
                }
            }
        }

        async startCamera(constraints) {
            const videoElem = document.getElementById('camera-video-preview');
            try {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    throw new Error('Camera hardware API not supported on this browser context.');
                }
                this.activeStream = await navigator.mediaDevices.getUserMedia(constraints);
                videoElem.srcObject = this.activeStream;
                videoElem.style.display = 'block';
                await videoElem.play();
            } catch (err) {
                console.warn('Camera stream error: ', err);
                // Graceful fallback to file mode
                const tabs = document.querySelectorAll('.media-tab-btn');
                tabs.forEach(t => t.classList.remove('active'));
                const fileTab = document.querySelector('.media-tab-btn[data-mode="file"]');
                if (fileTab) fileTab.classList.add('active');
                this.switchMode('file');
            }
        }

        stopMediaStream() {
            if (this.activeStream) {
                this.activeStream.getTracks().forEach(track => track.stop());
                this.activeStream = null;
            }
            const videoElem = document.getElementById('camera-video-preview');
            if (videoElem) videoElem.srcObject = null;
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

            const canvas = document.createElement('canvas');
            canvas.width = video.videoWidth || 1280;
            canvas.height = video.videoHeight || 720;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

            // Watermark GPS & timestamp
            ctx.fillStyle = 'rgba(0, 0, 0, 0.55)';
            ctx.fillRect(0, canvas.height - 40, canvas.width, 40);
            ctx.fillStyle = '#00e5ff';
            ctx.font = '16px monospace';
            const timeStr = new Date().toISOString().replace('T', ' ').substring(0, 19) + ' UTC';
            const gpsStr = this.currentGps ? `GPS: ${this.currentGps.lat.toFixed(4)}, ${this.currentGps.lng.toFixed(4)} (±${Math.round(this.currentGps.accuracy)}m)` : 'GPS: Unavailable';
            ctx.fillText(`DISASTER EVIDENCE [${timeStr}] | ${gpsStr}`, 15, canvas.height - 15);

            canvas.toBlob((blob) => {
                this.capturedBlob = blob;
                this.capturedMediaType = 'PHOTO';
                imgPreview.src = URL.createObjectURL(blob);
                imgPreview.style.display = 'block';
                video.style.display = 'none';

                shutterBtn.style.display = 'none';
                retakeBtn.style.display = 'block';
                confirmBtn.disabled = false;
                this.stopMediaStream();
            }, 'image/jpeg', 0.88);
        }

        startVideoRecording() {
            if (!this.activeStream) return;
            this.recordedChunks = [];

            const options = { mimeType: 'video/webm;codecs=vp8,opus' };
            try {
                this.mediaRecorder = new MediaRecorder(this.activeStream, options);
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

                const replayElem = document.getElementById('camera-video-replay');
                const videoElem = document.getElementById('camera-video-preview');
                replayElem.src = URL.createObjectURL(blob);
                replayElem.style.display = 'block';
                videoElem.style.display = 'none';

                document.getElementById('recording-hud').style.display = 'none';
                document.getElementById('btn-shutter-action').style.display = 'none';
                document.getElementById('btn-media-retake').style.display = 'block';
                document.getElementById('btn-media-confirm').disabled = false;
                this.stopMediaStream();
            };

            this.mediaRecorder.start(1000);
            this.recordingSeconds = 0;
            document.getElementById('recording-hud').style.display = 'flex';
            document.getElementById('btn-shutter-action').classList.add('recording');

            this.recordingTimer = setInterval(() => {
                this.recordingSeconds++;
                const mins = String(Math.floor(this.recordingSeconds / 60)).padStart(2, '0');
                const secs = String(this.recordingSeconds % 60).padStart(2, '0');
                document.getElementById('recording-timer-text').textContent = `${mins}:${secs}`;

                // Max 60 seconds auto-stop for safety
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
                this.mediaRecorder.stop();
            }
            document.getElementById('btn-shutter-action').classList.remove('recording');
        }

        retake() {
            const imgPreview = document.getElementById('camera-image-preview');
            const videoPreview = document.getElementById('camera-video-preview');
            const videoReplay = document.getElementById('camera-video-replay');
            const shutterBtn = document.getElementById('btn-shutter-action');
            const retakeBtn = document.getElementById('btn-media-retake');
            const confirmBtn = document.getElementById('btn-media-confirm');

            imgPreview.style.display = 'none';
            videoReplay.style.display = 'none';
            videoReplay.src = '';
            this.capturedBlob = null;
            this.capturedMediaType = null;

            if (this.currentMode !== 'file') {
                shutterBtn.style.display = 'flex';
                retakeBtn.style.display = 'none';
                confirmBtn.disabled = true;
                this.switchMode(this.currentMode);
            }
        }

        handleFileSelected(file) {
            this.capturedBlob = file;
            const isVideo = file.type.startsWith('video');
            this.capturedMediaType = isVideo ? 'VIDEO' : 'PHOTO';

            const viewport = document.getElementById('camera-viewport');
            const dropzone = document.getElementById('media-dropzone');
            const imgPreview = document.getElementById('camera-image-preview');
            const videoReplay = document.getElementById('camera-video-replay');
            const confirmBtn = document.getElementById('btn-media-confirm');
            const retakeBtn = document.getElementById('btn-media-retake');

            viewport.style.display = 'flex';
            dropzone.style.display = 'none';

            if (isVideo) {
                videoReplay.src = URL.createObjectURL(file);
                videoReplay.style.display = 'block';
                imgPreview.style.display = 'none';
            } else {
                imgPreview.src = URL.createObjectURL(file);
                imgPreview.style.display = 'block';
                videoReplay.style.display = 'none';
            }

            retakeBtn.style.display = 'block';
            confirmBtn.disabled = false;
        }

        acquireLocation() {
            const dot = document.getElementById('gps-indicator-dot');
            const coordsText = document.getElementById('gps-coords-text');
            const accuracyText = document.getElementById('gps-accuracy-text');

            if (!navigator.geolocation) {
                dot.className = 'gps-status-dot';
                coordsText.textContent = 'Geolocation not supported by client';
                return;
            }

            navigator.geolocation.getCurrentPosition(
                (pos) => {
                    this.currentGps = {
                        lat: pos.coords.latitude,
                        lng: pos.coords.longitude,
                        accuracy: pos.coords.accuracy
                    };
                    dot.className = 'gps-status-dot';
                    coordsText.textContent = `${this.currentGps.lat.toFixed(5)}°N, ${this.currentGps.lng.toFixed(5)}°E`;
                    accuracyText.textContent = `± ${Math.round(this.currentGps.accuracy)} m`;
                },
                (err) => {
                    console.warn('Geolocation warning: ', err.message);
                    dot.className = 'gps-status-dot searching';
                    coordsText.textContent = 'GPS permission needed / default zone used';
                },
                { enableHighAccuracy: true, timeout: 8000, maximumAge: 10000 }
            );
        }

        async uploadCapturedMedia() {
            if (!this.capturedBlob) return;

            const confirmBtn = document.getElementById('btn-media-confirm');
            confirmBtn.disabled = true;
            confirmBtn.innerHTML = '<span>⏳</span> Transmitting...';

            const caption = document.getElementById('media-caption-input').value.trim();
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

            const token = localStorage.getItem('token');

            try {
                const res = await fetch('/api/media/upload', {
                    method: 'POST',
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {},
                    body: formData
                });

                const data = await res.json();
                if (res.ok && data.status === 'success') {
                    if (window.showToast) {
                        window.showToast('Media evidence securely stored and indexed.', 'success');
                    } else if (window.CommandCenter && window.CommandCenter.showToast) {
                        window.CommandCenter.showToast('Media evidence securely stored and indexed.', 'success');
                    }

                    if (this.activeOptions && typeof this.activeOptions.onSuccess === 'function') {
                        this.activeOptions.onSuccess(data);
                    }

                    this.close();
                } else {
                    throw new Error(data.message || 'Upload failed');
                }
            } catch (err) {
                console.error('Upload failed: ', err);
                if (window.showToast) {
                    window.showToast(`Upload failed: ${err.message}`, 'error');
                }
                confirmBtn.disabled = false;
                confirmBtn.innerHTML = '<span>⬆️</span> Retry Transmission';
            }
        }
    }

    // Export singleton
    window.MediaCapture = new MediaCaptureManager();
})();

