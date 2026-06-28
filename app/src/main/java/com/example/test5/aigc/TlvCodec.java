package com.example.test5.aigc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** TLV 编解码，与 Web Demo utils.ts 一致 */
public final class TlvCodec {

    public static final String TYPE_FUNCTION_CALL = "tool";
    public static final String TYPE_FUNCTION_RESULT = "func";
    public static final String TYPE_SUBTITLE = "subv";

    private TlvCodec() {
    }

    public static byte[] encode(String type, String jsonValue) {
        byte[] typeBytes = padType(type);
        byte[] valueBytes = jsonValue.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(typeBytes.length + 4 + valueBytes.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(typeBytes);
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        return buffer.array();
    }

    public static ParsedTlv decode(byte[] data) {
        if (data == null || data.length < 8) {
            throw new IllegalArgumentException("TLV 数据过短");
        }
        String type = new String(data, 0, 4, StandardCharsets.US_ASCII).trim();
        int length = ((data[4] & 0xff) << 24)
                | ((data[5] & 0xff) << 16)
                | ((data[6] & 0xff) << 8)
                | (data[7] & 0xff);
        if (data.length < 8 + length) {
            throw new IllegalArgumentException("TLV 长度不匹配");
        }
        String value = new String(data, 8, length, StandardCharsets.UTF_8);
        return new ParsedTlv(type, value);
    }

    private static byte[] padType(String type) {
        byte[] bytes = new byte[4];
        byte[] raw = type.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, bytes, 0, Math.min(4, raw.length));
        return bytes;
    }

    public static final class ParsedTlv {
        public final String type;
        public final String value;

        ParsedTlv(String type, String value) {
            this.type = type;
            this.value = value;
        }
    }
}
