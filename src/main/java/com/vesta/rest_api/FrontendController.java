package com.vesta.rest_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


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
            LOG.warn(e.getMessage());
            return "error";
        }

        current = new Song("Starboy", "The Weeknd", "https://upload.wikimedia.org/wikipedia/en/3/39/The_Weeknd_-_Starboy.png");
        upNext = new Song("Die For You", "The Weeknd", "https://upload.wikimedia.org/wikipedia/en/3/39/The_Weeknd_-_Starboy.png");
        userName = "ahaduk";


        model.addAttribute("current", current);
        model.addAttribute("upNext", upNext);
        model.addAttribute("user", userName);

        return "takeover/connected";
    }
}
