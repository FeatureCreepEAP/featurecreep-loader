package featurecreep.loader.utils;

public class JavaUtils {

	public static boolean isJavaVersionNewerThan8() {
		// Get the current Java version
		String version = System.getProperty("java.version");

		// Parse the version number
		String[] versionParts = version.split("\\.");

		// Check if the version is valid and compare
		if (versionParts.length > 0) {
			int majorVersion = Integer.parseInt(versionParts[0]);

			// Check if the major version is greater than 8
			return majorVersion > 8;
		}

		return false; // Default return if version is not valid
	}

	public static int getMajorJavaVersion() {
		// Get the current Java version
		String version = System.getProperty("java.version");

		// Parse the version number
		String[] versionParts = version.split("\\.");

		// Check if the version is valid and compare
		if (versionParts.length > 0) {
			int majorVersion = Integer.parseInt(versionParts[0]);

			// Check if the major version is greater than 8
			return majorVersion;
		}

		return 0; // Default return if version is not valid
	}

}
