package price;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import price.Config;

@RestController
public class ProductController {
	
	private final DBConnection conn;
	private final LoadingCache<String, Product> cache;
	private final CacheBackupHandler cacheBackupHandler;
	private final CacheRebuilder cacheRebuilder;
	
	public ProductController() {
		
		conn = new DBConnection();
		
		//todo: create backup directories here.
		
		cacheBackupHandler = new CacheBackupHandler();
		cacheRebuilder = new CacheRebuilder();
		
		cache = CacheBuilder.newBuilder()
				.maximumSize(Config.CACHE_SIZE)
				.build(new CacheLoader<String, Product>() {
					@Override
					public Product load(String sku) throws Exception, ResourceUnavailableException {	    	
						conn.makeConnection();						
						return conn.getProduct(sku);
					}
				});	
		
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
    	
    	cacheBackupHandler.makeBackup(cache.asMap());
    	
    	return new ResponseEntity<Product>(product, HttpStatus.OK);  	
    }
}
