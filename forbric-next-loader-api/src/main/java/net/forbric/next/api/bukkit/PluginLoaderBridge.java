package net.forbric.next.api.bukkit;

import java.nio.file.Path;
import java.util.List;

/**
 * Bukkit 插件加载桥接 SPI（混合服务端用）。
 *
 * <p><b>定位：</b>这是一个<em>接口</em>，不是实现。NeoForbric 内核本身不内嵌 Bukkit 实现
 * （那会拖入整个 CraftBukkit/Spigot 依赖）。真正的桥接实现由可选的混合端模块提供
 * （参考 Mohist / Arclight 如何把 Bukkit PluginClassLoader 嫁接到 Forge 的模块系统上）。
 *
 * <p><b>什么时候被调用：</b>仅当 {@link MixedServerMode#fromSystemProperty()} 为
 * {@link MixedServerMode#FORBIC_WITH_BUKKIT} 时，服务端 bootstrap 才会去查找并安装这个桥。
 * 纯 Forbric 模式下完全不会触碰它。
 *
 * <p><b>生命周期顺序：</b>mod 体系（Forge/Fabric）先完成构造与注册，再由本桥加载并 enable
 * Bukkit 插件——避免插件在 mod 还没就绪时就调用注册表。
 */
public interface PluginLoaderBridge {

	/** 桥接实现的名字（日志/诊断用，例如 "arclight" / "mohist"）。 */
	String bridgeName();

	/**
	 * 扫描插件目录，发现待加载的 Bukkit 插件 jar。
	 *
	 * @param pluginsDir Bukkit 插件目录（通常是 {@code ./plugins}）
	 * @return 发现的插件 jar 路径
	 */
	List<Path> discoverPlugins(Path pluginsDir);

	/**
	 * 加载单个插件（读 plugin.yml、构造其插件类加载器、实例化主类）。
	 * 注意：这里只"加载"，不触发 onEnable。
	 *
	 * @param pluginJar 插件 jar 路径
	 * @throws Exception 插件加载失败
	 */
	void loadPlugin(Path pluginJar) throws Exception;

	/** 按依赖顺序 enable 所有已加载插件（调用各插件的 onEnable）。 */
	void enableAll() throws Exception;

	/** 停用所有插件（onDisable），在服务端关闭时调用。 */
	void disableAll();
}
