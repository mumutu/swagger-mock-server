import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

/**
 * Created by luye on 2016/4/7.
 */
public class ParserTest {
    @org.junit.Test
    public void testSwagger() throws Exception {

        Swagger swagger = new SwaggerParser()
                .read("http://139.196.241.98:8082/v2/api-docs");
        System.out.println(swagger.getPaths());
        Path path = swagger.getPaths().get("/bank/{type}");
        System.out.println(path.getOperations().get(0).getResponses());
        System.out.println(path.getGet());
        System.out.println(path.getPost());
        System.out.println(path.getPut());
        System.out.println(path.getDelete());
    }
}
