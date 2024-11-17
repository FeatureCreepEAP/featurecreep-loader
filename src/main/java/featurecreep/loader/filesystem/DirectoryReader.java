package featurecreep.loader.filesystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DirectoryReader extends VirtualFileSystem {

	public ArrayList<String> entries = new ArrayList<String>();
	public Map<String, byte[]> map = getMap();

	// 构造函数：从指定目录中读取文件
	public DirectoryReader(File directory) throws IOException {
		this(directory.toPath());
	}

	// 构造函数：从指定目录中读取文件
	public DirectoryReader(Path directory) throws IOException {
		super(directory.toUri());
		readDirectoryRecursively(directory);
	}

	// 递归读取目录的方法
	public void readDirectoryRecursively(Path directory) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
			for (Path entry : stream) {
				String entryName = entry.getFileName().toString();
				entries.add(entryName);
			}
		}
	}

	// 重写getURLForFile方法以处理目录中的文件
	@Override
	public URL getURLForFile(String file) {
		if (getURL() == null) {
			return null;
		}
		// 构建文件的URL
		try {
			File fileObj = new File(new File(getURL().getPath()), file);
			return fileObj.toURI().toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		return getURL();
	}

	/**
	 * Avoid when possible
	 */
	@Override
	public Map<String, byte[]> getMap() {
		// TODO Auto-generated method stub
		Map<String, byte[]> hash = new HashMap<String, byte[]>();
		for (String srt : entries) {
			File fil = new File(srt);
			if (!srt.endsWith("/")) {
				// 读取文件内容并存储在map中
				try (InputStream is = Files.newInputStream(fil.toPath());
						ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = is.read(buffer)) != -1) {
						baos.write(buffer, 0, bytesRead);
					}
					hash.put(srt, baos.toByteArray());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				// 对于目录，存储引用并递归读取
				hash.put(srt, null);
			}
		}

		return hash;
	}

	@Override
	public byte[] get(String file) throws FileNotFoundException, IOException {
		InputStream is = Files.newInputStream(new File(file).toPath());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte[] buffer = new byte[4096];
		int bytesRead;
		while ((bytesRead = is.read(buffer)) != -1) {
			baos.write(buffer, 0, bytesRead);
		}
		is.close();
		return baos.toByteArray();

	}
	
	@Override
	public boolean has(String file) {
		return entries.contains(file);
	}
	
	@Override
	public Collection<String> getFilenames(String prefix) {
		Collection<String> result = new ArrayList<>();
		for (String entryName : entries) {
			if (entryName.startsWith(prefix)) {
				result.add(entryName);
			}
		}
		return result;
	}


}