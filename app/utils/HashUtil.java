package utils;

/**
 * Created by Le'novo on 2017/7/2.
 */
public class HashUtil {
    public static int toInt(String s) {
        //return s.hashCode() & 0x7fffffff;
        return s.hashCode();
    }
}
