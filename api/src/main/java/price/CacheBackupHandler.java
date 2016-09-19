package price;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.PrimitiveSink;


public class CacheBackupHandler {
	
	final private File backupDir;
	final private File cachedFile;
	final private File invalidFile;
	
	private BloomFilter<String> cached;
	private BloomFilter<String> invalid;
 	final private Funnel<String> strFunnel;
	final private HashFunction hf;

	public CacheBackupHandler(File backupDir) {
		this.backupDir = backupDir;
		cachedFile = new File(backupDir, Config.CACHED_FILENAME);
		invalidFile = new File(backupDir, Config.INVALID_FILENAME);

		// Funnel is required by BloomFilter
		strFunnel = new Funnel<String>() {
			private static final long serialVersionUID = 1L;

			@Override
			public void funnel(String str, PrimitiveSink into) {
				into.putString(str, Charsets.UTF_8);
			}
		};
		
		cached = BloomFilter.create(strFunnel, Config.CACHE_SIZE, Config.BF_FALSE_PROB);
		invalid = BloomFilter.create(strFunnel, Config.CACHE_SIZE, Config.BF_FALSE_PROB);
			
		hf = Hashing.md5();
	}
	
	/**
	 * Clear the 'invalid' bloom filter and rebuild the 'cached' bloom filter
	 * using items in the cache.
	 * @param cache 
	 * The cache LoadingCache used to rebuild the bloom filter 'cached'.
	 */
	public void refreshBloomFilters(LoadingCache<String, Product> cache) {
		BloomFilter<String> newCached = BloomFilter.create(strFunnel, Config.CACHE_SIZE, Config.BF_FALSE_PROB);

		Set<String> cacheKeys = cache.asMap().keySet();
		
		for (Iterator<String> keys = cacheKeys.iterator(); keys.hasNext();) {
			String key = keys.next();
			newCached.put(key);
		}
		 	
		// This should be done atomically?
		cached = newCached;
		invalid = BloomFilter.create(strFunnel, Config.CACHE_SIZE, Config.BF_FALSE_PROB);
	}
	
	public boolean cachedMightContain(String sku) {
		return cached.mightContain(sku);
	}
	
	public boolean invalidMightContain(String sku) {
		return invalid.mightContain(sku);
	}

	public void backupItem(Product product) {
		String sku = product.getSku();
	
		// Put in bloom filter
		try {
			cached.put(sku);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed put to bloom filter 'cached'");
			return;
		}
		
		// Write the bloom filter to disk
		try {
			FileOutputStream bloomFos = new FileOutputStream(cachedFile);
			cached.writeTo(bloomFos);
			bloomFos.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to write bloom filter file 'cached' to disk");
			return;
		}
		
		long partitionNumber = getHashCodeFromSku(sku) % Config.BACKUP_PARTITIONS;
		
		String dataFileName = Config.BACKUP_FILENAME + Long.toString(partitionNumber);
		
		File dataFile = new File(backupDir, dataFileName);
		
		// Serialize the item and write to disk
		ArrayList<Product> fileContents = CacheBackupUtils.readProductsFromJSONFile(dataFile);
		fileContents.add(product);
		CacheBackupUtils.writeProductsToJSONFile(fileContents, dataFile);
	}
	
	public Product getItemFromBackup(String sku) {
	
		// check if valid entry in backup using bloom filters
		if (invalid.mightContain(sku) || cached.mightContain(sku) == false) {
			return null;
		}
		
		long partitionNumber = getHashCodeFromSku(sku) % Config.BACKUP_PARTITIONS;
		
		// Remove the item 'sku' from file
		String dataFileName = Config.BACKUP_FILENAME + Long.toString(partitionNumber);
		
		File dataFile = new File(backupDir, dataFileName);
		
		ArrayList<Product> products = CacheBackupUtils.readProductsFromJSONFile(dataFile);
		
		if (products == null) {
			return null;
		}
		
		for (int i = 0; i < products.size(); i++) {
			if (products.get(i).getSku().equals(sku)) {
				return products.get(i);
			}
		}
		
		return null;
	}
	
	public void invalidateItemInBackup(Product product) {
		String sku = product.getSku();
		
		// Put in INVALID bloom filter
		try {
			invalid.put(sku);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to put to bloom filter: 'invalid'");
			return;
		}
		
		// Write the bloom filter to disk
		try {
			FileOutputStream bloomFos = new FileOutputStream(invalidFile);
			invalid.writeTo(bloomFos);
			bloomFos.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to write bloom filter 'invalid' file to disk");
			return;
		}	
	}
	
	public void removeItemFromBackup(Product product) {
		String sku = product.getSku();
		
		invalidateItemInBackup(product);
		
		long partitionNumber = getHashCodeFromSku(sku) % Config.BACKUP_PARTITIONS;
		
		// Remove the item 'sku' from file
		String dataFileName = Config.BACKUP_FILENAME + Long.toString(partitionNumber);
		
		File dataFile = new File(backupDir, dataFileName);
		
		CacheBackupUtils.removeProductFromJSONFileBySKU(sku, dataFile);		
	}
	
	public void copyAndSuffixBackupFiles(String suffix) throws IOException {
		for (int i = 0; i < Config.BACKUP_PARTITIONS; i++) {
			File current = new File(backupDir, Config.BACKUP_FILENAME + Integer.toString(i));
			File future = new File(backupDir, Config.BACKUP_FILENAME + Integer.toString(i) + suffix);
			Files.copy(current.toPath(), future.toPath(), REPLACE_EXISTING);
		}
		/*
		File futureCached = new File(backupDir, Config.CACHED_FILENAME + suffix);
		Files.copy(cachedFile.toPath(), futureCached.toPath());
		File futureInvalid = new File(backupDir, Config.INVALID_FILENAME + suffix);
		Files.copy(invalidFile.toPath(), futureInvalid.toPath());
		*/
	}
	
	private long getHashCodeFromSku(String sku) {
		HashCode hc = hf.newHasher()
		       .putString(sku, Charsets.UTF_8)
		       .hash();
		
		return Integer.toUnsignedLong(hc.asInt());
	}
}
