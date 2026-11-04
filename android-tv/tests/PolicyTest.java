import com.limelight.tv.TvPolicy;
public class PolicyTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        check(TvPolicy.Profile.BALANCED.kbps == 50000);
        check(TvPolicy.Profile.LOW_LATENCY.pacing.equals("latency"));
        check(TvPolicy.lowerBitrate(50000) == 40000);
        check(TvPolicy.lowerBitrate(10000) == 10000);
        check(TvPolicy.lowerBitrate(-1) == -1);
        check(!TvPolicy.canReduce(5, 20000, 3, 4, 60));
        check(!TvPolicy.canReduce(8, 14000, 3, 4, 60));
        check(!TvPolicy.canReduce(8, 20000, Double.NaN, 4, 60));
        check(!TvPolicy.canReduce(8, 20000, 101, 4, 60));
        check(TvPolicy.canReduce(8, 20000, 3, 4, 60));
        check(!TvPolicy.canReduce(8, 20000, 0, 4, 60));
        check(TvPolicy.guidance(8, 20000, 0, 25, 60).contains("Decoder"));
        check(TvPolicy.guidance(8, 20000, 3, 4, 60).contains("frame loss"));
        check(TvPolicy.guidance(8, 20000, 0, 4, 60).contains("No sustained"));
        System.out.println("14 TV policy checks passed");
    }
}
