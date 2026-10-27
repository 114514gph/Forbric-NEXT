package net.forbric.next.fabricapi.modmenu;

/**
 * Mod Menu 集成的最小接口桩。
 *
 * <p>真实 Mod Menu 是一个 Fabric mod，它通过 {@code ModMenuApi} 接口让其他 mod
 * 提供自己的配置界面。NEXT 不实现 Mod Menu 本体，但定义这个接口，让 Fabric mod
 * 的 modmenu 入口能被识别和加载。
 *
 * <p>fabric.mod.json 里的声明：
 * <pre>
 * "entrypoints": {
 *   "modmenu": ["com.example.ModMenuImpl"]
 * }
 * </pre>
 */
public interface ModMenuApi {

	/** 返回主配置界面的类名（Mod Menu 用反射加载）。 */
	default String getConfigScreenClassName() {
		return null;
	}

	/** 返回 mod 的描述（Mod Menu mod 列表里显示）。 */
	default String getModDescription() {
		return "";
	}
}
