package net.neoforbric.api.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.neoforbric.api.ModCandidate;
import net.neoforbric.common.NextLog;

/**
 * Forge 风格的 mod 依赖排序器。
 *
 * <p>Forge 排序规则（对齐 FML ModSorter）：
 * <ul>
 *   <li><b>mandatory dependencies</b>（required = true）：只是存在性检查，<b>不是</b>排序边。
 *       缺了就报错，但不影响 mod 之间的相对顺序。</li>
 *   <li><b>BEFORE / AFTER edges</b>：mods.toml 里的 {@code ordering = "BEFORE"} /
 *       {@code ordering = "AFTER"} 才是真正的排序边。</li>
 *   <li>排序用 Kahn 算法做拓扑排序；有环时打破环并打 WARN。</li>
 * </ul>
 */
public final class ModDependencySorter {

	private ModDependencySorter() {}

	/**
	 * 对 Forge mod 列表做拓扑排序。
	 *
	 * @param candidates 未排序的 mod 列表
	 * @param orderEdges 排序边：modA → modB 表示 modA 必须在 modB 之前
	 * @return 排序后的列表
	 */
	public static List<ModCandidate> sort(List<ModCandidate> candidates,
			Map<String, Set<String>> orderEdges) {

		Map<String, ModCandidate> byId = new HashMap<>();
		for (ModCandidate c : candidates) {
			byId.put(c.id(), c);
		}

		// 入度表
		Map<String, Integer> inDegree = new HashMap<>();
		for (String id : byId.keySet()) {
			inDegree.put(id, 0);
		}
		// 建图：edge A→B 表示 A 必须在 B 前，B 的入度 +1
		Map<String, Set<String>> adj = new HashMap<>();
		for (Map.Entry<String, Set<String>> e : orderEdges.entrySet()) {
			String from = e.getKey();
			if (!byId.containsKey(from)) continue;
			for (String to : e.getValue()) {
				if (!byId.containsKey(to)) continue;
				adj.computeIfAbsent(from, k -> new HashSet<>()).add(to);
				inDegree.merge(to, 1, Integer::sum);
			}
		}

		// Kahn 算法：入度为 0 的先排
		List<ModCandidate> sorted = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		boolean progress = true;
		while (progress && visited.size() < byId.size()) {
			progress = false;
			for (String id : new ArrayList<>(byId.keySet())) {
				if (visited.contains(id)) continue;
				if (inDegree.getOrDefault(id, 0) == 0) {
					sorted.add(byId.get(id));
					visited.add(id);
					// 减少下游入度
					for (String downstream : adj.getOrDefault(id, Set.of())) {
						inDegree.merge(downstream, -1, Integer::sum);
					}
					progress = true;
				}
			}
		}

		// 有环：剩下的按字母序排，打 WARN
		if (visited.size() < byId.size()) {
			List<String> cyclic = new ArrayList<>();
			for (String id : byId.keySet()) {
				if (!visited.contains(id)) {
					cyclic.add(id);
					sorted.add(byId.get(id));
				}
			}
			NextLog.warn("ModDependencySorter: 检测到排序环，剩余 mod 按字母序排列: {}", cyclic);
		}

		// 无排序边时保持原顺序（稳定）
		sorted.sort(Comparator.comparingInt(c -> candidates.indexOf(c)));
		return sorted;
	}
}
