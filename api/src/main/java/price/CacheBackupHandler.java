package price;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Class constructor - provides utilities for creating, retrieving and modifying
     * backups of the cache in an efficient manner and maintaining a pair of bloom filters
     * which are used to quickly determine whether an item is in the backup.
     * @param backupDir The root directory for backup files
     */
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

        InputStream in = null;

        try {
            System.out.println("Reading bloom filter backups...");
            in = new FileInputStream(cachedFile);
            cached = BloomFilter.readFrom(in, strFunnel);
            in = new FileInputStream(cachedFile);
            invalid = BloomFilter.readFrom(in, strFunnel);
            System.out.println("Done reading bloom filter backups");
        } catch (Exception e) {
            System.out.println("Could not read bloom filters from file. Creating new bloom filters.");
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            cached = BloomFilter.create(strFunnel, Config.CACHE_SIZE, Config.BF_FALSE_PROB);
            invalid = BloomFilter.create(strFunnel, Config.CACHE_SIZE, Config.BF_FALSE_PROB);
        }

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

    /**
     * Returns true if cache backup might contain an item
     * @param sku SKU of the item in question
     * @return Returns true if item might be in backup, false if it isn't.
     */
    public boolean cachedMightContain(String sku) {
        return cached.mightContain(sku);
    }

    /**
     * Returns true if item might have been removed from cache backup
     * @param sku SKU of the item in question
     * @return Returns true if item might have been removed from backup, false if it has not.
     */
    public boolean invalidMightContain(String sku) {
        return invalid.mightContain(sku);
    }

    /**
     * Write a Product to a backup file. Each file is a partition which items are mapped
     * to by a hash function. Each product is also added to the 'cached' bloom filter,
     * which is also written to disk.
     * @param product The Product to be written to a backup file
     */
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

    /**
     * Attempt to get an item from a backup file. Item is only checked if it is
     * found by the 'cached' bloom filter and not by the 'invalid bloom filter.
     * @param sku The SKU of the Product in question
     * @return The deserialized Product object
     */
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

    /**
     * Invalidate a backup item by placing it in the 'invalid' bloom filter
     * @param product The Product to invalidate
     */
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

    /**
     * Attemts to delete an item from a backup file
     * @param product The product to try and remove
     */
    public void removeItemFromBackup(Product product) {
        String sku = product.getSku();

        invalidateItemInBackup(product);

        long partitionNumber = getHashCodeFromSku(sku) % Config.BACKUP_PARTITIONS;

        // Remove the item 'sku' from file
        String dataFileName = Config.BACKUP_FILENAME + Long.toString(partitionNumber);

        File dataFile = new File(backupDir, dataFileName);

        CacheBackupUtils.removeProductFromJSONFileBySKU(sku, dataFile);
    }

    /**
     * Make a backup of each backup.
     * @param suffix A string value which is appended to the filename of each backup to differentiate it from
     * the original.
     * @throws IOException
     */
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
