package net.neoforbric.fabric;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用的"快乐路径"entrypoint 类。
 *
 * <p>实现了一个简单接口 {@link Runnable}，带 public 无参构造器。
 * 构造器里自增 {@link #INSTANTIATED}，用来证明反射实例化真的发生了。
 *
 * <p>必须是 top-level public 类——{@link net.neoforbric.fabricapi.language.DefaultJavaLanguageAdapter}
 * 用 {@code Class.forName} 按全限定名加载，和真实 Fabric mod 入口类的写法一致。
 */
public class HappyEntrypoint implements Runnable {

	/** 被实例化的次数；每个测试前清零。 */
	public static final AtomicInteger INSTANTIATED = new AtomicInteger(0);

	public HappyEntrypoint() {
		INSTANTIATED.incrementAndGet();
	}

	@Override
	public void run() {
		// 骨架阶段 executor 不调用 run()，只做实例化；这里空实现即可
	}
}
