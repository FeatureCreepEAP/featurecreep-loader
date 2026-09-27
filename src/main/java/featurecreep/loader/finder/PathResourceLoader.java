package featurecreep.loader.finder;

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.NativeLibraryResourceLoader;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;

/**
 * Java NIO2 Path-based ResourceLoader
 *
 * @author <a href="mailto:bsideup@gmail.com">Sergei Egorov</a>
 */
public class PathResourceLoader extends AbstractResourceLoader implements IterableResourceLoader {

	public final String rootName;
	public final Path root;
	public final Manifest manifest;
	public final CodeSource codeSource;

	public PathResourceLoader(final String rootName, final Path root) {
		if (rootName == null) {
			throw new IllegalArgumentException("rootName is null");
		}
		if (root == null) {
			throw new IllegalArgumentException("root is null");
		}
		this.rootName = rootName;
		this.root = root;
		final Path manifestFile = root.resolve("META-INF").resolve("MANIFEST.MF");
		manifest = readManifestFile(manifestFile);

		try {
			codeSource = new CodeSource(root.toUri().toURL(), (CodeSigner[]) null);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid root file specified", e);
		}
	}

	private Manifest readManifestFile(final Path manifestFile) {
		if (!Files.isRegularFile(manifestFile)) {
			return null;
		}
		try (InputStream is = Files.newInputStream(manifestFile)) {
			return new Manifest(is);
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public String getRootName() {
		return rootName;
	}


	@Override
	public String getLibrary(String name) {
		if (root.getFileSystem() == FileSystems.getDefault()) {
			// Delegate native-library platform detection to JBoss Modules through its
			// public API. This preserves JBoss's OS-version/CPU fallback paths without
			// reflecting into its package-private Identification class.
			return new NativeLibraryResourceLoader(root.toFile()).getLibrary(name);
		}
		return null;
	}

	@Override
	public ClassSpec getClassSpec(final String fileName) throws IOException {
		final Path file;
		try {
			file = root.resolve(fileName);
		} catch (InvalidPathException ignored) {
			return null;
		}

		if (!Files.isRegularFile(file)) {
			return null;
		}
		final ClassSpec spec = new ClassSpec();
		spec.setCodeSource(codeSource);
		spec.setBytes(Files.readAllBytes(file));
		return spec;
	}

	@Override
	public PackageSpec getPackageSpec(final String name) throws IOException {
		URL rootUrl = root.toUri().toURL();
		return getPackageSpec(name, manifest, rootUrl);
	}

	@Override
	public Resource getResource(final String name) {
		final String cleanName = PathUtils.canonicalize(PathUtils.relativize(name));
		final Path file;
		try {
			file = root.resolve(cleanName);
		} catch (InvalidPathException ignored) {
			return null;
		}

		if (!Files.exists(file)) {
			return null;
		}

		return new PathResource(file, cleanName);
	}

	@Override
	public Iterator<Resource> iterateResources(final String startPath, final boolean recursive) {
		try {
			Path path;
			try {
				path = root.resolve(PathUtils.canonicalize(PathUtils.relativize(startPath)));
			} catch (InvalidPathException ignored) {
				return Collections.emptyIterator();
			}
			return Files.walk(path, recursive ? Integer.MAX_VALUE : 1, FileVisitOption.FOLLOW_LINKS)
					.filter(it -> !Files.isDirectory(it))
					.<Resource>map(resourcePath -> new PathResource(resourcePath,
							PathUtils.toGenericSeparators(root.relativize(resourcePath).toString())))
					.iterator();
		} catch (IOException e) {
			return Collections.emptyIterator();
		}
	}

	@Override
	public Collection<String> getPaths() {
		try (java.util.stream.Stream<Path> paths = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
			return paths.filter(Files::isDirectory).map(dir -> {
				final String result = root.relativize(dir).toString();
				final String canonical = PathUtils.toGenericSeparators(result);
				return canonical.endsWith("/") ? canonical.substring(0, canonical.length() - 1) : canonical;
			}).collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	@Override
	public URI getLocation() {
		return root.toUri();
	}

	public ResourceLoader createSubloader(final String relativePath, final String rootName) {
		return new PathResourceLoader(rootName,
				root.resolve(PathUtils.relativize(PathUtils.canonicalize(relativePath))));
	}

}
