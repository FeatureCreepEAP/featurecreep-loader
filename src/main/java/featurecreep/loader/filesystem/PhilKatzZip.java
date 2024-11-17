package featurecreep.loader.filesystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Reads PKZips. Can get byte[] or inputstream for files. Returns null for folders but keeps the name in the map.
 */
public class PhilKatzZip extends VirtualFileSystem{
	

	// 构造函数：从ZipFile中提取ZIP条目
	/**
	 * Creates a new PKZip Reader Instance
	 * 
	 * @param zip ZipFile to read from. This will be closed
	 * @throws IOException
	 */
	   public PhilKatzZip(ZipFile zip) throws IOException {
	        super(new File(zip.getName()).toURI());
	        Enumeration<? extends ZipEntry> entries = zip.entries();
	        while (entries.hasMoreElements()) {
	            ZipEntry entry = entries.nextElement();
	            String name = entry.getName();
	            if (!entry.isDirectory()) { // Ensure to process only file entries
	                // Use InputStream to read data
	                try (InputStream is = zip.getInputStream(entry); 
	                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
	                    byte[] buffer = new byte[4096]; // Buffer size of 4KB
	                    int bytesRead;
	                    while ((bytesRead = is.read(buffer)) != -1) {
	                        baos.write(buffer, 0, bytesRead);
	                    }
	                    map.put(name, baos.toByteArray());
	                    // If the entry is a JarEntry, retrieve its attributes
	                    if (entry instanceof JarEntry) {
	                        JarEntry je = (JarEntry) entry;
	                        codesigners.put(name, je.getCodeSigners());
	                        certificates.put(name, je.getCertificates());
	                        attributes.put(name, je.getAttributes());
	                    }
	                }
	            } else {
	                map.put(entry.getName(), null); // Put null for directories
	            }
	        }
	    }

	// 构造函数：从ZipInputStream中提取ZIP条目
	/**
	 * Creates a new PKZip Reader Instance
	 * 
	 * @param zip ZipFile to read from. This will be closed
	 * @throws IOException
	 */
	public PhilKatzZip(ZipInputStream zip, URI uri) throws IOException {
		super(uri);
		ZipEntry entry;
		while ((entry = zip.getNextEntry()) != null) {
			String name = entry.getName();
			if (!entry.isDirectory()) { // 确保只处理文件条目
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int len;
				while ((len = zip.read(buffer)) > 0) {
					baos.write(buffer, 0, len);
				}
				map.put(name, baos.toByteArray());
				baos.close();
			}
			zip.closeEntry();
		}
	}

	/**
	 * Creates a new PKZip Reader Instance
	 * 
	 * @param zip ZipFile to read from. This will be closed
	 * @throws IOException
	 */
	public PhilKatzZip(ZipInputStream zip) throws IOException {
		this(zip, null);
	}

	/**
	 * Creates a new PKZip Reader Instance
	 * 
	 * @param zip ZipFile to read from.
	 * @throws IOException
	 */
	public PhilKatzZip(String zip) throws IOException {
		this(new ZipFile(zip));
	}

	/**
	 * Creates a new PKZip Reader Instance
	 * 
	 * @param zip ZipFile to read from. This will be closed. MUST BE COMPATIBLE WITH
	 *            ZIP INPUTSTREAM
	 * @throws IOException
	 */
	public PhilKatzZip(InputStream zip) throws IOException {
		this(new ZipInputStream(zip));
	}
	
	/**
	 * Creates a new PKZip Reader Instance
	 * 
	 * @param zip ZipFile to read from. This will be closed. MUST BE COMPATIBLE WITH
	 *            ZIP INPUTSTREAM
	 * @throws IOException
	 */
	public PhilKatzZip(InputStream zip, URI uri) throws IOException {
		this(new ZipInputStream(zip), uri);
	}
	/**
	 * Creates a new PKZip Reader Instance
	 * 
	 * @param zip ZipFile to read from.
	 * @throws IOException
	 * @throws URISyntaxException 
	 */
	public PhilKatzZip(URL zip) throws IOException, URISyntaxException {
		this(zip.openStream(), zip.toURI());
	}
	
	@Override
	public URL getURLForFile(String file) {
		// TODO Auto-generated method stub
		if (getURL() == null) {
			return null;
		}

		// Ensure the entry name is properly formatted for a URL
		String entryPath = file.replace("\\", "/"); // Normalize path for URL
		try {
			String proto = getURL().toString() + "!/" + entryPath;
			if (!proto.startsWith("jar:")) {
				proto = "jar:" + proto;
			}
			return new URI(proto).toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return getURL();

	}
	
	
	
	
	
}

