import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractIOTest {

	protected static ExecutorService executorService;
	private Path testDirectoryPath = null;

	@BeforeAll
	public static void setupExecutorService() {
		executorService = Executors.newFixedThreadPool(1);
	}

	@AfterAll
	public static void shutdownExecutorService() {
		executorService.shutdown();
	}

	@BeforeEach
	public void setUpTmpDirectory() throws IOException {
		Path currentDirectory = Paths.get("").toAbsolutePath();
		testDirectoryPath = Files.createTempDirectory(currentDirectory, "tempTestDir");
	}

	@AfterEach
	public void deleteTmpDirectory() throws IOException {
		File dir = new File(testDirectoryPath.toString());
		if (dir.exists())
			Files.walkFileTree(testDirectoryPath, new TreeDeleter());
	}

	protected String getPlaygroundPath(String filePath) {
		return testDirectoryPath.resolve(filePath).toString();
	}


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
