package tu.mumu.swagger;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.*;
import akka.http.javadsl.server.*;
import akka.http.javadsl.settings.ServerSettings;
import akka.http.scaladsl.Http;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import scala.Tuple2;
import tu.mumu.mock.MockHelper;


import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static akka.actor.ActorSystem.*;

/**
 * Created by luye on 2016/4/7.
 * updated by luye on 2017/2/17
 */
public class SwaggerMockServer extends HttpApp {

    Logger log = LoggerFactory.getLogger(SwaggerMockServer.class);

    private Map<String, Path> paths;

    private Swagger swagger;

    private Http.ServerBinding binding;

    private static MockHelper mock = MockHelper.getInstance();

    private SwaggerJsonNodeParser parser;

    public SwaggerMockServer(String path){
        swagger = new SwaggerParser()
                .read(path);
        paths = swagger.getPaths();
        parser = new SwaggerJsonNodeParser(swagger, mock);
    }

    public Swagger getSwagger() {
        return swagger;
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        int port = 9000;

        if(args.length < 1){
            System.err.println("should have an input spec");
            return;
        }

        if(args.length == 2){
            port = Integer.parseInt(args[1]);
        }

        // boot up server using the route as defined below
        ActorSystem system = create();

        // HttpApp.bindRoute expects a route being provided by HttpApp.createRoute
        SwaggerMockServer server = new SwaggerMockServer(args[0]);
        ServerSettings serverSettings = ServerSettings.create(system);
        server.startServer("0.0.0.0", port, serverSettings);
    }

    public Http.ServerBinding getBinding() {
        return binding;
    }

    public void setBinding(Http.ServerBinding binding) {
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
                    //Generate Path Matcher with segment
                   PathMatcher1 match = parseSegement(segments, parameters);


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
                                    } else if (property instanceof BaseIntegerProperty) {
                                        tempParam = Parameters.longValue(p.getName());
                                    } else if (property instanceof DecimalProperty) {
                                        tempParam = Parameters.doubleValue(p.getName());
                                    } else if (property instanceof BooleanProperty) {
                                        tempParam = Parameters.fromString(p.getName(), Boolean.class, str -> {
                                            if (StringUtils.isBlank(str)) {
                                                return false;
                                            } else if("1".equals(str)) {
                                                return true;
                                            } else if("0".equals(str)){
                                                return false;
                                            } else {
                                                return Boolean.parseBoolean(str);
                                            }
                                        });
//                                        tempParam = Parameters.booleanValue(p.getName());
                                    } else if (property instanceof ArrayProperty) {
                                        //here only one nest
//                                        Property nestedProperty = ((ArrayProperty) property).getItems();
                                        tempParam = Parameters.fromString(property.getName(), List.class, str -> {
                                            if (StringUtils.isBlank(str)) {
                                                return Collections.emptyList();
                                            } else {
                                                return Arrays.asList(str.split(","));
                                            }
                                        });

                                    } else {
                                        throw new RuntimeException(String.format("Property type: %s is not supported yet", property.getClass()));
                                    }

                                    if (param.getDefault() != null) {
                                        return Tuple2.apply(p.getName(), tempParam.withDefault(param.getDefault()));//
                                    } else if (!param.getRequired()) {
                                        return Tuple2.apply(p.getName(), tempParam.optional());
                                    } else {
                                        return Tuple2.apply(p.getName(), tempParam);
                                    }
                                }).collect(Collectors.toMap(tuple -> tuple._1(), tuple -> tuple._2()));


                        Response rep = op.getResponses().getOrDefault("200", op.getResponses().get("404")); //TODO check
                        Response repToBeShown = rep;
                        Route route = path(matchers, () -> route(
                                handleWith(ctx -> {
                                    Optional<Response> response = Optional.ofNullable(swagger.getResponses()).map(r -> r.values()).orElse(Collections.emptyList()).stream()
                                            .filter(reps -> (Boolean) reps.getVendorExtensions().getOrDefault("x-is-global", false))
                                            .map(reps -> {
                                                Double chance = (Double) reps.getVendorExtensions().getOrDefault("x-chance", 0.0);
                                                if (Math.random() < chance) {
                                                    return reps;
                                                }
                                                return null;
                                            }).filter(reps -> reps != null).findFirst();
                                    Property property;
                                    Integer httpCode = 200;
                                    if (response.isPresent()) {
                                        httpCode = (Integer) response.get().getVendorExtensions().getOrDefault("x-status-code", 200);
                                        property = response.get().getSchema();
                                    } else {
                                        property = repToBeShown.getSchema();
                                    }

                                    Map context = new HashMap<>();
                                    paraNamedMapping.entrySet().stream().forEach(named -> {
                                        try {
                                            context.put(named.getKey(), named.getValue().get(ctx));
                                        } catch (Exception ex) {
                                            log.error(ex.getMessage());
                                        }
                                    });
                                    log.debug("CTX: {}", context);
                                    final HttpResponse httpResponse = HttpResponse.create()
                                            .addHeader(AccessControlAllowOrigin.create(HttpOriginRanges.ALL))
                                            .withEntity(ContentTypes.APPLICATION_JSON, fromPropertyToString(property, context))//tu.mumu.mock.MockHelper
                                            .withStatus(from(httpCode));
                                    return complete(httpResponse);
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


    private StatusCode from(Integer httpCode){
       return  StatusCodes.lookup(httpCode).orElse(StatusCodes.OK);
    }

    //TODO
    //current only support int and String
    private PathMatcher1 fromProperty(Property property){
        if(property instanceof IntegerProperty){
            return PathMatchers.integerSegment();
        }
        return PathMatchers.segment(); //dummy one
    }


    private PathMatcher1 parseSegement(String[] segments, Set<Parameter> parameters){
        PathMatcher1 matcher = Stream.of(segments).filter(StringUtils::isNotBlank).map(s -> {
            String segment = s;
            if (segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}') {
                segment = segment.substring(1, segment.length() - 1);
                //find the type of this name
                String pathSeg = segment;
                Parameter parameter = parameters.stream()
                        .filter(p -> "path".equals(p.getIn()) && pathSeg.equals(p.getName())                        )
                        .findFirst().orElseThrow(() -> {
                            log.error("seg:{}, pathVars:{}", new Object[]{pathSeg, parameters});
                            return new RuntimeException("Format error");
                        });//must have one

                PathParameter pathParameter = (PathParameter) parameter;
                Property property = PropertyBuilder.build(pathParameter.getType(), pathParameter.getFormat(), null);
                return fromProperty(property);
            }
            /**
             * make it as a PathMatcher1
             */
            return PathMatchers.segment(Pattern.compile(segment));

        }).collect(Collectors.reducing((a,b) -> {
            a.concat(b).concat("b").concat("b").con
        })).get();

        return matcher;
    }


    /**
     *
     * @param obj
     * @param context
     * @return
     */
    private Long getOrFromContext(Object obj, Map context){

        if(obj == null) {
            return null;
        }

        if(obj instanceof String){
            if(((String) obj).startsWith("$")){
                String paramName = ((String) obj).substring(1);
                if(context.get(paramName) != null){
                    return (Long) context.get(paramName);
                }
            }
        } else {
            return Long.valueOf(obj.toString());
        }

        throw new RuntimeException("Format Error " + obj.getClass() + ":" + obj); //TODO
    }

    public String fromPropertyToString(Property property, Map context){
        String typeName = (String) property.getVendorExtensions().getOrDefault("x-yod-name", "default");
        if(property instanceof ArrayProperty) {
            Long size;
            Long page;
            Long max;
            try {
                Map arraySetting = (Map) property.getVendorExtensions().getOrDefault("x-yod-array", Collections.emptyMap());

                //get size
                Object sizeObj = arraySetting.getOrDefault("size", 10l);//default 10
                size = getOrFromContext(sizeObj, context);

                //get max
                Object maxObj = arraySetting.get("max");
                max = getOrFromContext(maxObj, context);

                Object pageObj = arraySetting.get("page");
                page = getOrFromContext(pageObj, context);

            }catch(Exception ex){
                throw new RuntimeException(ex.getMessage());
            }

            if(page == null) page = 0l; //default
            if(max == null) max = (page + 1)*size;//default

            ArrayNode node = new ArrayNode(JsonNodeFactory.instance);

            List<JsonNode> jsonNodes = LongStream.range(page * size, Math.min(max, (page + 1) * size)).mapToObj(i -> {
                return parser.toJsonNode(((ArrayProperty) property).getItems(), typeName);
            }).collect(Collectors.toList());

            return node.addAll(jsonNodes).toString();
        }else {
            return parser.toJsonNode(property, typeName).toString();
        }
    }

    /**
     * add base path
     * @param routes
     * @return
     */
    private List<Route> wrapBasePath(List<Route> routes){
        if(swagger.getBasePath() != null) {
//            Object[] matchers = Stream.of(swagger.getBasePath().split("/"))
//                    .filter(StringUtils::isNotBlank).map( s -> PathMatchers.segment(s)).collect(Collectors.toList()).toArray();
            Route baseWelcome = pathSingleSlash(() -> complete("base"));
            List<Route> wrappedRoute = new LinkedList<>();
            wrappedRoute.add(baseWelcome);
            wrappedRoute.addAll(routes);

            Route wrappedRouteWithBase = pathPrefix(swagger.getBasePath(), () -> route(
                    wrappedRoute.toArray(new Route[]{})
            ));
            return Arrays.asList(wrappedRouteWithBase);
        }else{
            return routes;
        }
    }


    final RejectionHandler rejectionHandler = RejectionHandler.newBuilder()
            .handleAll(MethodRejection.class, rejs -> {
                List<akka.http.javadsl.model.HttpMethod> methods
                        = rejs.stream().map(rej -> rej.supported()).collect(Collectors.toList());
                return options(() ->complete("abc"));
            })
            .build();

    @Override
    protected Route route() {
        Route welcome =  pathSingleSlash(() -> complete("swagger mock server"));
        List<Route> routes =  wrapBasePath(toRoute(paths));
        List<Route> routeList = new LinkedList<>();
        routeList.add(welcome);
        routeList.addAll(routes);
        return
                route(
                        routeList.toArray(new Route[]{})
                );
    }
}
