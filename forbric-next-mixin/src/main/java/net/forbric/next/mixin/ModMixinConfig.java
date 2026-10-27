package net.forbric.next.mixin;

import java.util.List;

/**
 * 一个 mod jar 里 {@code xxx.mixins.json} 解析后的结构化描述。
 *
 * <p>字段对应 Fabric/Sponge mixin 配置文件的标准键：
 * <ul>
 *   <li>{@code package} — mixin 类所在包（相对 mod jar 根）。</li>
 *   <li>{@code mixins} — 通用 mixin（端无关，server/client 都加载）。</li>
 *   <li>{@code client} — 仅客户端加载的 mixin。</li>
 *   <li>{@code server} — 仅服务端加载的 mixin。</li>
 *   <li>{@code plugin} — mixin 配置插件类（可空）。</li>
 *   <li>{@code refmap} — 混淆映射文件（可空，dev 环境可没有）。</li>
 *   <li>{@code compatibilityLevel} — 目标字节码级别，如 {@code JAVA_21}。</li>
 * </ul>
 *
 * <p>这是个纯数据 record，不持有任何 classloader 引用，方便单测。
 *
 * @param configName        配置文件名（如 {@code forbric.test.mixins.json}），
 *                          也是注册进 MixinEnvironment 时用的 key
 * @param modJarPath        这个配置来自哪个 mod jar（打日志/排错用）
 * @param mixinPackage      mixin 类根包
 * @param commonMixins      端无关 mixin 简单类名列表
 * @param clientMixins      仅客户端 mixin 简单类名列表
 * @param serverMixins      仅服务端 mixin 简单类名列表
 * @param pluginClassName   mixin 插件类全限定名，可空
 * @param refmapName        refmap 文件名，可空
 * @param compatibilityLevel 字节码兼容级别字符串，可空（由环境兜底）
 */
public record ModMixinConfig(
		String configName,
		String modJarPath,
		String mixinPackage,
		List<String> commonMixins,
		List<String> clientMixins,
		List<String> serverMixins,
		String pluginClassName,
		String refmapName,
		String compatibilityLevel
) {
	/**
	 * 当前运行在 dedicated server 上时，实际要加载的 mixin 列表 = common + server。
	 *
	 * @return 本端要织入的 mixin 简单类名（不可变）
	 */
	public List<String> activeMixinsForServer() {
		return java.util.stream.Stream.concat(commonMixins.stream(), serverMixins.stream()).toList();
	}
}
