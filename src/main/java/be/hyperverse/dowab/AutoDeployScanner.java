package be.hyperverse.dowab;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.osgi.framework.BundleContext;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringUtil;

import be.hyperverse.dowab.wab.WabHandler;
import be.hyperverse.dowab.war.WarHandler;

public class AutoDeployScanner extends Thread {
	private static final Log LOG = LogFactoryUtil.getLog(AutoDeployScanner.class);

	private static final String TMP_DIR = "java.io.tmpdir";
	private static final String WAB_EXTENSION = ".wab";
	private static final String WAR_EXTENSION = ".war";
	private static final String WAB_WAR_EXTENSION = ".wab.war";

	private boolean started = true;
	private final File deployDir;

	private WabHandler wabHandler;
	private WarHandler warHandler;

	private final Map<String, Long> blacklistFileTimestamps;

	public AutoDeployScanner(final BundleContext bc, final ThreadGroup threadGroup, final String name, final File deployDir) {
		super(threadGroup, name);
		this.deployDir = deployDir;

		Class<?> clazz = getClass();

		setContextClassLoader(clazz.getClassLoader());

		setDaemon(true);
		setPriority(MIN_PRIORITY);

		wabHandler = new WabHandler(bc);
		warHandler = new WarHandler(bc);

		blacklistFileTimestamps = new HashMap<>();
	}

	public void pause() {
		started = false;
	}

	@Override
	public void run() {
		while (started) {
			try {
				sleep(2);
			} catch (InterruptedException ie) {
				LOG.debug(ie.getMessage());
			}

			try {
				checkDeployDir();
				scanDirectory();
			} catch (Exception e) {
				if (LOG.isWarnEnabled()) {
					LOG.warn("Unable to scan the auto deploy directory", e);
				}
			}
		}
	}
	
	private void checkDeployDir() {
		if (!deployDir.exists()) {
			if (LOG.isInfoEnabled()) {
				LOG.info("Creating missing directory " + deployDir);
			}

			boolean created = deployDir.mkdirs();

			if (!created) {
				LOG.error("Directory " + deployDir + " could not be created");
			}
		}
	}
	
	protected void scanDirectory() {
		File[] files = deployDir.listFiles();

		if (files == null) {
			return;
		}
		
		Set<String> blacklistedFileNames = blacklistFileTimestamps.keySet();

		Iterator<String> iterator = blacklistedFileNames.iterator();

		while (iterator.hasNext()) {
			String blacklistedFileName = iterator.next();

			boolean blacklistedFileExists = false;

			for (File file : files) {
				if (StringUtil.equalsIgnoreCase(blacklistedFileName, file.getName())) {

					blacklistedFileExists = true;
				}
			}

			if (!blacklistedFileExists) {
				iterator.remove();
			}
		}

		for (File file : files) {
			String fileName = file.getName();

			//fileName = StringUtil.toLowerCase(fileName);

			if (file.isFile()) {
				if (fileName.endsWith(WAB_EXTENSION) || fileName.endsWith(WAB_WAR_EXTENSION)) {
					LOG.info("Processing WAB file: " + file.getName());
					handleFile(fileName, file, wabHandler::processFile);
				} else if (fileName.endsWith(WAR_EXTENSION)) {
					LOG.info("Processing WAR file " + file.getName());
					handleFile(fileName, file, warHandler::processFile);
				}
			}
		}
	}

	private void handleFile(String fileName, File file, Consumer<File> function) {
		LocalDateTime start = LocalDateTime.now();
		
		if (blacklistFileTimestamps.containsKey(file.getName()) &&
			(blacklistFileTimestamps.get(file.getName()) == file.lastModified())) {
			return;
		}

		Path tempFile = Paths.get(System.getProperty(TMP_DIR), fileName);
		try {
			Files.move(Paths.get(file.getAbsolutePath()), tempFile,
					StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			blacklistFileTimestamps.put(file.getName(), file.lastModified());
			function.accept(tempFile.toFile());
		} catch (IOException e) {
			LOG.error(e);
		}

		LocalDateTime end = LocalDateTime.now();
		LOG.info("Deploy took " + getFormattedDuration(Duration.between(start, end)));
	}

	private String getFormattedDuration(Duration duration) {
		final long minutes = duration.toMinutes();
		final long seconds = duration.getSeconds() % 60;
		final long millis = duration.toMillis();

		return String.format("%s milliseconds (%s minutes %s seconds)", millis, minutes, seconds);
	}
}
