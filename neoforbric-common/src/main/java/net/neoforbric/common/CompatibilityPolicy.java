package net.neoforbric.common;

import java.nio.file.Path;

/**
 * 兼容性策略——单个 mod 失败时怎么办。
 *
 * <p>对齐原版 Forbric 的 {@code compatibility-policy} 配置：
 * <ul>
 *   <li><b>continue</b>（默认）：一个 mod 失败不炸整个游戏，记录到 load-report，继续加载其他 mod。</li>
 *   <li><b>fail</b>：遇到第一个失败立即停止启动，打完整错误栈退出。</li>
 * </ul>
 *
 * <p>配置从 {@code .neoforbric.toml} 的 {@code [load] compatibility-policy} 读，
 * 系统属性 {@code forbric.compatibilityPolicy} 可以覆盖。
 */
public enum CompatibilityPolicy {
	/** 继续：记录失败，不中断。 */
	CONTINUE,
	/** 失败即停：遇到第一个错误就退出。 */
	FAIL;

	/** 从系统属性 + 配置文件解析当前策略。 */
	public static CompatibilityPolicy resolve(Path gameDir) {
		// 系统属性优先
		String prop = System.getProperty("forbric.compatibilityPolicy");
		if (prop != null) {
			return "fail".equalsIgnoreCase(prop) ? FAIL : CONTINUE;
		}
		// 读配置文件（极简解析，不引 TOML 库）
		if (gameDir != null) {
			try {
				Path cfg = gameDir.resolve(".neoforbric.toml");
				if (java.nio.file.Files.exists(cfg)) {
					for (String line : java.nio.file.Files.readAllLines(cfg)) {
						if (line.contains("compatibility-policy")) {
							String v = line.split("=")[1].trim().replace("\"", "");
							if ("fail".equals(v)) return FAIL;
						}
					}
				}
			} catch (Exception ignored) {}
		}
		return CONTINUE; // 默认
	}
}
