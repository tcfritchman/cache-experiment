package price;

public final class Config {
	private Config() {}

	// Feature toggling
	public static boolean CACHING_ENABLED 	= true;
	public static boolean BACKUP_ENABLED	= true;
	
	// Database
	public static String DB_URL				= "http://localhost:8000";
	
	// Caching
	public static long CACHE_SIZE 			= 100;
	
	// Bloom Filter
	public static double BF_FALSE_PROB 		= 0.01;
	
	// Partitioning
	public static String BACKUP_DIR 		= "cache_backups/";
	public static String BLOOM_FILTER_DIR 	= "bloom_filters/";
	public static String DATA_DIR			= "data/";
	public static int BACKUP_PARTITIONS		= 10; // Do not change when using old backup files!
	public static String BACKUP_FILENAME 	= "Data_";
	public static String CACHED_FILENAME	= "Cached_1.bin";
	public static String INVALID_FILENAME	= "Invalid_1.bin";
}
