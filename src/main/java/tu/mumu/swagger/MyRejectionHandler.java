package tu.mumu.swagger;

import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.AccessControlAllowHeaders;
import akka.http.javadsl.model.headers.AccessControlAllowOrigin;
import akka.http.javadsl.model.headers.HttpOriginRange;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.RouteResult;
import akka.http.scaladsl.model.StatusCodes;

/**
 * Created by mumut on 2017/2/17.
 */
public class MyRejectionHandler extends RejectionHandler{

    @Override
    public RouteResult handleEmptyRejection(RequestContext context){
        HttpResponse response = HttpResponse.create();
        return context.complete(response.withStatus(404).withEntity("资源不存在"));
    }

    @Override
    public RouteResult handleMethodRejection(RequestContext context, HttpMethod method){
        if("OPTIONS".equalsIgnoreCase(context.request().method().name())){
            HttpResponse response = HttpResponse.create();
            return context.complete( response.withStatus(204).addHeader(AccessControlAllowOrigin.create(HttpOriginRange.ALL))
                    .addHeader(AccessControlAllowHeaders.create("Origin", "X-Requested-With", "Content-Type", "Accept")));
        }else{
            HttpResponse response = HttpResponse.create();
            return context.complete(response.withStatus(404));
        }
    }
}
