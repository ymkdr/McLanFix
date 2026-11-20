package lanpin;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class Log {
    private Log() {}

    public static String ts() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    public static void info(String s) {
        System.out.println("[" + ts() + "] " + s);
        System.out.flush();
    }

    public static void plain(String s) {
        System.out.println(s);
        System.out.flush();
    }
}
