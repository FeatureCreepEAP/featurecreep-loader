package featurecreep.loader.utils;

public class ArrayCombiner<T> {

	@SuppressWarnings("unchecked")
	public T[] combineArrays(T[] pri, T[] seg) {
	    T[] combined = (T[]) java.lang.reflect.Array.newInstance(pri.getClass().getComponentType(), pri.length + seg.length);
	    System.arraycopy(pri, 0, combined, 0, pri.length);
	    System.arraycopy(seg, 0, combined, pri.length, seg.length);
	    return combined;
	}

	
	
}
