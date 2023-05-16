package io;

public class StorageSpec {
	public final String locationPath;
	public final String exprValuePath;
	public final String annotationPath;

	public StorageSpec(final String locationPath, final String exprValuePath, final String annotationPath) {
		this.locationPath = locationPath;
		this.exprValuePath = exprValuePath;
		this.annotationPath = annotationPath;
	}
}
