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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.jboss.modules.Resource;

import featurecreep.loader.filesystem.FileSystem;

/** A resource view over a FeatureCreep {@link FileSystem}. */
public class FileSystemEntryResource implements Resource {
    private final String relativePath;
    private final String entryName;
    private final String fullEntryName;
    public final String nonMultiReleaseName;
    public final FileSystem fs;

    /**
     * @param name full path of the base resource in the underlying file system
     * @param relativePath optional resource-root prefix; hidden from getName()
     * @param multiRelease resolve META-INF/versions/&lt;runtime&gt; overrides
     */
    public FileSystemEntryResource(final String name, final String relativePath, FileSystem fs,
            boolean multiRelease) {
        this.relativePath = relativePath;
        this.fs = fs;

        String visible = name;
        if (relativePath != null && !relativePath.isEmpty() && name.startsWith(relativePath + "/")) {
            visible = name.substring(relativePath.length() + 1);
        }
        this.entryName = visible;
        this.nonMultiReleaseName = visible;

        String resolved = name;
        if (multiRelease) {
            try {
                // Resolve using the *full* path. For a resource root "lib", the
                // versioned JAR entry is META-INF/versions/N/lib/Foo.class, not
                // lib/META-INF/versions/N/Foo.class.
                resolved = fs.getMultiReleaseName(name);
            } catch (FileNotFoundException ignored) {
                // Keep the base path; exists() lets the caller reject it cleanly.
            }
        }
        this.fullEntryName = resolved;
    }

    public FileSystemEntryResource(final String name, final String relativePath, FileSystem fs) {
        this(name, relativePath, fs, true);
    }

    @Override
    public String getName() {
        // Resource names should remain the logical/base name even when bytes come
        // from a multi-release override.
        return entryName;
    }

    @Override
    public URL getURL() {
        return fs.getURLForFile(fullEntryName);
    }

    public URL getZipURL() {
        return fs.getURL();
    }

    @Override
    public InputStream openStream() throws IOException {
        return fs.getStream(fullEntryName);
    }

    @Override
    public long getSize() {
        try {
            return fs.getFileSize(fullEntryName);
        } catch (IOException e) {
            return 0;
        }
    }

    boolean exists() {
        return fs.has(fullEntryName);
    }

    String getFullEntryName() {
        return fullEntryName;
    }
}
