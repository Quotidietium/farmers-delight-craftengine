package dev.zurker.fdprobe.sink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 捕获接收器：被追加进 bootstrap classloader。
 * 仅允许使用 JDK 类型 —— advice 内联代码在【被插桩类】的加载器里解析本类。
 * 所有状态仅存于 bootstrap 载入的这一个副本；插件侧通过 Class.forName(name, false, null) 反射访问。
 */
public final class CaptureSink {

    public static volatile boolean ARMED = false;
    public static volatile long LINES = 0;
    public static volatile long DROPPED = 0;
    public static volatile long THREAD_ID = -1;

    private static final StringBuilder BUF = new StringBuilder(1 << 15);
    private static Path OUT;

    static {
        String p = System.getProperty("fdprobe.sink.file");
        if (p != null) {
            try {
                OUT = Paths.get(p);
                Files.createDirectories(OUT.getParent());
                Files.writeString(OUT, "# fdprobe capture sink start " + java.time.LocalDateTime.now() + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                OUT = null;
            }
        }
    }

    private CaptureSink() {}

    /** advice 内联入口。任何异常必须吞掉，绝不能影响被插桩对象。 */
    public static void enter(String sig, Object[] args) {
        if (!ARMED) return;
        try {
            // Bukkit 配置类的调用必须来自 PapersDelight（栈内含 NMSL_ 帧）才记录
            if (sig.startsWith("org.bukkit.configuration")) {
                StackTraceElement[] st = Thread.currentThread().getStackTrace();
                boolean from = false;
                for (int i = 2; i < st.length && i < 26; i++) {
                    String cn = st[i].getClassName();
                    if (cn.startsWith("NMSL_") || cn.startsWith("cn.dg32z")) { from = true; break; }
                    // CE/其它插件的配置读取一旦越层就不再继续(它们的调用栈不含 NMSL_)
                    if (cn.startsWith("net.momirealms")) return;
                }
                if (!from) return;
            }
            StringBuilder sb = new StringBuilder(192);
            sb.append('[').append(Thread.currentThread().getId()).append(']')
              .append('[').append(System.nanoTime()).append("] ")
              .append(sig);
            if (args != null && args.length > 0) {
                sb.append('(');
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(',');
                    Object a = args[i];
                    if (a == null) { sb.append("null"); continue; }
                    String cn = a.getClass().getName();
                    if (cn.startsWith("kotlin.coroutines") || cn.contains("Continuation")
                            || cn.startsWith("java.util.concurrent.") || cn.startsWith("kotlin.jvm.")) {
                        sb.append(cn.substring(cn.lastIndexOf('.') + 1));
                        continue;
                    }
                    String s;
                    try { s = String.valueOf(a); } catch (Throwable t) { s = "<toString!>"; }
                    if (s.length() > 160) s = s.substring(0, 160) + "…";
                    sb.append(s.replace('\n', ' ').replace('\r', ' '));
                }
                sb.append(')');
            }
            synchronized (BUF) {
                if (BUF.length() > (1 << 18)) { DROPPED++; return; } // 防洪
                BUF.append(sb).append('\n');
                LINES++;
                if (BUF.length() > (1 << 15)) flushLocked();
            }
        } catch (Throwable ignored) {
            DROPPED++;
        }
    }

    public static void flush() {
        synchronized (BUF) { flushLocked(); }
    }

    private static void flushLocked() {
        if (OUT == null || BUF.length() == 0) return;
        try {
            Files.writeString(OUT, BUF.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
        BUF.setLength(0);
    }
}
