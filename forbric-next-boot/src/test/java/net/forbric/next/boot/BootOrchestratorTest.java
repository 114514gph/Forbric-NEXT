package net.forbric.next.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;

/**
 * {@link DefaultBootOrchestrator} 的行为测试。
 *
 * <p>验证编排契约：
 * <ol>
 *   <li>discover → resolve → runPhase 三步被正确按序调用。</li>
 *   <li>每个 loader 只拿到自己 discover 出来的 candidates（不交叉传递）。</li>
 *   <li>一个 loader 在某个 phase 失败，不阻断其它 loader / 其它 phase 的推进。</li>
 * </ol>
 */
class BootOrchestratorTest {

	private static LoadContext ctx(Path dir) {
		return new LoadContext(dir, dir.resolve("mods"), dir.resolve("game.jar"),
				LoadContext.Environment.SERVER);
	}

	/** 造一个带 jarPath 的 candidate。 */
	private static ModCandidate cand(String id, Path dir) {
		return new ModCandidate(id, "1.0.0", Ecosystem.FABRIC, dir.resolve(id + ".jar"), List.of());
	}

	@Test
	void drivesDiscoverResolveRunPhaseInOrder(@TempDir Path dir) {
		DefaultBootOrchestrator orch = new DefaultBootOrchestrator();

		List<ModCandidate> fabricCands = List.of(cand("mod-a", dir), cand("mod-b", dir));
		FakeModLoader fabric = new FakeModLoader(Ecosystem.FABRIC, fabricCands);
		FakeModLoader forge = new FakeModLoader(Ecosystem.MINECRAFTFORGE, List.of());

		orch.installLoaders(List.of(fabric, forge));
		orch.discover(ctx(dir));
		orch.resolve();

		// discover 被调过一次
		assertEquals(1, fabric.discoverCalls);
		assertEquals(1, forge.discoverCalls);
		// resolve 收到的就是自己 discover 出来的那份（不是别人的，也不是 null）
		// 注：orchestrator 内部会包一层新 ArrayList，所以比内容不比引用
		assertEquals(fabricCands, fabric.resolvedCandidates,
				"resolve 必须把本 loader discover 出的 candidates 原样还回来");
		assertEquals(0, forge.resolvedCandidates.size());

		// 跑生命周期
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(orch::runLifecycle);

		// 9 个 phase 全部推进到每个 loader
		assertEquals(LoadPhase.values().length, fabric.phasesRun.size());
		assertEquals(LoadPhase.values().length, forge.phasesRun.size());
		// phase 顺序就是枚举声明顺序
		assertEquals(List.of(LoadPhase.values()), fabric.phasesRun);
	}

	@Test
	void doesNotCrossPassCandidatesBetweenLoaders(@TempDir Path dir) {
		DefaultBootOrchestrator orch = new DefaultBootOrchestrator();

		List<ModCandidate> fabricCands = List.of(cand("fabric-mod", dir));
		List<ModCandidate> forgeCands = List.of(cand("forge-mod", dir));
		FakeModLoader fabric = new FakeModLoader(Ecosystem.FABRIC, fabricCands);
		FakeModLoader forge = new FakeModLoader(Ecosystem.MINECRAFTFORGE, forgeCands);

		orch.installLoaders(List.of(fabric, forge));
		orch.discover(ctx(dir));
		orch.resolve();

		// fabric 拿到的是 fabric 自己的，forge 拿到的是 forge 自己的，绝不串
		assertEquals(List.of("fabric-mod"),
				fabric.resolvedCandidates.stream().map(ModCandidate::id).toList());
		assertEquals(List.of("forge-mod"),
				forge.resolvedCandidates.stream().map(ModCandidate::id).toList());
	}

	@Test
	void oneLoaderFailureDoesNotBlockOthers(@TempDir Path dir) {
		DefaultBootOrchestrator orch = new DefaultBootOrchestrator();

		FakeModLoader healthy = new FakeModLoader(Ecosystem.FABRIC, List.of());
		FakeModLoader broken = new FakeModLoader(Ecosystem.MINECRAFTFORGE, List.of());
		broken.throwOnRunPhase = true;

		orch.installLoaders(List.of(healthy, broken));
		orch.discover(ctx(dir));
		orch.resolve();

		// runLifecycle 最后会抛出第一个错误（让上层知道）
		Exception thrown = assertThrows(Exception.class, orch::runLifecycle);
		assertTrue(thrown.getMessage().contains("炸了"), "抛出的应是 broken loader 的错误");

		// 但 healthy loader 的 9 个 phase 一个都没少——失败没有阻断它
		assertEquals(LoadPhase.values().length, healthy.phasesRun.size(),
				"一个 loader 失败不应阻断另一个 loader 跑完所有 phase");
		// broken loader 自己也被推进了所有 phase（只是每个都报错）
		assertEquals(LoadPhase.values().length, broken.phasesRun.size());
	}

	@Test
	void globalCatalogAggregatesAcrossLoadersForEntrypointLookup(@TempDir Path dir) {
		DefaultBootOrchestrator orch = new DefaultBootOrchestrator();

		// SpectreLib 同时被 Fabric 和 NeoForge 两个 loader 发现（同一个 id）；
		// CherishedWorlds 只在 Fabric 侧。
		FakeModLoader fabric = new FakeModLoader(Ecosystem.FABRIC,
				List.of(cand("spectrelib", dir), cand("cherishedworlds", dir)));
		FakeModLoader neoforge = new FakeModLoader(Ecosystem.MINECRAFTFORGE,
				List.of(cand("spectrelib", dir)));

		orch.installLoaders(List.of(fabric, neoforge));
		orch.discover(ctx(dir));

		// 全局 catalog 汇总了所有 loader 发现的 mod（含重复 id）
		assertEquals(3, orch.globalCatalog().size(),
				"全局 catalog 应汇总所有 loader 发现的 mod，不按 loader 裁剪");
		// entrypoint 查找是全局的：spectrelib 即便主要由 NeoForge 发现，Fabric 侧也查得到
		assertEquals("spectrelib", orch.findGlobally("spectrelib").id());
		assertEquals("cherishedworlds", orch.findGlobally("cherishedworlds").id());
		assertTrue(orch.findGlobally("does-not-exist") == null, "找不到的 id 返回 null");
	}
}
