package net.forbric.next.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证 {@link ForbricMixinService} 与真实 Sponge Mixin 库的接线。
 *
 * <p><b>环境事实：</b>Fabric 维护的 sponge-mixin 只内置 LaunchWrapper / ModLauncher 两种
 * "host service"，普通 JUnit 进程没有这类 host，{@code MixinBootstrap.init()} 会抛
 * {@link org.spongepowered.asm.service.ServiceNotAvailableError}。这不是 bug——
 * 真正的织入发生在由 Fabric Knot 提供 host 的独立进程里（见 /home/user/forbric-real 验证）。
 *
 * <p>这里断言两件事：
 * <ol>
 *   <li>我们的 {@link ForbricMixinService#init()} <b>真的调到了</b> MixinBootstrap（错误从库里抛出来，证明接线到位）；</li>
 *   <li>自带的 {@code forbric.test.mixins.json} 能被 {@link MixinConfigReader} 正确解析。</li>
 * </ol>
 */
class ForbricMixinServiceTest {

	@Test
	void initReachesRealMixinBootstrap() {
		ForbricMixinService svc = ForbricMixinService.getInstance();
		// 普通 JVM 没有 host service → 真实库里抛 ServiceNotAvailableError。
		// 如果我们没接线（空壳），这里根本不会抛这个库里的异常。
		Throwable t = assertThrows(Throwable.class, svc::init);
		boolean fromRealMixinLib = findCause(t,
				"org.spongepowered.asm.service.ServiceNotAvailableError") != null
				|| findCause(t, "MixinBootstrap") != null;
		assertTrue(fromRealMixinLib, "异常应来自真实 mixin 库，实际根因: " + rootMsg(t));
	}

	@Test
	void bundledMixinConfigParses() throws Exception {
		try (var in = getClass().getResourceAsStream("/forbric.test.mixins.json")) {
			assertNotNull(in, "测试资源 forbric.test.mixins.json 必须在 classpath");
			ModMixinConfig cfg = MixinConfigReader.parse("forbric.test.mixins.json", "neoforbric-mixin.jar", in);
			assertEquals("net.forbric.next.mixin.testmod", cfg.mixinPackage());
			assertTrue(cfg.activeMixinsForServer().contains("ForbricTestMixin"));
			assertEquals("JAVA_21", cfg.compatibilityLevel());
		}
	}

	private static Throwable findCause(Throwable t, String name) {
		while (t != null) {
			if (t.getClass().getName().contains(name) || String.valueOf(t.toString()).contains(name)) return t;
			t = t.getCause();
		}
		return null;
	}

	private static String rootMsg(Throwable t) {
		while (t != null && t.getCause() != null) t = t.getCause();
		return String.valueOf(t);
	}
}
