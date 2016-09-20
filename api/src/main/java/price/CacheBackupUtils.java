package price;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.google.gson.stream.JsonReader;

public class CacheBackupUtils {

    /**
     * Read and deserialize a JSON file of Products
     * @param file The file to read from
     * @return Returns an ArrayList<Product> containing each Product object contained
     * in the JSON file
     */
    public static ArrayList<Product> readProductsFromJSONFile(File file) {
        ArrayList<Product> products = new ArrayList<Product>();
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            products = (ArrayList<Product>) readJsonStream(in);
        } catch (Exception e) {
            System.out.println("Could not read cache backup file "
                    + file.getName()
                    + " (or file hasn't been created yet)");
            return products;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return products;
    }

    /**
     * Serialize an ArrayList of products as JSON objects and write them to a file
     * @param products The products to serialize and write
     * @param file The file to write to
     */
    public static void writeProductsToJSONFile(ArrayList<Product> products, File file) {
        FileWriter dataFw = null;
        try {
            dataFw = new FileWriter(file);
            dataFw.write("[");
            for (int i = 0; i < products.size(); i++) {
                JSONObject serialized = new JSONObject();
                serialized.put("SKU", products.get(i).getSku());
                serialized.put("Price", products.get(i).getPrice().toPlainString());
                serialized.put("Type", products.get(i).getType());
                String serialStr = serialized.toString();
                if (i != products.size() - 1) {
                    serialStr += ',';
                }
                dataFw.write(serialStr);
            }
            dataFw.write("]");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to serialize products");
        } finally {
            try {
                if (dataFw != null) {
                    dataFw.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Remove a JSON Product object from a JSON file
     * @param sku The SKU of the product to try removing
     * @param file The file to remove from
     */
    public static void removeProductFromJSONFileBySKU(String sku, File file) {
        ArrayList<Product> products = null;
        products = readProductsFromJSONFile(file);

        if (products == null) {
            return;
        }

        for (int i = 0; i < products.size(); i++) {
            if (sku.equals(products.get(i).getSku())) {
                products.remove(i);
            }
        }

        writeProductsToJSONFile(products, file);
    }

    private static List<Product> readJsonStream(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        try {
            return readProductsArray(reader);
        } finally {
            reader.close();
        }
    }

    private static List<Product> readProductsArray(JsonReader reader) throws IOException {
        List<Product> products = new ArrayList<Product>();

        reader.beginArray();
        while (reader.hasNext()) {
            products.add(readProduct(reader));
        }
        reader.endArray();
        return products;
    }

    private static Product readProduct(JsonReader reader) throws IOException {
        String sku = null;
        BigDecimal price = null;
        String type = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("SKU")) {
                sku = reader.nextString();
            } else if (name.equals("Price")) {
                price = BigDecimal.valueOf(reader.nextDouble());
            } else if (name.equals("Type")) {
                type = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return new Product(sku, price, type);
    }
}
