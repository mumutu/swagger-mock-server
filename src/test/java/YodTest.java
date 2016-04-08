import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

/**
 * Created by luye on 2016/4/7.
 */
public class YodTest {

    @Test
    public void testEval(){
        System.out.println(MockHelper.getInstance().eval("@Int()"));
    }

    @Test
    public void testStringEval(){

        System.out.println(MockHelper.getInstance().eval("@String"));
    }

    @Test
    public void testMultiStringEval() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(200);


        long before = System.currentTimeMillis();
        MockHelper helper = MockHelper.getInstance();
        IntStream.range(0, 200).forEach(i -> {
            Thread thread = new Thread(() -> {
                System.out.println("value:" + helper.eval("@Str()"));
                latch.countDown();
            });

            thread.start();
//            try {
//                thread.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        });

        latch.await();
        System.out.println(System.currentTimeMillis()-before);

//        Thread.sleep(5000000);
    }
}
