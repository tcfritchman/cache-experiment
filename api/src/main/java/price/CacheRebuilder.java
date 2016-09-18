package price;

import java.io.File;
import java.util.ArrayList;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;

/* IMPORTANT: CacheRebuilder should not be used at the same time as 
 * CacheBackupHandler because they both operate on the same files.
 */

public class CacheRebuilder {
	final private File backupDir;
	
	public CacheRebuilder(File backupDir) {
		this.backupDir = backupDir;
	}
	
	public void rebuildCache(LoadingCache<String, Product> cache) {
		System.out.println("Begin rebuilding cache...");
		
		for (int i = 0; i < Config.BACKUP_PARTITIONS; i++) {
			String partitionFileName = Config.BACKUP_FILENAME + Integer.toString(i);
			File partitionFile = new File(backupDir, partitionFileName);
			
			ArrayList<Product> partitionContents = 
					CacheBackupUtils.readProductsFromJSONFile(partitionFile);
			
			cache.putAll(Maps.uniqueIndex(partitionContents, Product::getSku));
		}
		
		System.out.println("Done rebuilding cache.");
	}	
}
