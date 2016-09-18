package price;

import java.math.BigDecimal;

public class Product {
    private String sku;
    private BigDecimal price;
    private String type;
    
    public Product() {  	
    }

    public Product(String sku, BigDecimal price, String type) {
        this.sku = sku;
        this.price = price;
        this.type = type;
    }

    public void setSku(String sku) {
		this.sku = sku;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}
	
	public void setType(String type) {
		this.type = type;
	}

	public String getSku() {
        return sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getType() {
        return type;
    }    
}
