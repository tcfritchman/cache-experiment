package price;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;

/* IMPORTANT: CacheRebuilder should not be used at the same time as
 * CacheBackupHandler when they both operate on the same files.
 */

public class CacheRebuilder extends Thread {

    BlockingQueue<String> invalidQueue;

    final private File backupDir;
    private LoadingCache<String, Product> cache;
    private final static Logger LOGGER = Logger.getLogger(CacheRebuilder.class.getName());

    /**
     * Class constructor
     * @param backupDir Directory where cache backups will be located
     * @param cache A LoadingCache that is this service's cache
     */
    public CacheRebuilder(File backupDir, LoadingCache<String, Product> cache) {
        this.backupDir = backupDir;
        this.invalidQueue = new LinkedBlockingQueue<String>();
        this.cache = cache;
        this.setName("Cache Rebuilder");
    }

    /**
     * Entry point to start a new thread
     */
    public void run() {
        rebuildCache();
    }

    /**
     * Rebuilds the LoadingCache cache using the backup data files then invalidates any entries
     * to the cache whose values were updated during the rebuilding process.
     */
    public void rebuildCache() {
        LOGGER.info("Begin rebuilding cache...");

        for (int i = 0; i < Config.BACKUP_PARTITIONS; i++) {
            String partitionFileName = Config.BACKUP_FILENAME + Integer.toString(i) + ".old";
            File partitionFile = new File(backupDir, partitionFileName);

            ArrayList<Product> partitionContents =
                    CacheBackupUtils.readProductsFromJSONFile(partitionFile);

            cache.putAll(Maps.uniqueIndex(partitionContents, Product::getSku));
        }

        while (invalidQueue.isEmpty() == false) {
            cache.invalidate(invalidQueue.poll());
        }

        ProductController.isRebuildingCache.set(false);
        LOGGER.info("Done rebuilding cache.");
    }
}
