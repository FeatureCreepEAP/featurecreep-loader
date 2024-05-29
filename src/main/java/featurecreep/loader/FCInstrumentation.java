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

import org.jboss.modules.JLIClassTransformer;
import org.jboss.modules.ModuleLoadException;

public class FCInstrumentation implements Instrumentation{

	public FCLoaderBasic loader;
	
	public FCInstrumentation(FCLoaderBasic loader) {
this.loader=loader;		
	}
	
	
	@Override
	public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
		// TODO Auto-generated method stub
		//TODO Retransformation
		loader.addTransformer(transformer);
	}

	@Override
	public void addTransformer(ClassFileTransformer transformer) {
		// TODO Auto-generated method stub
this.addTransformer(transformer);		
	}

	@Override
	public boolean removeTransformer(ClassFileTransformer transformer) {
		// TODO Auto-generated method stub
		
		loader.getTransformers().remove(new JLIClassTransformer(transformer));
		
		return false;
	}

	@Override
	public boolean isRetransformClassesSupported() {
		// TODO Auto-generated method stub
		return false; //Not Yet TM
	}

	@Override
	public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
		// TODO Auto-generated method stub
		//Not Yet TM
	}

	@Override
	public boolean isRedefineClassesSupported() {
		// TODO Auto-generated method stub
		return false;//Not Yet TM
	}

	@Override
	public void redefineClasses(ClassDefinition... definitions)
			throws ClassNotFoundException, UnmodifiableClassException {
		// TODO Auto-generated method stub
		//Not Yet TM
	}

	@Override
	public boolean isModifiableClass(Class<?> theClass) {
		// TODO Auto-generated method stub
		return false;		//Not Yet TM
	}

	@Override
	public Class<?>[] getAllLoadedClasses() {
		// TODO Auto-generated method stub
		return null;		//Not Yet TM
	}

	@Override
	public Class<?>[] getInitiatedClasses(ClassLoader loader) {
		// TODO Auto-generated method stub
		return null;		//Not Yet TM
	}

	@Override
	public long getObjectSize(Object objectToSize) {
		// TODO Auto-generated method stub
		try {
			
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
	
				     ObjectOutputStream oos = new ObjectOutputStream(bos)) {
	
				    oos.writeObject(objectToSize);
	
			return bos.toByteArray().length;
	
			}
	
		} catch (IOException e) {
	
			// TODO Auto-generated catch block
	
			e.printStackTrace();
	
		}
	
		
	
		
	
		return 0;
		
	}

	@Override
	public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
		// TODO Auto-generated method stub
		//Prolly wont work but dont use this 
		try {
			loader.getBootModuleLoader().loadModule(jarfile.getName());
		} catch (ModuleLoadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void appendToSystemClassLoaderSearch(JarFile jarfile) {
		// TODO Auto-generated method stub
		//Soon TM
	}

	@Override
	public boolean isNativeMethodPrefixSupported() {
		// TODO Auto-generated method stub
		return false; //Soon TM
	}

	@Override
	public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
		// TODO Auto-generated method stub
		//Soon TM
	}

	@Override
	public void redefineModule(Module module, Set<Module> extraReads, Map<String, Set<Module>> extraExports,
			Map<String, Set<Module>> extraOpens, Set<Class<?>> extraUses, Map<Class<?>, List<Class<?>>> extraProvides) {
		// TODO Auto-generated method stub
		//Soon TM
	}

	@Override
	public boolean isModifiableModule(Module module) {
		// TODO Auto-generated method stub
		return false;//Soon TM
	}

}
