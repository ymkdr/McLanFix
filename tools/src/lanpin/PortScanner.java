package lanpin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 备用探测方式：找出本机 java 进程正在监听的端口，再用 Minecraft ping 确认。
 * 用于 UDP 广播被防火墙/虚拟网卡吞掉的情况。
 */
public final class PortScanner {
    private final boolean scanAll;
    private final int excludePort;
    private final java.util.concurrent.atomic.AtomicBoolean busy =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public PortScanner(boolean scanAll, int excludePort) {
        this.scanAll = scanAll;
        this.excludePort = excludePort;
    }

    public int scan() {
        if (!busy.compareAndSet(false, true)) return -1;   // 已有扫描在跑，别重复扫
        try {
            return doScan();
        } finally {
            busy.set(false);
        }
    }

    private int doScan() {
        List<Integer> ports = candidates();
        if (ports.isEmpty()) {
            if (!scanAll) Log.info("[扫描] 没找到正在监听的 Java 进程（游戏开了局域网吗？）");
            else Log.info("[扫描] 没找到可疑端口。");
            return -1;
        }
        // 高位端口优先（MC 集成服务器挑的随机端口都在临时端口段），能更快命中
        java.util.Collections.sort(ports, java.util.Collections.reverseOrder());

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, ports.size()));
        List<Future<String[]>> futures = new ArrayList<Future<String[]>>();
        for (final Integer p : ports) {
            futures.add(pool.submit(new Callable<String[]>() {
                public String[] call() {
                    String v = McPing.version(p, 500);
                    return v == null ? null : new String[] { String.valueOf(p), v };
                }
            }));
        }
        pool.shutdown();

        int found = -1;
        try {
            for (Future<String[]> f : futures) {
                String[] r;
                try {
                    r = f.get(3, TimeUnit.SECONDS);
                } catch (Exception ignore) {
                    continue;
                }
                if (r != null) {
                    found = Integer.parseInt(r[0]);
                    Log.info("[扫描] 端口 " + r[0] + " 响应了 Minecraft 握手（版本 " + r[1] + "）");
                    break;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return found;
    }

    private List<Integer> candidates() {
        Set<Integer> javaPids = scanAll ? null : javaPids();
        Map<Integer, Integer> ports = listeningPorts();
        List<Integer> out = new ArrayList<Integer>();
        for (Map.Entry<Integer, Integer> e : ports.entrySet()) {
            int port = e.getKey();
            int pid = e.getValue();
            if (port == excludePort) continue;
            if (javaPids != null && !javaPids.contains(pid)) continue;
            out.add(port);
        }
        return out;
    }

    private Set<Integer> javaPids() {
        Set<Integer> pids = new HashSet<Integer>();
        for (String img : new String[] { "javaw.exe", "java.exe" }) {
            try {
                Process p = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq " + img, "/FO", "CSV", "/NH")
                        .redirectErrorStream(true).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.indexOf('"') < 0) continue;
                    String[] parts = line.split("\",\"");
                    if (parts.length >= 2) {
                        try {
                            pids.add(Integer.parseInt(parts[1].trim()));
                        } catch (NumberFormatException ignore) {}
                    }
                }
                r.close();
                p.waitFor();
            } catch (Exception ignore) {}
        }
        return pids;
    }

    private Map<Integer, Integer> listeningPorts() {
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        try {
            Process p = new ProcessBuilder("netstat", "-ano", "-p", "tcp").redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 5) continue;
                if (!"TCP".equalsIgnoreCase(parts[0])) continue;
                if (!"LISTENING".equalsIgnoreCase(parts[3])) continue;
                String local = parts[1];
                int idx = local.lastIndexOf(':');
                if (idx < 0) continue;
                int port;
                try {
                    port = Integer.parseInt(local.substring(idx + 1));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (port < 1024 || port > 65535 || port == 4445) continue;
                int pid;
                try {
                    pid = Integer.parseInt(parts[4]);
                } catch (NumberFormatException e) {
                    continue;
                }
                map.put(port, pid);
            }
            r.close();
            p.waitFor();
        } catch (Exception ignore) {}
        return map;
    }
}
