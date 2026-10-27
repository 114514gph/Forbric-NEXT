package net.fabricmc.loader.api;

/**
 * Fabric Loader 原版 {@code net.fabricmc.loader.api.EntrypointException} 的桩实现。
 *
 * <p><b>为什么需要这个桩类：</b>很多现成 Fabric mod（典型如 JEI）的字节码里直接
 * {@code catch (EntrypointException e)}。如果我们的运行时连这个类都没有，mod 在
 * 类链接（linkage）阶段就会抛 {@code NoClassDefFoundError}，根本走不到我们的 entrypoint
 * 执行逻辑。因此即便骨架阶段不做完整的 entrypoint 解析，也必须把这个类型补出来，
 * 且包名 / 类名 / 继承关系与 Fabric Loader 原版逐字对齐——mod 是按
 * {@code net.fabricmc.loader.api.EntrypointException} 这个全限定名引用的。
 *
 * <p>与原版一致：继承 {@link RuntimeException}，提供 {@code (String)} 与
 * {@code (String, Throwable)} 两个构造器。
 */
public class EntrypointException extends RuntimeException {

	/**
	 * @param message 描述哪一个 mod / 哪一个 entrypoint 出了问题
	 */
	public EntrypointException(String message) {
		super(message);
	}

	/**
	 * @param message 描述哪一个 mod / 哪一个 entrypoint 出了问题
	 * @param cause   真正导致 entrypoint 失败的底层异常（被包装进来，保留堆栈）
	 */
	public EntrypointException(String message, Throwable cause) {
		super(message, cause);
	}
}
