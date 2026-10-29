package featurecreep.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.lang.Module;

import org.jboss.modules.ModuleLoadException;

public class FCInstrumentation implements Instrumentation {

	public FCLoaderBasic loader;

	public FCInstrumentation(FCLoaderBasic loader) {
		this.loader = loader;
	}

	/**
	 * The JVM agent instrumentation supplied by the host, or null when none is attached.
	 */
	private Instrumentation delegate() {
		Instrumentation supplied = loader.getInstrumentationForAgent();
		return supplied instanceof FCInstrumentation ? null : supplied;
	}

	private Instrumentation requireDelegate(String operation) {
		Instrumentation supplied = delegate();
		if (supplied == null) {
			throw new UnsupportedOperationException(operation + " requires a JVM agent Instrumentation; none is attached");
		}
		return supplied;
	}

	private void requireHotswapOptIn(Class<?> target, String property, String operation) {
		ClassLoader targetLoader = target.getClassLoader();
		for (var module : loader.getModules()) {
			if (module.getClassLoader() == targetLoader) {
				if (Boolean.parseBoolean(module.getProperty(property))) {
					return;
				}
				break;
			}
		}
		throw new UnsupportedOperationException(operation + " refused for " + target.getName()
				+ ": owning module does not set " + property + "=true");
	}

	@Override
	public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
		loader.addTransformer(transformer);
	}

	@Override
	public void addTransformer(ClassFileTransformer transformer) {
		loader.addTransformer(transformer);
	}

	@Override
	public boolean removeTransformer(ClassFileTransformer transformer) {
		return loader.getTransformers().remove(new RemovableClassFileTransformer(transformer));
	}

	@Override
	public boolean isRetransformClassesSupported() {
		Instrumentation supplied = delegate();
		return supplied != null && supplied.isRetransformClassesSupported();
	}

	@Override
	public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
		Instrumentation supplied = requireDelegate("retransformClasses");
		for (Class<?> target : classes) {
			requireHotswapOptIn(target, FCModuleProperties.CAN_RETRANSFORM_CLASSES, "retransformClasses");
		}
		supplied.retransformClasses(classes);
	}

	@Override
	public boolean isRedefineClassesSupported() {
		Instrumentation supplied = delegate();
		return supplied != null && supplied.isRedefineClassesSupported();
	}

	@Override
	public void redefineClasses(ClassDefinition... definitions)
			throws ClassNotFoundException, UnmodifiableClassException {
		Instrumentation supplied = requireDelegate("redefineClasses");
		for (ClassDefinition definition : definitions) {
			requireHotswapOptIn(definition.getDefinitionClass(), FCModuleProperties.CAN_REDEFINE_CLASSES,
					"redefineClasses");
		}
		supplied.redefineClasses(definitions);
	}

	@Override
	public boolean isModifiableClass(Class<?> theClass) {
		Instrumentation supplied = delegate();
		return supplied != null && supplied.isModifiableClass(theClass);
	}

	@Override
	public Class<?>[] getAllLoadedClasses() {
		return requireDelegate("getAllLoadedClasses").getAllLoadedClasses();
	}

	@Override
	public Class<?>[] getInitiatedClasses(ClassLoader loader) {
		return requireDelegate("getInitiatedClasses").getInitiatedClasses(loader);
	}

	@Override
	public long getObjectSize(Object objectToSize) {
		Instrumentation supplied = delegate();
		if (supplied != null) {
			return supplied.getObjectSize(objectToSize);
		}
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos)) {
			oos.writeObject(objectToSize);
			return bos.toByteArray().length;
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		}
	}

	@Override
	public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
		try {
			FCLoaderBasic.getBootModuleLoader().loadModule(jarfile.getName());
		} catch (ModuleLoadException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void appendToSystemClassLoaderSearch(JarFile jarfile) {
		requireDelegate("appendToSystemClassLoaderSearch").appendToSystemClassLoaderSearch(jarfile);
	}

	@Override
	public boolean isNativeMethodPrefixSupported() {
		Instrumentation supplied = delegate();
		return supplied != null && supplied.isNativeMethodPrefixSupported();
	}

	@Override
	public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
		requireDelegate("setNativeMethodPrefix").setNativeMethodPrefix(transformer, prefix);
	}

	@Override
	public void redefineModule(Module module, Set<Module> extraReads, Map<String, Set<Module>> extraExports,
			Map<String, Set<Module>> extraOpens, Set<Class<?>> extraUses, Map<Class<?>, List<Class<?>>> extraProvides) {
		requireDelegate("redefineModule").redefineModule(module, extraReads, extraExports, extraOpens, extraUses,
				extraProvides);
	}

	@Override
	public boolean isModifiableModule(Module module) {
		Instrumentation supplied = delegate();
		return supplied != null && supplied.isModifiableModule(module);
	}

}
