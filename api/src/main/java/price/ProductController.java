package price;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import price.Config;

@RestController
public class ProductController {
    private final DBConnection conn;
    private final LoadingCache<String, Product> cache;
    private final CacheBackupHandler cacheBackupHandler;
    private final CacheRebuilder cacheRebuilder;
    private final RemovalListener<String, Product> cacheRemovalListener;
    private final File backupDir;
    private final static Logger LOGGER = Logger.getLogger(ProductController.class.getName());
    private Long timeOfLastBloomRefresh = null;

    public static AtomicBoolean isRebuildingCache;

    /* Performance statistics */
    private ArrayList<Long> cacheRetrievalTimes = new ArrayList<Long>();
    private ArrayList<Long> backupRetrievalTimes = new ArrayList<Long>();
    private ArrayList<Long> databaseRetrievalTimes = new ArrayList<Long>();
    private long timeReqRecieved;
    private boolean cacheMissed = false;

    public ProductController() {

        conn = new DBConnection(); // Object which handles making DB connections

        // Create backup directories
        backupDir = new File(Config.BACKUP_DIR);
        backupDir.mkdirs();
        cacheBackupHandler = new CacheBackupHandler(backupDir);

        try {
            LOGGER.info("Duplicating backup files");
            cacheBackupHandler.copyAndSuffixBackupFiles(".old");
            LOGGER.info("Finished duplicating backup files");
        } catch (Exception e) {
            LOGGER.severe("Could not make copy of backup files");
        }

        // When item removed from cache, remove from backup too.
        cacheRemovalListener = new RemovalListener<String, Product>() {
            public void onRemoval(RemovalNotification<String, Product> removal) {
                Product product = removal.getValue();
                LOGGER.info("Item removed from cache: " + product.getSku());
                cacheBackupHandler.removeItemFromBackup(product);
            }
        };

        cache = CacheBuilder.newBuilder().maximumSize(Config.CACHE_SIZE).removalListener(cacheRemovalListener)
                .build(new CacheLoader<String, Product>() {
                    @Override
                    public Product load(String sku) throws Exception, ResourceUnavailableException {

                        conn.makeConnection();
                        Product product = conn.getProduct(sku);

                        try {
                            cacheBackupHandler.backupItem(product);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // add perf stat
                        //databaseRetrievalTimes.add(System.currentTimeMillis() - timeReqRecieved);
                        cacheMissed = true;

                        return product;
                    }
                });

        ProductController.isRebuildingCache = new AtomicBoolean(true);
        cacheRebuilder = new CacheRebuilder(backupDir, cache);
        cacheRebuilder.start();
    }

    @RequestMapping(value = "/product", method = RequestMethod.GET)
    public ResponseEntity<Product> getProductResponse(@RequestParam(value = "sku", required = true) String sku) {

        Product product;

        // Start "timer" for performance tests
        timeReqRecieved = System.currentTimeMillis();
        cacheMissed = false;

        // TODO wait on isRebuildingCache here???

        if (ProductController.isRebuildingCache.get()) {
            product = cacheBackupHandler.getItemFromBackup(sku);

            if (product == null) { // must get product from DB
                try {
                    conn.makeConnection();
                } catch (ResourceUnavailableException e) {
                    LOGGER.severe("Connection to database failed.");
                    return new ResponseEntity<Product>(HttpStatus.SERVICE_UNAVAILABLE);
                }

                try {
                    product = conn.getProduct(sku);
                } catch (Exception e) {
                    LOGGER.info("Could not get product " + sku + " from database");
                }
            } else {
                // add perf stat
                //backupRetrievalTimes.add(System.currentTimeMillis() - timeReqRecieved);
            }

        } else { // cache is available

            refreshBFOnInterval(Config.BF_REFRESH_INTERVAL);

            try {
                product = cache.getUnchecked(sku);

                // add perf stat
                if (cacheMissed == false) {
                    //cacheRetrievalTimes.add(System.currentTimeMillis() - timeReqRecieved);
                }

            } catch (Exception e) {
                LOGGER.info("Item was not found in database");
                return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
            }
        }

        if (product == null) { // Not found in DB
            LOGGER.info("Item was not found in database");
            return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<Product>(product, HttpStatus.OK);
    }

    @RequestMapping(value = "/product/{sku}", method = RequestMethod.PUT)
    public ResponseEntity<String> putProductResponse(@PathVariable(value = "sku") String sku,
            @RequestBody Product product) {

        // try to put in or update DB
        try {
            conn.makeConnection();
        } catch (ResourceUnavailableException e) {
            LOGGER.severe("Connection to database failed.");
            return new ResponseEntity<String>(HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            conn.putProduct(product);
        } catch (Exception e) {
            LOGGER.info("couldn't put product " + sku + " in db");
            return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
        }

        if (ProductController.isRebuildingCache.get() && cacheBackupHandler.cachedMightContain(sku)) {

            // add to invalid bloom filter so invalid backup doesn't get touched
            // during rebuilding
            cacheBackupHandler.removeItemFromBackup(product);

            cacheRebuilder.invalidQueue.add(sku);
        } else {
            cache.invalidate(sku);
        }

        return new ResponseEntity<String>(HttpStatus.OK);
    }

    /**
     * Refresh the bloom filters on an approximate time interval (dependent on
     * how often this function is called)
     *
     * @param interval
     *            Length of interval in milliseconds
     */
    private void refreshBFOnInterval(long interval) {
        if (timeOfLastBloomRefresh == null || System.currentTimeMillis() - timeOfLastBloomRefresh > interval) {
            LOGGER.info("Refreshing bloom filters");
            cacheBackupHandler.refreshBloomFilters(cache);
            LOGGER.info("Finished refreshing bloom filters");
            timeOfLastBloomRefresh = System.currentTimeMillis();

            /*
             * Let's print some perf statistics while we have an interval
             * going...
             */
            System.out.println("============PERFORMANCE STATISTICS============");
            System.out.println("Average database retrieval time:     " + average(databaseRetrievalTimes) + "ms");
            System.out.println("Average cache retrieval time:        " + average(cacheRetrievalTimes) + "ms");
            System.out.println("Average backup file retrieval time:  " + average(backupRetrievalTimes) + "ms");
        }
    }

    /**
     * Calculate the average of a list of values
     *
     * @param values
     *            List of Long integers
     * @return The average number as a Long int
     */
    private long average(ArrayList<Long> values) {
        if (values == null || values.size() == 0) {
            return 0;
        }

        long sum = 0;
        for (Long item : values) {
            sum += item;
        }

        return sum / values.size();
    }
}
