package featurecreep.loader;

import java.util.ArrayList;
import java.util.List;

public interface GetPackagesFromClassLoader {

	public static String[] getPackageNamesInCurrentClassLoader() // Soon we will need to get one for others, but it is
																	// currently harder in java 8 vanilla classloaders,
																	// JBM Classloaders are easier though for it
	{

		String[] packages_needed;

		List<String> package_list = new ArrayList();

		for (int j = 0; j < Package.getPackages().length; j++) {

			package_list.add(Package.getPackages()[j].getName().toString().replace(".", "/"));

		}

		packages_needed = package_list.toArray(new String[package_list.size()]);

		for (int p = 0; p < packages_needed.length; p++) {

			// System.out.println(packages_needed[p]);

		}

		return packages_needed;

	}

}