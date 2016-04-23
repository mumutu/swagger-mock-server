import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.properties.*;
import org.junit.Test;
import tu.mumu.swagger.SwaggerMockServer;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by luye on 2016/4/23.
 */
public class JsonTest {

    @Test
    public void propertyToJsonNode(){

        IntegerProperty property1 = new IntegerProperty();

        ArrayProperty arrayProperty = new ArrayProperty();
        arrayProperty.setItems(property1);

        Map<String, Property> innerMap = new HashMap<>();

        innerMap.put("name", new StringProperty());
        innerMap.put("iname", new StringProperty());

        RefProperty innerProperty = new RefProperty();
        innerProperty.set$ref("#/definitions/ShopOverview");

        Map<String, Property> map = new HashMap<>();
        map.put("abc", arrayProperty);
        map.put("bcd", innerProperty);

        ArrayProperty property = new ArrayProperty();
//        property.setProperties(map);

        RefProperty ref = new RefProperty();
        ref.set$ref("#/definitions/OrderOutlooking");


        SwaggerMockServer server = new SwaggerMockServer("F:\\paradise\\xplat-doc\\swagger-api\\swagger-all-v2.yaml");

        System.out.println(fromPropertyToString(ref, server.getSwagger()));
    }

    private String fromPropertyToString(Property property, Swagger swagger){
        return toJsonNode(property, swagger).toString();
    }

    private JsonNode toJsonNode(Property property, Swagger swagger){
        JsonNode rootNode = null;
        if(property instanceof RefProperty){
            JsonNodeFactory factory = JsonNodeFactory.instance;
            rootNode = new ObjectNode(factory);
            ObjectNode temp = (ObjectNode) rootNode;
            Model model = swagger.getDefinitions().get(((RefProperty) property).getSimpleRef());
            model.getProperties().forEach((k, v) -> {
                v.setName(k);
                JsonNode nested = toJsonNode(v, swagger);
                ((ObjectNode)temp).set(k, nested);
            });
        }else{
            rootNode = new TextNode("something");
        }
        return rootNode;
    }
}
