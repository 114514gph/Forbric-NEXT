package net.neoforbric.boot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.neoforbric.api.Ecosystem;
import net.neoforbric.api.ModCandidate;
import net.neoforbric.common.NextLog;

/**
 * 跨生态 mod 仲裁器。
 *
 * <p>原版 Forbric 没有统一的仲裁机制——三系 mod 混放时靠 merged base 隐式解决。
 * NEXT 不做 merged base，所以冲突必须显式记录和处理：
 * <ul>
 *   <li><b>mod id 去重</b>：同一个 mod 的 Fabric 版和 Forge 版都放进来时，按用户配置
 *       （mods/forbric-mods.txt）或先到先得选择一个。</li>
 *   <li><b>注册冲突检测</b>：两个 mod 注册同一个物品/方块 ID 时记录冲突。</li>
 *   <li><b>跨系加载顺序</b>：如果 Forge mod 依赖某个 Fabric mod，Fabric mod 先加载。</li>
 * </ul>
 */
public final class ModArbiter {

	/** 一条仲裁结果：某个 mod id 最终选用了哪个生态版本。 */
	public record Arbitration(String modId, Ecosystem chosen, List<String> rejected) {}

	/** 一条冲突记录：两个 mod 声明了同一个东西。 */
	public record Conflict(String type, String id, String modA, String modB, String resolution) {}

	private final Map<String, ModCandidate> chosenById = new HashMap<>();
	private final List<Arbitration> arbitrations = new ArrayList<>();
	private final List<Conflict> conflicts = new ArrayList<>();
	private final ModDedupConfig dedupConfig;

	public ModArbiter(Path modsDir) {
		this.dedupConfig = ModDedupConfig.load(modsDir);
	}

	/**
	 * 对所有 loader 发现的 mod 做仲裁。
	 *
	 * @param allCandidates 所有 loader discover 出来的候选（跨生态）
	 * @return 仲裁后保留的候选列表（每个 mod id 最多一个）
	 */
	public List<ModCandidate> arbitrate(List<ModCandidate> allCandidates) {
		// 按 mod id 分组
		Map<String, List<ModCandidate>> byId = new HashMap<>();
		for (ModCandidate c : allCandidates) {
			byId.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(c);
		}

		List<ModCandidate> kept = new ArrayList<>();
		for (Map.Entry<String, List<ModCandidate>> e : byId.entrySet()) {
			String modId = e.getKey();
			List<ModCandidate> group = e.getValue();

			if (group.size() == 1) {
				chosenById.put(modId, group.get(0));
				kept.add(group.get(0));
				continue;
			}

			// 同名多版本：需要去重
			ModCandidate chosen = pickOne(modId, group);
			List<String> rejected = new ArrayList<>();
			for (ModCandidate c : group) {
				if (c != chosen) {
					rejected.add(c.ecosystem().name() + " (" + c.jarPath().getFileName() + ")");
				}
			}
			arbitrations.add(new Arbitration(modId, chosen.ecosystem(), rejected));
			conflicts.add(new Conflict("duplicate-mod", modId,
					group.get(0).jarPath().getFileName().toString(),
					group.get(1).jarPath().getFileName().toString(),
					"chose " + chosen.ecosystem()));
			chosenById.put(modId, chosen);
			kept.add(chosen);

			// 固定标记串 "CONFLICT:"——启动校验脚本 grep 这个串就能证明跨生态同名 mod 被识别。
			NextLog.warn("CONFLICT: duplicate-mod mod id '{}' 同时出现在 {} 和 {}（{}）；仲裁结果=选用 {}",
					modId,
					group.get(0).jarPath().getFileName(),
					group.get(1).jarPath().getFileName(),
					group.stream().map(c -> c.ecosystem().name()).toList(),
					chosen.ecosystem());
			NextLog.warn("  仲裁: mod id '{}' 在 {} 个 jar 中出现，选用 {}（来自 {}）",
					modId, group.size(), chosen.ecosystem(), chosen.jarPath().getFileName());
			for (String r : rejected) {
				NextLog.warn("    跳过: {}", r);
			}
		}
		return kept;
	}

	/** 用户指定优先生态；否则先到先得（列表第一个）。 */
	private ModCandidate pickOne(String modId, List<ModCandidate> group) {
		Ecosystem preferred = dedupConfig.preferredEcosystem(modId);
		if (preferred != null) {
			for (ModCandidate c : group) {
				if (c.ecosystem() == preferred) return c;
			}
			NextLog.warn("  forbric-mods.txt 指定 {} 优先 {}，但没找到对应 jar", modId, preferred);
		}
		return group.get(0); // 先到先得
	}

	/** 记录一条注册冲突（物品/方块/实体 ID 重复）。 */
	public void recordRegistrationConflict(String type, String id, String modA, String modB) {
		conflicts.add(new Conflict("registration-" + type, id, modA, modB,
				"continue: first registration wins"));
		NextLog.warn("  注册冲突: {} '{}' 被 {} 和 {} 同时注册（先到先得）",
				type, id, modA, modB);
	}

	public List<Arbitration> arbitrations() { return List.copyOf(arbitrations); }
	public List<Conflict> conflicts() { return List.copyOf(conflicts); }
	public Map<String, ModCandidate> chosenById() { return Map.copyOf(chosenById); }

	/** 按 id 查找已仲裁的 mod（跨 loader 可见）。 */
	public ModCandidate find(String modId) { return chosenById.get(modId); }

	public Set<String> allModIds() { return new HashSet<>(chosenById.keySet()); }
}
