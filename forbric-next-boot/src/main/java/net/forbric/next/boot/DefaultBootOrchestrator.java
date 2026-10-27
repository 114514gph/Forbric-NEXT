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
 *
 * <p><b>初始化顺序原则（上游 b6fca64b 的教训，文档级约定）：</b>
 * {@link LoadPhase#PRE_LAUNCH} 阶段必须先完成 vanilla 的 {@code Bootstrap.validate()} 等价物
 * （即所有 {@code DefaultAttributes.init()} 这类 vanilla 静态默认值就绪），<b>再</b>触发任何 mod
 * 事件 / entrypoint。上游 merged base 曾把这一步弄丢，导致 {@code EntityAttributeModificationEvent}
 * 第一次访问时 DefaultAttributes 还没初始化，CreateFly 等 mod 的 mixin 直接炸。
 * 骨架阶段还没接真实 MC bootstrap，这条先写成原则；接真实 MC 时务必在 PRE_LAUNCH 开头、
 * 任何 mod 回调之前把 vanilla bootstrap 跑掉。{@link LoadPhase#PRE_LAUNCH} 在枚举里本就排在
 * {@link LoadPhase#REGISTRY} 之前，顺序无需调整。
 */
public class DefaultBootOrchestrator implements BootOrchestrator {

	private final List<ModLoader> loaders = new ArrayList<>();
	private LoadContext context;
	/** 每个 loader 自己 discover 出来的 candidates——resolve 时原样还给它，绝不交叉传递。 */
	private final Map<ModLoader, List<ModCandidate>> discoveredByLoader = new HashMap<>();
	/** 所有 loader discover 出来的 mod 汇总成的全局目录（只读，仅供 entrypoint 跨 loader 查找）。 */
	private final List<ModCandidate> globalCatalog = new ArrayList<>();

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
		globalCatalog.clear();
		NextLog.info("BootOrchestrator: starting discovery across {} loaders", loaders.size());
		for (ModLoader loader : loaders) {
			List<ModCandidate> found = loader.discover(context);
			// 存下来给 resolve 用——之前这里直接丢掉，是个 bug。
			discoveredByLoader.put(loader, new ArrayList<>(found));
			NextLog.info("  {}: {} candidate(s)", loader.ecosystem(), found.size());
		}
		// 汇总全局 catalog：entrypoint 查找要跨 loader 可见。
		buildGlobalCatalog();
	}

	/**
	 * 把所有 loader discover 出的 mod 汇总成只读全局目录，并打跨 loader 可见性日志。
	 *
	 * <p><b>不变量保留：</b>这里只是"汇总可见性"，不会把 A 的 candidate 传给 B 的 resolve——
	 * {@link #resolve()} 仍然只把每个 loader 自己发现的列表还给它。
	 */
	private void buildGlobalCatalog() {
		Map<String, List<String>> foundByEcosystem = new java.util.LinkedHashMap<>();
		for (Map.Entry<ModLoader, List<ModCandidate>> e : discoveredByLoader.entrySet()) {
			String eco = e.getKey().ecosystem().name();
			for (ModCandidate c : e.getValue()) {
				globalCatalog.add(c);
				foundByEcosystem.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(eco);
			}
		}
		NextLog.info("BootOrchestrator: 全局 catalog 汇总完成，共 {} 个 mod（跨 loader 只读可见）",
				globalCatalog.size());
		// 同一个 mod id 被多个 loader 发现 → 明确记录它对其它 loader 可见（库 mod 场景）。
		for (Map.Entry<String, List<String>> e : foundByEcosystem.entrySet()) {
			if (e.getValue().size() > 1) {
				NextLog.info("  entrypoint 全局可见: mod id '{}' 同时被 {} 发现，对其它 loader 可见",
						e.getKey(), e.getValue());
			}
		}
	}

	@Override
	public List<ModCandidate> globalCatalog() {
		return List.copyOf(globalCatalog);
	}

	/**
	 * 在全局 catalog 里按 id 查找 mod（entrypoint 跨 loader 查找用）。
	 *
	 * @param id mod id
	 * @return 第一个匹配的 candidate；找不到返回 null
	 */
	public ModCandidate findGlobally(String id) {
		for (ModCandidate c : globalCatalog) {
			if (id.equals(c.id())) return c;
		}
		return null;
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
