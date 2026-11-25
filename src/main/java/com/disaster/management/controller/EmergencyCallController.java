package com.disaster.management.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.EmergencyCallSession;
import com.disaster.management.service.EmergencyCallService;

@RestController
@RequestMapping("/api/calls")
public class EmergencyCallController {

    private static final Logger log = LoggerFactory.getLogger(EmergencyCallController.class);

    @Autowired
    private EmergencyCallService emergencyCallService;

    /**
     * Responder initiates emergency video call to Citizen
     */
    @PostMapping("/initiate")
    public ResponseEntity<?> initiateCall(@RequestBody Map<String, Object> req, Authentication auth) {
        try {
            Long incidentId = req.get("incidentId") != null ? Long.valueOf(req.get("incidentId").toString()) : null;
            String incidentType = req.get("incidentType") != null ? req.get("incidentType").toString() : null;
            String citizenEmail = req.get("citizenEmail") != null ? req.get("citizenEmail").toString() : null;
            String citizenName = req.get("citizenName") != null ? req.get("citizenName").toString() : null;
            String sdpOffer = req.get("sdpOffer") != null ? req.get("sdpOffer").toString() : "";
            String responderName = req.get("responderName") != null ? req.get("responderName").toString() : "Responder";
            String responderEmail = (auth != null) ? auth.getName() : "responder";

            EmergencyCallSession session = emergencyCallService.initiateCall(
                    incidentId,
                    incidentType,
                    responderEmail,
                    responderName,
                    citizenEmail,
                    citizenName,
                    sdpOffer
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("callId", session.getCallId());
            response.put("session", session);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[EMERGENCY-CALL] Error initiating call: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error");
            err.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
    }

    /**
     * Citizen checks for incoming emergency calls
     */
    @GetMapping("/incoming")
    public ResponseEntity<?> getIncomingCall(Authentication auth) {
        try {
            if (auth == null || auth.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String email = auth.getName();
            Optional<EmergencyCallSession> callOpt = emergencyCallService.getIncomingCallForCitizen(email);
            Map<String, Object> response = new HashMap<>();
            if (callOpt.isPresent()) {
                response.put("hasIncomingCall", true);
                response.put("call", callOpt.get());
            } else {
                response.put("hasIncomingCall", false);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[EMERGENCY-CALL] Error checking incoming call: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Poll state of a call (SDP answer, status, ICE candidates)
     */
    @GetMapping("/{callId}/poll")
    public ResponseEntity<?> pollCall(@PathVariable String callId) {
        Optional<EmergencyCallSession> callOpt = emergencyCallService.getCallSession(callId);
        if (!callOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("call", callOpt.get());
        return ResponseEntity.ok(response);
    }

    /**
     * Citizen answers incoming call with SDP Answer
     */
    @PostMapping("/{callId}/answer")
    public ResponseEntity<?> answerCall(@PathVariable String callId, @RequestBody Map<String, Object> req, Authentication auth) {
        try {
            String citizenEmail = (auth != null) ? auth.getName() : "";
            String sdpAnswer = req.get("sdpAnswer") != null ? req.get("sdpAnswer").toString() : "";
            EmergencyCallSession session = emergencyCallService.answerCall(callId, citizenEmail, sdpAnswer);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("session", session);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[EMERGENCY-CALL] Error answering call {}: {}", callId, e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error");
            err.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
    }

    /**
     * Reject incoming call
     */
    @PostMapping("/{callId}/reject")
    public ResponseEntity<?> rejectCall(@PathVariable String callId, @RequestBody(required = false) Map<String, Object> req) {
        try {
            String reason = (req != null && req.get("reason") != null) ? req.get("reason").toString() : "Decline";
            EmergencyCallSession session = emergencyCallService.rejectCall(callId, reason);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("session", session);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * End call
     */
    @PostMapping("/{callId}/end")
    public ResponseEntity<?> endCall(@PathVariable String callId, Authentication auth) {
        try {
            String endedBy = (auth != null) ? auth.getName() : "user";
            EmergencyCallSession session = emergencyCallService.endCall(callId, endedBy);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("session", session);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Send ICE candidate (for caller or callee)
     */
    @PostMapping("/{callId}/ice-candidate")
    public ResponseEntity<?> addIceCandidate(@PathVariable String callId, @RequestBody Map<String, Object> req) {
        try {
            String role = req.get("role") != null ? req.get("role").toString() : "CALLER";
            String candidateJson = req.get("candidate") != null ? req.get("candidate").toString() : "";
            EmergencyCallSession session = emergencyCallService.addIceCandidate(callId, role, candidateJson);
            return ResponseEntity.ok(Map.of("status", "success", "candidatesCount", 1));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
