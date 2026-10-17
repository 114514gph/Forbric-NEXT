package net.forbric.next.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.forbric.next.fabric.FabricModDiscovery.DiscoveredFabricMod;

/**
 * Fabric mod 排序器。
 *
 * <p><b>核心不变量（0.3.0 的 regression guard）：</b>
 * <ul>
 *   <li>Fabric mod 的执行顺序就是 <b>mod id 字典序</b>——这是 Fabric Loader 的原生行为。
 *       我们不在这里跑 Kahn 拓扑，也不调用 Forge 的 ModSorter。</li>
 *   <li>{@code depends} 只用来判断"这个 mod 能不能加载"（缺前置就跳过），<b>不</b>用来
 *       调整执行顺序。Fabric 的 depends 是"存在性要求"，不是"先后边"。</li>
 *   <li>只在 Fabric/Quilt mod 集合内部判断前置；绝不会把 Forge mod 拉进来。</li>
 * </ul>
 */
public final class FabricLoadOrder {

	/**
	 * 平台级依赖 key——这些不是 mod id，是 loader / 游戏 / JVM 本身的版本要求。
	 * 骨架阶段不校验它们，只当它们"一定满足"。
	 */
	private static final Set<String> PLATFORM_KEYS = Set.of(
			"fabricloader", "minecraft", "java", "quilt_loader", "quiltloader", "fabric"
	);

	private FabricLoadOrder() {}

	/**
	 * 排序结果。
	 *
	 * @param ordered  满足前置、按 id 字典序排好、将要加载的 mod
	 * @param skipped  因缺前置被跳过的 mod（id → 缺哪些前置）
	 */
	public record Result(List<DiscoveredFabricMod> ordered,
						 List<SkippedMod> skipped) {
		public record SkippedMod(DiscoveredFabricMod mod, List<String> missingDeps) {}
	}

	/**
	 * 对发现到的 Fabric/Quilt mod 做过滤 + 字典序排序。
	 *
	 * @param mods 发现阶段产出的全部 mod
	 * @return 排好序的结果（含被跳过的）
	 */
	public static Result order(List<DiscoveredFabricMod> mods) {
		// 当前集合里所有 mod id——用来判断前置是否被满足。
		Set<String> availableIds = new HashSet<>();
		for (DiscoveredFabricMod m : mods) {
			availableIds.add(m.id());
		}

		List<DiscoveredFabricMod> ordered = new ArrayList<>();
		List<Result.SkippedMod> skipped = new ArrayList<>();

		for (DiscoveredFabricMod m : mods) {
			List<String> missing = new ArrayList<>();
			for (Map.Entry<String, String> dep : m.depends().entrySet()) {
				String depId = dep.getKey();
				if (PLATFORM_KEYS.contains(depId.toLowerCase(Locale.ROOT))) continue;
				if (!availableIds.contains(depId)) {
					missing.add(depId);
				}
			}
			if (missing.isEmpty()) {
				ordered.add(m);
			} else {
				skipped.add(new Result.SkippedMod(m, missing));
			}
		}

		// Fabric 原生：纯字典序。不做拓扑。
		ordered.sort(Comparator.comparing(DiscoveredFabricMod::id));
		return new Result(ordered, skipped);
	}
}
