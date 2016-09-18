package price;

public final class Config {
	private Config() {}
	
	public static String DB_URL				= "http://localhost:8000";
	public static long CACHE_SIZE 			= 100;
	public static double BF_FALSE_PROB 		= 0.01;
	public static String BACKUP_DIR 		= "cache_backups/";
	public static String BLOOM_FILTER_DIR 	= "bloom_filters/";
	public static String DATA_DIR			= "data/";
	public static int BACKUP_PARTITIONS		= 10; // Do not change when using old backup files!
	public static String BACKUP_FILENAME 	= "Data_";
}
