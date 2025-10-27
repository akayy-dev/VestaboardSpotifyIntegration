package com.vesta.rest_api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class StateBroadcastService {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final Logger LOG = LogManager.getLogger(StateBroadcastService.class);

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));
        LOG.debug("Created new emitter", emitter);

        emitters.add(emitter);
        return emitter;
    }

    public void broadCastState(String eventName, SpotifyState state) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        LOG.debug("Sending " + eventName + "event to emitters", deadEmitters);
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter
                        .event()
                        .name(eventName)
                        .data(state));
            } catch (IOException e) {
                deadEmitters.add(emitter);
                LOG.debug("Found a dead emitter");
                e.printStackTrace();
            }
        });

        // remove all closed connections from the emitters list.
        emitters.removeAll(deadEmitters);
    }

}
