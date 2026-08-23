package dev.zurker.fdprobe;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JVM 层捕获：对 PapersDelight（NMSL_* / cn.dg32z.*）的类做 retransform，
 * 在所有方法入口内联 CaptureSink.enter —— 打开 ARMED 后记录完整调用流。
 */
public final class CaptureAgent {

    private static Instrumentation instrumentation;
    private static boolean installed = false;

    private CaptureAgent() {}

    public static synchronized boolean install(Path dataFolder) {
        if (installed) return true;
        try {
            instrumentation = ByteBuddyAgent.install(); // 需要 -XX:+EnableDynamicAgentLoading
        } catch (Throwable t) {
            throw new IllegalStateException("self-attach failed: " + t, t);
        }
        try {
            // 1) sink 注入 bootstrap
            Path sinkJar = dataFolder.resolve("fd-probe-sink.jar");
            try (InputStream in = CaptureAgent.class.getResourceAsStream("/fd-probe-sink.jar")) {
                if (in == null) throw new IllegalStateException("bundled sink jar missing");
                Files.copy(in, sinkJar, StandardCopyOption.REPLACE_EXISTING);
            }
            instrumentation.appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(sinkJar.toFile()));

            // 2) 对已加载 + 未来加载的 PapersDelight 类插桩（entry advice，不改格式，可 retransform）
            // org.bukkit.configuration 键值神谕（栈过滤在 sink 内做）——默认关闭：
            // 加上它后服务器曾静默退出（exit 1、无 hs_err），稳定性存疑，仅按需开启
            var target = ElementMatchers.nameStartsWith("NMSL_")
                    .or(ElementMatchers.nameStartsWith("cn.dg32z"));
            if (Boolean.getBoolean("fdprobe.oracle")) {
                target = target.or(ElementMatchers.nameStartsWith("org.bukkit.configuration."));
            }
            new AgentBuilder.Default()
                    .disableClassFormatChanges()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .ignore(ElementMatchers.none())
                    .type(target)
                    .transform((builder, type, cl, module, pd) ->
                            builder.visit(Advice.to(EntryAdvice.class)
                                    .on(ElementMatchers.isMethod()
                                            .and(ElementMatchers.not(ElementMatchers.isHashCode()))
                                            .and(ElementMatchers.not(ElementMatchers.isEquals()))
                                            .and(ElementMatchers.not(ElementMatchers.isToString()))
                                            .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
                    .installOn(instrumentation);
            installed = true;
            return true;
        } catch (Throwable t) {
            throw new IllegalStateException("install failed: " + t, t);
        }
    }

    public static boolean installed() { return installed; }

    /** 反射桥：拿到 bootstrap 里那份 CaptureSink（绕开插件类加载器，避免类双载） */
    public static Class<?> sinkClass() {
        try {
            return Class.forName("dev.zurker.fdprobe.sink.CaptureSink", false, null);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
