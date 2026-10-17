package net.forbric.next.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import net.forbric.next.api.Ecosystem;
import net.forbric.next.fabric.FabricModDiscovery.DiscoveredFabricMod;

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
}
