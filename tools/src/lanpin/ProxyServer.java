package lanpin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 固定端口 -> 当前局域网端口的 TCP 转发。
 *
 * 纯字节转发，不解析协议，所以对任何 MC 版本（甚至任何 TCP 服务）都成立。
 */
public final class ProxyServer {
    private final PortState state;
    private final PortScanner scanner;
    private final String bind;
    private final int localPort;
    private ServerSocket server;
    private Thread acceptThread;

    public ProxyServer(PortState state, PortScanner scanner, String bind, int localPort) {
        this.state = state;
        this.scanner = scanner;
        this.bind = bind;
        this.localPort = localPort;
    }

    public void start() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(bind, localPort), 100);

        acceptThread = new Thread(new Runnable() {
            public void run() {
                acceptLoop();
            }
        }, "proxy-accept");
        acceptThread.setDaemon(false);
        acceptThread.start();

        Log.info("[就绪] 固定入口已监听 " + bind + ":" + localPort);
    }

    public void await() {
        try {
            if (acceptThread != null) acceptThread.join();
        } catch (InterruptedException ignore) {}
    }

    private void acceptLoop() {
        while (true) {
            Socket client;
            try {
                client = server.accept();
            } catch (IOException e) {
                Log.info("[错误] 监听中断: " + e.getMessage());
                return;
            }
            Thread t = new Thread(new Handler(client), "conn-" + client.getPort());
            t.setDaemon(true);
            t.start();
        }
    }

    private final class Handler implements Runnable {
        private final Socket client;

        Handler(Socket client) {
            this.client = client;
        }

        public void run() {
            String who = client.getInetAddress().getHostAddress() + ":" + client.getPort();
            Socket upstream = null;
            int used = -1;
            try {
                int port = state.get();
                if (port <= 0) {
                    // 端口未知：一边扫一边等，别让客户端干站着
                    Thread probe = new Thread(new Runnable() {
                        public void run() {
                            int p = scanner.scan();
                            if (p > 0) state.update(p, "端口扫描");
                        }
                    }, "probe-on-demand");
                    probe.setDaemon(true);
                    probe.start();
                    port = state.waitFor(12000);
                }
                if (port <= 0) {
                    Log.info("[跳过] " + who + " 想连接，但还没发现游戏端口。请先在游戏里『对局域网开放』。");
                    return;
                }
                upstream = connect(port);
                if (upstream == null) {
                    Log.info("[重试] 端口 " + port + " 连不上，重新探测一次…");
                    int again = scanner.scan();
                    if (again > 0 && again != port) {
                        port = again;
                        upstream = connect(port);
                    }
                }
                if (upstream == null) {
                    Log.info("[失败] 无法连接 127.0.0.1:" + port + "（游戏里的局域网世界是否已关闭？）");
                    return;
                }
                used = port;
                Log.info("[转发] " + who + " -> 127.0.0.1:" + used);
                pump(client, upstream);
            } catch (Exception e) {
                Log.info("[异常] " + who + ": " + e.getMessage());
            } finally {
                closeQuietly(upstream);
                closeQuietly(client);
                if (used > 0) Log.info("[断开] " + who);
            }
        }
    }

    private Socket connect(int port) {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress("127.0.0.1", port), 2500);
            return s;
        } catch (Exception e) {
            closeQuietly(s);
            return null;
        }
    }

    private void pump(final Socket a, final Socket b) throws Exception {
        Thread up = new Thread(new Runnable() {
            public void run() {
                copy(a, b);
                try { b.shutdownOutput(); } catch (Exception ignore) {}
            }
        }, "pump-up");
        up.setDaemon(true);
        up.start();
        copy(b, a);
        try { a.shutdownOutput(); } catch (Exception ignore) {}
        up.join(5000);
    }

    private void copy(Socket from, Socket to) {
        byte[] buf = new byte[16384];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception ignore) {
        }
    }

    private void closeQuietly(Socket s) {
        if (s == null) return;
        try { s.close(); } catch (Exception ignore) {}
    }
}
