package se.sundsvall.aotloader.service.mapper;

import java.util.Locale;

/**
 * Compares resource paths of labels without regard to leading and trailing slashes or case, since SupportManagement
 * writes them with a leading slash and the solution document without.
 */
public final class LabelPaths {

	private LabelPaths() {}

	/**
	 * @return the path without slashes at the start and the end, in upper case, or null for null
	 */
	public static String normalize(final String path) {
		if (path == null) {
			return null;
		}
		return path.strip().replaceAll("^/+|/+$", "").toUpperCase(Locale.ROOT);
	}
}
