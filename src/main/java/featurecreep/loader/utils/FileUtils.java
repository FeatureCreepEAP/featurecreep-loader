package featurecreep.loader.utils;

import java.io.File;

public class FileUtils {

	/**
	 * 递归删除文件夹及其所有子文件夹和文件。
	 * 
	 * @param folder 要删除的文件夹
	 * @throws Exception 如果删除过程中出现问题，则抛出异常
	 */
	public static void deleteFolderWithFiles(File folder) throws Exception {
		if (folder.exists()) {
// 遍历文件夹中的所有文件和子文件夹  
			File[] files = folder.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
// 如果是子文件夹，则递归调用此方法  
						deleteFolderWithFiles(file);
					} else {
// 如果是文件，则直接删除  
						if (!file.delete()) {
							throw new Exception("Unable to delete file: " + file.getName());
						}
					}
				}
			}

// 删除空文件夹  
			if (!folder.delete()) {
				throw new Exception("Unable to delete folder: " + folder.getName());
			}
		}
	}

}
