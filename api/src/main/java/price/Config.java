package price;

public final class Config {
	private Config() {}
	
	public static long CACHE_SIZE 			= 100;
	public static double BF_FALSE_PROB 		= 0.01;
	public static String BACKUP_DIR 		= "cache_backups/";
	public static String BLOOM_FILTER_DIR 	= "bloom_filters/";
	public static String DATA_DIR			= "data/";
}
