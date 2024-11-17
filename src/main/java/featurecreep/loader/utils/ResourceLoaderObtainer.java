package featurecreep.loader.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ResourceLoader;

/**
 * Makes it easier to get the resource loaders
 */
public class ResourceLoaderObtainer {

	public static ResourceLoader[] getResourceLoaders(Module mod){
		try {
			ModuleClassLoader loader = mod.getClassLoader();
			Method rlgetter = loader.getClass().getDeclaredMethod("getResourceLoaders");
			rlgetter.setAccessible(true);
			ResourceLoader[] loaders= (ResourceLoader[]) rlgetter.invoke(loader);
		return loaders;
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
return null;
	}
	
	
	
	
	
}
