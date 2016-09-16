package price;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.cache.Cache;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.gson.stream.JsonReader;

/* IMPORTANT: CacheRebuilder should not be used at the same time as 
 * CacheBackupHandler because they both operate on the same files.
 */

public class CacheRebuilder {
	final private File backupDir;
	final private File bloomFilterFile;
	final private File dataFile;
	
	public CacheRebuilder() {
		// Create backup directories
		backupDir = new File(Config.BACKUP_DIR);
		bloomFilterFile = new File(backupDir, "BloomFile1.bin");
		dataFile = new File(backupDir, "data1.json");
	}
	
	public void rebuildCache(LoadingCache<String, Product> cache) {
		System.out.println("Begin rebuilding cache...");
		List<Product> products = null;
		try {
			InputStream in = new FileInputStream(dataFile);
			products = readJsonStream(in);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Could not read cache backup file");
			return;
		}
		
		if (products == null) {
			return;
		}
		
		
		cache.putAll(Maps.uniqueIndex(products, Product::getSku));
		System.out.println("Done rebuilding cache.");
	}
	
	public List<Product> readJsonStream(InputStream in) throws IOException {
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		try {
			return readProductsArray(reader);
		} finally {
			reader.close();
		}
	}
	
	public List<Product> readProductsArray(JsonReader reader) throws IOException {
		List<Product> products = new ArrayList<Product>();
		
		reader.beginArray();
		while (reader.hasNext()) {
			products.add(readProduct(reader));
		}
		reader.endArray();
		return products;
	}
	
	public Product readProduct(JsonReader reader) throws IOException {
		String sku = null;
		BigDecimal price = null;
		String type = null;
		
		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (name.equals("SKU")) {
				sku = reader.nextString();
			} else if (name.equals("Price")) {
				price = BigDecimal.valueOf(reader.nextDouble());
			} else if (name.equals("Type")) {
				type = reader.nextString();
			} else {
				reader.skipValue();
			}
		}
		reader.endObject();
		return new Product(sku, price, type);
	}		
}
