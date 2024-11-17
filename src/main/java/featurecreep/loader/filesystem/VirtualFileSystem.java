package featurecreep.loader.filesystem;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;

public class VirtualFileSystem implements FileSystem{

	public Map<String, byte[]> map = new HashMap<String, byte[]>();
	public Map<String,CodeSigner[]> codesigners = new HashMap<String,CodeSigner[]>();
	public Map<String,Certificate[]> certificates = new HashMap<String,Certificate[]>();
	public Map<String,Attributes> attributes = new HashMap<String,Attributes>();
	public URI uri;
	
	/**
	 * A virtual filesystem with a URI
	 * @param uri
	 */
	public VirtualFileSystem(URI uri) {
		this.uri=uri;
	}
	
	/**
	 * A VirtualFileSystem without a URI. Try to avoid
	 */
	public VirtualFileSystem() {}
	
	@Override
	public Map<String, byte[]> getMap() {
		// TODO Auto-generated method stub
		return map;
	}

	@Override
	public URI getURI() {
		// TODO Auto-generated method stub
		return uri;
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
			String str;
			if(getURL().toString().endsWith("/") || getURL().toString().isEmpty()) {
				str = getURL().toString();
			}else {
				str = getURL().toString()+"/";
			}
			String proto = str + entryPath;
			return new URI(proto).toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return getURL();

	}
	
	
	
	@Override
	public CodeSigner[] getCodeSigners(String file){
		return codesigners.get(file);
	}

	@Override
	public Certificate[] getCertificates(String file){
		return certificates.get(file);
	}
	
	@Override
	public Attributes getAttributes(String file) {
		return attributes.get(file);
	}
	

	
}
