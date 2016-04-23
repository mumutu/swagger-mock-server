package tu.mumu.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.properties.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tu.mumu.mock.MockHelper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Created by luye on 2016/4/23.
 */
public class SwaggerJsonNodeParser {

    Logger log = LoggerFactory.getLogger(SwaggerJsonNodeParser.class);

    private Swagger swagger;

    private MockHelper mock;

    public SwaggerJsonNodeParser(Swagger swagger, MockHelper mock){
        this.swagger = swagger;
        this.mock = mock;
    }

    private List<JsonNode> genCollections(ArrayProperty arrayProperty, String typeName){
        Map arraySetting = (Map) arrayProperty.getVendorExtensions().getOrDefault("x-yod-array", Collections.emptyMap());
        Long size = null;
        Random random = new Random();
        try {
            Object sizeObj = arraySetting.getOrDefault("size", random.nextInt(10));//default 10
            size = new Long(String.valueOf(sizeObj));
            return LongStream.range(0, size).mapToObj(i -> {
                Property itemProperty = arrayProperty.getItems();
                return toJsonNode(arrayProperty.getItems(), typeName);
            }).collect(Collectors.toList());
        }catch(Exception ex){
            log.error(ex.getMessage());
        }
        return Collections.emptyList();
    }

    public JsonNode toJsonNode(Property property, String typeName){
        JsonNode rootNode;
        if( property instanceof ArrayProperty ){
            rootNode = new ArrayNode(JsonNodeFactory.instance);
            Property itemProperty = ((ArrayProperty) property).getItems();
            ((ArrayNode)rootNode).addAll(genCollections((ArrayProperty) property, typeName));
        }else if( property instanceof RefProperty ){
            rootNode = new ObjectNode(JsonNodeFactory.instance);
            Model model = swagger.getDefinitions().get(((RefProperty) property).getSimpleRef());
            model.getProperties().forEach((k, v) -> {
                ((ObjectNode)rootNode).set(k, toJsonNode(v, typeName));
            });
        }else{
            //TODO test
            rootNode = new TextNode(mock.eval(getTypeEvalScript(property, typeName)).toString());
        }
        return rootNode;
    }

    private String getTypeEvalScript(Property property, String typeName){
        Object script = property.getVendorExtensions().get("x-yod-type");
        if(script != null ) {
            if (script instanceof String) {
                return (String) script;
            } else if (script instanceof Map) {
                return (String) ((Map) script).getOrDefault(typeName, defaultScript(property));
            }
            throw new RuntimeException(String.format("%s:%s is not supported here", property.getName(), script.getClass()));
        }
        return defaultScript(property);
    }

    private String defaultScript(Property property){
        if(property instanceof BaseIntegerProperty){
            return "@Int";
        }else if(property instanceof EmailProperty){
            return "@Email";
        }else if(property instanceof StringProperty
                || property instanceof ByteArrayProperty
                || property instanceof PasswordProperty){
            return "@String";
        }else if(property instanceof DecimalProperty){
            return "@Float";
        }else if(property instanceof UUIDProperty){
            return "@UUID";
        }else if (property instanceof BooleanProperty) {
            return "@Bool";
        }else if(property instanceof DateProperty){
            return "@Date('YYYY-MM-DD')";
        }else if(property instanceof DateTimeProperty){
            return "@Date('YYYY-MM-DD HH:mm:ss')";
        }
        return "@String";
    }
}
