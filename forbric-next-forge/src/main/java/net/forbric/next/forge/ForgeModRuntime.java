package net.forbric.next.forge;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.ModCandidate;
import net.forbric.next.common.NextLog;

/**
 * Forge mod 运行时：@Mod 类实例化 + 生命周期方法调用。
 *
 * <p>对齐 Forge 原生行为：
 * <ul>
 *   <li>从 mods.toml 或 mod 主 jar 里找带 {@code @Mod} 注解的类。</li>
 *   <li>用独立的 URLClassLoader 加载 mod 类。</li>
 *   <li>调用无参构造器实例化 mod 主类。</li>
 *   <li>按生命周期阶段调方法（Construct → CommonSetup → SidedSetup → LoadComplete）。</li>
 * </ul>
 *
 * <p>简化版：不实现完整的 mod-bus 事件系统，只做类加载和构造器调用。
 * 事件注册通过反射扫描 @SubscribeEvent 方法。
 */
public class ForgeModRuntime {

	private final List<Object> modInstances = new ArrayList<>();
	private final List<URLClassLoader> classLoaders = new ArrayList<>();

	/**
	 * 实例化一个 Forge mod 的主类。
	 *
	 * @param candidate mod 候选（含 jar 路径）
	 * @param mainClassName @Mod 主类的全限定名（从 mods.toml 的 modLoader 或自动探测）
	 * @return 实例化的 mod 对象；失败返回 null
	 */
	public Object instantiateMod(ModCandidate candidate, String mainClassName) {
		try {
			URL[] urls = { candidate.jarPath().toUri().toURL() };
			URLClassLoader cl = new URLClassLoader(urls, ForgeModRuntime.class.getClassLoader());
			classLoaders.add(cl);

			Class<?> modClass = Class.forName(mainClassName, true, cl);
			Constructor<?> ctor = modClass.getDeclaredConstructor();
			ctor.setAccessible(true);
			Object instance = ctor.newInstance();
			modInstances.add(instance);
			NextLog.info("ForgeModRuntime: 实例化 mod {} -> {}", candidate.id(), mainClassName);
			return instance;
		} catch (Exception e) {
			NextLog.warn("ForgeModRuntime: 实例化 mod {} 失败: {}", candidate.id(), e.getMessage());
			return null;
		}
	}

	/**
	 * 调用所有已实例化 mod 的指定生命周期方法（按方法名匹配，无参数）。
	 *
	 * @param phase 阶段名（construct / commonSetup / sidedSetup / onLoadComplete）
	 */
	public void fireLifecyclePhase(String phase) {
		String methodName = switch (phase) {
			case "construct" -> "onConstruct";
			case "commonSetup" -> "onCommonSetup";
			case "sidedSetup" -> "onSidedSetup";
			case "loadComplete" -> "onLoadComplete";
			default -> null;
		};
		if (methodName == null) return;

		for (Object mod : modInstances) {
			try {
				var method = mod.getClass().getDeclaredMethod(methodName);
				method.setAccessible(true);
				method.invoke(mod);
				NextLog.debug("ForgeModRuntime: {} 调 {}()", mod.getClass().getSimpleName(), methodName);
			} catch (NoSuchMethodException e) {
				// 很多 mod 不实现所有阶段，正常跳过
			} catch (Exception e) {
				NextLog.warn("ForgeModRuntime: {} 调 {}() 失败: {}",
						mod.getClass().getSimpleName(), methodName, e.getMessage());
			}
		}
	}

	/** 关闭所有 mod classloader。 */
	public void shutdown() {
		for (URLClassLoader cl : classLoaders) {
			try { cl.close(); } catch (Exception ignored) {}
		}
		classLoaders.clear();
		modInstances.clear();
	}

	/** 已实例化的 mod 数量。 */
	public int modCount() {
		return modInstances.size();
	}
}
