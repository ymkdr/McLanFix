package lanpin;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 监听 Minecraft "对局域网开放" 时发出的 UDP 广播。
 *
 * 游戏把这条报文发到 224.0.2.60:4445，内容形如:
 *   [MOTD]某某的世界[/MOTD][AD]54321[/AD]
 * 其中 AD 就是当期集成服务器真实监听的 TCP 端口。
 *
 * 这条报文在局域网开放期间大约每 1.5 秒重发一次，所以端口一变我们马上就能知道。
 */
public final class MulticastSniffer extends Thread {
    private static final String GROUP = "224.0.2.60";
    private static final int PORT = 4445;
    private static final Pattern AD = Pattern.compile("\\[AD\\](\\d{1,5})\\[/AD\\]");
    private static final Pattern MOTD = Pattern.compile("\\[MOTD\\](.*?)\\[/MOTD\\]");

    private final PortState state;

    public MulticastSniffer(PortState state) {
        super("lan-multicast");
        setDaemon(true);
        this.state = state;
    }

    public void run() {
        MulticastSocket sock = null;
        try {
            sock = new MulticastSocket(null);
            sock.setReuseAddress(true);
            sock.bind(new InetSocketAddress(PORT));
            sock.setSoTimeout(2000);
            sock.setTimeToLive(1);

            InetAddress group = InetAddress.getByName(GROUP);
            InetSocketAddress addr = new InetSocketAddress(group, PORT);
            boolean joined = false;

            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                try {
                    if (ni.isUp() && ni.supportsMulticast()) {
                        sock.joinGroup(addr, ni);
                        joined = true;
                    }
                } catch (Exception ignore) {
                    // 某些虚拟网卡（VPN/虚拟机）加入会失败，忽略即可
                }
            }
            try {
                sock.joinGroup(group);
                joined = true;
            } catch (Exception ignore) {}

            if (!joined) {
                Log.info("[提示] 无法加入广播组，改用端口扫描探测。");
                return;
            }

            byte[] buf = new byte[1500];
            while (!isInterrupted()) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                try {
                    sock.receive(pkt);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    break;
                }
                String msg = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8");
                Matcher m = AD.matcher(msg);
                if (!m.find()) continue;
                int port;
                try {
                    port = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    continue;
                }
                String motd = "";
                Matcher mm = MOTD.matcher(msg);
                if (mm.find()) motd = mm.group(1).trim();
                state.update(port, motd.length() > 0 ? "广播: " + motd : "UDP 广播");
            }
        } catch (Exception e) {
            Log.info("[提示] UDP 广播监听不可用（" + e.getMessage() + "），改用端口扫描探测。");
        } finally {
            if (sock != null) {
                try { sock.close(); } catch (Exception ignore) {}
            }
        }
    }
}
