package price;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
public class ProductController {

    @RequestMapping(value="/product", method=RequestMethod.GET)
    public Product product(@RequestParam(value="sku", required=true) String sku) {
        return new Product(sku, 100.0f, "regular");
    }
}
