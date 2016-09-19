package price;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

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
	
	public static AtomicBoolean isRebuildingCache;
	
	public ProductController() {
		
		conn = new DBConnection(); // Object which handles making DB connections
		
		// Create backup directories
		backupDir = new File(Config.BACKUP_DIR);
		backupDir.mkdirs();	
		cacheBackupHandler = new CacheBackupHandler(backupDir);
		
		try {
			System.out.println("Duplicating backup files");
			cacheBackupHandler.copyAndSuffixBackupFiles(".old");
			System.out.println("Finished duplicating backup files");
		} catch (Exception e) {
			System.out.println("Could not make copy of backup files");
		}

		// When item removed from cache, remove from backup too.
		cacheRemovalListener = new RemovalListener<String, Product>() {
			public void onRemoval(RemovalNotification<String, Product> removal) {
				Product product = removal.getValue();
				System.out.println("item removed from cache: " + product.getSku());
				
				cacheBackupHandler.removeItemFromBackup(product);
			}
		};
		
		cache = CacheBuilder.newBuilder()
				.maximumSize(Config.CACHE_SIZE)
				.removalListener(cacheRemovalListener)
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
						
						return product;
					}
				});	
		
		
		//if (Config.BACKUP_ENABLED && Config.CACHING_ENABLED) {
			// do this on a new thread...
			ProductController.isRebuildingCache = new AtomicBoolean(true);
			cacheRebuilder = new CacheRebuilder(backupDir, cache);	
			cacheRebuilder.start();

		//}	
	}

    @RequestMapping(value="/product", method=RequestMethod.GET)
    public ResponseEntity<Product> getProductResponse(@RequestParam(value="sku", required=true) String sku) {
    	   	
    	Product product;
    	
    	if (ProductController.isRebuildingCache.get()) {
    		product = cacheBackupHandler.getItemFromBackup(sku);
    		
    		if (product == null) { // must get product from DB
    			try {
    				conn.makeConnection();
    			} catch (ResourceUnavailableException e) {
    	    		return new ResponseEntity<Product>(HttpStatus.SERVICE_UNAVAILABLE);
    			}
    			
    			try {
    				product = conn.getProduct(sku);
    			} catch (Exception e) {
    				System.out.println("Could not get product " + sku + " from database");
    			}
    		}
		
    	} else { // cache is available
    		try {
        		product = cache.getUnchecked(sku);
        	} catch (Exception e) {
        		return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
        	}  	
    	}
    	
		if (product == null) { // Not found in DB
			return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
		}
    	
    	return new ResponseEntity<Product>(product, HttpStatus.OK);  	
    }
    
    @RequestMapping(value="/product/{sku}", method=RequestMethod.PUT)
    public ResponseEntity<String> putProductResponse(@PathVariable(value="sku") String sku, @RequestBody Product product) {
    	
    	// try to put in or update DB
    	try {
    		conn.makeConnection();
    	} catch (ResourceUnavailableException e) {
    		return new ResponseEntity<String>(HttpStatus.SERVICE_UNAVAILABLE);
    	}  	
    	
    	try {
    		conn.putProduct(product);
    	} catch (Exception e) {
    		System.out.println("couldn't put product in db");
    		return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    	}    	

    	if (ProductController.isRebuildingCache.get() && cacheBackupHandler.cachedMightContain(sku)) {
    		
    		// add to invalid bloom filter so invalid backup doesn't get touched during rebuilding
    		cacheBackupHandler.removeItemFromBackup(product);
    		
    		cacheRebuilder.invalidQueue.add(sku);
    	} else {
    		cache.invalidate(sku);
    	}
    	
    	return new ResponseEntity<String>(HttpStatus.OK);
    }
}
