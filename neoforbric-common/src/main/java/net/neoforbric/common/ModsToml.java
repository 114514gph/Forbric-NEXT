package net.neoforbric.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

/**
 * 极简 Forge mods.toml 解析器。
 *
 * <p>只支持 NeoForbric 实际需要的子集：
 * <pre>
 * [[mods]]
 * modId   = "jei"
 * version = "17.0.0.11"
 * displayName = "Just Enough Items"
 * </pre>
 * 不解析 dependencies、features、modLoader 等段——那些在真正接入 Forge runtime 时再补。
 */
public final class ModsToml {

	public record Mod(String modId, String version, String displayName) {}

	private ModsToml() {}

	/** 从 jar 里读指定 entry（META-INF/mods.toml 或 META-INF/neoforge.mods.toml）。 */
	public static Optional<List<Mod>> readFromJar(Path jar, String entryName) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			var entry = zip.getEntry(entryName);
			if (entry == null) return Optional.empty();
			try (InputStream in = zip.getInputStream(entry)) {
				return Optional.of(parse(in));
			}
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/** 逐行状态机解析 [[mods]] 段。 */
	public static List<Mod> parse(InputStream in) throws IOException {
		List<Mod> mods = new ArrayList<>();
		String currentId = null;
		String currentVersion = null;
		String currentName = null;
		boolean inModsSection = false;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;

				// 段头：[[mods]] 开新条目；其他 [[x]] / [x] 收尾并离开
				if (line.startsWith("[[mods]]")) {
					flush(mods, currentId, currentVersion, currentName);
					currentId = null; currentVersion = null; currentName = null;
					inModsSection = true;
					continue;
				}
				if (line.startsWith("[[") || line.startsWith("[")) {
					// 离开 [[mods]] 段，收尾
					if (inModsSection) {
						flush(mods, currentId, currentVersion, currentName);
						currentId = null; currentVersion = null; currentName = null;
						inModsSection = false;
					}
					continue;
				}

				if (!inModsSection) continue;

				// key = "value"
				int eq = line.indexOf('=');
				if (eq < 0) continue;
				String key = line.substring(0, eq).trim();
				String value = stripQuotes(line.substring(eq + 1).trim());

				switch (key) {
					case "modId", "modid" -> currentId = value;
					case "version" -> currentVersion = resolveVersion(value);
					case "displayName", "displayname" -> currentName = value;
				}
			}
			// 文件结束，收尾最后一条
			if (inModsSection) {
				flush(mods, currentId, currentVersion, currentName);
			}
		}
		return mods;
	}

	private static void flush(List<Mod> out, String id, String ver, String name) {
		if (id != null && !id.isEmpty()) {
			out.add(new Mod(id, ver != null ? ver : "1.0.0", name != null ? name : id));
		}
	}

	private static String stripQuotes(String s) {
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	/** Forge 常用 "${file.jarVersion}" 占位符——解析不了就回退 1.0.0。 */
	private static String resolveVersion(String raw) {
		if (raw == null || raw.isEmpty()) return "1.0.0";
		if (raw.startsWith("${")) return "1.0.0";
		return raw;
	}
}
