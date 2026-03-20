package featurecreep.loader.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;

public class JBMUtilsAccessors {

	/**
	 * Returns the JDKSpecific or Utils class on newer JBossModules Versions
	 * 
	 * @return
	 */
	public static Class<?> getJDKSpecificClass() {
		Class<?> clazz = null;
		try {
			clazz = Class.forName("org.jboss.modules.JDKSpecific");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			try {
				clazz = Class.forName("org.jboss.modules.Utils");
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		return clazz;
	}

	@SuppressWarnings("unchecked")
	public static Set<String> getJDKPaths() {
		try {
			Method def = getJDKSpecificClass().getDeclaredMethod("getJDKPaths");
			def.setAccessible(true);
			return (Set<String>) def.invoke(null);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new HashSet<String>();
	}

	// I made it public
	public static ClassLoader setContextClassLoader(final ClassLoader loader) {

		try {
			Class<?> clazz = Class.forName("org.jboss.modules.SecurityActions");
			Method def = clazz.getDeclaredMethod("setContextClassLoader", ClassLoader.class);
			def.setAccessible(true);
			return (ClassLoader) def.invoke(null, loader);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
				| InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return loader;
	}

	public static String getMainClass(Module mod) {
		try {
			Method def = Module.class.getDeclaredMethod("getMainClass");
			def.setAccessible(true);
			return (String) def.invoke(mod);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static void setModuleDependencies(Module mod, final List<DependencySpec> dependencySpecs) {
		try {
			Method def = Module.class.getDeclaredMethod("setDependencies", List.class);
			def.setAccessible(true);
			def.invoke(mod, dependencySpecs);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// mod.setDependencies(dependencySpecs);
	}

	public static JarFile getJarFile(final File name, final boolean verify) throws IOException {
		if (JavaUtils.isJavaVersionNewerThan8()) {
			return new JarFile(name, verify, JarFile.OPEN_READ, JarFile.runtimeVersion());
		} else {
			return new JarFile(name, verify, JarFile.OPEN_READ);
		}
	}

	public static LocalLoader getSystemLocalLoader() {
		try {
			Method def = getJDKSpecificClass().getDeclaredMethod("getSystemLocalLoader");
			def.setAccessible(true);
			return (LocalLoader) def.invoke(null);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

//	//It is final but just in case lets keep this
//	public static String getModuleDir() {
//		try {
//			Field var = Class.forName("org.jboss.modules.Utils").getField("MODULES_DIR");
//			var.setAccessible(true);
//			return (String)var.get(null);
//		} catch (NoSuchFieldException | SecurityException | ClassNotFoundException | IllegalArgumentException
//				| IllegalAccessException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			return "modules";
//		}
//	}
//	
//	//It is final but just in case lets keep this
//	public static String getModuleFile() {
//		try {
//			Field var = Class.forName("org.jboss.modules.Utils").getField("MODULE_FILE");
//			var.setAccessible(true);
//			return (String)var.get(null);
//		} catch (NoSuchFieldException | SecurityException | ClassNotFoundException | IllegalArgumentException
//				| IllegalAccessException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			return "module.xml";
//		}
//	}

}
