package lanpin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft 服务器列表 ping（1.7+ 的现代协议）。
 * 用来在"扫描端口"时确认某个端口真的是 MC 服务端，而不是别的程序。
 */
public final class McPing {
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private McPing() {}

    /** 成功返回版本名（例如 1.12.2），失败返回 null。 */
    public static String version(int port, int timeoutMs) {
        String modern = modernVersion(port, timeoutMs);
        if (modern != null) return modern;
        return legacyVersion(port, timeoutMs);
    }

    /** 1.7+ 的现代 ping。 */
    private static String modernVersion(int port, int timeoutMs) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            s.setTcpNoDelay(true);

            OutputStream out = s.getOutputStream();
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(hs);
            writeVarInt(d, 0x00);      // packet id: handshake
            writeVarInt(d, 340);       // protocol version（只为 ping，服务端不校验）
            writeString(d, "127.0.0.1");
            d.writeShort(port);
            writeVarInt(d, 1);         // next state: status
            writeVarInt(out, hs.size());
            out.write(hs.toByteArray());
            writeVarInt(out, 1);       // length
            writeVarInt(out, 0x00);    // status request
            out.flush();

            DataInputStream in = new DataInputStream(s.getInputStream());
            int len = readVarInt(in);
            if (len <= 0 || len > 4000000) return null;
            byte[] buf = new byte[len];
            in.readFully(buf);

            DataInputStream p = new DataInputStream(new ByteArrayInputStream(buf));
            if (readVarInt(p) != 0x00) return null;
            String json = readString(p);
            if (!json.contains("\"version\"")) return null;
            if (!json.contains("\"protocol\"") && !json.contains("\"minecraft\"")) return null;

            Matcher m = NAME.matcher(json);
            if (m.find()) return m.group(1);
            return "?";
        } catch (Exception e) {
            return null;
        } finally {
            try { s.close(); } catch (Exception ignore) {}
        }
    }

    /**
     * 1.4 ~ 1.6 的老式 ping（0xFE 0x01），给当年的老版本兜底。
     * 响应是 0xFF + 长度 + UTF-16BE 字符串，字段以 NUL 分隔，形如 "§1" NUL 协议号 NUL 版本名 NUL MOTD。
     */
    private static String legacyVersion(int port, int timeoutMs) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            OutputStream out = s.getOutputStream();
            out.write(new byte[] { (byte) 0xFE, (byte) 0x01 });
            out.flush();

            java.io.DataInputStream in = new java.io.DataInputStream(s.getInputStream());
            if (in.readUnsignedByte() != 0xFF) return null;
            int len = in.readUnsignedShort();
            if (len <= 0 || len > 8192) return null;
            byte[] buf = new byte[len * 2];
            in.readFully(buf);
            String text = new String(buf, "UTF-16BE");
            if (text.length() < 3) return null;
            if (text.charAt(0) != (char) 0xA7 || text.charAt(1) != '1') return null;
            String[] parts = text.substring(3).split(String.valueOf((char) 0));
            if (parts.length >= 2 && parts[1].length() > 0) return parts[1];
            return "old";
        } catch (Exception e) {
            return null;
        } finally {
            try { s.close(); } catch (Exception ignore) {}
        }
    }

    static void writeVarInt(OutputStream out, int value) throws Exception {
        while (true) {
            int b = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                out.write(b | 0x80);
            } else {
                out.write(b);
                return;
            }
        }
    }

    static int readVarInt(InputStream in) throws Exception {
        int value = 0;
        int position = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new java.io.EOFException();
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) return value;
            position += 7;
            if (position >= 32) throw new java.io.IOException("varint too big");
        }
    }

    static void writeString(DataOutputStream out, String s) throws Exception {
        byte[] b = s.getBytes("UTF-8");
        writeVarInt(out, b.length);
        out.write(b);
    }

    static String readString(DataInputStream in) throws Exception {
        int len = readVarInt(in);
        if (len < 0 || len > 32767 * 4) throw new java.io.IOException("bad string");
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, "UTF-8");
    }
}
