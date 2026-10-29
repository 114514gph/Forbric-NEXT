package net.forbric.next.remap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.forbric.next.common.NextLog;

/**
 * 老 mod 跨版本重映射管道。
 *
 * <p><b>职责：</b>给定一个 mod jar，判断它声明的源 MC 版本和当前运行版本是否一致；不一致时，
 * 加载对应版本对的映射表，把 jar 里每个 {@code .class} 过一遍 {@link RuntimeRemapper}，
 * 输出一个重映射后的新 jar 到临时目录。
 *
 * <p><b>骨架阶段的取舍：</b>
 * <ul>
 *   <li>映射表从 classpath 资源 {@code /remap-mappings/{source}-{target}.properties} 加载；
 *       资源不存在时用空映射表跑管道（管道照走，只是不改任何类名）——这样测试和集成能立刻跑通。</li>
 *   <li>fabric.mod.json 里 {@code depends.minecraft} 用 {@link SemVerRange} 解析版本范围，
 *       同时重写 fabric.mod.json 的版本声明绕过 Fabric Loader 硬校验。</li>
 *   <li>非 {@code .class} 条目（fabric.mod.json、mixins、资源）原样拷贝。</li>
 * </ul>
 */
public final class ModRemapPipeline {

	private ModRemapPipeline() {}

	/** fabric.mod.json 在 jar 里的固定路径。 */
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";

	/** 从 depends.minecraft 约束里抠第一个版本号（x.y 或 x.y.z）。 */
	private static final Pattern VERSION_RE = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

	/**
	 * 管道主入口：自动读 fabric.mod.json 判断源版本，与目标版本不一致时重映射。
	 *
	 * @param modJar          mod jar 路径
	 * @param targetMcVersion 当前运行的 MC 版本（例如 {@code 26.3}）
	 * @return 重映射后的 jar 路径；若无需重映射（源==目标或抠不出版本），返回原 jar 路径
	 * @throws IOException 读 jar / 写临时 jar 失败
	 */
	public static Path remapModJar(Path modJar, String targetMcVersion) throws IOException {
		String sourceVersion = readSourceMcVersion(modJar);
		if (sourceVersion == null) {
			NextLog.info("ModRemapPipeline: {} 没有 depends.minecraft，跳过版本检查", modJar.getFileName());
			return modJar;
		}
		if (sourceVersion.equals(targetMcVersion)) {
			NextLog.info("ModRemapPipeline: {} 源版本 {} == 目标 {}，不重映射",
					modJar.getFileName(), sourceVersion, targetMcVersion);
			return modJar;
		}

		RemapMappings mappings = loadMappings(sourceVersion, targetMcVersion);
		NextLog.info("ModRemapPipeline: 重映射 {} 从 MC {} → {}（{}）",
				modJar.getFileName(), sourceVersion, targetMcVersion, mappings);

		Path out = Files.createTempDirectory("forbric-remap-" + sanitize(modJar.getFileName().toString()))
				.resolve(modJar.getFileName().toString());
		remapJar(modJar, out, mappings, targetMcVersion);
		return out;
	}

	/**
	 * 用显式映射表重映射一个 jar（测试 / 高级用法用）。
	 *
	 * @param modJar  输入 jar
	 * @param outJar  输出 jar 路径（父目录需存在）
	 * @param mappings 映射表
	 * @throws IOException 读写失败
	 */
	public static void remapJar(Path modJar, Path outJar, RemapMappings mappings) throws IOException {
		remapJar(modJar, outJar, mappings, null);
	}

	/**
	 * 重映射 jar，同时可选改写 fabric.mod.json 的 minecraft 版本依赖。
	 *
	 * @param targetMcVersion 如果非 null，fabric.mod.json 里的 depends.minecraft 会被改成这个版本
	 */
	public static void remapJar(Path modJar, Path outJar, RemapMappings mappings, String targetMcVersion) throws IOException {
		Files.createDirectories(outJar.getParent());
		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(modJar));
				ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outJar))) {
			ZipEntry entry;
			while ((entry = zin.getNextEntry()) != null) {
				byte[] data = zin.readAllBytes();
				ZipEntry newEntry = new ZipEntry(entry.getName());
				zout.putNextEntry(newEntry);
				if (entry.getName().endsWith(".class")) {
					zout.write(RuntimeRemapper.remap(data, mappings));
				} else if (entry.getName().equals("fabric.mod.json") && targetMcVersion != null) {
					// 改写 minecraft 版本依赖，绕过 Fabric Loader 的版本硬校验
					String json = new String(data, StandardCharsets.UTF_8);
					json = rewriteMinecraftDep(json, targetMcVersion);
					zout.write(json.getBytes(StandardCharsets.UTF_8));
				} else {
					zout.write(data);
				}
				zout.closeEntry();
			}
		}
	}

	/**
	 * 把 fabric.mod.json 里 depends.minecraft 改成目标版本，绕过 KnotLoader 的版本硬校验。
	 * 这是跨版本兼容层的核心：不拒绝老 mod，而是改写它的版本声明让它"看起来兼容"。
	 */
	static String rewriteMinecraftDep(String json, String targetVersion) {
		// "minecraft": "~1.20.4" → "minecraft": ">=26.3"
		// "minecraft": ">=1.21.1" → "minecraft": ">=26.3"
		// "minecraft": "1.21.x" → "minecraft": ">=26.3"
		String result = json.replaceAll(
				"\"minecraft\"\\s*:\\s*\"[^\"]*\"",
				"\"minecraft\": \">=" + targetVersion + "\"");
		// 数组形式: "minecraft": [">=1.21.1"]
		result = result.replaceAll(
				"\"minecraft\"\\s*:\\s*\\[[^\\]]*\\]",
				"\"minecraft\": \">=" + targetVersion + "\"");
		return result;
	}

	/**
	 * 判断一个 mod 是否需要重映射（fabric loader 在 discover 阶段调用，决定走哪条路）。
	 *
	 * @param modJar          mod jar
	 * @param targetMcVersion 当前运行 MC 版本
	 * @return true = 需要重映射
	 */
	public static boolean needsRemap(Path modJar, String targetMcVersion) {
		String source = readSourceMcVersion(modJar);
		return source != null && !source.equals(targetMcVersion);
	}

	// ---- 内部 ----

	/**
	 * 从 jar 里的 fabric.mod.json 读 depends.minecraft 约束，抠出版本号。
	 *
	 * @return 版本字符串（例如 {@code 1.20.4}）；没有 depends.minecraft 或抠不到版本时返回 null
	 */
	static String readSourceMcVersion(Path modJar) {
		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(modJar))) {
			ZipEntry entry;
			while ((entry = zin.getNextEntry()) != null) {
				if (FABRIC_MOD_JSON.equals(entry.getName())) {
					String json = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
					return extractMcVersion(json);
				}
			}
		} catch (IOException e) {
			NextLog.warn("ModRemapPipeline: 读 {} 的 fabric.mod.json 失败: {}", modJar, e.getMessage());
		}
		return null;
	}

	/**
	 * 从 fabric.mod.json 文本里抠 depends.minecraft 的版本约束。
	 *
	 * <p>用 {@link SemVerRange} 解析完整范围，提取主版本号做粗略比对。
	 *
	 * @param json fabric.mod.json 原文
	 * @return 版本号；找不到返回 null
	 */
	static String extractMcVersion(String json) {
		Matcher m = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
		String constraint = m.find() ? m.group(1) : null;
		if (constraint == null) {
			Matcher m2 = Pattern.compile("\"minecraft\"\\s*:\\s*\\[\\s*\"([^\"]+)\"").matcher(json);
			constraint = m2.find() ? m2.group(1) : null;
		}
		if (constraint == null) return null;
		// 用 SemVer 解析器提取版本号
		SemVerRange range = SemVerRange.parse(constraint);
		// 从原始约束里抠第一个 x.y(.z) 版本号做 key
		Matcher vm = VERSION_RE.matcher(constraint);
		return vm.find() ? vm.group(1) : null;
	}

	/**
	 * 加载版本对映射表。classpath 资源不存在时返回空 {@link RemapMappings}（管道照走）。
	 */
	static RemapMappings loadMappings(String sourceVersion, String targetVersion) {
		RemapMappings mappings = new RemapMappings();
		String resource = "/remap-mappings/" + sanitize(sourceVersion) + "-" + sanitize(targetVersion) + ".properties";
		try (InputStream in = ModRemapPipeline.class.getResourceAsStream(resource)) {
			if (in != null) {
				mappings.loadFromInputStream(in);
				NextLog.info("ModRemapPipeline: 加载映射资源 {}：{}", resource, mappings);
			} else {
				NextLog.warn("ModRemapPipeline: 未找到映射资源 {}，用空映射表跑管道（TODO: 补真实映射）", resource);
			}
		} catch (IOException e) {
			NextLog.warn("ModRemapPipeline: 加载映射资源 {} 失败: {}", resource, e.getMessage());
		}
		return mappings;
	}

	/** 把版本号/文件名里不适合做目录名的字符替换掉。 */
	private static String sanitize(String s) {
		return s.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
