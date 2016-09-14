package price;

public class Product {
    private final String sku;
    private final float price;
    private final String type;

    public Product(String sku, float price, String type) {
        this.sku = sku;
        this.price = price;
        this.type = type;
    }

    public String getSku() {
        return sku;
    }

    public float getPrice() {
        return price;
    }

    public String getType() {
        return type;
    }
}
