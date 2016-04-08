import akka.http.javadsl.server.values.Parameter;
import akka.http.javadsl.server.values.Parameters;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Created by luye on 2016/4/8.
 */
public class HttpTest {

    @Test
    public void test(){
        Parameter para = Parameters.floatValue("abc");

        List<Map> mapping = Stream.of(1, 2, 3, 4, 5).map( i -> {
            Map a = new HashMap();
            return a;
        }).collect(Collectors.toList());
    }
}
