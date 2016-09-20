package price;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;

public class DBConnection {
    private Table table = null;
    private AmazonDynamoDBClient client;
    private DynamoDB dynamoDB;

    /**
     * Connect to the DynamoDB database. Database URL specified by Config.DB_URL
     * @throws ResourceUnavailableException
     */
    public void makeConnection() throws ResourceUnavailableException {

        try { // Connect to DB and get table
            client = new AmazonDynamoDBClient(new ProfileCredentialsProvider())
                    .withEndpoint(Config.DB_URL);

            dynamoDB = new DynamoDB(client);

            table = dynamoDB.getTable("Products");
        } catch (Exception e) {
            throw new ResourceUnavailableException();
        }
    }

    /**
     * Get a product from the database
     * @param sku The sku of the product (Primary key)
     * @return A Product object
     * @throws Exception if not found
     */
    public Product getProduct(String sku) throws Exception {

        Item item = null;
        try {
            item = table.getItem("SKU", sku);
        } catch (Exception e) {
            // Error making query
            throw e;
        }

        if (item == null) {
            // Item with SKU not found
            throw new Exception("Item " + sku + " not found");
        }

        // Wait longer to simulate network
        try {
            long time = 30L;
            Thread.sleep(time);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        return new Product(sku, item.getNumber("Price"), item.getString("Type"));
    }

    /**
     * Add/Update a product in the database
     * @param product The product to add/update
     * @throws Exception if unsuccessful
     */
    public void putProduct(Product product) throws Exception {
        Item item = new Item()
                .withPrimaryKey("SKU", product.getSku())
                .withNumber("Price", product.getPrice())
                .withString("Type", product.getType());

        table.putItem(item);
    }
}
