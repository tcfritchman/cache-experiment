package price;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;

@RestController
public class ProductController {

    @RequestMapping(value="/product", method=RequestMethod.GET)
    public ResponseEntity<Product> getProduct(@RequestParam(value="sku", required=true) String sku) {
    	
    	Table table = null;
    	
    	try { // Connect to DB and get table
	    	AmazonDynamoDBClient client = new AmazonDynamoDBClient(new ProfileCredentialsProvider())
	    			.withEndpoint("http://localhost:8000");
	    	
	    	DynamoDB dynamoDB = new DynamoDB(client);
	    	
	    	table = dynamoDB.getTable("Products");
    	} catch (Exception e) {
    		return new ResponseEntity<Product>(HttpStatus.SERVICE_UNAVAILABLE);
    	}
    	
    	Item item = null;
    	Product product = null;
    	
    	try {
    		item = table.getItem("SKU", sku);	
    	} catch (Exception e) {
    		// Error making query
    		return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
    	}
    	
    	if (item == null) {
    		// Item with SKU was not found
    		return new ResponseEntity<Product>(HttpStatus.NOT_FOUND);
    	} else {
    		product = new Product(sku, item.getNumber("Price"), item.getString("Type"));
    		return new ResponseEntity<Product>(product, HttpStatus.OK);
    	}	
    }
}
