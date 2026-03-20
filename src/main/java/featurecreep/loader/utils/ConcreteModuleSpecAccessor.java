package featurecreep.loader.utils;

import java.lang.reflect.Method;
import java.security.PermissionCollection;
import java.util.Map;

import org.jboss.modules.AssertionSetting;
import org.jboss.modules.ClassTransformer;
import org.jboss.modules.ConcreteModuleSpec;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.Version;

public class ConcreteModuleSpecAccessor {

	public static String getName(ConcreteModuleSpec moduleSpec) {
		return moduleSpec.getName();
	}

	public static String getMainClass(ConcreteModuleSpec moduleSpec) {
		return moduleSpec.getMainClass();
	}

	public static AssertionSetting getAssertionSetting(ConcreteModuleSpec moduleSpec) {
		try {
			Method method = ConcreteModuleSpec.class.getDeclaredMethod("getAssertionSetting");
			method.setAccessible(true);
			return (AssertionSetting) method.invoke(moduleSpec);
		} catch (Exception e) {
			throw new RuntimeException("Unable to access getAssertionSetting", e);
		}
	}

	public static ResourceLoaderSpec[] getResourceLoaders(ConcreteModuleSpec moduleSpec) {
		try {
			Method method = ConcreteModuleSpec.class.getDeclaredMethod("getResourceLoaders");
			method.setAccessible(true);
			return (ResourceLoaderSpec[]) method.invoke(moduleSpec);
		} catch (Exception e) {
			throw new RuntimeException("Unable to access getResourceLoaders", e);
		}
	}

	public static DependencySpec[] getDependencies(ConcreteModuleSpec moduleSpec) {
		return moduleSpec.getDependencies();
	}

	public static LocalLoader getFallbackLoader(ConcreteModuleSpec moduleSpec) {
		try {
			Method method = ConcreteModuleSpec.class.getDeclaredMethod("getFallbackLoader");
			method.setAccessible(true);
			return (LocalLoader) method.invoke(moduleSpec);
		} catch (Exception e) {
			throw new RuntimeException("Unable to access getFallbackLoader", e);
		}
	}

	public static ModuleClassLoaderFactory getModuleClassLoaderFactory(ConcreteModuleSpec moduleSpec) {
		try {
			Method method = ConcreteModuleSpec.class.getDeclaredMethod("getModuleClassLoaderFactory");
			method.setAccessible(true);
			return (ModuleClassLoaderFactory) method.invoke(moduleSpec);
		} catch (Exception e) {
			throw new RuntimeException("Unable to access getModuleClassLoaderFactory", e);
		}
	}

	public static ClassTransformer getClassFileTransformer(ConcreteModuleSpec moduleSpec) {
		try {
			Method method = ConcreteModuleSpec.class.getDeclaredMethod("getClassFileTransformer");
			method.setAccessible(true);
			return (ClassTransformer) method.invoke(moduleSpec);
		} catch (Exception e) {
			throw new RuntimeException("Unable to access getClassFileTransformer", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static Map<String, String> getProperties(ConcreteModuleSpec moduleSpec) {
		try {
			Method method = ConcreteModuleSpec.class.getDeclaredMethod("getProperties");
			method.setAccessible(true);
			return (Map<String, String>) method.invoke(moduleSpec);
		} catch (Exception e) {
			throw new RuntimeException("Unable to access getProperties", e);
		}
	}

	public static PermissionCollection getPermissionCollection(ConcreteModuleSpec moduleSpec) {
		try {
			Method method = ConcreteModuleSpec.class.getDeclaredMethod("getPermissionCollection");
			method.setAccessible(true);
			return (PermissionCollection) method.invoke(moduleSpec);
		} catch (Exception e) {
			throw new RuntimeException("Unable to access getPermissionCollection", e);
		}
	}

	public static Version getVersion(ConcreteModuleSpec moduleSpec) {
		return moduleSpec.getVersion();
	}

	public static ModuleSpec.Builder getBuilder(ConcreteModuleSpec spec) {
		ModuleSpec.Builder builder = ModuleSpec.build(getName(spec));
		for (DependencySpec dep : getDependencies(spec)) {
			builder.addDependency(dep);
		}
		builder.setMainClass(getMainClass(spec));

		builder.setAssertionSetting(getAssertionSetting(spec));

		for (ResourceLoaderSpec rl : getResourceLoaders(spec)) {
			builder.addResourceRoot(rl);
		}

		builder.setFallbackLoader(getFallbackLoader(spec));
		builder.setModuleClassLoaderFactory(getModuleClassLoaderFactory(spec));
		builder.setClassFileTransformer(getClassFileTransformer(spec));
		for (Map.Entry<String, String> entry : getProperties(spec).entrySet()) {
			builder.addProperty(entry.getKey(), entry.getValue());
		}

		builder.setPermissionCollection(getPermissionCollection(spec));
		builder.setVersion(getVersion(spec));

		return builder;

	}

}
