package net.forbric.next.fabric;

/**
 * 测试用的"坏路径"entrypoint 类。
 *
 * <p>故意只提供一个带参构造器，没有 public 无参构造器。
 * {@code cls.getDeclaredConstructor()} 会抛 {@link NoSuchMethodException}，
 * 验证 executor 把这种失败捕获进 {@code EntrypointFailure} 而不是直接炸出去。
 */
public class BrokenEntrypoint implements Runnable {

	/** 唯一的构造器需要一个参数 → 没有无参构造器。 */
	public BrokenEntrypoint(String requiredArg) {
	}

	@Override
	public void run() {
	}
}
