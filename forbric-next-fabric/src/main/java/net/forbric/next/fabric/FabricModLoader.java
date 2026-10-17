package net.forbric.next.fabric;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.NextLog;
import net.forbric.next.fabric.FabricLoadOrder.Result;
import net.forbric.next.fabric.FabricModDiscovery.DiscoveredFabricMod;
import net.forbric.next.fabricapi.event.ClientLifecycleEvents;
import net.forbric.next.fabricapi.event.ServerLifecycleEvents;

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
 *   <li>{@link #onShutdown} — 触发 stopping 事件 + 写 {@code .forbric-next/load-report.txt}。</li>
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

		// 转成中立 ModCandidate 返回给 boot orchestrator（boot 不读内部细节）。
		List<ModCandidate> candidates = new ArrayList<>();
		for (DiscoveredFabricMod m : rawDiscovered) {
			candidates.add(new ModCandidate(m.id(), m.version(), m.ecosystem(), m.jarPath(), List.of()));
		}
		return candidates;
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
				if (context != null && context.environment() == LoadContext.Environment.CLIENT) {
					NextLog.info("FabricModLoader: SIDED_SETUP (CLIENT) → client entrypoints");
					report.addFailures(executor.executeGroup(FabricEntrypointExecutor.CLIENT, ordered));
					ClientLifecycleEvents.CLIENT_STARTED.invoker().run();
				} else {
					NextLog.info("FabricModLoader: SIDED_SETUP (SERVER) → server entrypoints");
					report.addFailures(executor.executeGroup(FabricEntrypointExecutor.SERVER, ordered));
				}
			}
			case GAME_STARTED -> {
				NextLog.info("FabricModLoader: GAME_STARTED → 触发 server lifecycle 事件");
				ServerLifecycleEvents.SERVER_STARTING.invoker().run();
				ServerLifecycleEvents.SERVER_STARTED.invoker().run();
			}
			default -> { /* 其它阶段 Fabric 没有对应 entrypoint 组 */ }
		}
	}

	@Override
	public void onShutdown() {
		NextLog.info("FabricModLoader: onShutdown → stopping/stopped 事件 + 写加载报告");
		ServerLifecycleEvents.SERVER_STOPPING.invoker().run();
		ServerLifecycleEvents.SERVER_STOPPED.invoker().run();
		ClientLifecycleEvents.CLIENT_STOPPING.invoker().run();

		if (context != null) {
			report.write(context.gameDir());
		}
	}

	/**
	 * 建 URLClassLoader：把所有排好序的 mod jar 都放进去，parent 用当前 loader 的 classloader。
	 * 骨架阶段所有 mod 共享一个 classloader（真实 Fabric 是每个 mod 独立 classloader，
	 * 但骨架阶段共享一个就够"能用"了）。
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
