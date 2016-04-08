import org.junit.Test;

/**
 * Created by luye on 2016/4/7.
 */
public class YodTest {

    @Test
    public void testEval(){
        System.out.println(MockHelper.getInstance().eval("@Int()"));
    }
}
