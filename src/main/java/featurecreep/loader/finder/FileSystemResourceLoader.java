/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package featurecreep.loader.finder;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.jar.Manifest;

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;

import featurecreep.loader.filesystem.FileSystem;
import featurecreep.loader.filesystem.PhilKatzZip;

/**
 *
 * For FileSystems except for folders, for folders use PathResourceLoader
 *
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Thomas.Diesler@jboss.com
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class FileSystemResourceLoader extends AbstractResourceLoader implements IterableResourceLoader {
	public final URL rootUrl;
	public final String relativePath;
	public volatile List<String> directory;
	public FileSystem fs;

	// protected by {@code this}
	public final Map<CodeSigners, CodeSource> codeSources = new HashMap<>();

	/**
	 * Creates a new instance
	 * 
	 * @param fs a filesystem to get files from. Should not be a directory
	 */
	public FileSystemResourceLoader(final FileSystem fs) {
		this(fs, null);
	}

	@Deprecated
	public FileSystemResourceLoader(FileSystem fs, final String relativePath) {
		if (fs == null) {
			throw new IllegalArgumentException("jarFile is null");
		}
//        if (rootName == null) {
//            throw new IllegalArgumentException("rootName is null");
//        }
		String realPath = relativePath == null ? null : PathUtils.canonicalize(relativePath);
		if (realPath != null && realPath.endsWith("/"))
			realPath = realPath.substring(0, realPath.length() - 1);
		this.relativePath = realPath;
		try {
			if (fs instanceof PhilKatzZip) {
				rootUrl = getJarURI(fs.getURI(), realPath).toURL();
			} else {
				rootUrl = fs.getURL();
			}
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid root file specified", e);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid root file specified", e);
		}
		this.fs = fs;
	}

	public static URI getJarURI(final URI original, final String nestedPath) throws URISyntaxException {
		final StringBuilder b = new StringBuilder();
		b.append("file:");
		assert original.getScheme().equals("file");
		final String path = original.getPath();
		assert path != null;
		final String host = original.getHost();
		if (host != null) {
			final String userInfo = original.getRawUserInfo();
			b.append("//");
			if (userInfo != null) {
				b.append(userInfo).append('@');
			}
			b.append(host);
		}
		b.append(path).append("!/");
		if (nestedPath != null) {
			b.append(nestedPath);
		}
		return new URI("jar", b.toString(), null);
	}

	public synchronized ClassSpec getClassSpec(final String fileName) throws IOException {
		final ClassSpec spec = new ClassSpec();
		try {
			if (fs.get(fileName) == null) {
				// no such entry
				return null;
			}
			final long size = fs.getFileSize(fileName);
			try (final InputStream is = getJarEntryStream(fileName)) {
				if (size == -1) {
					// size unknown
					final ByteArrayOutputStream baos = new ByteArrayOutputStream();
					final byte[] buf = new byte[16384];
					int res;
					while ((res = is.read(buf)) > 0) {
						baos.write(buf, 0, res);
					}
					// done
					CodeSource codeSource = createCodeSource(fileName);
					baos.close();
					is.close();
					spec.setBytes(baos.toByteArray());
					spec.setCodeSource(codeSource);
					return spec;
				} else if (size <= (long) Integer.MAX_VALUE) {
					final int castSize = (int) size;
					byte[] bytes = new byte[castSize];
					int a = 0, res;
					while ((res = is.read(bytes, a, castSize - a)) > 0) {
						a += res;
					}
					// consume remainder so that cert check doesn't fail in case of wonky JARs
					while (is.read() != -1) {
						//
					}
					// done
					CodeSource codeSource = createCodeSource(fileName);
					is.close();
					spec.setBytes(bytes);
					spec.setCodeSource(codeSource);
					return spec;
				} else {
					throw new IOException("Resource is too large to be a valid class file");
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;// I dont think this will happen
		}

	}

	// this MUST only be called after the input stream is fully read (see
	// MODULES-201)
	private CodeSource createCodeSource(String name) {
		final CodeSigner[] entryCodeSigners = fs.getCodeSigners(name);
		final CodeSigners codeSigners = entryCodeSigners == null || entryCodeSigners.length == 0 ? EMPTY_CODE_SIGNERS
				: new CodeSigners(entryCodeSigners);
		CodeSource codeSource = codeSources.get(codeSigners);
		if (codeSource == null) {
			codeSources.put(codeSigners, codeSource = new CodeSource(rootUrl, entryCodeSigners));
		}
		return codeSource;
	}

	public InputStream getJarEntryStream(final String fileName) throws IOException {
		return relativePath == null ? getResource(fileName).openStream()
				: getResource(relativePath + "/" + fileName).openStream();
	}

	public PackageSpec getPackageSpec(final String name) {
		Manifest manifest;

		InputStream jarEntry;
		try {
			jarEntry = getJarEntryStream("META-INF/MANIFEST.MF");
			manifest = new Manifest(jarEntry);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			manifest = null;
		}

		return getPackageSpec(name, manifest, rootUrl);
	}

	public String getLibrary(final String name) {
		// JARs cannot have libraries in them
		return null;
	}

	public Resource getResource(String name) {
		name = PathUtils.canonicalize(PathUtils.relativize(name));
		if (!fs.has(name)) {
			return null;
		}
		return new FileSystemEntryResource(name, relativePath, fs);
	}

	public Iterator<Resource> iterateResources(String startPath, final boolean recursive) {
		if (relativePath != null)
			startPath = startPath.equals("") ? relativePath : relativePath + "/" + startPath;
		final String startName = PathUtils.canonicalize(PathUtils.relativize(startPath));
		List<String> directory = this.directory;
		if (directory == null) {
			synchronized (fs) {
				directory = this.directory;
				if (directory == null) {
					directory = new ArrayList<>();
					for (String entry : fs.getFilenames(startName)) {
						directory.add(entry);
					}
					this.directory = directory;
				}
			}
		}
		final Iterator<String> iterator = directory.iterator();
		return new Iterator<Resource>() {
			private Resource next;

			public boolean hasNext() {
				while (next == null) {
					if (!iterator.hasNext()) {
						return false;
					}
					final String name = iterator.next();
					if ((recursive ? PathUtils.isChild(startName, name) : PathUtils.isDirectChild(startName, name))) {
						try {
							next = new FileSystemEntryResource(name, relativePath, fs);
						} catch (Exception ignored) {
						}
					}
				}
				return true;
			}

			public Resource next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				try {
					return next;
				} finally {
					next = null;
				}
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public Collection<String> getPaths() {
		final Collection<String> index = new HashSet<String>();
		index.add("");
		extractJarPaths(fs, relativePath, index);
		return index;
	}

	@Override
	public void close() {
		try {
			super.close();
		} finally {
//            try {  TODO
//                jarFile.close();
//            } catch (IOException e) {
//                // ignored
//            }
		}
	}

	public URI getLocation() {
		try {
			if (fs instanceof PhilKatzZip) {
				return getJarURI(fs.getURI(), "");
			} else {
				return fs.getURI();
			}
		} catch (URISyntaxException e) {
			return null;
		}
	}

	public ResourceLoader createSubloader(final String relativePath) {
		final String ourRelativePath = this.relativePath;
		final String fixedPath = PathUtils.relativize(PathUtils.canonicalize(relativePath));
		return new FileSystemResourceLoader(fs,
				ourRelativePath == null ? fixedPath : ourRelativePath + "/" + fixedPath);
	}

//    public static void extractJarPaths(final JarInputStream jarFile, String relativePath, final Collection<String> index) {
//        index.add("");
//        
//       ZipEntry jarEntry = null;
//	try {
//		jarEntry = jarFile.getNextEntry();
//	} catch (IOException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//        while (jarEntry!=null) {
//            final String name = jarEntry.getName();
//            System.out.println(name);
//            final int idx = name.lastIndexOf('/');
//            if (idx == -1) continue;
//            final String path = name.substring(0, idx);
//            if (path.length() == 0 || path.endsWith("/")) {
//                // invalid name, just skip...
//                continue;
//            }
//            if (relativePath == null) {
//                index.add(path);
//            } else {
//                if (path.startsWith(relativePath + "/")) {
//                    index.add(path.substring(relativePath.length() + 1));
//                }
//            }
//            try {
//				jarEntry=jarFile.getNextEntry();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				//e.printStackTrace();
//				jarEntry=null;
//			}
//        }
//    }

	public static void extractJarPaths(FileSystem fs, String relativePath, final Collection<String> index) {
		index.add("");

		for (String name : fs.getFilenames("")) {
			final int idx = name.lastIndexOf('/');

			if (idx == -1) {
				// Update jarEntry before continuing to avoid infinite loop
				continue;
			}

			final String path = name.substring(0, idx);

			if (path.length() == 0 || path.endsWith("/")) {
				// Update jarEntry before continuing to avoid infinite loop
				continue;
			}

			if (relativePath == null) {
				index.add(path);
			} else {
				if (path.startsWith(relativePath + "/")) {
					index.add(path.substring(relativePath.length() + 1));
				}
			}
		}

	}

	private static final CodeSigners EMPTY_CODE_SIGNERS = new CodeSigners(new CodeSigner[0]);

	static final class CodeSigners {

		private final CodeSigner[] codeSigners;
		private final int hashCode;

		CodeSigners(final CodeSigner[] codeSigners) {
			this.codeSigners = codeSigners;
			hashCode = Arrays.hashCode(codeSigners);
		}

		public boolean equals(final Object obj) {
			return obj instanceof CodeSigners && equals((CodeSigners) obj);
		}

		private boolean equals(final CodeSigners other) {
			return Arrays.equals(codeSigners, other.codeSigners);
		}

		public int hashCode() {
			return hashCode;
		}
	}
}
