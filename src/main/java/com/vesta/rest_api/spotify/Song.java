package com.vesta.rest_api.spotify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Song(String title, String artist, String albumArt) { 
	
	public String getTitle() {
		return title;
	}
	public String getArtist() {
		return artist;
	}

	public String getAlbumArt() {
		return albumArt;
	}

    public String getTrimmedTitle() {
        // use regex to remove features from the title.
        String pattern = "\\s*\\(.*?\\b(ft|featuring|with|feat)\\b.*?\\)";

        Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher(title);

        String strippedTitle = matcher.replaceAll("");

        return strippedTitle;
    }
}
