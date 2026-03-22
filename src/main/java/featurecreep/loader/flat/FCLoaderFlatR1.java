package featurecreep.loader.flat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import featurecreep.loader.GameProvider;
import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.filesystem.PhilKatzZip;

public class FCLoaderFlatR1 implements FCLoaderFlat {

    private final GameProvider provider;
    private final EventViewer eventViewer = new EventViewer();
    private final ArrayList<ClassFileTransformer> transformers = new ArrayList<>();
    private final Map<String, FlatModMetadata> modMetadataMap = new HashMap<>();

    private FlatClassLoader flatClassLoader;
    private boolean modsLoaded = false;
    private boolean hotswapNeeded = false;
    private boolean modsRun = false; // Prevent running twice

    public FCLoaderFlatR1(GameProvider provider) {
        this.provider = provider;
        this.flatClassLoader = new FlatClassLoader(new URL[0], Thread.currentThread().getContextClassLoader());
    }

    @Override
    public GameProvider getGameProvider() {
        return provider;
    }

    @Override
    public ClassLoader getFlatClassLoader() {
        return flatClassLoader;
    }

    @Override
    public boolean isHotswapNeeded() {
        return hotswapNeeded;
    }

    @Override
    public void loadMods() {
        if (this.modsLoaded) {
            System.out.println("[FCLoaderFlat] Mods already loaded, skipping.");
            return;
        }

        System.out.println("[FCLoaderFlat] Loading mods in Flat ClassLoader mode (No Agents supported)...");

        File[] modFiles = getModLocations();
        if (modFiles == null) return;

        List<URL> urlsToAdd = new ArrayList<>();

        for (File file : modFiles) {
            if (file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".fpm"))) {
                try {
                    FlatModMetadata meta = parseMetadata(file);
                    if (meta != null) {
                        // Check for duplicate ModID
                        if (modMetadataMap.containsKey(meta.getModId())) {
                            System.err.println("[FCLoaderFlat] Duplicate ModID detected: " + meta.getModId() + " in file " + file.getName() + ". Skipping.");
                            continue;
                        }
                        
                        modMetadataMap.put(meta.getModId(), meta);
                        checkHotswap(file);
                        urlsToAdd.add(file.toURI().toURL());
                        processJIJ(file, urlsToAdd);
                    }

                } catch (IOException e) {
                    if (getDebugMode()) e.printStackTrace();
                }
            }
        }

        for (URL url : urlsToAdd) {
            flatClassLoader.addURL(url);
        }

        loadTransformersFromMetadata();
        
        this.modsLoaded = true;
        System.out.println("[FCLoaderFlat] Finished loading mods.");
    }
    
    @Override
    public Collection<String> getEntries(String prefix) {
        Set<String> entries = new HashSet<>();
        // Delegate to the ClassLoader which has access to the local URL list
        flatClassLoader.collectEntries(prefix, entries);
        return entries;
    }

    private void loadTransformersFromMetadata() {
        for (FlatModMetadata meta : modMetadataMap.values()) {
            if (meta.getTransformerClass() != null && !meta.getTransformerClass().isEmpty()) {
                try {
                    System.out.println("[FCLoaderFlat] Loading transformer: " + meta.getTransformerClass());
                    Class<?> clazz = Class.forName(meta.getTransformerClass(), true, flatClassLoader);
                    if (ClassFileTransformer.class.isAssignableFrom(clazz)) {
                        Object instance = clazz.getDeclaredConstructor().newInstance();
                        this.addTransformer((ClassFileTransformer) instance);
                    } else {
                        System.err.println("Transformer class " + meta.getTransformerClass() + " does not implement ClassFileTransformer");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load transformer " + meta.getTransformerClass());
                    e.printStackTrace();
                }
            }
        }
    }

    private FlatModMetadata parseMetadata(File file) throws IOException {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("fcflat.properties");
            if (entry != null) {
                try (InputStream is = jar.getInputStream(entry)) {
                    Properties props = new Properties();
                    props.load(is);
                    return new FlatModMetadata(props, file);
                }
            }
        }
        return null;
    }

    private void checkHotswap(File file) throws IOException {
        if (hotswapNeeded) return;

        String name = file.getName();
        if ((name.endsWith(".jar") || name.endsWith(".fpm")) && (name.startsWith("featurecreep") || name.startsWith("crashdetector"))) {
            return;
        }

        try (JarFile jar = new JarFile(file)) {
            if (jar.getManifest() != null) {
                String redefine = jar.getManifest().getMainAttributes().getValue("Can-Redefine-Classes");
                String retransform = jar.getManifest().getMainAttributes().getValue("Can-Retransform-Classes");
                if (Boolean.parseBoolean(redefine) || Boolean.parseBoolean(retransform)) {
                    this.hotswapNeeded = true;
                    System.out.println("[FCLoaderFlat] Warning: " + file.getName() + " requests Hotswap, but FlatLoader does not support runtime agents.");
                }
            }
        }
    }

    private void processJIJ(File file, List<URL> urlsToAdd) {
        try {
            PhilKatzZip zip = new PhilKatzZip(file.getAbsolutePath());
            try (JarFile jar = new JarFile(file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".jar")) {
                        File temp = extractNestedJar(jar, entry);
                        if (temp != null) {
                            urlsToAdd.add(temp.toURI().toURL());
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (getDebugMode()) System.out.println("Skipping JIJ processing for " + file.getName() + ": " + e.getMessage());
        }
    }

    private File extractNestedJar(JarFile parent, JarEntry entry) throws IOException {
        File tempFile = File.createTempFile("jij_", ".jar");
        tempFile.deleteOnExit();
        try (InputStream is = parent.getInputStream(entry)) {
            java.nio.file.Files.copy(is, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    @Override
    public void runMods() {
        if (modsRun) {
            System.out.println("[FCLoaderFlat] Mods already run, skipping.");
            return;
        }
        modsRun = true;

        System.out.println("[FCLoaderFlat] Running mods...");
        for (Map.Entry<String, FlatModMetadata> entry : modMetadataMap.entrySet()) {
            FlatModMetadata meta = entry.getValue();
            if (meta.getMainClass() != null) {
                try {
                    System.out.println("[FCLoaderFlat] Running main for mod: " + meta.getModId());
                    Class<?> mainClass = Class.forName(meta.getMainClass(), true, flatClassLoader);
                    MethodHandle mh = MethodHandles.lookup().findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class));
                    mh.invokeExact(new String[]{""});
                } catch (Throwable e) {
                    if (getDebugMode()) e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void addTransformer(ClassFileTransformer transformer) {
        this.transformers.add(transformer);
    }

    @Override
    public ArrayList<ClassFileTransformer> getTransformers() {
        return transformers;
    }

    @Override
    public EventViewer getEventViewer() {
        return eventViewer;
    }

    @Override
    public Map<String, FlatModMetadata> getModMetadataMap() {
        return modMetadataMap;
    }

    @Override
    public boolean getModsLoaded() {
        return modsLoaded;
    }

    /**
     * Custom ClassLoader that maintains an internal list of URLs to avoid
     * illegal reflective access on Java 9+.
     */
    private class FlatClassLoader extends URLClassLoader {

        private final List<URL> localUrls = new ArrayList<>();

        public FlatClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        public void addURL(URL url) {
            localUrls.add(url);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/').concat(".class");
            
            byte[] classBytes = findClassInLocalUrls(path);
            
            if (classBytes == null) {
                return super.findClass(name);
            }

            byte[] transformedBytes = applyTransformers(name, classBytes);

            int i = name.lastIndexOf('.');
            if (i != -1) {
                String pkgname = name.substring(0, i);
                if (getPackage(pkgname) == null) {
                    definePackage(pkgname, null, null, null, null, null, null, null);
                }
            }

            return defineClass(name, transformedBytes, 0, transformedBytes.length);
        }
        
        private byte[] findClassInLocalUrls(String path) {
            for (URL url : localUrls) {
                try {
                    if (url.getProtocol().equals("jar") || url.getFile().endsWith(".jar") || url.getFile().endsWith(".fpm")) {
                        File f = new File(url.toURI());
                        if (f.exists()) {
                            try (JarFile jar = new JarFile(f)) {
                                JarEntry entry = jar.getJarEntry(path);
                                if (entry != null) {
                                    try (InputStream is = jar.getInputStream(entry)) {
                                        return is.readAllBytes();
                                    }
                                }
                            }
                        }
                    } else {
                        URL resourceUrl = new URL(url, path);
                        try (InputStream is = resourceUrl.openStream()) {
                            return is.readAllBytes();
                        }
                    }
                } catch (Exception ignored) {
                    // Try next URL
                }
            }
            return null;
        }
        
        /**
         * Scans the local URLs for entries starting with the given prefix.
         */
        public void collectEntries(String prefix, Set<String> entries) {
            for (URL url : localUrls) {
                try {
                    if (url.getProtocol().equals("jar") || url.getFile().endsWith(".jar") || url.getFile().endsWith(".fpm")) {
                        File f = new File(url.toURI());
                        if (f.exists()) {
                            try (JarFile jar = new JarFile(f)) {
                                Enumeration<JarEntry> jarEntries = jar.entries();
                                while (jarEntries.hasMoreElements()) {
                                    JarEntry entry = jarEntries.nextElement();
                                    if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                                        entries.add(entry.getName());
                                    }
                                }
                            }
                        }
                    } else {
                        // Directory handling (basic implementation)
                        // Could be expanded to walk file tree if needed
                    }
                } catch (Exception ignored) {
                    // Ignore errors for single URLs
                }
            }
        }

        private byte[] applyTransformers(String className, byte[] bytes) {
            byte[] currentBytes = bytes;
            for (ClassFileTransformer transformer : transformers) {
                try {
                    byte[] result = transformer.transform(this, className.replace('.', '/'), null, null, currentBytes);
                    if (result != null) {
                        currentBytes = result;
                    }
                } catch (IllegalClassFormatException e) {
                    System.err.println("Transformer error for class " + className);
                    e.printStackTrace();
                }
            }
            return currentBytes;
        }
        
        @Override
        public URL findResource(String name) {
            for (URL url : localUrls) {
                try {
                     if (url.getProtocol().equals("jar") || url.getFile().endsWith(".jar") || url.getFile().endsWith(".fpm")) {
                         File f = new File(url.toURI());
                         try (JarFile jar = new JarFile(f)) {
                             if (jar.getJarEntry(name) != null) {
                                 return new URL("jar:" + url.toString() + "!/" + name);
                             }
                         }
                     } else {
                         URL res = new URL(url, name);
                         try (InputStream is = res.openStream()) {
                             return res;
                         }
                     }
                } catch (Exception ignored) {}
            }
            return super.findResource(name);
        }

        @Override
        public Enumeration<URL> findResources(String name) throws IOException {
            List<URL> resources = new ArrayList<>();
            
            for (URL url : localUrls) {
                try {
                    if (url.getProtocol().equals("jar") || url.getFile().endsWith(".jar") || url.getFile().endsWith(".fpm")) {
                        File f = new File(url.toURI());
                        try (JarFile jar = new JarFile(f)) {
                            if (jar.getJarEntry(name) != null) {
                                resources.add(new URL("jar:" + url.toString() + "!/" + name));
                            }
                        }
                    } else {
                        URL res = new URL(url, name);
                        try (InputStream is = res.openStream()) {
                            resources.add(res);
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            Enumeration<URL> superResources = super.findResources(name);
            while (superResources.hasMoreElements()) {
                resources.add(superResources.nextElement());
            }
            
            return java.util.Collections.enumeration(resources);
        }
    }
}