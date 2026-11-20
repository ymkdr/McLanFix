package lanpin;

public final class Main {
    public static void main(String[] args) {
        Options opt;
        try {
            opt = Options.parse(args);
        } catch (IllegalArgumentException e) {
            Log.plain("参数错误: " + e.getMessage());
            Log.plain("");
            Options.printUsage();
            System.exit(1);
            return;
        }
        if (opt.help) {
            Options.printUsage();
            return;
        }

        Log.plain("==========================================================");
        Log.plain("  McLanPin  ·  我的世界局域网固定端口转发器");
        Log.plain("  不需要装任何 mod，任何游戏版本都能用");
        Log.plain("==========================================================");
        Log.plain("  固定入口   : " + opt.bind + ":" + opt.localPort);
        Log.plain("  探测方式   : " + (opt.noMulticast ? "端口扫描" : "UDP 广播 + 端口扫描"));
        if (opt.mcPort > 0) Log.plain("  游戏端口   : " + opt.mcPort + "（手动指定）");
        Log.plain("----------------------------------------------------------");

        final PortState state = new PortState();
        if (opt.mcPort > 0) state.update(opt.mcPort, "手动指定");

        if (!opt.noMulticast) {
            new MulticastSniffer(state).start();
        }

        final PortScanner scanner = new PortScanner(opt.scanAll, opt.localPort);
        ProxyServer proxy;
        try {
            proxy = new ProxyServer(state, scanner, opt.bind, opt.localPort);
            proxy.start();
        } catch (Exception e) {
            Log.plain("[错误] 无法监听 " + opt.bind + ":" + opt.localPort + " —— " + e.getMessage());
            Log.plain("       端口可能被占用，换一个：--port 25566");
            System.exit(2);
            return;
        }

        Thread watchdog = new Thread(new Runnable() {
            public void run() {
                boolean first = true;
                while (true) {
                    try {
                        Thread.sleep(first ? 2500L : 4000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    first = false;
                    if (state.get() > 0) continue;
                    Log.info("[探测] 还没收到局域网广播，扫描本机监听端口…");
                    int p = scanner.scan();
                    if (p > 0) state.update(p, "端口扫描");
                }
            }
        }, "watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        Log.plain("");
        Log.plain("下一步:");
        Log.plain("  1) 进游戏 -> 存档 -> Esc -> 『对局域网开放』");
        Log.plain("  2) 本窗口会自动打印『发现局域网端口 xxxxx』");
        Log.plain("  3) 樱花穿透里把『本地端口』填 " + opt.localPort + "（本地IP填 127.0.0.1）");
        Log.plain("");
        Log.plain("按 Ctrl+C 退出。");
        Log.plain("");

        proxy.await();
    }
}
