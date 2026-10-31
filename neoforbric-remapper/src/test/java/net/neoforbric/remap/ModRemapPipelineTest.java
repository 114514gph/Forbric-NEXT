package net.neoforbric.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ModRemapPipeline} 行为测试。
 *
 * <p>不注水：用 {@link ZipOutputStream} 动态伪造 mod jar，真实验证
 * "版本不匹配 → 走管道产出新 jar"、"版本匹配 → 原样返回"、"没有 MC 依赖 → 不重映射"。
 */
class ModRemapPipelineTest {

	/** 在临时路径写一个 jar，entry 名 → 内容。 */
	private static void writeJar(Path jarPath, Map<String, byte[]> entries) throws IOException {
		Files.createDirectories(jarPath.getParent());
		try (ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(jarPath))) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				zout.putNextEntry(new ZipEntry(e.getKey()));
				zout.write(e.getValue());
				zout.closeEntry();
			}
		}
	}

	/** 读 jar 里所有 entry，返回名字 → 字节。 */
	private static Map<String, byte[]> readJar(Path jar) throws IOException {
		Map<String, byte[]> out = new HashMap<>();
		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				out.put(e.getName(), zin.readAllBytes());
			}
		}
		return out;
	}

	@Test
	void extractsMcVersionFromConstraint() {
		// ~1.20.4
		assertEquals("1.20.4", ModRemapPipeline.extractMcVersion(
				"{\"depends\":{\"minecraft\":\"~1.20.4\"}}"));
		// >=1.21.1
		assertEquals("1.21.1", ModRemapPipeline.extractMcVersion(
				"{\"depends\":{\"minecraft\":\">=1.21.1\"}}"));
		// 数组形式
		assertEquals("1.20.1", ModRemapPipeline.extractMcVersion(
				"{\"depends\":{\"minecraft\":[\">=1.20.1\", \"<1.21\"]}}"));
		// 没有 minecraft 依赖
		assertNull(ModRemapPipeline.extractMcVersion("{\"depends\":{\"fabricloader\":\"*\"}}"));
	}

	@Test
	void remapsWhenSourceVersionDiffersFromTarget(@TempDir Path tmp) throws IOException {
		Path modJar = tmp.resolve("mods").resolve("old-mod.jar");
		writeJar(modJar, Map.of(
				"fabric.mod.json",
				("{\"id\":\"old-mod\",\"version\":\"1.0.0\","
						+ "\"depends\":{\"minecraft\":\"~1.20.4\"}}").getBytes(StandardCharsets.UTF_8),
				"com/example/old/Thing.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0}
		));

		Path outJar = ModRemapPipeline.remapModJar(modJar, "26.3");

		// 应该产出一个新 jar，而不是原 jar
		assertFalse(outJar.equals(modJar), "版本不匹配时应输出重映射后的新 jar");
		assertTrue(Files.exists(outJar), "输出 jar 应存在");

		Map<String, byte[]> outEntries = readJar(outJar);
		assertTrue(outEntries.containsKey("fabric.mod.json"), "重映射后的 jar 应保留 fabric.mod.json");
		assertTrue(outEntries.containsKey("com/example/old/Thing.class"), "重映射后的 jar 应保留 class 条目");
	}

	@Test
	void returnsOriginalJarWhenVersionMatches(@TempDir Path tmp) throws IOException {
		Path modJar = tmp.resolve("mods").resolve("current-mod.jar");
		writeJar(modJar, Map.of(
				"fabric.mod.json",
				("{\"id\":\"current\",\"version\":\"1.0.0\","
						+ "\"depends\":{\"minecraft\":\"~26.3\"}}").getBytes(StandardCharsets.UTF_8)
		));

		Path outJar = ModRemapPipeline.remapModJar(modJar, "26.3");
		assertSame(modJar, outJar, "源==目标时应原样返回原 jar 路径");
	}

	@Test
	void skipsRemapWhenNoMinecraftDependency(@TempDir Path tmp) throws IOException {
		Path modJar = tmp.resolve("mods").resolve("independent.jar");
		writeJar(modJar, Map.of(
				"fabric.mod.json",
				("{\"id\":\"ind\",\"version\":\"1.0.0\","
						+ "\"depends\":{\"fabricloader\":\">=0.15\"}}").getBytes(StandardCharsets.UTF_8)
		));

		Path outJar = ModRemapPipeline.remapModJar(modJar, "26.3");
		assertSame(modJar, outJar, "没有 depends.minecraft 时不应触发重映射");
	}

	@Test
	void remapJarAppliesMappingsToClassEntry(@TempDir Path tmp) throws IOException {
		// 直接测 remapJar：往 jar 里塞一个真编译出来的 class，再给它一条类映射，验证 class 被改写
		Path src = tmp.resolve("src");
		Path out = tmp.resolve("compile-out");
		Files.createDirectories(src.resolve("com/example/old"));
		Files.createDirectories(out);
		Files.writeString(src.resolve("com/example/old/Old.java"),
				"package com.example.old; public class Old {}");
		Files.writeString(src.resolve("com/example/old/UsesOld.java"),
				"package com.example.old; public class UsesOld { public Old make() { return new Old(); } }");

		// 编译 UsesOld + Old
		JavaCompileHelper.compile(src, out);

		byte[] usesOldBytes = Files.readAllBytes(out.resolve("com/example/old/UsesOld.class"));

		Path modJar = tmp.resolve("in.jar");
		writeJar(modJar, Map.of(
				"fabric.mod.json",
				"{\"id\":\"x\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8),
				"com/example/old/UsesOld.class", usesOldBytes
		));

		RemapMappings mappings = new RemapMappings();
		mappings.addClassMapping("com.example.old.Old", "com.example.new.New");

		Path remappedJar = tmp.resolve("out.jar");
		ModRemapPipeline.remapJar(modJar, remappedJar, mappings);

		Map<String, byte[]> entries = readJar(remappedJar);
		byte[] remappedClass = entries.get("com/example/old/UsesOld.class");
		assertNotNull(remappedClass);
		String oldInternal = "com/example/old/Old";
		String newInternal = "com/example/new/New";
		assertFalse(indexOf(remappedClass, oldInternal.getBytes(StandardCharsets.UTF_8)) >= 0,
				"管道走过后 class 里不应再有老类名");
		assertTrue(indexOf(remappedClass, newInternal.getBytes(StandardCharsets.UTF_8)) >= 0,
				"管道走过后 class 里应出现新类名");
	}

	private static int indexOf(byte[] hay, byte[] needle) {
		outer:
		for (int i = 0; i <= hay.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (hay[i + j] != needle[j]) continue outer;
			}
			return i;
		}
		return -1;
	}
}
