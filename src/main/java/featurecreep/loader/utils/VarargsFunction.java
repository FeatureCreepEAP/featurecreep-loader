package featurecreep.loader.utils;

@FunctionalInterface
public interface VarargsFunction<T> {

	public T apply(Object...objects);
	
}
