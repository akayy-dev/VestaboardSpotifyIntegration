package com.vesta.rest_api;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VestaboardController {

    private SpotifyIntegration spot;

    public VestaboardController() {
        String clientID = System.getenv("CLIENT_ID");
        String clientSecret = System.getenv("CLIENT_SECRET");
        String redirectURL = System.getenv("REDIRECT_URL");
        String vestaboardKey = System.getenv("VESTABOARD_KEY");
        spot = new SpotifyIntegration(clientID, clientSecret, redirectURL, vestaboardKey);
    }

    @GetMapping("/get_auth_url")
    public String vestaboard() {
        return spot.getAuthURL();
    }

    @GetMapping("/send_auth_token")
    public boolean sendToken(@RequestParam(value = "code") String token) {
        // This assumes the spotify app is configured to send a request to this
        // endpoint.
        // In the future, I will have spotify send the code to my frontend and post it
        // here.

        // BUG: Despite returning true, when using accounts other than mine
        // I can't utilize the API, will work on this later.
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

        StateResponse stateResponse = new StateResponse(spot.isConnected(), spot.getConnectedUser(), spot.isPlaying(),
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


    @Scheduled(fixedRate = 8000)
    public void update() {
        // This will run every 5 seconds to update the board.
        System.out.println("Checking for update...");
        spot.run();
    }
}