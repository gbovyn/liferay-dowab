package be.hyperverse.dowab;

import com.liferay.portal.kernel.util.FileUtil;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BundleUtils {
	private static final Pattern PATTERN = Pattern.compile("(.*?)(-\\d+\\.\\d+\\.\\d+\\.\\d+)?");

	private BundleUtils() {}

	public static String getSymbolicName(final File f) {
		String path = f.getAbsolutePath();

		int x = path.lastIndexOf(File.separator);
		final String extension = FileUtil.getExtension(path);
		int y = path.lastIndexOf(extension);

		String name = path.substring(x + 1, y);

		Matcher matcher = PATTERN.matcher(name);

		if (matcher.matches()) {
			name = matcher.group(1);
		}

		return name;
	}
}
