package com.vesta.rest_api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.vesta.rest_api.events.ObservableEvents;
import com.vesta.rest_api.patterns.SongChangeObserver;
import com.vesta.rest_api.patterns.Subject;

import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.special.PlaybackQueue;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.player.GetUsersCurrentlyPlayingTrackRequest;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.User;


/**
 * Respoonsible for facillitating the connection of the
 * Spotify API to the Vestaboard API.
 * 
 * The class only directly interacts with the Spotify API,
 * it's a {@link Subject} to send an update event to the
 * {@link SongChangeObserver }.
 */
public class SpotifyIntegration implements Subject{

    /**
     * Whether or not a user is connected stored in the cache.
     */
    private Boolean isConnectedCached;

    /**
     * Cache boolean representing whether or not the user is playing anything
     */
    private Boolean isPlayingCached;

    /**
     * Cache string representing the connected user.
     */
    private String connectedUserCached;

    // Spotify API client and auth state (merged from previous SpotifySession)
    private SpotifyApi spot;
    private boolean isAuthenticated;

    private static final Logger LOG = LogManager.getLogger(SpotifyIntegration.class);

    private SpotifyState board;

    public SpotifyIntegration(String clientID, String clientSecret, String redirectURI, String vestaboardKey) {
        LOG.debug("SpotifyIntegration created.");

        // initialize internal state
        isConnectedCached = false;
        isPlayingCached = false;
        isAuthenticated = false;

        // build Spotify API client
        try {
            spot = new SpotifyApi.Builder()
                    .setClientId(clientID)
                    .setClientSecret(clientSecret)
                    .setRedirectUri(SpotifyHttpManager.makeUri(redirectURI))
                    .build();
        } catch (Exception e) {
            LOG.warn("Failed to initialize SpotifyApi. ERROR_MSG: " + e.getLocalizedMessage());
        }

        board = new SpotifyState();

        SongChangeObserver onSongChange = new SongChangeObserver(vestaboardKey);
        attach(onSongChange);

        LOG.debug("SpotifyUserSingleton has been created.");
    }

    /**
     * Get the Authorizaiton URL that gives
     * the "Allow Spotify to connect to" dialog
     * 
     * @return The authorization URL.
     */
    public String getAuthURL() {
        final AuthorizationCodeUriRequest authorizationCodeUriRequest = spot
                .authorizationCodeUri()
                .scope("user-modify-playback-state user-read-playback-state user-read-currently-playing user-read-email user-read-private")
                .show_dialog(true)
                .build();
        final String authURI = authorizationCodeUriRequest.execute().toString();
        LOG.debug("Retrieved auth URL.");
        return authURI;
    }

    /**
     * When the user submits an auth token, this method is responsible
     * for "logging in" the user.
     * 
     * @param auth_code
     * @return
     */
    public boolean useAuthToken(String auth_code) {
        try {
            AuthorizationCodeCredentials creds = spot
                    .authorizationCode(auth_code)
                    .build()
                    .execute();
            spot.setAccessToken(creds.getAccessToken());
            spot.setRefreshToken(creds.getRefreshToken());
            isAuthenticated = true;
            // update cache after authentication
            updateCache();
            LOG.info("Auth token submitted, logged in as " + connectedUserCached);
        } catch (Exception e) {
            LOG.info("Error submitting auth token, ERROR_MSG: " + e.getMessage());
        }
        isConnectedCached = isAuthenticated;
        return isConnectedCached;
    }

    /** Disconnects the users account from the application. */
    public void logout() {
        LOG.info("Logged out, resetting spotify auth");
        // clear tokens and auth state
        if (spot != null) {
            spot.setAccessToken(null);
            spot.setRefreshToken(null);
        }
        isAuthenticated = false;
        isConnectedCached = false;

        notifyObservers(ObservableEvents.LOGOUT);
    }

    public String getConnectedUser() {
        String connectedUser;
        try {
            User me = spot.getCurrentUsersProfile().build().execute();
            connectedUser = me.getDisplayName();
            return connectedUser;
        } catch (Exception e) {
            LOG.warn("Could not get connected user due to " + e.getClass().getSimpleName() + " ERROR MSG: "
                    + e.getMessage());
        }
        return null;
    }

    public String getConnectedUserCached() {
        return connectedUserCached;
    }

    public Boolean getAuthStatus() {
        return isConnectedCached;
    }

    /**
     * Returns a cached list of the current song and whats next, use this to reduce
     * API calls to spotify.
     * 
     * @return A list of songs, the first being the current, the second being the
     */
    public Song[] getSongState() {
        try {
            Song[] songState = { board.getCurrentSong(), board.getNextSong() };
            return songState;
        } catch (Throwable t) {
            String message = t.getLocalizedMessage();
            LOG.warn("Could not get state. ERROR_MSG: " + message);
        }
        return null;
    }

    /**
     * @return A boolean variable representing whether or not the user is connected.
     */
    public Boolean isConnected() {
        return isConnectedCached;
    }

    /**
     * @return A boolean variable representing whether or not the user is currently
     *         playing a song.
     */
    public Boolean isPlaying() {
        return isPlayingCached;
    }

    /**
     * Retrieves the currently playing song from the Spotify service.
     * 
     * @return the currently playing {@link Song} if available, or {@code null} if
     *         an error occurs or no song is playing.
     * @throws RuntimeException if there is an issue with the Spotify service or
     *                          user authentication.
     */
    /**
     * Retrieve currently playing track and convert to Song.
     */
    public Song getCurrentSong() {
        try {
            final GetUsersCurrentlyPlayingTrackRequest currentlyPlayingRequest = spot.getUsersCurrentlyPlayingTrack()
                    .build();
            final CurrentlyPlaying currentlyPlaying = currentlyPlayingRequest.execute();
            if (currentlyPlaying != null && currentlyPlaying.getItem() != null) {
                String songName = currentlyPlaying.getItem().getName();
                String songID = currentlyPlaying.getItem().getId();

                String trackArtist = getSongArtistFromID(songID);
                String albumArt = getAlbumArtFromSongID(songID);

                Song currentSong = new Song(songName, trackArtist, albumArt);
                LOG.debug("Retreiving current song, SONG: " + currentSong.getTitle() + " - " + currentSong.getArtist());
                isPlayingCached = true;
                return currentSong;
            }
        } catch (TooManyRequestsException e) {
            Integer retryAfter = e.getRetryAfter();
            LOG.warn("getCurrentSong() raised TooManyRequests exceptions, backing off for " + retryAfter + " seconds.");
            try {
                Thread.sleep(retryAfter * 1000);
                LOG.info("Backoff finished, retrying.");
                return getCurrentSong();
            } catch (InterruptedException i) {
                LOG.warn("Backoff attempt interrupted, ERROR_MSG: " + i.getMessage());
            }
        } catch (SpotifyWebApiException s) {
            String message = s.getLocalizedMessage();
            LOG.warn("Could not get current song due to SpotifyWebApiException ERROR MSG: " + message);
            if (message != null && message.equals("The access token expired")) {
                LOG.warn("Expired access token, should create a method to refresh access token.");
                notifyObservers(ObservableEvents.SPOTIFY_TOKEN_EXPIRED);
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("Not playing anything.");
            isPlayingCached = false;
        } catch (Exception e) {
            String errorName = e.getClass().getSimpleName();
            String message = e.getLocalizedMessage();
            LOG.warn("Could not get current song due to " + errorName + " ERROR MSG: " + message);
        }
        return null;
    }

    /**
     * Retrieves the next song in the queue from the Spotify integration.
     * 
     * @return the next song in the queue, or null if an error occurs.
     * @throws Throwable if there is an issue retrieving the next song.
     */
    public Song getNextUp() {
        if (isPlayingCached) {
            try {
                final PlaybackQueue queue = spot.getTheUsersQueue().build().execute();
                final IPlaylistItem nextUp = queue.getQueue().get(0);
                final String songName = nextUp.getName();
                final String artist = getSongArtistFromID(nextUp.getId());
                final String albumArt = getAlbumArtFromSongID(nextUp.getId());
                Song nextSongUp = new Song(songName, artist, albumArt);
                return nextSongUp;
            } catch (NullPointerException n) {
                // typically thrown when nothing is playing
                LOG.info("Could not get next up due to NullPointerException, likely user isn't playing anything.");
            } catch (Throwable t) {
                String message = t.getLocalizedMessage();
                LOG.warn("Could not get next song, is the user authenticated? ERROR MSG: " + message);
            }
        }
        return null;
    }

    /**
     * Get's the songs in the players QUEUE:
     * NOTE: This is untested.
     */
    public Song[] getQueue() {
        try {
            final List<IPlaylistItem> queue = spot.getTheUsersQueue().build().execute().getQueue();

            List<Song> queueList = new ArrayList<Song>();
            for (IPlaylistItem song : queue) {
                String songName = song.getName();
                String songID = song.getId();

                String artistName = getSongArtistFromID(songID);
                String albumArt = getAlbumArtFromSongID(songID);
                Song songObj = new Song(songName, artistName, albumArt);
                queueList.add(songObj);
            }
            return queueList.toArray(new Song[0]);
        } catch (Throwable t) {
            String message = t.getLocalizedMessage();
            LOG.warn("Could not get queue, is the user authenticated? ERROR MSG: " + message);
        }
        return null;
    }

    public Song requestSong(String trackName, String artistName) {
        String query = "\"track\":" + trackName + "\"artist:\"" + artistName;
        return addToQueue(query);
    }

    /**
     * Search a song by its name and add to the user's queue.
     */
    public Song addToQueue(String query) {
        try {
            LOG.info("Looking for " + query + " to add to queue.");
            Track[] searchedSongs = spot.searchTracks(query).build().execute().getItems();
            Track selectedSong = searchedSongs[0]; // Add the first song found in the search to the queue.
            spot.addItemToUsersPlaybackQueue(selectedSong.getUri()).build().execute();
            String songName = selectedSong.getName();
            String artist = selectedSong.getArtists()[0].getName();
            String albumArt = selectedSong.getAlbum().getImages()[0].getUrl();
            LOG.info("Added " + songName + " by " + artist + " to queue");

            return new Song(songName, artist, albumArt);
        } catch (SpotifyWebApiException | IOException | ParseException e) {
            LOG.error("Failed add song " + query + " to queue" + " ERROR TYPE: " + e.getClass().getName()
                    + " ERROR MSG: "
                    + e.getLocalizedMessage());
            return null;
        }

    }

    /**
     * Helper: get artist by track id
     */
    private String getSongArtistFromID(String ID) throws IOException, ParseException, SpotifyWebApiException {
        Track trackObj = spot.getTrack(ID).build().execute();
        String trackArtist = trackObj.getArtists()[0].getName();
        return trackArtist;
    }

    private String getAlbumArtFromSongID(String ID) throws IOException, ParseException, SpotifyWebApiException {
        Track trackObj = spot.getTrack(ID).build().execute();
        String albumArt = trackObj.getAlbum().getImages()[0].getUrl();
        return albumArt;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Main function that runs the main program, designed to be run every n seconds,
     * if the song playing is different from the last time it ran, update the board.
     */
    public void run() {
        try {
            // Won't run if spotify isn't authenticated, that way I won't get any errors.
            if (isConnectedCached) {
                Song currentSong = getCurrentSong();
                Song upNext = getNextUp();
                isPlayingCached = isPlaying();

                // if cached songs are empty, that likely means user just logged in.
                if (board.getCurrentSong() == null && board.getNextSong() == null) {
                    LOG.trace("Cached songs are empty, updating currentSongCached and upNextCached");
                    board.setCurrentSong(currentSong);
                    board.setNextSong(upNext);
                }

                /*
                 * BUG: Apparently this if statement doesn't run on the first update when a user
                 * connects,
                 * meaning that the board will only start working after the first song/queue
                 * change,
                 * figure this out later.
                 */
                if (currentSong != null && !currentSong.equals(board.getCurrentSong())) {
                    LOG.info("Now playing changed from " + board.getCurrentSong().getTitle() + " to "
                            + currentSong.getTitle());

                    /*
                     * update the cache to match the current song before
                     * notifying the observer
                     */
                    board.setCurrentSong(currentSong);
                    board.setNextSong(upNext);
                }
                // also update if the queue is updated. will come useful when requests are
                // implemented.
                else if (upNext != null && !upNext.equals(board.getNextSong())) {

                    LOG.info("Up next changed from " + board.getNextSong().getTitle() + " to " + upNext.getTitle());

                    // see above
                    board.setCurrentSong(currentSong);
                    board.setNextSong(upNext);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the cache.
     * Call this sparingly, as this can result in rate limits.
     */
    public void updateCache() {
        try {
                isPlayingCached = isPlaying();

            if (isPlayingCached) {
                isConnectedCached = isAuthenticated();
                board.setCurrentSong(getCurrentSong());
                connectedUserCached = getConnectedUser();
                board.setNextSong(getNextUp());
            }
        } catch (Exception e) {
            LOG.warn("Error updating cache, ERROR MSG: " + e.getMessage());
        }
    }
}
