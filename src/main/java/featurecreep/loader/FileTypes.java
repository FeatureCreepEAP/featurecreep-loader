package featurecreep.loader;

import java.util.ArrayList;

public class FileTypes {
	
	public static ArrayList<String> PKZIP_COMPATIBLES = new ArrayList<String>();
	public static ArrayList<String> EUGENE_ROSHALS_ARCHIVE_COMPATIBLE = new ArrayList<String>();
	
	public FileTypes() {
		System.out.println("Adding Filetypes");
		PKZIP_COMPATIBLES.add(".zip");
		PKZIP_COMPATIBLES.add(".jar");
		PKZIP_COMPATIBLES.add(".fpm");
		PKZIP_COMPATIBLES.add(".rar");//For Java Resource Adapter Archive, not Eugene Roshal's RAR 
		PKZIP_COMPATIBLES.add(".war");
		PKZIP_COMPATIBLES.add(".ear");
		EUGENE_ROSHALS_ARCHIVE_COMPATIBLE.add("rar");//For Eugene Roshal's RAR, not Java Resource Adapter Archive
	}
	
	
	

}
