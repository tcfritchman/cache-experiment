package price;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONObject;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

public class CacheBackupHandler {
	final private File backupDir;
	//final private File bloomFilterDir;
	final private File bloomFilterFile;
	final private File dataFile;

	
	public CacheBackupHandler() {
		// Create backup directories
		backupDir = new File(Config.BACKUP_DIR);
		backupDir.mkdirs();
		bloomFilterFile = new File(backupDir, "BloomFile1.bin");
		dataFile = new File(backupDir, "data1.json");
	}
	
	public void makeBackup(ConcurrentMap<String,Product> cacheMap) {
		Set<String> keySet = cacheMap.keySet();
		

		// Funnel is required by BloomFilter
		Funnel<String> strFunnel = new Funnel<String>() {
			private static final long serialVersionUID = 1L;

			@Override
			public void funnel(String str, PrimitiveSink into) {
				into.putString(str, Charsets.UTF_8);
			}
		};
		
		BloomFilter<String> cached = BloomFilter.create(strFunnel, Config.CACHE_SIZE, Config.BF_FALSE_PROB); 
		
		// Create the bloom filter
		keySet.forEach(sku -> cached.put(sku));
		
		// Write the bloom filter to disk
		try {
			FileOutputStream bloomFos = new FileOutputStream(bloomFilterFile);
			cached.writeTo(bloomFos);
			bloomFos.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to write bloom filter file to disk");
		}
		
		// Serialize the cache map and write to disk
		FileWriter dataFw = null;
		try {
			dataFw = new FileWriter(dataFile);
			dataFw.write("{Products:[");
			int cacheSize = cacheMap.size();
			int i = 0;
			for (Iterator<Product> cacheIter = cacheMap.values().iterator(); cacheIter.hasNext();) {
				JSONObject serialized = new JSONObject();
				Product nextProduct = cacheIter.next();
				serialized.put("SKU", nextProduct.getSku());
				serialized.put("Price", nextProduct.getPrice().toPlainString());
				serialized.put("Type", nextProduct.getType());
				String serialStr = serialized.toString();
				if (i != cacheSize - 1) {
					serialStr += ',';
				}
				dataFw.write(serialStr);
				i++;
			}
			dataFw.write("]}");
			//serializedCache.put("Products", serializedCacheArray);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to serialize products");
		} finally {	
			try {
				if (dataFw != null) { 
					dataFw.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}	
	}
}
