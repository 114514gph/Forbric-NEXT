package net.neoforbric.fabric;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.neoforbric.api.Ecosystem;
import net.neoforbric.api.LoadContext;
import net.neoforbric.api.LoadPhase;
import net.neoforbric.api.ModCandidate;
import net.neoforbric.api.ModLoader;
import net.neoforbric.common.NextLog;
import net.neoforbric.fabric.FabricLoadOrder.Result;
import net.neoforbric.fabric.FabricModDiscovery.DiscoveredFabricMod;
import net.neoforbric.fabricapi.event.ClientLifecycleEvents;
import net.neoforbric.fabricapi.event.ServerLifecycleEvents;
import net.neoforbric.remap.ModRemapPipeline;

/**
 * Fabric ecosystem loader.
 *
 * <p><b>完整加载流程（从骨架推进到可用）：</b>
 * <ol>
 *   <li>{@link #discover} — 用 {@link FabricModDiscovery} 扫 mods/ 下的 jar，解析
 *       fabric.mod.json / quilt.mod.json，产出 {@link DiscoveredFabricMod}。</li>
 *   <li>{@link #resolve} — 用 {@link FabricLoadOrder} 做前置过滤 + mod id 字典序排序
 *       （Fabric 原生行为，不进 Forge 拓扑图），并建好 mod 的 {@link URLClassLoader}。</li>
 *   <li>{@link #runPhase} — 按 {@link LoadPhase} 驱动 entrypoint 组：
 *       <ul>
 *         <li>{@link LoadPhase#PRE_LAUNCH} → preLaunch entrypoints</li>
 *         <li>{@link LoadPhase#CONSTRUCT} → main entrypoints</li>
 *         <li>{@link LoadPhase#SIDED_SETUP} → client（CLIENT 环境）或 server（SERVER 环境）entrypoints</li>
 *       </ul>
 *   </li>
 *   <li>{@link #onShutdown} — 触发 stopping 事件 + 写 {@code .neoforbric/load-report.txt}。</li>
 * </ol>
 *
 * <p><b>关键不变量：</b>Fabric mod 顺序只按 id 字典序；depends 只用来判断能不能加载，
 * 不调整顺序；任何 Fabric mod 都不会被拉进 Forge 的 @Mod 构造图。
 */
public class FabricModLoader implements ModLoader {

	private LoadContext context;
	/** discover 阶段产出的全部 Fabric/Quilt mod。 */
	private final List<DiscoveredFabricMod> rawDiscovered = new ArrayList<>();
	/** resolve 阶段排好序、将要加载的 mod。 */
	private final List<DiscoveredFabricMod> ordered = new ArrayList<>();
	/** 因缺前置被跳过的 mod。 */
	private final List<Result.SkippedMod> skipped = new ArrayList<>();
	/** entrypoint 执行器（resolve 阶段建好 classloader 后创建）。 */
	private FabricEntrypointExecutor executor;
	/** 加载报告收集器。 */
	private final FabricLoadReport report = new FabricLoadReport();

	@Override
	public Ecosystem ecosystem() {
		return Ecosystem.FABRIC;
	}

	@Override
	public List<ModCandidate> discover(LoadContext context) {
		this.context = context;
		NextLog.info("FabricModLoader: 扫描 {} 下的 Fabric/Quilt mod", context.modsDir());
		rawDiscovered.clear();
		rawDiscovered.addAll(FabricModDiscovery.discover(context.modsDir()));

		// 跨版本老 mod 兼容：检查每个 mod 声明的 depends.minecraft 是否对得上当前运行版本，
		// 对不上就走重映射管道，把 jar 换成重映射后的新 jar。
		String currentMcVersion = currentMcVersion();
		List<DiscoveredFabricMod> remapped = new ArrayList<>();
		for (DiscoveredFabricMod m : rawDiscovered) {
			remapped.add(remapIfVersionMismatch(m, currentMcVersion));
		}
		rawDiscovered.clear();
		rawDiscovered.addAll(remapped);

		// 转成中立 ModCandidate 返回给 boot orchestrator（boot 不读内部细节）。
		List<ModCandidate> candidates = new ArrayList<>();
		for (DiscoveredFabricMod m : rawDiscovered) {
			candidates.add(new ModCandidate(m.id(), m.version(), m.ecosystem(), m.jarPath(), List.of()));
		}
		return candidates;
	}

	/**
	 * 当前运行的 MC 版本。骨架阶段从系统属性 {@code forbric.mc.version} 读，
	 * 缺省 {@code 26.3}（NeoForbric 的目标基线）。后续由 boot 阶段从版本清单里注入。
	 */
	private static String currentMcVersion() {
		return System.getProperty("forbric.mc.version", "26.3");
	}

	/**
	 * 如果 mod 声明的 MC 版本和当前运行版本对不上，走 {@link ModRemapPipeline} 重映射，
	 * 返回一个 jarPath 换成重映射产物的新 record；否则原样返回。
	 *
	 * <p>骨架阶段：fabric.mod.json 里 {@code depends.minecraft} 是 {@code ~1.20.4} 这种，
	 * 管道会自己抠版本号并打 "Remapping mod X from MC 1.20.4 to 26.3" 日志。
	 */
	/**
	 * 跨版本兼容：mod 声明的 MC 版本和当前运行版本不一致时，不拒绝加载。
	 *
	 * <p>策略：
	 * <ol>
	 *   <li>版本一致 → 原样加载。</li>
	 *   <li>版本不一致 → 打 WARN 说明"跨版本兼容模式"，尝试走重映射管道。</li>
	 *   <li>重映射失败 → 回退原 jar 继续加载（mod 可能在某些类上崩，但不挡发现流程）。</li>
	 * </ol>
	 *
	 * <p>不会因为版本号不匹配就把 mod 拒掉——这是 NeoForbric 作为兼容层的核心行为。
	 */
	private DiscoveredFabricMod remapIfVersionMismatch(DiscoveredFabricMod m, String currentMcVersion) {
		String mcRange = m.depends() == null ? null : m.depends().get("minecraft");
		if (mcRange == null) {
			return m;
		}
		if (!ModRemapPipeline.needsRemap(m.jarPath(), currentMcVersion)) {
			return m;
		}
		// 版本不匹配：明确告诉用户这是跨版本兼容模式，不拒绝
		NextLog.warn("FabricModLoader: mod {} 声明 MC {}，当前运行 {} —— 跨版本兼容模式，尝试重映射",
				m.id(), mcRange, currentMcVersion);
		try {
			Path remappedJar = ModRemapPipeline.remapModJar(m.jarPath(), currentMcVersion);
			return new DiscoveredFabricMod(m.id(), m.version(), m.name(), m.ecosystem(),
					remappedJar, m.entrypoints(), m.depends(), m.mixinConfigs(), m.parentJar());
		} catch (IOException e) {
			NextLog.warn("FabricModLoader: mod {} 重映射失败，回退原 jar 继续加载: {}", m.id(), e.getMessage());
			return m;
		}
	}

	@Override
	public void resolve(List<ModCandidate> candidates, LoadContext context) {
		if (context != null) this.context = context;
		ordered.clear();
		skipped.clear();

		Result result = FabricLoadOrder.order(rawDiscovered);
		ordered.addAll(result.ordered());
		skipped.addAll(result.skipped());

		// 把跳过的 mod 记进报告
		report.addSkipped(skipped);
		for (Result.SkippedMod s : skipped) {
			NextLog.warn("FabricModLoader: 跳过 mod '{}'，缺少前置: {}",
					s.mod().id(), s.missingDeps());
		}

		// 注册表冲突单独记一段——不能像上游那样被 catch 静默吞掉
		report.addConflicts(result.conflicts());

		// 建一个能加载所有 mod jar 的 URLClassLoader
		this.executor = new FabricEntrypointExecutor(buildModClassLoader(ordered));
		NextLog.info("FabricModLoader: 解析完成，{} 个 mod 将按 id 字典序加载，{} 个被跳过",
				ordered.size(), skipped.size());
	}

	@Override
	public void runPhase(LoadPhase phase) {
		if (executor == null) return;
		switch (phase) {
			case PRE_LAUNCH -> {
				NextLog.info("FabricModLoader: PRE_LAUNCH → preLaunch entrypoints");
				report.addFailures(executor.executeGroup(FabricEntrypointExecutor.PRE_LAUNCH, ordered));
			}
			case CONSTRUCT -> {
				NextLog.info("FabricModLoader: CONSTRUCT → main entrypoints");
				// main entrypoints 跑成功的 mod 记进报告
				for (DiscoveredFabricMod m : ordered) report.addSuccess(m);
				report.addFailures(executor.executeGroup(FabricEntrypointExecutor.MAIN, ordered));
			}
			case SIDED_SETUP -> {
				// CLIENT_SETUP / SERVER_SETUP 概念上对应这里：按环境只跑一边。
				boolean wantClient = context != null
						&& context.environment() == LoadContext.Environment.CLIENT;
				if (wantClient) {
					// 无头服务端（容器/SSH/启动脚本 -Dforbric.headless=true）即使 environment 被
					// 标成 CLIENT，也没有显示器——硬跑 client entrypoint 会去碰 LWJGL/awt 直接崩。
					// 这里显式检测并跳过，打一条带固定标记串的日志供启动校验 grep。
					if (net.neoforbric.common.HeadlessMode.isHeadless()) {
						NextLog.info("FabricModLoader: SIDED_SETUP (CLIENT requested but headless) "
								+ "→ Skipping client entrypoints in headless mode");
						// 不抛异常、不崩；客户端生命周期事件也不触发。
					} else {
						NextLog.info("FabricModLoader: SIDED_SETUP (CLIENT) → client entrypoints");
						report.addFailures(executor.executeGroup(FabricEntrypointExecutor.CLIENT, ordered));
						ClientLifecycleEvents.CLIENT_STARTED.invoker()
								.accept(new net.neoforbric.fabricapi.event.ClientStartedEvent("Minecraft"));
					}
				} else {
					NextLog.info("FabricModLoader: SIDED_SETUP (SERVER) → server entrypoints");
					report.addFailures(executor.executeGroup(FabricEntrypointExecutor.SERVER, ordered));
				}
			}
			case GAME_STARTED -> {
				NextLog.info("FabricModLoader: GAME_STARTED → 触发 server lifecycle 事件");
				// 修复：之前这里把 SERVER_STARTING 连打了两遍，且从不触发 SERVER_STARTED。
				// 按 Fabric 语义，服务器启动窗口依次是 starting → started。
				// 事件现在带具体 record 上下文（服务端目录），不再是无参 Runnable。
				Path gameDir = context != null ? context.gameDir() : null;
				ServerLifecycleEvents.SERVER_STARTING.invoker()
						.accept(new net.neoforbric.fabricapi.event.ServerStartingEvent(gameDir));
				ServerLifecycleEvents.SERVER_STARTED.invoker()
						.accept(new net.neoforbric.fabricapi.event.ServerStartedEvent(gameDir));
			}
			default -> { /* 其它阶段 Fabric 没有对应 entrypoint 组 */ }
		}
	}

	@Override
	public void onShutdown() {
		NextLog.info("FabricModLoader: onShutdown → stopping/stopped 事件 + 写加载报告");
		Path gameDir = context != null ? context.gameDir() : null;
		ServerLifecycleEvents.SERVER_STOPPING.invoker()
				.accept(new net.neoforbric.fabricapi.event.ServerStoppingEvent(gameDir));
		ServerLifecycleEvents.SERVER_STOPPED.invoker()
				.accept(new net.neoforbric.fabricapi.event.ServerStoppedEvent(gameDir));
		ClientLifecycleEvents.CLIENT_STOPPING.invoker().accept(new net.neoforbric.fabricapi.event.ClientStoppingEvent());

		if (context != null) {
			report.write(context.gameDir());
		}
	}

	/**
	 * 建 URLClassLoader：把所有排好序的 mod jar 都放进去，parent 用当前 loader 的 classloader。
	 * 所有 mod 共享一个 classloader（真实 Fabric 是每个 mod 独立 classloader，
	 * 共享模式足以满足当前阶段的 mod 加载需求）。
	 */
	private ClassLoader buildModClassLoader(List<DiscoveredFabricMod> mods) {
		List<URL> urls = new ArrayList<>();
		for (DiscoveredFabricMod m : mods) {
			try {
				urls.add(m.jarPath().toUri().toURL());
			} catch (MalformedURLException e) {
				NextLog.warn("FabricModLoader: jar 路径转 URL 失败，跳过: {} ({})", m.jarPath(), e.getMessage());
			}
		}
		return new URLClassLoader(urls.toArray(new URL[0]), FabricModLoader.class.getClassLoader());
	}
}
