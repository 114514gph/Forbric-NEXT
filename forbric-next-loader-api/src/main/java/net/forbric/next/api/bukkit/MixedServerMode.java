package net.forbric.next.api.bukkit;

/**
 * 服务端运行模式（Forbric 原生 vs. Forbric + Bukkit 混合端）。
 *
 * <p>参考 Mohist / Arclight 的混合服务端架构：在一个 Minecraft 服务端进程里同时加载
 * Forge/Fabric mod 和 Bukkit/Spigot 插件。本枚举只描述"当前进程打算以哪种模式启动"，
 * 真正的插件加载由 {@link PluginLoaderBridge} 这个 SPI 完成。
 *
 * <ul>
 *   <li>{@link #PURE_FORBIC} —— 纯 Forbric 服务端（默认），不挂载任何 Bukkit 插件桥。</li>
 *   <li>{@link #FORBIC_WITH_BUKKIT} —— 混合端，在 Forbric mod 体系之上再挂一层 Bukkit 插件加载。</li>
 * </ul>
 */
public enum MixedServerMode {

	/** 纯 Forbric 服务端：只跑 mod，不加载 Bukkit 插件。 */
	PURE_FORBIC,

	/** Forbric + Bukkit 混合端：mod 与插件共存（Mohist/Arclight 架构）。 */
	FORBIC_WITH_BUKKIT;

	/** 控制服务端模式的 JVM 系统属性名：{@value}。 */
	public static final String SYSTEM_PROPERTY = "forbric.serverMode";

	/**
	 * 从 JVM 系统属性 {@value #SYSTEM_PROPERTY} 解析当前模式。
	 *
	 * <p>约定：安装器在 mixed 模式写入 {@code -Dforbric.serverMode=mixed}；
	 * 未设置或无法识别时回退到 {@link #PURE_FORBIC}（保持默认行为不变）。
	 *
	 * @return 解析出的运行模式
	 */
	public static MixedServerMode fromSystemProperty() {
		String raw = System.getProperty(SYSTEM_PROPERTY);
		if (raw == null) {
			return PURE_FORBIC;
		}
		return switch (raw.trim().toLowerCase()) {
			case "mixed", "bukkit", "arclight", "mohist" -> FORBIC_WITH_BUKKIT;
			case "pure", "forbic", "vanilla" -> PURE_FORBIC;
			default -> PURE_FORBIC;
		};
	}

	/** 是否为混合端。 */
	public boolean isMixed() {
		return this == FORBIC_WITH_BUKKIT;
	}
}
