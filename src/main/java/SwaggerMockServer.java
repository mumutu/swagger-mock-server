import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.*;
import akka.http.javadsl.server.values.Parameters;
import akka.http.javadsl.server.values.PathMatcher;
import akka.http.javadsl.server.values.PathMatchers;
import akka.http.scaladsl.Http;
import com.google.common.collect.Sets;
import io.swagger.models.*;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.*;
import io.swagger.parser.SwaggerParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;
import scala.Tuple2;
import scala.collection.JavaConversions;


import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static akka.actor.ActorSystem.*;

/**
 * Created by luye on 2016/4/7.
 */
public class SwaggerMockServer extends HttpApp {

    Logger log = LoggerFactory.getLogger(SwaggerMockServer.class);

    private Map<String, Path> paths;

    private Swagger swagger;

    private CompletionStage<Http.ServerBinding> binding;

    private static MockHelper mock = MockHelper.getInstance();

    public SwaggerMockServer(){
        swagger = new SwaggerParser()
                .read("./pet.yaml");
        paths = swagger.getPaths();
    }

    public static void main(String[] args) throws IOException {
        // boot up server using the route as defined below
        ActorSystem system = create();

        // HttpApp.bindRoute expects a route being provided by HttpApp.createRoute
        SwaggerMockServer server = new SwaggerMockServer();
        server.setBinding(server.bindRoute("localhost", 9000, system));
        System.out.println("Type RETURN to exit");
        System.in.read();
        system.terminate();
    }

    public CompletionStage<Http.ServerBinding> getBinding() {
        return binding;
    }

    public void setBinding(CompletionStage<Http.ServerBinding> binding) {
        this.binding = binding;
    }

    public List<Route> toRoute(Map<String, Path> paths){
        return paths.entrySet().stream()
                .flatMap(entry -> {
                    String[] segments = entry.getKey().split("/");
                    Path path = entry.getValue();
                    //List all the operation
                    //Path parameter
                    Set<Parameter> parameters = Optional
                            .ofNullable(path.getParameters()).map(Sets::newHashSet)
                            .orElseGet(HashSet::new);

                    if (!entry.getValue().getOperations().isEmpty()) {
                        parameters.addAll(entry.getValue().getOperations().get(0).getParameters());
                    }
                    //Generate Path Matcher
                    List<PathMatcher> matchers = Stream.of(segments).filter(StringUtils::isNotBlank).map(s -> {
                        String segment = s;
                        if (segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}') {
                            segment = segment.substring(1, segment.length() - 1);
                            //find the type of this name
                            String pathSeg = segment;
                            Parameter parameter = parameters.stream()
                                    .filter(p -> {
                                        return "path".equals(p.getIn()) && pathSeg.equals(p.getName());
                                    })
                                    .findFirst().orElseThrow(() -> {
                                        log.error("seg:{}, path:{}, pathVars:{}", new Object[]{pathSeg, entry.getKey(), parameters});
                                        return new RuntimeException("Format error");
                                    });//must have one

                            PathParameter pathParameter = (PathParameter) parameter;
                            Property property = PropertyBuilder.build(pathParameter.getType(), pathParameter.getFormat(), null);
                            return fromProperty(property);
                        }
                        return PathMatchers.segment(segment);

                    }).collect(Collectors.toList());


                    //Get route
                    return entry.getValue().getOperationMap().entrySet().stream().map(e -> {
                        HttpMethod method = e.getKey();
                        Operation op = e.getValue();

                        //fill the parameters
                        parameters.addAll(op.getParameters());
                        Map<String, RequestVal> paraNamedMapping = parameters.stream()
                                .filter(p -> {
                                    return "query".equals(p.getIn());
                                }).map(p -> {
                                    QueryParameter param = (QueryParameter) p;
                                    Property property = PropertyBuilder.build(param.getType(), param.getFormat(), null);
                                    akka.http.javadsl.server.values.Parameter tempParam;
                                    if (property instanceof StringProperty) {
                                        tempParam = Parameters.stringValue(p.getName());
                                    } else if (property instanceof IntegerProperty || property instanceof BaseIntegerProperty) {
                                        tempParam = Parameters.intValue(p.getName());
                                    } else if (property instanceof LongProperty) {
                                        tempParam = Parameters.longValue(p.getName());
                                    } else if (property instanceof DoubleProperty) {
                                        tempParam = Parameters.doubleValue(p.getName());
                                    } else if (property instanceof FloatProperty) {
                                        tempParam = Parameters.floatValue(p.getName());
                                    } else if (property instanceof BooleanProperty) {
                                        tempParam = Parameters.booleanValue(p.getName());
                                    } else {
                                        throw new RuntimeException(String.format("Property type: %s is not supported yet", property.getClass()));
                                    }

                                    if (param.getDefault() != null) {
                                        return Tuple2.apply(p.getName(), tempParam.withDefault(param.getDefault()));
                                    } else if (!param.getRequired()) {
                                        return Tuple2.apply(p.getName(), tempParam.optional());
                                    } else {
                                        return Tuple2.apply(p.getName(), tempParam);
                                    }
                                }).collect(Collectors.toMap(tuple -> tuple._1(), tuple -> tuple._2()));


                        Response rep = op.getResponses().getOrDefault("200", op.getResponses().get("404")); //TODO check


                        Route route = path(matchers.toArray()).route(
                                handleWith(ctx -> {
                                    Property property = rep.getSchema();

                                    Map context = new HashMap<>();
                                    paraNamedMapping.entrySet().stream().forEach(named -> {
                                        try {
                                            context.put(named.getKey(), named.getValue().get(ctx));
                                        } catch (Exception ex) {
                                            log.error(ex.getMessage());
                                        }
                                    });
                                    log.debug("CTX: {}", context);
                                    final HttpResponse response = HttpResponse.create()
                                            .withEntity(fromPropertyToString(property))//MockHelper
                                            .withStatus(StatusCodes.ACCEPTED);
                                    return ctx.complete(response);
                                }, paraNamedMapping.values().toArray(new RequestVal[]{}))
                        );


                        if (method == HttpMethod.GET) {
                            return get(route);
                        } else if (method == HttpMethod.DELETE) {
                            return delete(route);
                        } else if (method == HttpMethod.POST) {
                            return post(route);
                        } else if (method == HttpMethod.PUT) {
                            return put(route);
                        } else {
                            return route; //Dummy
                        }
                    });
                })
                .collect(Collectors.toList());
    }


    //TODO
    private PathMatcher fromProperty(Property property){
        if(property instanceof IntegerProperty){
            return PathMatchers.intValue();
        }
        return PathMatchers.intValue(); //dummy one
    }

    private String fromPropertyToString(Property property){
        String typeName = (String) property.getVendorExtensions().getOrDefault("x-yod-name", "default");
        if(property instanceof ArrayProperty) {
            //TODO
            Random random = new Random();
            int max = random.nextInt(20);
            return Json.toJson(IntStream.range(0, max).mapToObj(i -> {
                return toResponse(((ArrayProperty) property).getItems(), typeName);
            }).collect(Collectors.toList())).toString();
        }else {
            return Json.toJson(toResponse(property, typeName)).toString();
        }
    }

    /**
     * stick to the first type
     * @param property
     * @param typeName
     * @return
     */
    private Map<String, Object> toResponse(Property property, String typeName){
        Map<String, Object> mapping = new HashMap<>();
        if(property instanceof ArrayProperty) {
            mapping.put(property.getName(), toResponse(((ArrayProperty) property).getItems(), typeName));
        }else if(property instanceof RefProperty){
            Model model = swagger.getDefinitions().get(((RefProperty) property).getSimpleRef());
            model.getProperties().forEach((k, v) -> {
                v.setName(k);
                Map<String, Object> nested = toResponse(v, typeName);
                mapping.putAll(nested);
            });

        }else if(property instanceof ObjectProperty){
            mapping.putAll(((ObjectProperty)property).getProperties());
            ((ObjectProperty) property).getProperties().forEach((k, v) -> {
                v.setName(k);
                Map<String, Object> nested = toResponse(v, typeName);
                mapping.putAll(nested);
            });
        }else{
            //baseType?
            //type -> default
            mapping.put(property.getName(), mock.eval(getTypeEvalScript(property, typeName)));
        }
        return mapping;
    }

    //TODO ctx
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
        if(property instanceof IntegerProperty || property instanceof LongProperty){
            return "@Int";
        }else if(property instanceof StringProperty
                || property instanceof ByteArrayProperty
                || property instanceof PasswordProperty){
            return "@String";
        }else if(property instanceof DecimalProperty){
            return "@Float";
        }else if(property instanceof EmailProperty){
            return "@Email";
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


    /**
     * how to make this from swagger?
     * Dynamic create
     * @return
     */
    @Override
    public Route createRoute() {
        PathMatcher matcher = PathMatchers.segments();
        return
                // here the complete behavior for this server is defined
                route(
                        pathSingleSlash().route(handleWith(ctx -> {
                            return ctx.complete("Swagger Mock Server");
                        })),
                        JavaConversions.asScalaBuffer(toRoute(paths)).toList());

    }
}
