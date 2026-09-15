package com.vesta.rest_api.routes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.vesta.rest_api.spotify.Song;
import com.vesta.rest_api.spotify.SpotifyIntegration;

@Controller
public class FrontendController {
    private static final Logger LOG = LogManager.getLogger(FrontendController.class);
    @Autowired
    SpotifyIntegration spotify;

    @GetMapping("/")
    public String indexPage(Model model) {

        if (!spotify.isAuthenticated()) {
            LOG.info("Routing to not connected page.");
            model.addAttribute("authURL", spotify.getAuthURL());
            return "takeover/notconnected";
        }

        Song current = null;
        Song upNext = null;
        String userName = null;
        try {
            current = spotify.getCurrentSong();
            upNext = spotify.getNextUp();
            userName = spotify.getConnectedUser();
        } catch (Exception e) {
            LOG.warn("Error fetching Spotify data: {}", e.getMessage());
            return "error";
        }

        model.addAttribute("user", userName);
        model.addAttribute("current", current);
        model.addAttribute("upNext", upNext);

        return "takeover/connected";
    }
}
