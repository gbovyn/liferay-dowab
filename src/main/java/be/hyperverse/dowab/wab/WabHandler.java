package be.hyperverse.dowab.wab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.startlevel.BundleStartLevel;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import be.hyperverse.dowab.BundleUtils;

public class WabHandler {
	private static final Log LOG = LogFactoryUtil.getLog(WabHandler.class);

	private final BundleContext bc;

	public WabHandler(final BundleContext bc) {
		this.bc = bc;
	}

	public void processFile(final File file) {
		try {
			final String symbolicName = BundleUtils.getSymbolicName(file);

			final Optional<Bundle> bundle = Arrays.stream(bc.getBundles())
					.filter(b -> b.getSymbolicName().equals(symbolicName))
					.findFirst();

			try (final FileInputStream fileStream = new FileInputStream(file)) {
				if (bundle.isPresent()) {
					updateBundle(file, bundle.get(), fileStream);
				} else {
					installBundle(file, symbolicName);
				}
				LOG.info("Processed: " + file.getName());
			} catch (IOException e) {
				LOG.warn(e);
			}
		} catch (BundleException e) {
			LOG.warn(e);
		} finally {
			file.delete();
		}
	}

	private void updateBundle(final File file, final Bundle bundle, final FileInputStream fileStream)
			throws BundleException {
		LOG.info("Updating: " + bundle + " - " + file);
		bundle.update(fileStream);
	}

	private void installBundle(final File file, final String symbolicName)
			throws BundleException, FileNotFoundException, MalformedURLException {
		final URL artifactPath = createBundleLocation(file.getAbsolutePath(), symbolicName);
		LOG.info("Installing: " + symbolicName + " - " + file);
		final Bundle b = bc.installBundle(artifactPath.toString(), new FileInputStream(file));
		final BundleStartLevel bundleStartLevel = b.adapt(BundleStartLevel.class);
		bundleStartLevel.setStartLevel(1);
		b.start();
	}

	private URL createBundleLocation(final String path, final String symbolicName) throws MalformedURLException {
		final String contextName = symbolicName;

		final StringBuilder sb = new StringBuilder();

		sb.append("file:");
		sb.append(path.replaceAll("\\\\", "/"));
		sb.append("?");
		sb.append(Constants.BUNDLE_SYMBOLICNAME);
		sb.append("=");
		sb.append(symbolicName);
		sb.append("&Web-ContextPath=/");
		sb.append(contextName);
		sb.append("&protocol=file");
		
		return new URL("webbundle", null, sb.toString());
	}
}
