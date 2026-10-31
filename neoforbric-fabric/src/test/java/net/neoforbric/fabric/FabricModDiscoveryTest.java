package net.neoforbric.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.neoforbric.api.Ecosystem;
import net.neoforbric.fabric.FabricModDiscovery.DiscoveredFabricMod;

/**
 * {@link FabricModDiscovery} 的行为测试。
 *
 * <p>不注水：用 {@link JarOutputStream} 动态伪造 mods/ 目录下的 jar，真实验证
 * "合法 fabric mod 被识别为 FABRIC / quilt mod 被识别为 QUILT / 坏 jar 被跳过不崩"。
 */
class FabricModDiscoveryTest {

	/** 在临时目录里写一个 jar，里面塞若干 entry（路径 → 内容）。 */
	private static void writeJar(Path jarPath, Map<String, String> entries) throws IOException {
		try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
			for (Map.Entry<String, String> e : entries.entrySet()) {
				jos.putNextEntry(new JarEntry(e.getKey()));
				jos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
				jos.closeEntry();
			}
		}
	}

	@Test
	void discoversFabricModAsFabric(@TempDir Path modsDir) throws IOException {
		// 合法 fabric.mod.json → 应被发现为 FABRIC
		writeJar(modsDir.resolve("cool-mod.jar"), Map.of(
				"fabric.mod.json",
				"{\"id\":\"cool-mod\",\"version\":\"1.2.3\",\"name\":\"Cool Mod\","
						+ "\"entrypoints\":{\"main\":[\"com.example.CoolMod\"]}}"));

		List<DiscoveredFabricMod> mods = FabricModDiscovery.discover(modsDir);

		assertEquals(1, mods.size(), "只有一个合法 jar，应只发现一个 mod");
		DiscoveredFabricMod m = mods.get(0);
		assertEquals("cool-mod", m.id());
		assertEquals("1.2.3", m.version());
		assertEquals(Ecosystem.FABRIC, m.ecosystem(), "只带 fabric.mod.json 的应为 FABRIC");
		assertTrue(m.entrypoints().containsKey("main"), "应读出 entrypoints.main");
	}

	@Test
	void discoversQuiltModAsQuilt(@TempDir Path modsDir) throws IOException {
		// 带 quilt.mod.json（含 quilt_loader 节点）→ 应被识别为 QUILT
		writeJar(modsDir.resolve("quilted.jar"), Map.of(
				"quilt.mod.json",
				"{\"quilt_loader\":{\"id\":\"quilted\",\"version\":\"0.5.0\","
						+ "\"entrypoints\":{\"main\":[\"com.quilted.Main\"]}}}"));

		List<DiscoveredFabricMod> mods = FabricModDiscovery.discover(modsDir);

		assertEquals(1, mods.size());
		DiscoveredFabricMod m = mods.get(0);
		assertEquals("quilted", m.id());
		assertEquals(Ecosystem.QUILT, m.ecosystem(), "带 quilt.mod.json 的应为 QUILT");
	}

	@Test
	void skipsBrokenJarWithoutCrashing(@TempDir Path modsDir) throws IOException {
		// 一个合法 fabric mod
		writeJar(modsDir.resolve("good.jar"), Map.of(
				"fabric.mod.json", "{\"id\":\"good\",\"version\":\"1.0.0\"}"));
		// 一个坏 jar：根本不是 zip，写一坨随机字节
		Path broken = modsDir.resolve("broken.jar");
		Files.write(broken, "this is NOT a zip file at all".getBytes(StandardCharsets.UTF_8));

		List<DiscoveredFabricMod> mods = FabricModDiscovery.discover(modsDir);

		// 坏 jar 被跳过，好 jar 仍然被发现，整个过程不抛异常
		assertEquals(1, mods.size(), "坏 jar 应被跳过，只剩合法 mod");
		assertEquals("good", mods.get(0).id());
	}

	@Test
	void missingModsDirReturnsEmpty(@TempDir Path root) {
		// 目录不存在 → 返回空列表而不是崩
		Path missing = root.resolve("does-not-exist");
		List<DiscoveredFabricMod> mods = FabricModDiscovery.discover(missing);
		assertTrue(mods.isEmpty());
	}

	@Test
	void jarWithoutModMetadataIsSkipped(@TempDir Path modsDir) throws IOException {
		// 是合法 zip，但既没有 fabric.mod.json 也没有 quilt.mod.json → 跳过
		writeJar(modsDir.resolve("plain.jar"), Map.of(
				"META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"));

		List<DiscoveredFabricMod> mods = FabricModDiscovery.discover(modsDir);
		assertTrue(mods.isEmpty(), "没有任何 mod 元数据的 jar 应被跳过");
	}

	/** 把若干 entry（路径 → 内容字节）写进一个 jar，返回 jar 的字节。 */
	private static byte[] buildJarBytes(Map<String, byte[]> entries) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (JarOutputStream jos = new JarOutputStream(baos)) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				jos.putNextEntry(new JarEntry(e.getKey()));
				jos.write(e.getValue());
				jos.closeEntry();
			}
		}
		return baos.toByteArray();
	}

	@Test
	void discoversNestedJijModWithParentJar(@TempDir Path modsDir) throws IOException {
		// 嵌套 jar 自身的 fabric.mod.json
		byte[] nestedJar = buildJarBytes(Map.of("fabric.mod.json",
				"{\"id\":\"mixinextras\",\"version\":\"0.2.0\"}".getBytes(StandardCharsets.UTF_8)));
		// 外层 mod jar：自己的 fabric.mod.json + META-INF/jars/mixinextras.jar（裸字节）
		byte[] outerJar = buildJarBytes(Map.of(
				"fabric.mod.json",
				"{\"id\":\"loader\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8),
				"META-INF/jars/mixinextras.jar", nestedJar));
		Files.write(modsDir.resolve("loader.jar"), outerJar);

		List<DiscoveredFabricMod> mods = FabricModDiscovery.discover(modsDir);

		// 外层 mod + 嵌套 mod 都应被发现
		assertEquals(2, mods.size(), "外层 mod 与它 JiJ 的嵌套 mod 都应进入候选列表");
		DiscoveredFabricMod outer = mods.stream().filter(m -> m.id().equals("loader")).findFirst().orElseThrow();
		DiscoveredFabricMod nested = mods.stream().filter(m -> m.id().equals("mixinextras")).findFirst().orElseThrow();
		assertTrue(outer.parentJar() == null, "外层 mod 的 parentJar 应为 null");
		assertEquals(outer.jarPath(), nested.parentJar(),
				"嵌套 mod 的 parentJar 必须指向外层 mod jar");
	}

	@Test
	void readsDeclaredMixinConfigsFromFabricModJson(@TempDir Path modsDir) throws IOException {
		// "mixins" 字段：既有纯字符串，也有对象形式 {"config": ...}
		writeJar(modsDir.resolve("modmenu.jar"), Map.of(
				"fabric.mod.json",
				"{\"id\":\"modmenu\",\"version\":\"1.0.0\",\"mixins\":["
						+ "\"modmenu.mixins.json\","
						+ "{\"config\":\"modmenu.mixins.modmenu.json\",\"environment\":\"*\"}]}"));

		List<DiscoveredFabricMod> mods = FabricModDiscovery.discover(modsDir);

		assertEquals(1, mods.size());
		List<String> mixins = mods.get(0).mixinConfigs();
		assertEquals(2, mixins.size(), "字符串形式与对象形式的 config 名都应被读出");
		assertTrue(mixins.contains("modmenu.mixins.json"));
		assertTrue(mixins.contains("modmenu.mixins.modmenu.json"));
	}
}
