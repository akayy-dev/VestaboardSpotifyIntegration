package com.vesta.rest_api.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.special.PlaybackQueue;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.player.GetUsersCurrentlyPlayingTrackRequest;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.User;

import com.vesta.rest_api.routes.StateBroadcastService;


/**
 * Respoonsible for facillitating the connection of the
 * Spotify API to the Vestaboard API.
 * 
 * The class only directly interacts with the Spotify API,
 * it's a {@link Subject} to send an update event to the
 * {@link SongChangeObserver }.
 */
public class SpotifyIntegration {

    /**
     * Maximum number of retries for rate-limited requests.
     */
    private static final int MAX_RETRIES = 3;

    /**
     * Whether or not a user is connected stored in the cache.
     * Thread-safe using AtomicBoolean.
     */
    private final AtomicBoolean isConnectedCached = new AtomicBoolean(false);

    /**
     * Cache boolean representing whether or not the user is playing anything.
     * Thread-safe using AtomicBoolean.
     */
    private final AtomicBoolean isPlayingCached = new AtomicBoolean(false);

    /**
     * Cache string representing the connected user.
     * Thread-safe using AtomicReference.
     */
    private final AtomicReference<String> connectedUserCached = new AtomicReference<>(null);

    // Spotify API client and auth state (merged from previous SpotifySession)
    private SpotifyApi spot;
    private final AtomicBoolean isAuthenticated = new AtomicBoolean(false);

    private static final Logger LOG = LogManager.getLogger(SpotifyIntegration.class);

    private SpotifyState board;

    private final StateBroadcastService broadcaster;

    public SpotifyIntegration(String clientID, String clientSecret, String redirectURI, String vestaboardKey, StateBroadcastService stateBroadcastService) {
        LOG.debug("SpotifyIntegration created.");

        // Set broadcaster
        this.broadcaster = stateBroadcastService;
        board = new SpotifyState();

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
            isAuthenticated.set(true);
            // update cache after authentication
            updateCache();
            LOG.info("Auth token submitted, logged in as " + connectedUserCached.get());
            broadcaster.broadCastState("login", board);
        } catch (Exception e) {
            LOG.info("Error submitting auth token, ERROR_MSG: " + e.getMessage());
        }
        isConnectedCached.set(isAuthenticated.get());
        return isConnectedCached.get();
    }

    /** Disconnects the users account from the application. */
    public void logout() {
        LOG.info("Logged out, resetting spotify auth");
        // clear tokens and auth state
        if (spot != null) {
            spot.setAccessToken(null);
            spot.setRefreshToken(null);
        }
        isAuthenticated.set(false);
        isConnectedCached.set(false);
        isPlayingCached.set(false);
        connectedUserCached.set(null);

        broadcaster.broadCastState("logout", board);
    }

    /**
     * Refreshes the Spotify access token using the refresh token.
     * Should be called when the access token expires.
     * 
     * @return true if refresh was successful, false otherwise
     */
    public boolean refreshAccessToken() {
        try {
            if (spot.getRefreshToken() == null) {
                LOG.warn("Cannot refresh token: no refresh token available");
                return false;
            }
            
            AuthorizationCodeCredentials creds = spot
                    .authorizationCodeRefresh()
                    .build()
                    .execute();
            
            spot.setAccessToken(creds.getAccessToken());
            // Spotify may return a new refresh token
            if (creds.getRefreshToken() != null) {
                spot.setRefreshToken(creds.getRefreshToken());
            }
            
            LOG.info("Successfully refreshed access token");
            return true;
        } catch (Exception e) {
            LOG.error("Failed to refresh access token: " + e.getMessage());
            // Token refresh failed, user needs to re-authenticate
            isAuthenticated.set(false);
            isConnectedCached.set(false);
            return false;
        }
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
        return connectedUserCached.get();
    }

    public Boolean getAuthStatus() {
        return isConnectedCached.get();
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
        return isConnectedCached.get();
    }

    /**
     * @return A boolean variable representing whether or not the user is currently
     *         playing a song.
     */
    public Boolean isPlaying() {
        return isPlayingCached.get();
    }

    /**
     * Retrieves the currently playing song from the Spotify service.
     * Uses iterative retry with backoff for rate limiting.
     * 
     * @return the currently playing {@link Song} if available, or {@code null} if
     *         an error occurs or no song is playing.
     */
    public Song getCurrentSong() {
        int retryCount = 0;
        
        while (retryCount < MAX_RETRIES) {
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
                    LOG.debug("Retrieving current song, SONG: " + currentSong.getTitle() + " - " + currentSong.getArtist());
                    if (!isPlayingCached.get()) {
                        // if the user wasn't playing anything before, send the change in state to the emitter.
                        broadcaster.broadCastState("play", board);
                    }
                    isPlayingCached.set(true);
                    return currentSong;
                } else {
                    // No song currently playing
                    if (isPlayingCached.get()) {
                        isPlayingCached.set(false);
                        broadcaster.broadCastState("paused", board);
                    }
                    return null;
                }
            } catch (TooManyRequestsException e) {
                retryCount++;
                Integer retryAfter = e.getRetryAfter();
                LOG.warn("getCurrentSong() raised TooManyRequests exception, backing off for " + retryAfter + " seconds. Retry " + retryCount + "/" + MAX_RETRIES);
                if (retryCount >= MAX_RETRIES) {
                    LOG.error("Max retries exceeded for getCurrentSong()");
                    return null;
                }
                try {
                    Thread.sleep(retryAfter * 1000L);
                    LOG.info("Backoff finished, retrying.");
                } catch (InterruptedException i) {
                    LOG.warn("Backoff attempt interrupted, ERROR_MSG: " + i.getMessage());
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (SpotifyWebApiException s) {
                String message = s.getLocalizedMessage();
                LOG.warn("Could not get current song due to SpotifyWebApiException ERROR MSG: " + message);
                if (message != null && message.contains("access token expired")) {
                    LOG.info("Access token expired, attempting refresh...");
                    if (refreshAccessToken()) {
                        retryCount++;
                        continue; // Retry with new token
                    }
                }
                return null;
            } catch (IndexOutOfBoundsException e) {
                LOG.info("Not playing anything.");
                if (isPlayingCached.get()) {
                    isPlayingCached.set(false);
                    broadcaster.broadCastState("paused", board);
                }
                return null;
            } catch (Exception e) {
                String errorName = e.getClass().getSimpleName();
                String message = e.getLocalizedMessage();
                LOG.warn("Could not get current song due to " + errorName + " ERROR MSG: " + message);
                return null;
            }
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
        if (isPlayingCached.get()) {
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
        String query = "track:" + trackName + " artist:" + artistName;
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
        return isAuthenticated.get();
    }

    /**
     * Main function that runs the main program, designed to be run every n seconds,
     * if the song playing is different from the last time it ran, update the board.
     */
    public void run() {
        try {
            // Won't run if spotify isn't authenticated, that way I won't get any errors.
            if (isConnectedCached.get()) {
                Song currentSong = getCurrentSong();
                Song upNext = getNextUp();

                // if cached songs are empty, that likely means user just logged in.
                Song cachedCurrent = board.getCurrentSong();
                Song cachedNext = board.getNextSong();
                
                if (cachedCurrent == null || cachedCurrent.getTitle().isEmpty()) {
                    LOG.trace("Cached songs are empty, updating currentSongCached and upNextCached");
                    if (currentSong != null) {
                        board.setCurrentSong(currentSong);
                    }
                    if (upNext != null) {
                        board.setNextSong(upNext);
                    }
                    // Broadcast initial state
                    if (currentSong != null) {
                        broadcaster.broadCastState("song_change", board);
                    }
                    return;
                }

                // Check if song changed
                if (currentSong != null && !currentSong.equals(cachedCurrent)) {
                    String previousTitle = cachedCurrent != null ? cachedCurrent.getTitle() : "nothing";
                    LOG.info("Now playing changed from " + previousTitle + " to " + currentSong.getTitle());

                    /*
                     * update the cache to match the current song before
                     * notifying the observer
                     */
                    board.setCurrentSong(currentSong);
                    if (upNext != null) {
                        board.setNextSong(upNext);
                    }
                    
                    // Broadcast to emitters
                    broadcaster.broadCastState("song_change", board);
                }
                // also update if the queue is updated. will come useful when requests are
                // implemented.
                else if (upNext != null && !upNext.equals(cachedNext)) {
                    String previousTitle = cachedNext != null ? cachedNext.getTitle() : "nothing";
                    LOG.info("Up next changed from " + previousTitle + " to " + upNext.getTitle());

                    // see above
                    if (currentSong != null) {
                        board.setCurrentSong(currentSong);
                    }
                    board.setNextSong(upNext);
                    
                    // Broadcast to emitters
                    broadcaster.broadCastState("queue_change", board);
                }
            }
        } catch (Exception e) {
            LOG.error("Error in run(): " + e.getMessage(), e);
        }
    }

    /**
     * Update the cache.
     * Call this sparingly, as this can result in rate limits.
     */
    public void updateCache() {
        try {
            isConnectedCached.set(isAuthenticated.get());
            connectedUserCached.set(getConnectedUser());
            
            Song currentSong = getCurrentSong();
            if (currentSong != null) {
                board.setCurrentSong(currentSong);
                isPlayingCached.set(true);
                Song nextSong = getNextUp();
                if (nextSong != null) {
                    board.setNextSong(nextSong);
                }
            } else {
                isPlayingCached.set(false);
            }
        } catch (Exception e) {
            LOG.warn("Error updating cache, ERROR MSG: " + e.getMessage());
        }
    }
}
