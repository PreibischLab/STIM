import org.junit.jupiter.api.AfterEach;

import java.io.File;

public abstract class AbstractIOTest {

	@AfterEach
	public void cleanUp() {
		File file = new File(getPath());
		if (file.exists())
			file.delete();
	}

	protected abstract String getPath();
}
