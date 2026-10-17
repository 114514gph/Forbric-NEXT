package net.forbric.next.boot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.forbric.next.api.BootOrchestrator;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.NextLog;

/**
 * Default boot orchestrator.
 *
 * <p><b>Critical invariant (regression guard against Forbric 0.3.0):</b> this class never
 * inspects the internal mod lists of a {@link ModLoader}. It calls {@link ModLoader#discover},
 * {@link ModLoader#resolve}, and {@link ModLoader#runPhase} only. It does not re-sort mods,
 * does not mix Fabric and Forge mods into one dependency graph, and does not know any mod id.
 *
 * <p><b>阶段 → Fabric entrypoint 组映射（在 FabricModLoader.runPhase 里落地）：</b>
 * <ul>
 *   <li>{@link LoadPhase#PRE_LAUNCH} → Fabric {@code preLaunch} entrypoints</li>
 *   <li>{@link LoadPhase#CONSTRUCT} → Fabric {@code main} entrypoints</li>
 *   <li>{@link LoadPhase#SIDED_SETUP} → Fabric {@code client}（CLIENT 环境）或 {@code server}
 *       （SERVER 环境）entrypoints。概念上的 CLIENT_SETUP / SERVER_SETUP 在这里合并成一个
 *       按环境分流的 SIDED_SETUP。</li>
 *   <li>{@link LoadPhase#GAME_STARTED} → 触发 ServerLifecycleEvents 的 starting/started</li>
 * </ul>
 *
 * <p>Forge/NeoForge 加载器在同样的 phase 下跑它们自己的 FML 事件；orchestrator 不关心两边
 * 的组名怎么对应，只负责按固定顺序把 9 个 phase 挨个推进。
 */
public class DefaultBootOrchestrator implements BootOrchestrator {

	private final List<ModLoader> loaders = new ArrayList<>();
	private LoadContext context;
	/** 每个 loader 自己 discover 出来的 candidates——resolve 时原样还给它，绝不交叉传递。 */
	private final Map<ModLoader, List<ModCandidate>> discoveredByLoader = new HashMap<>();

	@Override
	public void installLoaders(List<ModLoader> loaders) {
		this.loaders.clear();
		this.loaders.addAll(loaders);
		NextLog.info("BootOrchestrator: installed {} loader(s): {}",
				loaders.size(),
				loaders.stream().map(l -> l.ecosystem().name()).toList());
	}

	@Override
	public void discover(LoadContext context) {
		this.context = context;
		discoveredByLoader.clear();
		NextLog.info("BootOrchestrator: starting discovery across {} loaders", loaders.size());
		for (ModLoader loader : loaders) {
			List<ModCandidate> found = loader.discover(context);
			// 存下来给 resolve 用——之前这里直接丢掉，是个 bug。
			discoveredByLoader.put(loader, new ArrayList<>(found));
			NextLog.info("  {}: {} candidate(s)", loader.ecosystem(), found.size());
		}
	}

	@Override
	public void resolve() {
		NextLog.info("BootOrchestrator: resolving each loader's own dependency graph independently");
		// 每个 loader 只拿到自己 discover 出来的 candidates；绝不把 A 的列表传给 B。
		for (ModLoader loader : loaders) {
			List<ModCandidate> own = discoveredByLoader.getOrDefault(loader, List.of());
			loader.resolve(own, context);
		}
	}

	@Override
	public void runLifecycle() throws Exception {
		NextLog.info("BootOrchestrator: driving lifecycle phases");
		Deque<Exception> errors = new ArrayDeque<>();
		for (LoadPhase phase : LoadPhase.values()) {
			NextLog.info("  -- phase: {}", phase);
			for (ModLoader loader : loaders) {
				try {
					loader.runPhase(phase);
				} catch (Exception e) {
					// 一个 loader 在某个 phase 失败，不阻断其它 loader / 后续 phase 的尝试，
					// 但记录下来最后抛出，让上层知道有问题。
					NextLog.error("BootOrchestrator: loader {} 在 phase {} 失败: {}",
							loader.ecosystem(), phase, e.toString());
					errors.add(e);
				}
			}
		}
		NextLog.info("BootOrchestrator: lifecycle complete");
		if (!errors.isEmpty()) {
			// 抛第一个错误，其它已在日志里
			throw errors.peekFirst();
		}
	}
}
