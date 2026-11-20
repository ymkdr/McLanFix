package lanpin;

public final class Options {
    public int localPort = 25565;
    public String bind = "127.0.0.1";
    public int mcPort = 0;
    public boolean scanAll = false;
    public boolean noMulticast = false;
    public boolean help = false;

    public static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--help".equals(a) || "-h".equals(a) || "-?".equals(a)) {
                o.help = true;
            } else if ("--port".equals(a) || "-p".equals(a)) {
                o.localPort = parseInt(next(args, ++i, a));
            } else if ("--bind".equals(a)) {
                o.bind = next(args, ++i, a);
            } else if ("--mc-port".equals(a)) {
                o.mcPort = parseInt(next(args, ++i, a));
            } else if ("--scan-all".equals(a)) {
                o.scanAll = true;
            } else if ("--no-multicast".equals(a)) {
                o.noMulticast = true;
            } else {
                throw new IllegalArgumentException("未知参数 " + a);
            }
            if (o.localPort < 1 || o.localPort > 65535) {
                throw new IllegalArgumentException("端口范围 1-65535");
            }
        }
        return o;
    }

    private static String next(String[] args, int i, String flag) {
        if (i >= args.length) throw new IllegalArgumentException(flag + " 缺少值");
        return args[i];
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("不是数字: " + s);
        }
    }

    public static void printUsage() {
        Log.plain("用法:");
        Log.plain("  java -jar McLanPin.jar [选项]");
        Log.plain("");
        Log.plain("  -p, --port <端口>   本工具监听的固定端口（默认 25565），樱花穿透的本地端口填这个");
        Log.plain("      --bind <地址>    监听地址（默认 127.0.0.1；局域网内其它设备也要用就填 0.0.0.0）");
        Log.plain("      --mc-port <端口> 手动指定游戏端口，跳过自动探测（排错用）");
        Log.plain("      --scan-all       扫描所有进程的监听端口（默认只看 java 进程，排错用）");
        Log.plain("      --no-multicast   不监听局域网 UDP 广播，只用端口扫描（排错用）");
        Log.plain("  -h, --help           显示本帮助");
    }
}
