import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import java.lang.reflect.Field;
import java.net.InetAddress;

public class TestSession {
    public static void main(String[] args) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) f.get(null);
        Object svc = u.allocateInstance(YggdrasilMinecraftSessionService.class);

        GameProfile p = new GameProfile(null, "ukyo");
        Object r = svc.getClass()
                .getMethod("hasJoinedServer", GameProfile.class, String.class, InetAddress.class)
                .invoke(svc, p, "server-id-123", null);
        System.out.println("hasJoinedServer -> " + (r == null ? "null" : ((GameProfile) r).getName() + " / " + ((GameProfile) r).getId()));

        svc.getClass()
                .getMethod("joinServer", GameProfile.class, String.class, String.class)
                .invoke(svc, p, "fake-token", "server-id-123");
        System.out.println("joinServer -> returned without exception");
    }
}
