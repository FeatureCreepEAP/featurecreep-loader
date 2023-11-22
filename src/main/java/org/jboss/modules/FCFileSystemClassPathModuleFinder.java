package org.jboss.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import featurecreep.loader.FCLoaderBasic;

public class FCFileSystemClassPathModuleFinder extends FileSystemClassPathModuleFinder {

        FCLoaderBasic load;
        public static Set < String > jdk_paths = JDKSpecific.getJDKPaths();

        public FCFileSystemClassPathModuleFinder(ModuleLoader baseModuleLoader, FCLoaderBasic load) {
                super(baseModuleLoader);
                // TODO Auto-generated constructor stub
                this.load = load;
        }

        @Override
        public void addSystemDependencies(final ModuleSpec.Builder builder) {

                //This need to be reworked to account for already loaded modules

        	//ModuleSpec spec = load.getCustomRootSpecs().get(builder.getName());

        	builder.setClassFileTransformer(load.getMainTransformer());
        	
                LocalLoader lod = JDKSpecific.getSystemLocalLoader();

                builder.addDependency(new LocalDependencySpecBuilder()
                        .setLocalLoader(lod)
                        .setLoaderPaths(load.getNeededPackages())
                        .build());

         /*       for (int j = 0; j < load.getCombinedFiles().size(); j++) {

                        File file = load.getCombinedFiles().get(j);
                        //System.out.println(FCLoaderBasicR5.fcfile);
                        if (file != null && file.exists() && !file.toString().contains(".nil.jar") && !load.known_nils().contains(file.getAbsolutePath())) {
                                //  System.out.println("Adding Dependancy to Module " + file); soon enable for debug mode

                                final ModuleLoader loader;
                                final ModuleLoader environmentLoader;
                                environmentLoader = load.getBootModuleLoader();

                                loader = new ModuleLoader(new FileSystemClassPathModuleFinder(environmentLoader));
                                String depname =  new File(file.toString()).getAbsolutePath();
System.out.println(depname);
								Module agentModule = load.getModule(depname);
										
               
								    if(agentModule == null) {

								        boolean is_run = false;
								        if(load.getRunOnlyFiles().contains(new File(depname))) {
								        	is_run = true;
								        }
								    	agentModule = load.loadModule(depname, is_run);      
								    	agentModule.dep
								    }
								    
								if(agentModule!= null) {   

									builder.addDependency(
								            new ModuleDependencySpecBuilder()
								            //           .setModuleLoader(agentModule.getModuleLoader())
								            .setName(agentModule.getName())
								            .build()
								    );
								
								}else {
									load.known_nils().add(depname);
									System.out.println("Nil "+depname);
								}
								
                        } 
                        }*/

                }

        

        public static String getMainClass(Module mod) {
                return mod.getMainClass();
        }

        @Override //the parent often causes crashes and is not needed
        void addClassPathDependencies(final ModuleSpec.Builder builder, final ModuleLoader moduleLoader, final Path path, final Attributes mainAttributes) {

        }

        @Override
        public ModuleSpec findModule(final String name, final ModuleLoader delegateLoader) throws ModuleLoadException {
                final Path path = Paths.get(name);
                if (!path.isAbsolute()) {
                        return null;
                }
                final Path normalizedPath = path.normalize();
                if (!path.equals(normalizedPath)) {
                        return null;
                }

                if (Files.isDirectory(path)) {
                        final Path manifestPath = path.resolve("META-INF/MANIFEST.MF");
                        if (Files.exists(manifestPath)) {
                                return super.findModule(name, delegateLoader);
                        } else {
                                return null;
                        }

                } else {
                        // assume some kind of JAR file
                        try {
                                final JarFile jarFile = JDKSpecific.getJarFile(path.toFile(), true);

                                if (jarFile != null) {
                                        if (jarFile.getManifest() != null) {

                                                return super.findModule(name, delegateLoader);
                                        } else {
                                                return null;
                                        }
                                } else {
                                        return null;
                                }
                        } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (ModuleLoadException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        }

                }

                return null;
                // now build the module specification from the manifest information

        }
        
        
        
        
        //I made it public
        public static ClassLoader setContextClassLoader(final ClassLoader classLoader) {
   return SecurityActions.setContextClassLoader(classLoader);
        }
        
        
        public static void setModuleDependencies(Module mod, List<DependencySpec> specs) {
        	mod.setDependencies(specs);
        }
        
        
        
        
        
        

}
