package com.vesta.rest_api;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@RestController
public class VestaboardController {

    @Autowired
    private SpotifyIntegration spot;

    // For spring to do dependency injection
    public VestaboardController(SpotifyIntegration spot) {
        this.spot = spot;
    }

    @GetMapping("/send_auth_token")
    public boolean sendToken(@RequestParam(value = "code") String token) {
        // BUG: Despite returning true, when using accounts other than mine
        // I can't utilize the API, will work on this later.
        System.out.println(token);
        return spot.useAuthToken(token);
    }

    /**
     * Returns the current state of the board.
     */
    @GetMapping("/current")
    public StateResponse getCurrentState() {
        Song[] songs = spot.getSongState();
        Song currentSong = songs[0];
        Song upNext = songs[1];

        StateResponse stateResponse = new StateResponse(spot.isConnected(), spot.getConnectedUserCached(), spot.isPlaying(),
                currentSong, upNext);
        return stateResponse;
    }

    @GetMapping("/logout")
    public void logout() {
        spot.logout();
    }

    /**
     * Endpoint to get the authentication status.
     *
     * @return a Response object containing the status of the authentication.
     */
    @GetMapping("/auth_status")
    public Response getAuthStatus() {
        return new Response("success", spot.getAuthStatus());
    }

    @GetMapping("/connected_user")
    public String getConnectedUser() {
        return spot.getConnectedUser();
    }

    @GetMapping("/request_song")
    public Song requestSong(@RequestParam(value = "title") String title,
            @RequestParam(value = "artist") String artist) {
        return spot.requestSong(title, artist);
    }

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * This endpoint emits messages from the server when a song is changed.
     * 
     * @return
     */
    @GetMapping("/current-sse")
    public SseEmitter streamCurrentSong() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // create a long-lived connection
        executor.execute(() -> {
            StateResponse pastState = this.getCurrentState();
            try {
                StateResponse currentState = this.getCurrentState();
                SseEventBuilder initialEvent = SseEmitter.event()
                        .data(currentState)
                        .name("initial");
                emitter.send(initialEvent);
                while (true) {
                    currentState = this.getCurrentState();
                    SseEventBuilder event = SseEmitter.event().data(currentState);

                    if (!currentState.isConnected().equals(pastState.isConnected())) {
                        if (currentState.isConnected()) {
                            // if the user has logged in
                            event.name("login");
                        } else {
                            // if the user has logged out
                            event.name("logout");
                        }
                    } else if (!currentState.equals(pastState)) {
                        if (!currentState.getNowPlaying().equals(pastState.getNowPlaying())) {
                            // if the current song has changed.
                            event.name("song_change");

                        } else if (!currentState.getUpNext().equals(pastState.getUpNext())) {
                            // if the next up song has changed
                            event.name("queue_change");
                        }
                        if (!currentState.isPlaying().equals(pastState.isPlaying())) {
                            if (currentState.isPlaying()) {
                                // if the user starts playing a song
                                event.name("play");
                            } else {
                                // if the user pauses a song
                                event.name("pause");
                            }

                        }
                        emitter.send(event);
                    }
                    pastState = currentState;
                    Thread.sleep(5000);
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @Scheduled(fixedRate = 8000)
    public void update() {
        // This will run every 5 seconds to update the board.
        if (spot.isConnected()) {
            System.out.println("Checking for update...");
            spot.run();
        }
    }
}
