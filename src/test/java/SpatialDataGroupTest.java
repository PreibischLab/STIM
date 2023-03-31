import io.SpatialDataGroup;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SpatialDataGroupTest extends AbstractIOTest {

	protected String getPath() {
		return "data/group.n5";
	}

	@Test
	public void new_empty_group_is_empty() throws IOException {
		SpatialDataGroup group = SpatialDataGroup.createNew(getPath());
		assertTrue((new File(getPath())).exists(), "File '" + getPath() + "' does not exist.");

		List<String> datasets = group.getDatasets();
		assertTrue(datasets.isEmpty(), "Dataset is not empty.");
	}
}
