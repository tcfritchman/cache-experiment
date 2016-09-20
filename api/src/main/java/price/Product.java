package price;

import java.math.BigDecimal;

public class Product {

    private String sku;
    private BigDecimal price;
    private String type;

    /**
     * Default constructor
     */
    public Product() {
    }

    /**
     * Constructor with all parameters
     * @param sku The item's SKU
     * @param price The item price
     * @param type The item type
     */
    public Product(String sku, BigDecimal price, String type) {
        this.sku = sku;
        this.price = price;
        this.type = type;
    }

    /**
     * Set the item's SKU
     * @param sku
     */
    public void setSku(String sku) {
        this.sku = sku;
    }

    /**
     * Set the item's price
     * @param price
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * Set the item's type
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the item's SKU
     * @return
     */
    public String getSku() {
        return sku;
    }

    /**
     * Get the item's price
     * @return
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Get the item's type
     * @return
     */
    public String getType() {
        return type;
    }
}
