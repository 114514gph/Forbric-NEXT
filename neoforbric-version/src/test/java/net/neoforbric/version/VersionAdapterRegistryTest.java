package net.neoforbric.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link VersionAdapterRegistry} 的行为测试。
 *
 * <p>注册 4 个测试适配器，验证：
 * <ul>
 *   <li>{@code find("1.21.1")} 精确返回我们注册的那一个；</li>
 *   <li>{@code find("99.99")} 返回 empty。</li>
 * </ul>
 *
 * <p>用唯一版本字符串避免和内置适配器冲突；registry 是静态表，这里只断言
 * "我们注册的那个对象能被精确找回"，不依赖其它测试是否注册过。
 */
class VersionAdapterRegistryTest {

	/** 一个最小可用的 VersionAdapter 桩。 */
	private static VersionAdapter stub(String versionString) {
		MinecraftVersion v = MinecraftVersion.of(versionString, 1, 0, 0, "mojmap", 21);
		return new VersionAdapter() {
			@Override public MinecraftVersion version() { return v; }
			@Override public String resolveClassName(String key) { return "fake." + key; }
			@Override public boolean hasEvent(String eventKey) { return true; }
			@Override public boolean isFunctional() { return false; }
		};
	}

	@Test
	void findsExactRegisteredAdapter() {
		VersionAdapter a1 = stub("1.21.1");
		VersionAdapter a2 = stub("1.20.1");
		VersionAdapter a3 = stub("1.19.4");
		VersionAdapter a4 = stub("26.3");
		VersionAdapterRegistry.register(a1);
		VersionAdapterRegistry.register(a2);
		VersionAdapterRegistry.register(a3);
		VersionAdapterRegistry.register(a4);

		// 精确匹配：找回来的就是我们注册的那个对象本身
		assertTrue(VersionAdapterRegistry.find("1.21.1").isPresent());
		assertSame(a1, VersionAdapterRegistry.find("1.21.1").orElseThrow(),
				"find(\"1.21.1\") 应返回我们注册的 1.21.1 适配器");

		// 其它版本也能各自找对
		assertSame(a2, VersionAdapterRegistry.find("1.20.1").orElseThrow());
		assertSame(a4, VersionAdapterRegistry.find("26.3").orElseThrow());
	}

	@Test
	void returnsEmptyForUnknownVersion() {
		// 99.99 不可能有适配器
		assertTrue(VersionAdapterRegistry.find("99.99").isEmpty(),
				"未注册的版本应返回 empty Optional");
	}
}
