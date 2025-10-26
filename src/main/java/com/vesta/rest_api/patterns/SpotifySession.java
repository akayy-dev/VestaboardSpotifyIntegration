package com.vesta.rest_api.patterns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.vesta.rest_api.Song;

import org.apache.commons.logging.Log;
import org.apache.hc.core5.http.ParseException;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
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

public class SpotifySession {
	private SpotifyApi spot;
	private boolean isAuthenticated;
	private static final Logger LOG = LogManager.getLogger(SpotifySession.class);

	/**
	 * Singleton for interacting with spotify API.
	 */
	public SpotifySession(String clientID, String clientSecret, String redirectURI) {
		// Initialize Logger

		try {
			spot = new SpotifyApi.Builder()
					.setClientId(clientID)
					.setClientSecret(clientSecret)
					.setRedirectUri(SpotifyHttpManager.makeUri(redirectURI))
					.build();
		} catch (Exception e) {
			LOG.warn("Failed to authenticate, are your keys correct? ERROR_MSG: " + e.getLocalizedMessage());
		}

		isAuthenticated = false;
	}

	/**
	 * Creates an authorization code URI request for Spotify's OAuth 2.0 flow.
	 * 
	 * The request is also configured to show a dialog to the user.
	 * 
	 * @return A {@link String} containing a URL to the Spotify Authentication page.
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
	 * Retrieves the display name of the currently connected Spotify user.
	 *
	 * @return A string representing the display name of the connected user.
	 * @throws SpotifyWebApiException If an error occurs while communicating with
	 *                                the Spotify Web API.
	 * @throws IOException            If an I/O error occurs.
	 * @throws ParseException         If an error occurs while parsing the response.
	 */
	public String getConnectedUser() throws SpotifyWebApiException, IOException, ParseException {
		LOG.debug("Getting connected user");
		String username;
		User me;
		me = spot
				.getCurrentUsersProfile()
				.build()
				.execute();
		username = me.getDisplayName().toString();
		return username;
	}

	/**
	 * Checks if the user is currently playing a song.
	 * 
	 * @return true if playing something, false if not.
	 */
	public boolean isPlaying() throws IOException, ParseException, SpotifyWebApiException {
		CurrentlyPlayingContext playbackState = spot.getInformationAboutUsersCurrentPlayback().build().execute();

		// playbackState returns null if user isn't playing anything
		if (playbackState == null) {
			return false;
		}
		return playbackState.getIs_playing();
	}

	/**
	 * Uses the provided authorization code to obtain and set the access and refresh
	 * tokens.
	 *
	 * @param auth_code The authorization code received from the Spotify
	 *                  authorization process.
	 * @return true if the tokens were successfully obtained and set.
	 * @throws IOException            If an input or output exception occurs.
	 * @throws ParseException         If a parsing exception occurs.
	 * @throws SpotifyWebApiException If an error occurs while interacting with the
	 *                                Spotify Web API.
	 */
	public boolean useAuthToken(String auth_code) throws IOException, ParseException, SpotifyWebApiException {
		LOG.debug("Submitting an auth token");
		AuthorizationCodeCredentials creds = spot
				.authorizationCode(auth_code)
				.build()
				.execute();
		spot.setAccessToken(creds.getAccessToken());
		spot.setRefreshToken(creds.getRefreshToken());
		isAuthenticated = true;
		LOG.debug("Auth code is valid.");
		return isAuthenticated;
	}

	/**
	 * Searches for tracks based on the given query.
	 *
	 * @param query The search query string.
	 * @return An array of Track objects that match the search query.
	 * @throws IOException            If an input or output exception occurs.
	 * @throws ParseException         If a parsing exception occurs.
	 * @throws SpotifyWebApiException If a Spotify Web API exception occurs.
	 */
	public Track[] searchForTracks(String query) throws IOException, ParseException, SpotifyWebApiException {
		// Search for the song
		final Paging<Track> tracks = spot
				.searchTracks(query)
				.build()
				.execute();
		return tracks.getItems();
	}

	public boolean isAuthenticated() {
		return isAuthenticated;
	}

	/**
	 * Retrieves the currently playing song from the Spotify API.
	 *
	 * @return A HashMap containing the song name and artist name if a song is
	 *         currently playing,
	 *         or null if no song is currently playing. The key "name" returns the
	 *         songname, the key "artist" returns the artist.
	 * @throws IOException            If there is an issue with network
	 *                                communication.
	 * @throws ParseException         If there is an issue parsing the response.
	 * @throws SpotifyWebApiException If there is an issue with the Spotify Web API.
	 */
	public Song getCurrentSong() throws IOException, ParseException, SpotifyWebApiException {
		final GetUsersCurrentlyPlayingTrackRequest currentlyPlayingRequest = spot.getUsersCurrentlyPlayingTrack()
				.build();
		final CurrentlyPlaying currentlyPlaying = currentlyPlayingRequest.execute();
		if (currentlyPlaying != null) { // Check if a song is actually playing at the moment.
			String songName = currentlyPlaying.getItem().getName();
			String songID = currentlyPlaying.getItem().getId();

			String trackArtist = getSongArtistFromID(songID);
			String albumArt = getAlbumArtFromSongID(songID);

			Song currentSong = new Song(songName, trackArtist, albumArt);
			LOG.debug("Retreiving current song, SONG: " + currentSong.getTitle() + " - " + currentSong.getArtist());
			return currentSong;
		} else {
			return null;
		}
	}

	/**
	 * Get the artist of a song by its ID, because the library devs didn't think
	 * that a Track
	 * object should be have the artist in it's attributes.
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

	/**
	 * Retrieves the name of the next item in the user's Spotify playback queue.
	 *
	 * @return the name of the next item in the playback queue.
	 * @throws IOException            if an I/O error occurs.
	 * @throws ParseException         if a parsing error occurs.
	 * @throws SpotifyWebApiException if an error occurs with the Spotify Web API.
	 */
	public Song getNextUp() throws IOException, ParseException, SpotifyWebApiException {
		final PlaybackQueue queue = spot
				.getTheUsersQueue()
				.build()
				.execute();
		final IPlaylistItem nextUp = queue.getQueue().get(0);
		final String songName = nextUp.getName();
		final String artist = getSongArtistFromID(nextUp.getId());
		final String albumArt = getAlbumArtFromSongID(nextUp.getId());
		Song nextSongUp = new Song(songName, artist, albumArt);
		return nextSongUp;
	}

	/**
	 * Retrieves the current user's Spotify queue.
	 *
	 * @return An array of strings representing the names of the songs in the user's
	 *         queue.
	 * @throws IOException            If an input or output exception occurs.
	 * @throws ParseException         If a parsing exception occurs.
	 * @throws SpotifyWebApiException If an error occurs while interacting with the
	 *                                Spotify Web API.
	 */
	public Song[] getQueue() throws IOException, ParseException, SpotifyWebApiException {
		final List<IPlaylistItem> queue = spot
				.getTheUsersQueue()
				.build()
				.execute()
				.getQueue();

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
	}

	/**
	 * Search a song by it's name and add to it a users queue.
	 * 
	 * @param query The name of the song to search for
	 * @return The song added to the queue.
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
	 * Resets the authentication tokens and updates the authentication status.
	 * This method sets the access token and refresh token to null, and marks the
	 * user as not authenticated.
	 */
	public void resetAuth() {
		spot.setAccessToken(null);
		spot.setRefreshToken(null);
		this.isAuthenticated = false;
	}

}
