package com.cresensolutions.userservice.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterService.class);
    private static final long EMITTER_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String username) {
        SseEmitter existing = emitters.remove(username);
        if (existing != null) {
            existing.complete();
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.put(username, emitter);

        emitter.onCompletion(() -> emitters.remove(username));
        emitter.onTimeout(() -> emitters.remove(username));
        emitter.onError(e -> emitters.remove(username));

        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (IOException e) {
            emitters.remove(username);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void broadcast(String eventName, String data) {
        emitters.forEach((username, emitter) -> sendTo(emitter, username, eventName, data));
    }

    public void sendToUser(String username, String eventName, String data) {
        SseEmitter emitter = emitters.get(username);
        if (emitter != null) {
            sendTo(emitter, username, eventName, data);
        }
    }

    private void sendTo(SseEmitter emitter, String username, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.debug("SSE send failed for user {}, removing emitter", username);
            emitters.remove(username);
            emitter.completeWithError(e);
        }
    }

    @Scheduled(fixedDelay = 25_000)
    public void sendHeartbeat() {
        emitters.forEach((username, emitter) -> sendTo(emitter, username, "HEARTBEAT", "ping"));
    }
}
