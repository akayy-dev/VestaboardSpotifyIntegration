package com.vesta.rest_api.routes;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vesta.rest_api.spotify.SpotifyState;

@Service
public class StateBroadcastService {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final Logger LOG = LogManager.getLogger(StateBroadcastService.class);

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));
        LOG.debug("Created new SSE emitter");

        emitters.add(emitter);
        return emitter;
    }

    public void broadCastState(String eventName, SpotifyState state) {
        LOG.debug("Sending {} event to {} SSE emitters", eventName, emitters.size());
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter
                        .event()
                        .name(eventName)
                        .data(state));
            } catch (IOException e) {
                removeEmitter(emitter);
                LOG.debug("Removed disconnected SSE emitter");
            } catch (IllegalStateException e) {
                removeEmitter(emitter);
                LOG.debug("Removed completed SSE emitter");
            }
        });
    }

    private void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }

}
