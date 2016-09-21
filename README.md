# cache-experiment

### Purpose
To improve performance of a web service while its cache is being deserialized from a backup file by using a Bloom Filter.

### The System
This service is a RESTful API for retrieving information about products which are stored in an AWS DybamoDB.

When a GET request is made for a product, the service first checks its cache before retrieving from the database. When the cache is rebuilding however, it then looks in a backup file.

Price changes are triggered by a PUT request which updates the item in the DB. The cached item is invalidated unless the cache is rebuilding in which case the item is added to an "invalid queue".

To make lookups to the backup more efficient, the data is stored in X number of partition files. SKUs are then mapped to a partition file using a hash function.


Restoration from the requires that we use a second thread so as to not block requests to the API. This approach has inherent difficulties regarding accessing shared data between threads. To solve these difficulties my system uses does the following:

1. Give each thread it's own copy of the backup files, this way there will be no conflicting writes. Only the original backup file will be written to and should reflect the current state of the cache once it has been rebuilt.

2. While the cache is rebuilding, use a queue to collect item price changes so that once the queue is done rebuilding those items can be invalidated.



### Performance
These measurements were made on my system (2.4ghz Core i5 linux machine) using the following configuration settings:

~~~~
// Database
public static String DB_URL                	= "http://localhost:8000";

// Caching
public static long CACHE_SIZE            	= 200000;

// Bloom Filter
public static double BF_FALSE_PROB         	= 0.01;
public static long BF_REFRESH_INTERVAL     	= 60000; // 1 min

// Partitioning
public static String BACKUP_DIR         	= "cache_backups/";
public static String BLOOM_FILTER_DIR     	= "bloom_filters/";
public static String DATA_DIR            	= "data/";
public static int BACKUP_PARTITIONS        	= 2000;
public static String BACKUP_FILENAME     	= "Data_";
public static String CACHED_FILENAME    	= "Cached_1.bin";
public static String INVALID_FILENAME    	= "Invalid_1.bin";
~~~~

The database contains 500k Products totalling ~100MB.

#### Results
~~~~
* Average database retrieval time:     85002415ns
* Average backup file retrieval time:  631788ns
* Average cache retrieval time:        198211ns
~~~~

As you can see, the partitioned backup files provide over 100x faster lookups than database calls.

### Project Setup
###### Requirements
* Java 8
* Maven
* Node.js
* AWS DynamoDB (can be run [locally](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html))

1. Configure DynamoDB with the following settings:
  `
  "AttributeDefinitions" [ 
  0: { 
  "AttributeName":"SKU"
  "AttributeType":"S"
  "TableName":"Products"
  "KeySchema" [ 
  0: { 
  "AttributeName":"SKU"
  "KeyType":"HASH"
  "ReadCapacityUnits":1
  "WriteCapacityUnits":1
  `
2. Build the test database using `cache-experiment/scripts/DB/buildtestdb.js`

3. Install Java project dependencies (`cache-experiment/api/src/`) using Maven
4. Install the test script: `cd cache-experiment/scripts/API/` and `npm install`
5. You may change configuration of the web service in the file `cache-experiment/api/src/main/java/price/Config.java`

To run:
1. Build and Run the web service (Java project)
2. Run the test script using `node loadsim.js`
