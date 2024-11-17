package featurecreep.loader.filesystem;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.jar.Attributes;

import featurecreep.loader.utils.JavaUtils;

public interface FileSystem {

	/**
	 * Try to avoid using this as it takes up memory. Use getFileNameInstead
	 * @return
	 */
	public Map<String, byte[]> getMap();

	public default byte[] get(String file) throws FileNotFoundException, IOException {
		byte[] ret = getMap().get(file);
		if (ret == null) {
			throw new FileNotFoundException(file);
		}
		return ret;
	}

	public default InputStream getStream(String file) throws FileNotFoundException, IOException {
		return new ByteArrayInputStream(get(file));
	}

	public default boolean has(String file) {
		return getFilenames(file).contains(file);
	}

	public default int getFileSize(String file) throws FileNotFoundException, IOException {
		return get(file).length;
	}

	public default Collection<String> getFilenames(String prefix) {
		Collection<String> result = new ArrayList<>();
		for (Map.Entry<String, byte[]> entryName : getMap().entrySet()) {
			if (entryName.getKey().startsWith(prefix)) {
				result.add(entryName.getKey());
			}
		}
		return result;
	}

	/**
	 * URI for the FileSystem. MAY BE NULL SHOULD CONTAIN RELATIVE DIRECTORY
	 * 
	 * @return
	 */
	public URI getURI();

	/**
	 * URL for the FileSystem. MAY BE NULL. SHOULD CONTAIN RELATIVE DIRECTORY
	 * 
	 * @return
	 */
	public default URL getURL() {
		URI uri = getURI();
		if (uri != null) {
			try {
				return uri.toURL();
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * URL for a File. MAY BE NULL SHOULD CONTAIN RELATIVE DIRECTORY
	 * 
	 * @return
	 */
	public URL getURLForFile(String file);

	/**
	 * CodeSigners. Null by default. Should be overriden if they exist, which is
	 * mainly for jars
	 * 
	 * @return
	 */
	public default Map<String, CodeSigner[]> getCodeSigners() {
		return null;
	}

	/**
	 * Certificates. Null by default. Should be overriden if they exist, which is
	 * mainly for jars
	 * 
	 * @return
	 */
	public default Map<String, Certificate[]> getCertificates() {
		return null;
	}

	/**
	 * ManifestAttributes. Null by default. Should be overriden if they exist, which
	 * is mainly for jars
	 * 
	 * @return
	 */
	public default Map<String, Attributes> getAttributes() {
		return null;
	}

	/**
	 * CodeSigners. Null by default. Should be overriden if they exist, which is
	 * mainly for jars
	 * 
	 * @return
	 */
	public default CodeSigner[] getCodeSigners(String file) {
		if (getCodeSigners() != null) {
			return getCodeSigners().get(file);
		}
		return null;

	}

	/**
	 * Certificates. Null by default. Should be overriden if they exist, which is
	 * mainly for jars
	 * 
	 * @return
	 */
	public default Certificate[] getCertificates(String file) {
		if (getCertificates() != null) {
			return getCertificates().get(file);
		}
		return null;
	}

	/**
	 * ManifestAttributes. Null by default. Should be overriden if they exist, which
	 * is mainly for jars
	 * 
	 * @return
	 */
	public default Attributes getAttributes(String file) {
		if (getAttributes() != null) {
			return getAttributes().get(file);
		}
		return null;
	}

//	/**
//	 * A clone of the filesystem. Should be castable to the original type
//	 * 
//	 * @return
//	 */
//	public FileSystem copy();

//	/**
//	 * For subdirectories. Should be "" if none
//	 * 
//	 * @return
//	 */
//	public String getRelativeDirectory();
//
//	/**
//	 * for subloaders, a filter for this filesystem
//	 */
//	public void setRelativeDirectory(String subdir);

	/**
	 * Removes enties which are not iin the relative directory
	 */
	public default void refreshWithRelativeDirectory(String relative_directory) {
		int len = relative_directory.length();
		if (!relative_directory.endsWith("/")) {
			len++;
		}
		for (Map.Entry<String, byte[]> entry : getMap().entrySet()) {
			if (!entry.getKey().startsWith(relative_directory)) {
				byte[] value = entry.getValue();
				String nuevo = entry.getKey().substring(len);
				getMap().put(nuevo, value);
			} else {
				getMap().remove(entry.getKey());
			}
		}

		if (this.getCodeSigners() != null) {
			for (Map.Entry<String, CodeSigner[]> entry : getCodeSigners().entrySet()) {
				if (!entry.getKey().startsWith(relative_directory)) {
					CodeSigner[] value = entry.getValue();
					String nuevo = entry.getKey().substring(len);
					getCodeSigners().put(nuevo, value);
				} else {
					getCodeSigners().remove(entry.getKey());
				}
			}
		}

		if (this.getCertificates() != null) {
			for (Map.Entry<String, Certificate[]> entry : getCertificates().entrySet()) {
				if (!entry.getKey().startsWith(relative_directory)) {
					Certificate[] value = entry.getValue();
					String nuevo = entry.getKey().substring(len);
					getCertificates().put(nuevo, value);
				} else {
					getCertificates().remove(entry.getKey());
				}
			}
		}

		if (this.getCertificates() != null) {
			for (Map.Entry<String, Attributes> entry : getAttributes().entrySet()) {
				if (!entry.getKey().startsWith(relative_directory)) {
					Attributes value = entry.getValue();
					String nuevo = entry.getKey().substring(len);
					getAttributes().put(nuevo, value);
				} else {
					getAttributes().remove(entry.getKey());
				}
			}
		}

	}

	/**
	 * Creates a sub filesystem with only the files in the relative directory.
	 * Changes are not reflected in the original filesystem
	 * 
	 * @param uri                A URI for the new filesystem. May be null.
	 * @param relative_directory The relative directory
	 * @return
	 */
	public default FileSystem createSubFileSystem(String relative_directory) {
		URL url = this.getURLForFile(relative_directory);
		URI uri = null;
		if (url != null) {
			try {
				url.toURI();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		FileSystem cp = new VirtualFileSystem(uri);
		if (getAttributes() != null) {
			cp.getAttributes().putAll(getAttributes());
		}
		if (getCertificates() != null) {
			cp.getCertificates().putAll(getCertificates());
		}
		if (getCodeSigners() != null) {
			cp.getCodeSigners().putAll(getCodeSigners());
		}
		cp.getMap().putAll(getMap());
		// cp.setRelativeDirectory(relative_directory);
		cp.refreshWithRelativeDirectory(relative_directory);
		return cp;
	}

	public default boolean isMultiRelease() {
		if (this.getAttributes() != null) {
			Attributes attrs = this.getAttributes().get("Multi-Release");
			if (attrs != null) {
				return attrs.get("Multi-Release").equals("true");
			}
		}

		return false;
	}

	/**
	 * Gets the multirelease location of a file. If it is not in the multirelease it
	 * will return original name. If not found anywhere it will throw not found
	 * execption
	 * 
	 * @param original file name you want to check in multirelease for
	 * @return
	 */
	public default String getMultiReleaseName(String original) throws FileNotFoundException {
		if (JavaUtils.isJavaVersionNewerThan8()) {
			int i = JavaUtils.getMajorJavaVersion();
			while (i > 8) {
				String name = "META-INF/versions/" + Integer.toString(i) + "/" + original;
				if (has(name)) {
					return name;
				}
				i = i - 1;
			}
		}
		if (!has(original)) {
			throw new FileNotFoundException(original);
		}
		return original;
	}

	public default byte[] getMultiRelease(String file) throws FileNotFoundException, IOException {
		byte[] ret = get(getMultiReleaseName(file));
		if (ret == null) {
			throw new FileNotFoundException(file);
		}
		return ret;
	}

	public default InputStream getMultiReleaseStream(String file) throws FileNotFoundException, IOException {
		return new ByteArrayInputStream(getMultiRelease(file));
	}

}
