package featurecreep.loader.filesystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class PhilKatzZip extends VirtualFileSystem {
    private final ZipFile zipFile;
    private final Map<String, byte[]> preloadedEntries;
    private final Set<String> entryNames;

    // For seekable ZIP files (top-level JARs)
    public PhilKatzZip(ZipFile zip) throws IOException {
        super(new File(zip.getName()).toURI());
        this.zipFile = zip;
        this.preloadedEntries = null;
        
        // Collect entry names without loading content
        this.entryNames = Collections.unmodifiableSet(
            zip.stream()
                .map(ZipEntry::getName)
                .collect(Collectors.toSet())
        );
    }

    // For non-seekable streams (nested JARs - minimal preload)
    public PhilKatzZip(ZipInputStream zip, URI uri) throws IOException {
        super(uri);
        this.zipFile = null;
        this.preloadedEntries = new HashMap<>();
        this.entryNames = new HashSet<>();

        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            entryNames.add(name);
            
            if (!entry.isDirectory()) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = zip.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    preloadedEntries.put(name, baos.toByteArray());
                }
            }
            zip.closeEntry();
        }
    }

    // Convenience constructors
    public PhilKatzZip(String zipPath) throws IOException {
        this(new ZipFile(zipPath));
    }

    public PhilKatzZip(InputStream zipStream, URI uri) throws IOException {
        this(new ZipInputStream(zipStream), uri);
    }

    public PhilKatzZip(URL zipUrl) throws IOException, URISyntaxException {
        this(zipUrl.openStream(), zipUrl.toURI());
    }

    @Override
    public URL getURLForFile(String file) {
        try {
            String proto = getURL().toString() + "!/" + file.replace('\\', '/');
            if (!proto.startsWith("jar:")) proto = "jar:" + proto;
            return new URI(proto).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Invalid URL for " + file, e);
        }
    }

    @Override
    public Map<String, byte[]> getMap() {
        if (zipFile != null) {
            return new LazyZipMap();
        }
        return Collections.unmodifiableMap(preloadedEntries);
    }

    @Override
    public byte[] get(String file) throws IOException {
        if (zipFile != null) {
            ZipEntry entry = zipFile.getEntry(file);
            if (entry == null || entry.isDirectory()) {
                throw new FileNotFoundException(file);
            }
            try (InputStream is = zipFile.getInputStream(entry);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return baos.toByteArray();
            }
        } else {
            byte[] data = preloadedEntries.get(file);
            if (data == null) {
                throw new FileNotFoundException(file);
            }
            return data;
        }
    }

    @Override
    public boolean has(String file) {
        return entryNames.contains(file);
    }

    @Override
    public Collection<String> getFilenames(String prefix) {
        return entryNames.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    private class LazyZipMap implements Map<String, byte[]> {
        @Override
        public int size() {
            return entryNames.size();
        }

        @Override
        public boolean isEmpty() {
            return entryNames.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return has((String) key);
        }

        @Override
        public byte[] get(Object key) {
            String file = (String) key;
            try {
                return PhilKatzZip.this.get(file);
            } catch (IOException e) {
                throw new RuntimeException("Error reading ZIP entry: " + file, e);
            }
        }

        // Unsupported operations
        @Override
        public byte[] put(String key, byte[] value) {
            throw new UnsupportedOperationException("Read-only ZIP");
        }

        @Override
        public Set<String> keySet() {
            return Collections.unmodifiableSet(entryNames);
        }

        @Override
        public Collection<byte[]> values() {
            throw new UnsupportedOperationException(
                "values() would require loading all entries - use get() instead"
            );
        }

        @Override
        public Set<Entry<String, byte[]>> entrySet() {
            throw new UnsupportedOperationException(
                "entrySet() would require loading all entries - use get() instead"
            );
        }

        // Other methods throw UnsupportedOperationException
        @Override public byte[] remove(Object key) { throw new UnsupportedOperationException(); }
        @Override public void putAll(Map<? extends String, ? extends byte[]> m) { throw new UnsupportedOperationException(); }
        @Override public void clear() { throw new UnsupportedOperationException(); }
        @Override public boolean containsValue(Object value) { throw new UnsupportedOperationException(); }
    }
}