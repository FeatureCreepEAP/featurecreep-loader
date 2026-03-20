package featurecreep.loader.finder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ResourceLoader;

import featurecreep.loader.finder.ModuleLoadingMap.ModuleLoadingMapEntry;

//I just realised I may not actually need this after making it :((((
public class ModuleLoadingMap implements Map<String, ModuleLoadingMapEntry> {

	public static class ModuleLoadingMapEntry {

		public String name;
		public ResourceLoader loader;
		// public ModuleFinder finder;

		public ModuleLoadingMapEntry(String name, ResourceLoader loader, ModuleFinder finder) {// Possibly Make Finder
			super();
			this.name = name;
			this.loader = loader;
			// this.finder = finder;
		}

		public ModuleLoadingMapEntry(String name, ResourceLoader loader) {
			super();
			this.name = name;
			this.loader = loader;
			// this.finder = finder;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public ResourceLoader getLoader() {
			return loader;
		}

		public void setLoader(ResourceLoader loader) {
			this.loader = loader;
		}

//		public ModuleFinder getFinder() {
//			return finder;
//		}
//
//		public void setFinder(ModuleFinder finder) {
//			this.finder = finder;
//		}

	}

	public LinkedHashSet<ModuleLoadingMapEntry> list = new LinkedHashSet<ModuleLoadingMapEntry>();

	public ModuleLoadingMapEntry get(String url) {
		for (ModuleLoadingMapEntry entry : list) {
			if (entry.getName().equals(url)) {
				return entry;
			}
		}
		return null;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		list.clear();
	}

	@Override
	public boolean containsKey(Object arg0) {
		// TODO Auto-generated method stub

		for (ModuleLoadingMapEntry entry : list) {
			if (entry.getName().equals((String) arg0)) {
				return true;
			}
		}
		return false;

	}

	@Override
	public boolean containsValue(Object arg0) {
		// TODO Auto-generated method stub
		return list.contains(arg0);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set entrySet() {
		// TODO Auto-generated method stub
		return list;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Set keySet() {
		// TODO Auto-generated method stub
		LinkedHashSet<String> set = new LinkedHashSet<String>();
		for (ModuleLoadingMapEntry entry : list) {
			set.add(entry.getName());
		}
		return set;
	}

	public ModuleLoadingMapEntry put(String arg0, ModuleLoadingMapEntry arg1) {
		// TODO Auto-generated method stub
		removebyName(arg0);
		list.add(arg1);

		return arg1;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void putAll(Map arg0) {
		// TODO Auto-generated method stub
//TODO
		list.addAll(arg0.values());
	}

	@Override
	public ModuleLoadingMapEntry remove(Object moduleloadingmapentry) {
		list.remove(moduleloadingmapentry);
		return (ModuleLoadingMapEntry) moduleloadingmapentry;
	}

	public ModuleLoadingMapEntry remove(ModuleLoadingMapEntry ModuleLoadingMapEntry) {
		return remove(ModuleLoadingMapEntry);
	}

	public ModuleLoadingMapEntry removebyName(String url) {
		// TODO Auto-generated method stub
		ModuleLoadingMapEntry existing = get(url);
		if (existing != null) {
			list.remove(existing);
		}

		return existing;
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return list.size();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Collection values() {
		// TODO Auto-generated method stub
		return list;
	}

	@Override
	public ModuleLoadingMapEntry get(Object key) {
		// TODO Auto-generated method stub
		return get((String) key);
	}

}
