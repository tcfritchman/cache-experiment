package price;

import java.math.BigDecimal;

public class Product {
    private final String sku;
    private final BigDecimal price;
    private final String type;

    public Product(String sku, BigDecimal price, String type) {
        this.sku = sku;
        this.price = price;
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
