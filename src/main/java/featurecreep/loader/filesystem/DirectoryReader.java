package featurecreep.loader.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DirectoryReader extends VirtualFileSystem {
	private final Path rootDirectory;
	private final List<String> entries = new ArrayList<>();

	public DirectoryReader(File directory) throws IOException {
		this(directory.toPath());
	}

	public DirectoryReader(Path directory) throws IOException {
		super(directory.toUri());
		this.rootDirectory = directory;
		readDirectoryRecursively(directory);
	}

	private void readDirectoryRecursively(Path directory) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
			for (Path entry : stream) {
				String relativePath = rootDirectory.relativize(entry).toString().replace('\\', '/');
				entries.add(relativePath);

				if (Files.isDirectory(entry)) {
					readDirectoryRecursively(entry);
				}
			}
		}
	}

	@Override
	public URL getURLForFile(String file) {
		try {
			Path resolved = rootDirectory.resolve(file);
			return resolved.toUri().toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Map<String, byte[]> getMap() {
		return new LazyMap();
	}

	@Override
	public byte[] get(String file) throws IOException {
		Path resolved = rootDirectory.resolve(file);
		if (Files.isDirectory(resolved)) {
			throw new FileNotFoundException("Is a directory: " + file);
		}
		return Files.readAllBytes(resolved);
	}

	@Override
	public boolean has(String file) {
		return entries.contains(file.replace('\\', '/'));
	}

	@Override
	public Collection<String> getFilenames(String prefix) {
		String normalisedPrefix = prefix.replace('\\', '/');
		return entries.stream().filter(e -> e.startsWith(normalisedPrefix)).collect(Collectors.toList());
	}

	private class LazyMap implements Map<String, byte[]> {
		@Override
		public int size() {
			return entries.size();
		}

		@Override
		public boolean isEmpty() {
			return entries.isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			return has((String) key);
		}

		@Override
		public byte[] get(Object key) {
			String file = (String) key;
			Path resolved = rootDirectory.resolve(file);
			return Files.isDirectory(resolved) ? null : get(file);
		}

		// Unsupported operations that would require full preload
		@Override
		public byte[] put(String key, byte[] value) {
			throw new UnsupportedOperationException("Read-only filesystem");
		}

		@Override
		public byte[] remove(Object key) {
			throw new UnsupportedOperationException("Read-only filesystem");
		}

		@Override
		public void putAll(Map<? extends String, ? extends byte[]> m) {
			throw new UnsupportedOperationException("Read-only filesystem");
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException("Read-only filesystem");
		}

		@Override
		public Set<String> keySet() {
			return Collections.unmodifiableSet(entries.stream().collect(Collectors.toSet()));
		}

		@Override
		public Collection<byte[]> values() {
			throw new UnsupportedOperationException("values() would require loading all files - use get() instead");
		}

		@Override
		public Set<Entry<String, byte[]>> entrySet() {
			throw new UnsupportedOperationException("entrySet() would require loading all files - use get() instead");
		}

		@Override
		public boolean containsValue(Object arg0) {
			// TODO Auto-generated method stub
			return false;// Unsupported
		}
	}
}