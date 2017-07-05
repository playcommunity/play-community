package utils;

import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Created by Le'novo on 2017/7/3.
 */
public class RoaringBitmapUtil {
    public static String toBase64String(RoaringBitmap bitmap) throws IOException {
        bitmap.runOptimize();
        ByteBuffer buffer = ByteBuffer.allocate(bitmap.serializedSizeInBytes());
        bitmap.serialize(new DataOutputStream(new OutputStream(){
            ByteBuffer mBB;
            OutputStream init(ByteBuffer mbb) { mBB = mbb; return this; }
            public void close() {}
            public void flush() {}
            public void write(int b) { mBB.put((byte) b); }
            public void write(byte[] b) { mBB.put(b); }
            public void write(byte[] b, int off, int l) { mBB.put(b,off,l); }
        }.init(buffer)));

        buffer.flip();
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public static RoaringBitmap fromBase64String(String base64) {
        if (base64 != null && !base64.trim().equals("")) {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(base64.trim()));
            return new RoaringBitmap(new ImmutableRoaringBitmap(buffer));
        } else {
            return new RoaringBitmap();
        }
    }
}
