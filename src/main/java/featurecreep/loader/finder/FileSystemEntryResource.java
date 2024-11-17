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

public class FileSystemEntryResource implements Resource {
	private final String relativePath;
	private String entryName;
	public String nonMultiReleaseName;
	public FileSystem fs;

	/**
	 * Constructs a new PKZipEntryResource.
	 *
	 * @param name                     the name of the resource. It will be turned
	 *                                 into a multirelease file by default
	 * @param relativePath             the relative path inside the JAR
	 * @param zip                      the zip
	 * @param multi_release_entry_name Should convert the entry to the multi release
	 *                                 name;
	 */
	public FileSystemEntryResource(final String name, final String relativePath, FileSystem fs,
			boolean multi_release_entry_name) {
		this.relativePath = relativePath;
		this.entryName = relativePath == null ? name : name.substring(relativePath.length() + 1);
		this.nonMultiReleaseName = entryName;
		try {
			this.entryName = fs.getMultiReleaseName(entryName);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		}
		this.fs = fs;
	}

	/**
	 * Constructs a new PKZipEntryResource.
	 *
	 * @param name         the name of the resource. It will be turned into a
	 *                     multirelease file by default
	 * @param relativePath the relative path inside the JAR
	 * @param zip          the zip
	 */
	public FileSystemEntryResource(final String name, final String relativePath, FileSystem fs) {
		this(name, relativePath, fs, true);
	}

	@Override
	public String getName() {
		return entryName;
	}

	@Override
	public URL getURL() {
		return fs.getURLForFile(entryName);
	}

	public URL getZipURL() {
		return fs.getURL();
	}

	@Override
	public InputStream openStream() throws IOException {
		try {
			return fs.getStream(getFullEntryName());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;// I dont think this will ever happen
	}

	@Override
	public long getSize() {
		try {
			return fs.getFileSize(getFullEntryName());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;// I dont think this will ever happen
	}

	private String getFullEntryName() {
		return relativePath == null ? entryName : relativePath + "/" + entryName;
	}

}
