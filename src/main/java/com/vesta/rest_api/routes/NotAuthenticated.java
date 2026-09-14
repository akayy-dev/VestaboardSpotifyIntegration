package com.vesta.rest_api.routes;

public class NotAuthenticated extends Exception {
	public NotAuthenticated(String message) {
		super(message);
	}

	public NotAuthenticated(String message, Throwable cause) {
		super(message, cause);
	}
}
