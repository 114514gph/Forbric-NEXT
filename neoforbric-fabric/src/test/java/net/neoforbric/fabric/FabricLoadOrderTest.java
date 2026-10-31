package net.neoforbric.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.neoforbric.api.Ecosystem;
import net.neoforbric.fabric.FabricLoadOrder.Result;
import net.neoforbric.fabric.FabricModDiscovery.DiscoveredFabricMod;

/**
 * {@link FabricLoadOrder} 的行为测试。
 *
 * <p>核心回归点（0.3.0 的 bug #3）：Fabric mod 的顺序必须是 <b>mod id 字典序</b>，
 * {@code depends} 只能用来"过滤掉缺前置的 mod"，绝不能用来重排顺序。
 */
class FabricLoadOrderTest {

	/** 造一个 DiscoveredFabricMod；order 只用到 id 和 depends，jarPath 传 null 即可。 */
	private static DiscoveredFabricMod mod(String id, Map<String, String> depends) {
		return new DiscoveredFabricMod(id, "1.0.0", id, Ecosystem.FABRIC,
				null, Map.of(), depends == null ? Map.of() : depends);
	}

	/** 造一个带具体 jar 路径的 DiscoveredFabricMod（用来区分冲突里的是哪几个 jar）。 */
	private static DiscoveredFabricMod modWithJar(String id, String jarName) {
		return new DiscoveredFabricMod(id, "1.0.0", id, Ecosystem.FABRIC,
				java.nio.file.Path.of("/tmp", jarName), Map.of(), Map.of());
	}

	@Test
	void ordersByIdLexicographically() {
		// 故意打乱插入顺序
		List<DiscoveredFabricMod> input = List.of(
				mod("zeta", null),
				mod("alpha", null),
				mod("mike", null),
				mod("bravo", null),
				mod("november", null));

		Result result = FabricLoadOrder.order(input);

		List<String> ids = result.ordered().stream().map(DiscoveredFabricMod::id).toList();
		// 必须是字典序，而不是插入顺序
		assertEquals(List.of("alpha", "bravo", "mike", "november", "zeta"), ids);
		assertTrue(result.skipped().isEmpty(), "全部前置满足，不应有跳过");
	}

	@Test
	void missingDependencyFiltersOutButDoesNotReorderOthers() {
		// child 依赖一个不存在的前置 "missing-dep" → 应被跳过
		// 其余 mod 仍然按字典序，depends 关系本身不改变字典序
		List<DiscoveredFabricMod> input = List.of(
				mod("zeta", null),
				mod("alpha", Map.of("missing-dep", "*")), // 缺前置 → 跳过
				mod("mike", null),
				mod("bravo", null));

		Result result = FabricLoadOrder.order(input);

		List<String> ids = result.ordered().stream().map(DiscoveredFabricMod::id).toList();
		// alpha 被过滤掉，剩下的仍严格字典序
		assertEquals(List.of("bravo", "mike", "zeta"), ids);
		assertEquals(1, result.skipped().size());
		assertEquals("alpha", result.skipped().get(0).mod().id());
		assertTrue(result.skipped().get(0).missingDeps().contains("missing-dep"),
				"应记录缺的是哪个前置");
	}

	@Test
	void satisfiedDependencyDoesNotChangeLexicographicOrder() {
		// parent 先注册，child 依赖 parent（前置满足）。
		// 关键：即使 child 在"依赖上"排在 parent 后面，输出仍是纯字典序，
		// 而不是拓扑序（parent 在前）。这里 parent="parent" > child="child"，
		// 字典序会让 child 排在 parent 前面——如果代码偷偷做了拓扑排序就会反过来。
		List<DiscoveredFabricMod> input = new ArrayList<>();
		input.add(mod("parent", null));
		input.add(mod("child", Map.of("parent", "*")));

		Result result = FabricLoadOrder.order(input);

		List<String> ids = result.ordered().stream().map(DiscoveredFabricMod::id).toList();
		// child < parent 字典序，所以 child 在前；这证明 depends 不是排序边
		assertEquals(List.of("child", "parent"), ids,
				"depends 只做存在性校验，不能变成拓扑边——输出必须是字典序");
		assertTrue(result.skipped().isEmpty());
	}

	@Test
	void platformKeysAreTreatedAsSatisfied() {
		// fabricloader / minecraft / java 这些平台 key 不参与缺前置判定
		List<DiscoveredFabricMod> input = List.of(
				mod("x", Map.of("fabricloader", ">=0.16", "minecraft", "~1.21", "java", ">=21")));

		Result result = FabricLoadOrder.order(input);
		assertEquals(1, result.ordered().size());
		assertTrue(result.skipped().isEmpty(), "平台级依赖 key 不应导致 mod 被跳过");
	}

	@Test
	void duplicateModIdIsReportedAsConflictNotSilentlySwallowed() {
		// 同一个 id "jei" 出现在两个不同 jar 里 → 必须明确记一条冲突
		List<DiscoveredFabricMod> input = List.of(
				modWithJar("jei", "jei-1.jar"),
				modWithJar("jei", "jei-2.jar"),
				modWithJar("solo", "solo.jar"));

		Result result = FabricLoadOrder.order(input);

		assertEquals(1, result.conflicts().size(), "重复 id 应产生一条冲突记录");
		Result.ModConflict c = result.conflicts().get(0);
		assertEquals("jei", c.modId());
		assertTrue(c.jarNames().contains("jei-1.jar") && c.jarNames().contains("jei-2.jar"),
				"冲突记录要指出是哪两个 jar 撞了 id");
		// solo 不冲突
		assertTrue(result.conflicts().stream().noneMatch(x -> x.modId().equals("solo")));
	}
}
