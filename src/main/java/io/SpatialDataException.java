package io;

public class SpatialDataException extends RuntimeException {
	public SpatialDataException() {}
	public SpatialDataException(Exception e) {
		super(e);
	}

	public SpatialDataException(String message) {
		super(message);
	}

	public SpatialDataException(String message, Exception e) {
		super(message, e);
	}
}