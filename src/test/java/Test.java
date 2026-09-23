import java.math.BigDecimal;
import java.util.Arrays;

public class Test {
    @org.junit.Test
    public void test() {
        String[] s = new String[] {
                "1.16544128E8",
                "9.4435674E7",
                "1.45739967E8",
                "9.5055565E7",
                "1.4475547E8",
                "1.10957045E8",
                "8.4296636E7",
                "6.9526088E7",
                "1.35088591E8",
                "1.17448293E8",
                "2.32261643E8",
                "2.4024385E7",
                "2.6270145E7",
                "5468681.0",
                "1.089284E7",
                "4297016.0"
        };

        Arrays.stream(s).map(BigDecimal::new).forEach(b -> System.out.println(b.toPlainString()));

        Arrays.stream(s).map(BigDecimal::new).forEach(System.out::println);
    }
}
