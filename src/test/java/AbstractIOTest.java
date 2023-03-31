import org.junit.jupiter.api.AfterEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public abstract class AbstractIOTest {

	@AfterEach
	public void cleanUp() throws IOException {
		File file = new File(getPath());
		if (file.exists()) {
			if (file.isFile())
				file.delete();
			else
				Files.walkFileTree(Paths.get(file.getAbsolutePath()), new TreeDeleter());
		}
	}

	protected abstract String getPath();


	private static class TreeDeleter extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.delete(file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
			if (e == null) {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			} else {
				// directory iteration failed
				throw e;
			}
		}
	}
}
