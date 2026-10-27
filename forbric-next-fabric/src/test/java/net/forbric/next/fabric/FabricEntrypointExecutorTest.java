package net.forbric.next.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.EntrypointException;
import net.forbric.next.api.Ecosystem;
import net.forbric.next.fabric.FabricEntrypointExecutor.EntrypointFailure;
import net.forbric.next.fabric.FabricModDiscovery.DiscoveredFabricMod;

/**
 * {@link FabricEntrypointExecutor} 的行为测试。
 *
 * <p>验证两件事：
 * <ol>
 *   <li>有公开无参构造器的类 → 反射实例化成功（构造器真的被跑了）。</li>
 *   <li>没有无参构造器的类 → 失败被捕获进返回值列表，而不是抛出去炸掉加载。</li>
 * </ol>
 */
class FabricEntrypointExecutorTest {

	private FabricEntrypointExecutor executor;

	@BeforeEach
	void setUp() {
		// 用测试类自己的 classloader，这样 HappyEntrypoint / BrokenEntrypoint 能被 Class.forName 找到
		executor = new FabricEntrypointExecutor(FabricEntrypointExecutorTest.class.getClassLoader());
		HappyEntrypoint.INSTANTIATED.set(0);
	}

	/** 造一个只在指定组里声明了指定入口类的 mod。 */
	private static DiscoveredFabricMod modWithEntrypoint(String modId, String group, String className) {
		return new DiscoveredFabricMod(modId, "1.0.0", modId, Ecosystem.FABRIC,
				null, Map.of(group, List.of(className)), Map.of());
	}

	@Test
	void instantiatesClassWithPublicNoArgConstructor() {
		DiscoveredFabricMod mod = modWithEntrypoint("happy-mod",
				FabricEntrypointExecutor.MAIN, "net.forbric.next.fabric.HappyEntrypoint");

		List<EntrypointFailure> failures = executor.executeGroup(FabricEntrypointExecutor.MAIN, List.of(mod));

		assertTrue(failures.isEmpty(), "无参构造器的类应实例化成功，不应有失败");
		// 构造器真的被调用了一次——这是"反射实例化成功"的硬证据，不是 assertTrue(true)
		assertEquals(1, HappyEntrypoint.INSTANTIATED.get(), "HappyEntrypoint 的构造器应被执行一次");
	}

	@Test
	void capturesFailureWhenNoNoArgConstructor() {
		DiscoveredFabricMod mod = modWithEntrypoint("broken-mod",
				FabricEntrypointExecutor.MAIN, "net.forbric.next.fabric.BrokenEntrypoint");

		List<EntrypointFailure> failures = executor.executeGroup(FabricEntrypointExecutor.MAIN, List.of(mod));

		// 没有抛异常出去，而是被收集成一条失败记录
		assertEquals(1, failures.size(), "无无参构造器的类应产生一条失败记录");
		EntrypointFailure f = failures.get(0);
		assertEquals("broken-mod", f.modId());
		assertEquals(FabricEntrypointExecutor.MAIN, f.group());
		assertEquals("net.forbric.next.fabric.BrokenEntrypoint", f.className());
	}

	@Test
	void oneBadEntrypointDoesNotBlockOthers() {
		// 同一组里先放坏的，再放好的 → 坏的记失败，好的仍被实例化
		DiscoveredFabricMod bad = modWithEntrypoint("broken-mod",
				FabricEntrypointExecutor.MAIN, "net.forbric.next.fabric.BrokenEntrypoint");
		DiscoveredFabricMod good = modWithEntrypoint("happy-mod",
				FabricEntrypointExecutor.MAIN, "net.forbric.next.fabric.HappyEntrypoint");

		List<EntrypointFailure> failures = executor.executeGroup(
				FabricEntrypointExecutor.MAIN, List.of(bad, good));

		assertEquals(1, failures.size(), "只有坏 mod 记失败");
		assertEquals("broken-mod", failures.get(0).modId());
		assertEquals(1, HappyEntrypoint.INSTANTIATED.get(), "坏 mod 不应阻断后面好 mod 的实例化");
	}

	@Test
	void failureIsReportedAsFabricEntrypointException() {
		DiscoveredFabricMod mod = modWithEntrypoint("broken-mod",
				FabricEntrypointExecutor.MAIN, "net.forbric.next.fabric.BrokenEntrypoint");

		List<EntrypointFailure> failures = executor.executeGroup(FabricEntrypointExecutor.MAIN, List.of(mod));

		assertEquals(1, failures.size());
		// 失败必须用 Fabric 原生的 EntrypointException 包装（而不是裸 RuntimeException），
		// 这样 mod 里 catch (EntrypointException) 才能链接成功。
		assertInstanceOf(EntrypointException.class, failures.get(0).error(),
				"entrypoint 失败应被包装成 net.fabricmc.loader.api.EntrypointException");
	}
}
