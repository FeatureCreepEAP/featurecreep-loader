package featurecreep.loader.finder;

import featurecreep.loader.FCLoaderBasic;

/**
 * All ModuleFinders that run through FCLoaderBasicR* that have this will set
 * the FCLoaderBasic
 */
public interface NeedsFCLoaderBasic {

	public void setFCLoaderBasic(FCLoaderBasic fcloader);

}
