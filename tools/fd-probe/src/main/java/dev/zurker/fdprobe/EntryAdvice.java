package dev.zurker.fdprobe;

import net.bytebuddy.asm.Advice;

/**
 * 方法入口 advice：字节码会被内联进被插桩方法。
 * 体内只能引用 bootstrap 可见的类（CaptureSink 经 appendToBootstrapClassLoaderSearch 提供）。
 */
public class EntryAdvice {

    @Advice.OnMethodEnter
    public static void enter(@Advice.Origin("#t#m") String sig, @Advice.AllArguments Object[] args) {
        dev.zurker.fdprobe.sink.CaptureSink.enter(sig, args);
    }
}
