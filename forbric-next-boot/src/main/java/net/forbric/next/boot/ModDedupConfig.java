package net.forbric.next.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.common.NextLog;

/**
 * 读取 {@code mods/forbric-mods.txt}——用户手动指定同名 mod 用哪个生态版本。
 *
 * <p>文件格式（每行一条）：
 * <pre>
 * # 注释行
 * jei=fabric        # JEI 用 Fabric 版
 * journeyneymap=neoforge
 * </pre>
 *
 * <p>如果文件不存在，所有同名 mod 都按先到先得处理。
 */
public final class ModDedupConfig {

	private final Map<String, Ecosystem> preferred = new HashMap<>();

	private ModDedupConfig() {}

	public static ModDedupConfig load(Path modsDir) {
		ModDedupConfig cfg = new ModDedupConfig();
		if (modsDir == null) return cfg;
		Path file = modsDir.resolve("forbric-mods.txt");
		if (!Files.exists(file)) return cfg;

		try {
			for (String line : Files.readAllLines(file)) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				int eq = line.indexOf('=');
				if (eq < 0) continue;
				String modId = line.substring(0, eq).trim();
				String eco = line.substring(eq + 1).trim().toUpperCase();
				try {
					cfg.preferred.put(modId, Ecosystem.valueOf(eco));
				} catch (IllegalArgumentException e) {
					NextLog.warn("forbric-mods.txt: 未知生态 '{}'（mod {}），忽略", eco, modId);
				}
			}
			NextLog.info("forbric-mods.txt: 加载了 {} 条优先选择", cfg.preferred.size());
		} catch (IOException e) {
			NextLog.warn("forbric-mods.txt: 读取失败（{}），按先到先得处理", e.getMessage());
		}
		return cfg;
	}

	public Ecosystem preferredEcosystem(String modId) {
		return preferred.get(modId);
	}
}
