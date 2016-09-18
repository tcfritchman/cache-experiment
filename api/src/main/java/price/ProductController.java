package price;

import java.io.File;

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
	
	public ProductController() {
		// Object which handles making DB connections
		conn = new DBConnection();
		
		// Create backup directories
		backupDir = new File(Config.BACKUP_DIR);
		backupDir.mkdirs();
		
		cacheBackupHandler = new CacheBackupHandler(backupDir);
		
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
		
		cacheRebuilder = new CacheRebuilder(backupDir);		
		cacheRebuilder.rebuildCache(cache);		
	}

    @RequestMapping(value="/product", method=RequestMethod.GET)
    public ResponseEntity<Product> getProductResponse(@RequestParam(value="sku", required=true) String sku) {
    	   	
    	Product product;
    	
    	try {
    		product = cache.getUnchecked(sku); // get product item with caching
    	} catch (Exception e) {
    		return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
    	}  	

    	return new ResponseEntity<Product>(product, HttpStatus.OK);  	
    }
    
    @RequestMapping(value="/product/{sku}", method=RequestMethod.PUT)
    public ResponseEntity<String> putProductResponse(@PathVariable(value="sku") String sku, @RequestBody Product product) {
    	
    	//BigDecimal priceValue = BigDecimal.valueOf(Double.valueOf(rbPrice)); 	
    	//Product product = new Product(sku, priceValue, rbType);
    	
    	// try to put in or update DB
    	try {
    		conn.makeConnection();
    	} catch (ResourceUnavailableException e) {
    		return new ResponseEntity<String>(HttpStatus.SERVICE_UNAVAILABLE);
    	}  	
    	
    	try {
    		conn.putProduct(product);
    	} catch (Exception e) {
    		System.out.println("couldn't get prodouct from db");
    		return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    	}    	

    	cache.invalidate(sku);
    	
    	return new ResponseEntity<String>(HttpStatus.OK);
    }
}
