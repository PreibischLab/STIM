package io;

// TODO: separate package from non-package exceptions
public class SpatialDataIOException extends RuntimeException {
	public SpatialDataIOException() {}

	public SpatialDataIOException(String message) {
		super(message);
	}

	public SpatialDataIOException(String message, Exception e) {
		super(message, e);
	}
}