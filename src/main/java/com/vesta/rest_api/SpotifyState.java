package com.vesta.rest_api;

// Logging
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Represents the state of the connected Spotify User
 * Automatically syncs with the board
 */
public class SpotifyState extends Vestaboard {

	/**
	 * Represents the current song being played.
	 */
	private Song currentSong;
	/**
	 * Represents the next song to be played.
	 */
	private Song nextSong;

	/**
	 * Indicates whether the Spotify player is currently playing.
	 */
	private boolean isPlaying;
	/**
	 * Indicates whether the Spotify service is connected.
	 */
	private boolean isConnected;

	/**
	 * Logger instance for SpotifyState class.
	 * This logger is used to log messages for the SpotifyState class.
	 * It is initialized using LogManager.getLogger with SpotifyState.class as the
	 * parameter.
	 */
	private static final Logger LOG = LogManager.getLogger(SpotifyState.class);

	/**
	 * A flag indicating whether a display update is pending.
	 * When set to true, indicates that changes have been made but not yet displayed on the Vestaboard.
	 * When set to false, indicates that all changes have been successfully displayed.
	 * 
	 * Used primarily in schedulePush()
	 */
	private boolean updatePending = false;
	

	/**
	 * A Timer instance used for scheduling and executing tasks at specified intervals.
	 * The timer runs as a dedicated thread for executing scheduled tasks.
	 *
	 * Used primarily in schedulePush()
	 */
	private final Timer timer = new Timer();

	public SpotifyState() {
		super(System.getenv("VESTABOARD_KEY"));
		LOG.info("Attaching observers to SpotifyState object.");
		LOG.info("Observers attached!");

		// set the song states to empty songs by default.
		currentSong = new Song("", "", "");
		nextSong = new Song("", "", "");
	}

	/**
	 * Retrieves the next song in the playlist.
	 *
	 * @return the next {@link Song} in the playlist.
	 */
	public Song getNextSong() {
		return nextSong;
	}

	/**
	 * Sets the next song to be played.
	 *
	 * @param nextSong the Song object representing the next song
	 */
	public void setNextSong(Song nextSong) {
		if (this.nextSong != nextSong) {

			LOG.info("Updating next song in state to " + nextSong.getTitle());
			this.nextSong = nextSong;
			schedulePush();
		}

	}

	/**
	 * "push" the state to the board. basically updates it.
	 */
	private void push() {
		Component nowPlaying = new Component();
		nowPlaying.setAlign("top");
		nowPlaying.setJustify("left");
		nowPlaying.setHeight(3);
		nowPlaying.setBody(
				"{66} Now Playing\n{64} " +
						currentSong.getTrimmedTitle() +
						"\n{63} " +
						currentSong.getArtist());
		Component upNext = new Component();
		upNext.setAlign("top");
		upNext.setJustify("left");
		upNext.setHeight(3);
		upNext.setBody(
				"\n{65} Next Up\n{67} " +
						nextSong.getTrimmedTitle());
		String VBML = Component.compileComponents(nowPlaying, upNext);
		LOG.info("Submitting state to board, NOW PLAYING: " + currentSong.getTrimmedTitle() + " UP NEXT: "
				+ nextSong.getTrimmedTitle());
		sendRaw(VBML);
	}

	public Song getCurrentSong() {
		return currentSong;
	}

	public void setCurrentSong(Song currentSong) {
		if (this.currentSong != currentSong) {

			LOG.info("Updating current song in state to " + currentSong.getTitle());
			this.currentSong = currentSong;
			schedulePush();
		}
	};

	/**
	 * Checks if the Spotify player is currently playing.
	 *
	 * @return true if the player is playing, false otherwise.
	 */
	public boolean isPlaying() {
		return isPlaying;
	}

	/**
	 * Sets the playing state of the Spotify player.
	 *
	 * @param isPlaying a boolean indicating whether the player is currently
	 *                  playing.
	 */
	public void setisPlaying(boolean isPlaying) {
		this.isPlaying = isPlaying;
	}

	/**
	 * Checks if the Spotify service is connected.
	 *
	 * @return true if the service is connected, false otherwise.
	 */
	public boolean isConnected() {
		return isConnected;
	}

	/**
	 * Sets the connection state of the Spotify service.
	 *
	 * @param isConnected a boolean indicating whether the Spotify service is
	 *                    connected (true) or not (false)
	 */
	public void setisConnected(boolean isConnected) {
		this.isConnected = isConnected;
	}

	
	/**
	 * Schedules a push update to the board with a 100ms delay if no update is currently pending.
	 * This method uses a timer to avoid excessive updates and ensures that only one update
	 * is scheduled at a time by checking and setting the updatePending flag.
	 * Once the scheduled push is complete, the updatePending flag is reset to false.
	 */
	private void schedulePush() {
		// debounce
		if (!updatePending) {
			updatePending = true;
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					push();
					updatePending = false;
				}
			}, 100); // Delay of 100 milliseconds
		}
	}
}
