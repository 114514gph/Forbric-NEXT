package net.neoforbric.mixin.hook;

/**
 * 测试 mixin 的钩子。
 *
 * <p><b>必须放在 mixin 配置 package（{@code net.neoforbric.mixin.testmod}）之外</b>：
 * Sponge Mixin 会把 mixin 包下的所有类视为"mixin 私有"，禁止被织入后的目标类
 * （这里是 vanilla MinecraftServer）直接引用。这是真实验证时踩的第一个坑——
 * 织入本身成功了，但回调类放错包导致 IllegalClassLoadError。
 *
 * <p>被织进 {@code net.minecraft.server.MinecraftServer} 的代码回调这里，
 * 打一条带唯一标记的日志。
 */
public final class ForbricHooks {

	/** 唯一标记串：在 server log 里 grep 这个串即可证明 mixin 真的跑了。 */
	public static final String MARKER = "FORBRIC-MIXIN-APPLIED";

	private ForbricHooks() {}

	/** 由织入进 MinecraftServer 构造器的代码调用。 */
	public static void onServerConstructed() {
		System.out.println("[ForbricTestMixin] >>> " + MARKER
				+ " net.minecraft.server.MinecraftServer 构造器已被织入并回调");
	}
}
