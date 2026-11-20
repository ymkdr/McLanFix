package lanpin;

/**
 * 保存"当前游戏局域网端口"这一事实。
 * 多个探测源（UDP 广播 / 端口扫描 / 手动指定）都会往这里写。
 */
public final class PortState {
    private int port = -1;
    private String source = "";
    private long updatedAt = 0L;

    public synchronized void update(int p, String src) {
        if (p <= 0) return;
        long now = System.currentTimeMillis();
        if (p != port) {
            port = p;
            source = src;
            updatedAt = now;
            Log.info("发现局域网端口 " + p + "  (" + src + ")");
            notifyAll();
        } else {
            source = src;
            updatedAt = now;
        }
    }

    public synchronized int get() {
        return port;
    }

    public synchronized String source() {
        return source;
    }

    /** 等待一个已知端口；超时返回 -1。 */
    public synchronized int waitFor(long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (port <= 0) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return -1;
            try {
                wait(Math.min(left, 200L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        return port;
    }
}
