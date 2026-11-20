package lanfix;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * McLanFix —— 运行时改写 authlib 的会话验证，让"离线账号"也能加入局域网世界。
 *
 * 不需要 Forge / Fabric / 任何 mod：它工作在 JVM 层，Minecraft 一启动就把
 * com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService 的两个方法换掉：
 *
 *   joinServer(...)      -> 直接返回（客户端不再向 Mojang 发验证请求）
 *   hasJoinedServer(...) -> 直接返回玩家档案（服务端认为验证通过）
 *
 * 因为 authlib 是 Mojang 的库、从不混淆，这个补丁对 1.7 ~ 1.21 的所有版本通用。
 */
public final class Agent {

    /** 版本号与官方地址：任何二次打包都改不掉这行日志，便于用户辨认来源。 */
    public static final String VERSION = "1.0.1";
    public static final String HOMEPAGE = "https://github.com/ymkdr/McLanFix";

    public static void premain(String args, Instrumentation inst) {
        System.out.println("[McLanFix] v" + VERSION + " 官方仓库 " + HOMEPAGE);
        System.out.println("[McLanFix] 已加载，正在关闭正版会话验证（离线联机模式）");
        inst.addTransformer(new SessionTransformer(), true);
    }

    private static final class SessionTransformer implements ClassFileTransformer {
        public byte[] transform(ClassLoader loader, String name, Class<?> clazz,
                                ProtectionDomain pd, byte[] bytes) {
            if (name == null) return null;
            if (!name.equals("com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService")
                    && !name.equals("com/mojang/authlib/minecraft/HttpMinecraftSessionService")) {
                return null;
            }
            try {
                ClassReader cr = new ClassReader(bytes);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                cr.accept(new Patcher(cw), 0);
                System.out.println("[McLanFix] 已补丁 " + name.replace('/', '.'));
                return cw.toByteArray();
            } catch (Throwable t) {
                System.out.println("[McLanFix] 补丁失败 " + name + ": " + t);
                return null;
            }
        }
    }

    private static final class Patcher extends ClassVisitor {
        Patcher(ClassVisitor cv) { super(Opcodes.ASM9, cv); }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (mv == null) return null;

            if ("joinServer".equals(name) && desc.startsWith("(Lcom/mojang/authlib/GameProfile;")) {
                System.out.println("[McLanFix]   替换 joinServer" + desc);
                return new Replace(mv) {
                    void emit(MethodVisitor t) {
                        t.visitInsn(Opcodes.RETURN);
                    }
                };
            }
            if ("hasJoinedServer".equals(name) && desc.endsWith(")Lcom/mojang/authlib/GameProfile;")) {
                System.out.println("[McLanFix]   替换 hasJoinedServer" + desc);
                final boolean profileArg = desc.startsWith("(Lcom/mojang/authlib/GameProfile;");
                return new Replace(mv) {
                    void emit(MethodVisitor t) {
                        if (profileArg) {
                            t.visitVarInsn(Opcodes.ALOAD, 1);   // profile
                            t.visitInsn(Opcodes.ARETURN);
                        } else {
                            // 老签名：hasJoinedServer(String name, String serverId, ...)
                            String gp = "com/mojang/authlib/GameProfile";
                            t.visitTypeInsn(Opcodes.NEW, gp);
                            t.visitInsn(Opcodes.DUP);
                            t.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
                            t.visitInsn(Opcodes.DUP);
                            t.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
                                    "<init>", "()V", false);
                            t.visitLdcInsn("OfflinePlayer:");
                            t.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                                    "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                            t.visitVarInsn(Opcodes.ALOAD, 1);
                            t.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                                    "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                            t.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                                    "toString", "()Ljava/lang/String;", false);
                            t.visitFieldInsn(Opcodes.GETSTATIC, "java/nio/charset/StandardCharsets",
                                    "UTF_8", "Ljava/nio/charset/Charset;");
                            t.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String",
                                    "getBytes", "(Ljava/nio/charset/Charset;)[B", false);
                            t.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/UUID",
                                    "nameUUIDFromBytes", "([B)Ljava/util/UUID;", false);
                            t.visitVarInsn(Opcodes.ALOAD, 1);
                            t.visitMethodInsn(Opcodes.INVOKESPECIAL, gp, "<init>",
                                    "(Ljava/util/UUID;Ljava/lang/String;)V", false);
                            t.visitInsn(Opcodes.ARETURN);
                        }
                    }
                };
            }
            return mv;
        }
    }

    /** 丢掉原方法体，只执行 emit() 生成的新指令。 */
    private abstract static class Replace extends MethodVisitor {
        private final MethodVisitor target;
        private boolean done;

        Replace(MethodVisitor target) {
            super(Opcodes.ASM9);
            this.target = target;
        }

        abstract void emit(MethodVisitor t);

        @Override
        public void visitCode() {
            if (done) return;
            done = true;
            target.visitCode();
            emit(target);
            target.visitMaxs(0, 0);
        }

        @Override
        public void visitEnd() {
            target.visitEnd();
        }
    }
}
