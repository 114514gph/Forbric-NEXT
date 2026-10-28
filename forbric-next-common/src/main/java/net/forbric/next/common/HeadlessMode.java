package net.forbric.next.common;

/**
 * 无头（headless）环境判定。
 *
 * <p>真实 26.3 无头服务端验证时，启动脚本会传 {@code -Dforbric.headless=true}；
 * 即使没传，容器/SSH 下 {@link java.awt.GraphicsEnvironment#isHeadless()} 也会返回 true。
 *
 * <p><b>为什么单独抽一个类：</b>{@link FabricEntrypointExecutor} / {@code FabricModLoader}
 * 都要在执行 {@code client} entrypoint 组之前问一句"现在到底有没有显示器"，
 * 判定逻辑集中在这里，避免两边各写一遍、判定结果不一致。
 *
 * <p>判定优先级：
 * <ol>
 *   <li>系统属性 {@code forbric.headless=true}（启动脚本显式声明，最高优先）。</li>
 *   <li>{@link java.awt.GraphicsEnvironment#isHeadless()}：JVM 认为无显示器/无图形环境。</li>
 *   <li>安全兜底：awt 类都加载不出来（极端精简 JRE）时，按 headless 处理——
 *       没有显示器还硬跑客户端 entrypoint 只会崩。</li>
 * </ol>
 */
public final class HeadlessMode {

	private HeadlessMode() {}

	/**
	 * 当前是否运行在无头环境。
	 *
	 * @return true = 没有可用显示器，client entrypoint / 客户端相关初始化必须跳过
	 */
	public static boolean isHeadless() {
		// 1. 启动脚本显式声明
		if (Boolean.getBoolean("forbric.headless")) {
			return true;
		}
		// 2. 问 awt；awt 不可用时按 headless 处理
		try {
			return java.awt.GraphicsEnvironment.isHeadless();
		} catch (Throwable t) {
			// 极端 JRE 连 java.awt 都没有 → 必然无头
			return true;
		}
	}
}
