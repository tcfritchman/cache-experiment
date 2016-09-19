package price;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;

/* IMPORTANT: CacheRebuilder should not be used at the same time as 
 * CacheBackupHandler when they both operate on the same files.
 */

public class CacheRebuilder extends Thread {
	
	BlockingQueue<String> invalidQueue;
	
	final private File backupDir;
	private LoadingCache<String, Product> cache;
	
	public CacheRebuilder(File backupDir, LoadingCache<String, Product> cache) {
		this.backupDir = backupDir;
		this.invalidQueue = new LinkedBlockingQueue<String>();
		this.cache = cache;
	}
	
	public void run() {
		rebuildCache();
	}
	
	public void rebuildCache() {
		System.out.println("Begin rebuilding cache...");
		
		for (int i = 0; i < Config.BACKUP_PARTITIONS; i++) {
			String partitionFileName = Config.BACKUP_FILENAME + Integer.toString(i) + ".old";
			File partitionFile = new File(backupDir, partitionFileName);
			
			ArrayList<Product> partitionContents = 
					CacheBackupUtils.readProductsFromJSONFile(partitionFile);
				
			cache.putAll(Maps.uniqueIndex(partitionContents, Product::getSku));
			
			// Simulate this actually taking a while....
			try {
				Thread.sleep(3000L);
			} catch (InterruptedException e) {
				System.out.println("this nonsense was interrupted");
			}
		}
		
		// Delete all invalid entries from backup and cache
		while (invalidQueue.isEmpty() == false) {
			cache.invalidate(invalidQueue.poll());		
		}
			
		ProductController.isRebuildingCache.set(false);
		System.out.println("Done rebuilding cache.");	
	}	
}
