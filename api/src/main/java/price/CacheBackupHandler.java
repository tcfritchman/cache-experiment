package price;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.PrimitiveSink;

public class CacheBackupHandler {
	
	final private File backupDir;
	//final private File bloomFilterDir;
	final private File cachedFile;
	final private File invalidFile;
	//final private File dataFile;
	
	final private BloomFilter<String> cached;
	final private BloomFilter<String> invalid;
 	final private Funnel<String> strFunnel;
	final private HashFunction hf;

	public CacheBackupHandler(File backupDir) {
		this.backupDir = backupDir;
		cachedFile = new File(backupDir, "Cached_1.bin");
		invalidFile = new File(backupDir, "Invalid_1.bin");
		//dataFile = new File(backupDir, "data1.json");
		
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
		
		// Serialize the item and write to disk
		String dataFileName = Config.BACKUP_FILENAME + Long.toString(partitionNumber);
		File dataFile = new File(backupDir, dataFileName);
		ArrayList<Product> fileContents = CacheBackupUtils.readProductsFromJSONFile(dataFile);
		fileContents.add(product);
		CacheBackupUtils.writeProductsToJSONFile(fileContents, dataFile);
	}
	
	public Product getItemFromBackup(String sku) {
	
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
	
	public void removeItemFromBackup(Product product) {
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
		
		long partitionNumber = getHashCodeFromSku(sku) % Config.BACKUP_PARTITIONS;
		
		// Remove the item 'sku' from file
		String dataFileName = Config.BACKUP_FILENAME + Long.toString(partitionNumber);
		File dataFile = new File(backupDir, dataFileName);
		CacheBackupUtils.removeProductFromJSONFileBySKU(sku, dataFile);		
	}
	
	private long getHashCodeFromSku(String sku) {
		HashCode hc = hf.newHasher()
		       .putString(sku, Charsets.UTF_8)
		       .hash();
		
		return Integer.toUnsignedLong(hc.asInt());
	}
}
