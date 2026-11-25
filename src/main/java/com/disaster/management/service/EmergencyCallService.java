package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.EmergencyCallSession;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.EmergencyCallSessionRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;

@Service
@Transactional
public class EmergencyCallService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyCallService.class);

    @Autowired
    private EmergencyCallSessionRepository callRepository;

    @Autowired
    private CitizenReportRepository citizenReportRepository;

    @Autowired
    private RescueTaskRepository rescueTaskRepository;

    @Autowired
    private UserRepository userRepository;

    public EmergencyCallSession initiateCall(Long incidentId, String responderEmail, String responderName,
                                             String citizenEmail, String citizenName, String sdpOffer) {
        // Resolve incident citizen details if not provided
        if ((citizenEmail == null || citizenEmail.trim().isEmpty()) && incidentId != null) {
            Optional<CitizenReport> crOpt = citizenReportRepository.findById(incidentId);
            if (crOpt.isPresent()) {
                CitizenReport cr = crOpt.get();
                citizenEmail = cr.getCitizenEmail();
                if (citizenName == null || citizenName.trim().isEmpty()) {
                    final String cEmail = citizenEmail;
                    citizenName = userRepository.findByEmail(citizenEmail).map(User::getName).orElse(cEmail != null ? cEmail.split("@")[0] : "Citizen");
                }
            } else {
                Optional<RescueTask> taskOpt = rescueTaskRepository.findById(incidentId);
                if (taskOpt.isPresent() && taskOpt.get().getCitizenReportId() != null) {
                    Optional<CitizenReport> linkedCr = citizenReportRepository.findById(taskOpt.get().getCitizenReportId());
                    if (linkedCr.isPresent()) {
                        citizenEmail = linkedCr.get().getCitizenEmail();
                        final String cEmail = citizenEmail;
                        citizenName = userRepository.findByEmail(citizenEmail).map(User::getName).orElse(cEmail != null ? cEmail.split("@")[0] : "Citizen");
                    }
                }
            }
        }

        if (citizenEmail == null || citizenEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot initiate emergency call: Citizen contact email not found for incident #" + incidentId);
        }

        citizenEmail = citizenEmail.trim().toLowerCase();

        // Expire any existing active calls for this citizen older than 2 minutes
        Optional<EmergencyCallSession> activeCall = callRepository.findTopByCitizenEmailIgnoreCaseAndStatusOrderByStartedAtDesc(citizenEmail, "RINGING");
        if (activeCall.isPresent()) {
            EmergencyCallSession prev = activeCall.get();
            if (prev.getStartedAt() != null && prev.getStartedAt().isBefore(LocalDateTime.now().minusMinutes(2))) {
                prev.setStatus("MISSED");
                prev.setEndedAt(LocalDateTime.now());
                callRepository.save(prev);
            }
        }

        String callId = "CALL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        EmergencyCallSession session = new EmergencyCallSession(
                callId,
                incidentId != null ? incidentId : 0L,
                responderEmail != null ? responderEmail.trim().toLowerCase() : "responder",
                responderName != null ? responderName : "Emergency Responder",
                citizenEmail,
                citizenName != null ? citizenName : "Citizen",
                sdpOffer
        );

        log.info("[EMERGENCY-CALL] Call session initiated: callId={}, incidentId={}, responder={}, citizen={}",
                callId, incidentId, responderEmail, citizenEmail);

        return callRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<EmergencyCallSession> getIncomingCallForCitizen(String citizenEmail) {
        if (citizenEmail == null || citizenEmail.trim().isEmpty()) {
            return Optional.empty();
        }
        citizenEmail = citizenEmail.trim().toLowerCase();
        Optional<EmergencyCallSession> callOpt = callRepository.findTopByCitizenEmailIgnoreCaseAndStatusOrderByStartedAtDesc(citizenEmail, "RINGING");
        if (callOpt.isPresent()) {
            EmergencyCallSession call = callOpt.get();
            // If ringing for more than 45 seconds, mark MISSED
            if (call.getStartedAt() != null && call.getStartedAt().isBefore(LocalDateTime.now().minusSeconds(45))) {
                return Optional.empty();
            }
            return Optional.of(call);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<EmergencyCallSession> getCallSession(String callId) {
        return callRepository.findByCallId(callId);
    }

    public EmergencyCallSession answerCall(String callId, String citizenEmail, String sdpAnswer) {
        EmergencyCallSession session = callRepository.findByCallId(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call session not found: " + callId));

        if (!"RINGING".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalStateException("Call is no longer ringing (Current status: " + session.getStatus() + ")");
        }

        session.setSdpAnswer(sdpAnswer);
        session.setStatus("CONNECTED");
        session.setConnectedAt(LocalDateTime.now());
        log.info("[EMERGENCY-CALL] Call answered: callId={}, connectedAt={}", callId, session.getConnectedAt());
        return callRepository.save(session);
    }

    public EmergencyCallSession rejectCall(String callId, String reason) {
        EmergencyCallSession session = callRepository.findByCallId(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call session not found: " + callId));

        session.setStatus("REJECTED");
        session.setEndedAt(LocalDateTime.now());
        log.info("[EMERGENCY-CALL] Call rejected: callId={}, reason={}", callId, reason);
        return callRepository.save(session);
    }

    public EmergencyCallSession endCall(String callId, String endedBy) {
        EmergencyCallSession session = callRepository.findByCallId(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call session not found: " + callId));

        session.setStatus("ENDED");
        session.setEndedAt(LocalDateTime.now());
        log.info("[EMERGENCY-CALL] Call ended by {}: callId={}", endedBy, callId);
        return callRepository.save(session);
    }

    public EmergencyCallSession addIceCandidate(String callId, String role, String candidateJson) {
        EmergencyCallSession session = callRepository.findByCallId(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call session not found: " + callId));

        if (candidateJson == null || candidateJson.trim().isEmpty()) {
            return session;
        }

        // Parse candidate and append into JSON array string
        if ("RESPONDER".equalsIgnoreCase(role) || "CALLER".equalsIgnoreCase(role)) {
            String current = session.getResponderCandidatesJson();
            session.setResponderCandidatesJson(appendCandidate(current, candidateJson));
        } else {
            String current = session.getCitizenCandidatesJson();
            session.setCitizenCandidatesJson(appendCandidate(current, candidateJson));
        }

        return callRepository.save(session);
    }

    private String appendCandidate(String currentArrayJson, String newCandidate) {
        if (currentArrayJson == null || currentArrayJson.trim().isEmpty() || "[]".equals(currentArrayJson.trim())) {
            return "[" + newCandidate.trim() + "]";
        }
        String trimmed = currentArrayJson.trim();
        if (trimmed.endsWith("]")) {
            return trimmed.substring(0, trimmed.length() - 1) + "," + newCandidate.trim() + "]";
        }
        return "[" + newCandidate.trim() + "]";
    }
}
