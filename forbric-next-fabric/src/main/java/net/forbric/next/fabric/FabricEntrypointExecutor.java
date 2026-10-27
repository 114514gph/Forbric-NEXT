package net.forbric.next.fabric;

import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.EntrypointException;
import net.forbric.next.common.NextLog;
import net.forbric.next.fabric.FabricModDiscovery.DiscoveredFabricMod;
import net.forbric.next.fabricapi.language.LanguageAdapterRegistry;

/**
 * Fabric entrypoint 执行器。
 *
 * <p>按 Fabric 原生分组顺序执行：{@code preLaunch → main → client → server}。
 * 每个组内按 {@link FabricLoadOrder} 排好的 mod id 字典序逐个执行。
 *
 * <p><b>隔离策略：</b>单个 mod 的某个 entrypoint 抛异常时，打 ERROR 日志（含 mod id +
 * 完整堆栈），记进加载报告，然后继续下一个 mod——一个坏 mod 不会炸掉整个游戏启动。
 *
 * <p>实例化走 {@link LanguageAdapterRegistry}（默认 Java adapter），支持
 * {@code com.example.Mod} / {@code Mod::FIELD} / {@code Mod::method} 三种形式。
 *
 * <p>骨架阶段只做"实例化"——真正 fabric-api 里 mod 会实现 {@code ModInitializer} 等接口，
 * 这里不做接口回调（那些接口等 fabric-api 补全）。实例化本身就会触发 mod 的静态初始化块。
 */
public final class FabricEntrypointExecutor {

	/** Fabric 原生 entrypoint 组执行顺序。 */
	public static final String PRE_LAUNCH = "preLaunch";
	public static final String MAIN = "main";
	public static final String CLIENT = "client";
	public static final String SERVER = "server";

	private final ClassLoader modClassLoader;

	public FabricEntrypointExecutor(ClassLoader modClassLoader) {
		this.modClassLoader = modClassLoader;
	}

	/**
	 * 执行一个 entrypoint 组。
	 *
	 * @param groupName 组名（preLaunch / main / client / server）
	 * @param mods       已排好序、将要加载的 mod
	 * @return 该组内失败的条目列表（成功的不占地方）
	 */
	public List<EntrypointFailure> executeGroup(String groupName, List<DiscoveredFabricMod> mods) {
		List<EntrypointFailure> failures = new java.util.ArrayList<>();
		NextLog.info("FabricEntrypointExecutor: 执行 entrypoint 组 '{}'（{} 个 mod）", groupName, mods.size());

		for (DiscoveredFabricMod mod : mods) {
			List<String> classes = mod.entrypoints().get(groupName);
			if (classes == null || classes.isEmpty()) continue;

			for (String className : classes) {
				try {
					// 用默认 Java language adapter 实例化；返回值暂时不消费。
					Object instance = LanguageAdapterRegistry.get("default")
							.create(className, Object.class, modClassLoader);
					NextLog.info("  [{}] {} -> {} (实例化成功)", groupName, mod.id(), className);
					// 占位：等 fabric-api 补了 ModInitializer 等接口后，在这里做 instanceof 回调。
				} catch (Throwable t) {
					NextLog.error("  [{}] mod '{}' entrypoint '{}' 执行失败: {}",
							groupName, mod.id(), className, t.toString());
					// 打完整堆栈，方便排查
					for (StackTraceElement el : t.getStackTrace()) {
						NextLog.error("      at {}", el);
					}
					Throwable cause = t.getCause();
					while (cause != null) {
						NextLog.error("      caused by: {}", cause.toString());
						cause = cause.getCause();
					}
					// 用 Fabric 原生的 EntrypointException 包装失败，而不是随便抛个 RuntimeException——
					// 这样 mod 里 catch (EntrypointException) 才能链接成功、也能按原版语义识别 entrypoint 失败。
					EntrypointException wrapped = new EntrypointException(
							"mod '" + mod.id() + "' entrypoint '" + className + "' 执行失败", t);
					failures.add(new EntrypointFailure(mod.id(), groupName, className, wrapped));
				}
			}
		}
		return failures;
	}

	/**
	 * 按 Fabric 原生顺序跑完四个组。
	 *
	 * @param mods 已排序的 mod
	 * @return 全部组的失败条目
	 */
	public List<EntrypointFailure> executeAll(List<DiscoveredFabricMod> mods) {
		List<EntrypointFailure> all = new java.util.ArrayList<>();
		all.addAll(executeGroup(PRE_LAUNCH, mods));
		all.addAll(executeGroup(MAIN, mods));
		all.addAll(executeGroup(CLIENT, mods));
		all.addAll(executeGroup(SERVER, mods));
		return all;
	}

	/**
	 * 单个 entrypoint 失败记录。
	 *
	 * @param modId     失败的 mod id
	 * @param group     哪个 entrypoint 组
	 * @param className 哪个入口类
	 * @param error     抛出的异常
	 */
	public record EntrypointFailure(String modId, String group, String className, Throwable error) {
	}
}
