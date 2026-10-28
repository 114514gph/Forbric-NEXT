package net.forbric.next.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 资源包（材质包）兼容层。
 *
 * <p>职责：
 * <ul>
 *   <li>读取 pack.mcmeta，修正 pack_format 版本范围反转的警告（min > max 时自动交换）。</li>
 *   <li>跨版本路径映射：老版本材质包的 assets 路径在新版本可能变了。</li>
 *   <li>包格式版本兼容：老 pack_format 在新 MC 里标记为"过时但可用"。</li>
 * </ul>
 */
public final class ResourcePackCompat {

	private ResourcePackCompat() {}

	/**
	 * 读取材质包的 pack.mcmeta，修正已知问题。
	 *
	 * @param packPath 材质包 zip 路径
	 * @return 修正后的 pack.mcmeta 内容；读不到返回空串
	 */
	public static String readAndFixPackMcmeta(Path packPath) {
		if (!Files.exists(packPath)) return "";
		try (ZipFile zip = new ZipFile(packPath.toFile())) {
			ZipEntry entry = zip.getEntry("pack.mcmeta");
			if (entry == null) return "";
			try (InputStream in = zip.getInputStream(entry)) {
				String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				return fixPackFormatRange(json);
			}
		} catch (IOException e) {
			NextLog.warn("ResourcePackCompat: 读 {} 的 pack.mcmeta 失败: {}", packPath.getFileName(), e.getMessage());
			return "";
		}
	}

	/**
	 * 修正 pack_format 范围反转问题。
	 *
	 * <p>有些材质包写 {@code min_inclusive=34, max_inclusive=33}（min > max），
	 * MC 会打警告。这里自动交换成正确顺序。
	 */
	static String fixPackFormatRange(String json) {
		// 找 min_inclusive 和 max_inclusive
		Pattern minPat = Pattern.compile("\"min_inclusive\"\\s*:\\s*(\\d+)");
		Pattern maxPat = Pattern.compile("\"max_inclusive\"\\s*:\\s*(\\d+)");
		Matcher minM = minPat.matcher(json);
		Matcher maxM = maxPat.matcher(json);
		if (!minM.find() || !maxM.find()) return json;

		int min = Integer.parseInt(minM.group(1));
		int max = Integer.parseInt(maxM.group(1));
		if (min <= max) return json; // 正常范围

		// 反转了，交换
		NextLog.info("ResourcePackCompat: 修正 pack_format 范围反转 ({} > {})，自动交换", min, max);
		json = json.replace(minM.group(0), "\"min_inclusive\": " + max);
		json = json.replace(maxM.group(0), "\"max_inclusive\": " + min);
		return json;
	}
}
