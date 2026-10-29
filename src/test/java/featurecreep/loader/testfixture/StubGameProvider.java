package featurecreep.loader.testfixture;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.modules.ModuleFinder;

import featurecreep.loader.ExecutionSide;
import featurecreep.loader.GameProvider;
import featurecreep.loader.config.FCConfigApi;
import featurecreep.loader.config.FCConfigRegistry;

public final class StubGameProvider implements GameProvider {
    private final Path[] mods;
    private final Set<String> packages = new LinkedHashSet<String>();
    private final FCConfigRegistry configs = new FCConfigRegistry();

    public StubGameProvider(Path mods) {
        this.mods = new Path[] { mods };
        packages.add("featurecreep/loader/testfixture");
    }

    @Override public boolean getDebugMode() { return true; }
    @Override public boolean setDebugMode(boolean value) { return value; }
    @Override public Path[] getModulePKZipLocations() { return mods.clone(); }
    @Override public Path[] getClassPathPKZipLocations() { return new Path[0]; }
    @Override public Instrumentation getInstrumentation() { return null; }
    @Override public Instrumentation setInstrumentation(Instrumentation instrumentation) { return instrumentation; }
    @Override public Set<String> getNeededPackages() { return packages; }
    @Override public void addNeededPackage(String pkg) { if (pkg != null) packages.add(pkg); }
    @Override public List<String> getAvoidedModSuffixes() { return Collections.emptyList(); }
    @Override public ExecutionSide getExecutionSide() { return ExecutionSide.CLIENT; }
    @Override public List<ModuleFinder> getDefaultModuleFinders() { return Collections.emptyList(); }
    @Override public boolean isSuperLoaderModZip(File file) { return false; }
    @Override public boolean isSuperLoaderModFolder(File folder) { return false; }
    @Override public ClassLoader getGameClassLoader() { return getClass().getClassLoader(); }
    @Override public String getGameId() { return "minecraft"; }
    @Override public String getGameVersion() { return "1.21.1"; }
    @Override public String getLoaderVersion() { return "12"; }
    @Override public FCConfigApi getConfigApi() { return configs; }
}
