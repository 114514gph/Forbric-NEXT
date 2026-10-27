package net.forbric.next.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.forbric.next.common.NextLog;

/**
 * 从 mod jar 里读取并解析 {@code .mixins.json}。
 *
 * <p>只负责"读文件 → 结构化 record"，不碰 MixinEnvironment（那是 {@link ForbricMixinService} 的事）。
 * 这样解析逻辑可以脱离真实 MC 环境做单测。
 */
public final class MixinConfigReader {

	private MixinConfigReader() {}

	/**
	 * 解析一个 mixins.json 文本。
	 *
	 * @param configName   配置文件名（注册到环境用的 key）
	 * @param modJarPath   来源 jar 路径（打日志用）
	 * @param jsonText     文件内容
	 * @return 结构化配置
	 */
	public static ModMixinConfig parse(String configName, String modJarPath, String jsonText) {
		Map<String, Object> root = MixinJson.parseObject(jsonText);

		String pkg = str(root, "package");
		if (pkg == null || pkg.isEmpty()) {
			throw new IllegalStateException("mixins.json 缺少必填字段 \"package\": " + configName);
		}
		return new ModMixinConfig(
				configName,
				modJarPath,
				pkg,
				strList(root, "mixins"),
				strList(root, "client"),
				strList(root, "server"),
				str(root, "plugin"),
				str(root, "refmap"),
				str(root, "compatibilityLevel")
		);
	}

	/**
	 * 从输入流读取并解析（流会被关闭）。
	 */
	public static ModMixinConfig parse(String configName, String modJarPath, InputStream in) throws IOException {
		try (in) {
			return parse(configName, modJarPath, new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> strList(Map<String, Object> root, String key) {
		Object v = root.get(key);
		if (!(v instanceof List<?> list)) return List.of();
		List<String> out = new ArrayList<>(list.size());
		for (Object o : list) {
			if (o instanceof String s) out.add(s);
		}
		return List.copyOf(out);
	}

	private static String str(Map<String, Object> root, String key) {
		Object v = root.get(key);
		return v instanceof String s ? s : null;
	}

	/**
	 * 打一条人类可读的配置摘要日志，便于真实验证时在 server log 里确认。
	 */
	public static void logSummary(ModMixinConfig cfg) {
		NextLog.info("  mixins.json[{}] pkg={} common={} server={} client={} plugin={} compat={}",
				cfg.configName(), cfg.mixinPackage(),
				cfg.commonMixins(), cfg.serverMixins(), cfg.clientMixins(),
				cfg.pluginClassName(), cfg.compatibilityLevel());
	}
}
