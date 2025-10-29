package com.vesta.rest_api;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class VestaboardController {

    @Autowired
    private SpotifyIntegration spot;

    private final StateBroadcastService broadcaster;

    private static final Logger LOG = LogManager.getLogger(VestaboardController.class);

    private SpotifyState state;

    // For spring to do dependency injection
    public VestaboardController(SpotifyIntegration spot, StateBroadcastService broadcaster) {
        this.spot = spot;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/send_auth_token")
    public RedirectView sendToken(@RequestParam(value = "code") String token, RedirectAttributes attributes) {
        // BUG: Despite returning true, when using accounts other than mine
        // I can't utilize the API, will work on this later.
        LOG.debug("Logging in with token " + token);
        spot.useAuthToken(token);
        attributes.addFlashAttribute("flashAttribute", "redirectWithRedirectView");
        attributes.addAttribute("attribute", "redirectWithRedirectView");
        return new RedirectView("/");
    }

    /**
     * Returns the current state of the board.
     */
    @GetMapping("/current")
    public StateResponse getCurrentState() {
        Song[] songs = spot.getSongState();
        Song currentSong = songs[0];
        Song upNext = songs[1];

        StateResponse stateResponse = new StateResponse(spot.isConnected(), spot.getConnectedUserCached(),
                spot.isPlaying(),
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

    /**
     * This endpoint emits messages from the server when a song is changed.
     * 
     * @return
     */
    @GetMapping("/current-sse")
    public SseEmitter streamCurrentSong() {
        LOG.debug("current-sse endpoint hit");
        return broadcaster.createEmitter();
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
