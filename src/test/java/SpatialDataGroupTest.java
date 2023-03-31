import io.SpatialDataGroup;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SpatialDataGroupTest {

	protected String groupPath = "data/group.n5";

	@Test
	public void create_group() throws IOException {
		SpatialDataGroup group = SpatialDataGroup.createNew(groupPath);
		File file = new File(groupPath);
		assertTrue(file.exists(), "File '" + groupPath + "' does not exist.");

		List<String> datasets = group.getDatasets();
		assertTrue(datasets.isEmpty(), "Dataset is not empty.");

		if (file.exists())
			file.delete();
	}
}
