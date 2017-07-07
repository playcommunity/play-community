package utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;

/**
 * Created by joymufeng on 2017/7/2.
 */
public class HashUtil {
    public static int toInt(String s) {
        //return s.hashCode() & 0x7fffffff;
        return s.hashCode();
    }

    public static String md5(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digester = MessageDigest.getInstance("MD5");
        digester.update(s.getBytes("UTF-8"));
        return Hex.encodeHexString(digester.digest());
    }

    public static String sha256(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digester = MessageDigest.getInstance("SHA-256");
        digester.update(s.getBytes("UTF-8"));
        return Hex.encodeHexString(digester.digest());
    }
}
