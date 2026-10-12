package net.forbric.next.fabric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.ModCandidate;

/**
 * Quilt mod 识别与元数据读取器。
 *
 * <p><b>为什么单独一个类：</b>Quilt 是 Fabric 的分支，mod 格式高度兼容——都带
 * {@code fabric.mod.json}，Quilt 额外带 {@code quilt.mod.json}。我们不想改动现有
 * {@link FabricModLoader} 的任何加载逻辑（任务约束：最小侵入），所以把"识别 + 元数据解析"
 * 抽成这个独立工具类，Fabric 加载路径后续可以直接调用它。
 *
 * <p><b>识别规则（与 Fabric 不混淆）：</b>
 * <ul>
 *   <li>jar 根目录同时存在 {@code quilt.mod.json} → 判定为 <b>Quilt mod</b>（catalog 标记 QUILT）。</li>
 *   <li>只有 {@code fabric.mod.json}，没有 {@code quilt.mod.json} → 判定为纯 <b>Fabric mod</b>（现有逻辑不变）。</li>
 * </ul>
 *
 * <p><b>元数据解析：</b>
 * <ul>
 *   <li>{@code quilt.mod.json} 的 id / version / entrypoints 嵌套在 {@code quilt_loader} 对象下。</li>
 *   <li>{@code fabric.mod.json} 的这些字段在顶层。</li>
 *   <li>Quilt mod 优先读 {@code quilt_loader}，缺失的字段 fallback 到 {@code fabric.mod.json} 顶层。</li>
 * </ul>
 *
 * <p>entrypoint 的执行、mixin 配置的注册，都沿用 Fabric 同一套流程——本类只负责"认得它、读出它叫什么、
 * 有哪些 entrypoint 组"，不负责执行。
 */
public final class QuiltModDetector {

	/** fabric.mod.json 在 jar 里的固定路径。 */
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";
	/** quilt.mod.json 在 jar 里的固定路径。 */
	private static final String QUILT_MOD_JSON = "quilt.mod.json";

	private QuiltModDetector() {}

	/**
	 * Quilt/Fabric mod 的中性元数据。
	 *
	 * @param id         mod id
	 * @param version    版本字符串
	 * @param entrypoints entrypoint 组 → 入口类列表（组名如 main / client / server）
	 * @param ecosystem  识别出的生态系（QUILT 或 FABRIC）
	 */
	public record QuiltModMetadata(String id,
								   String version,
								   Map<String, List<String>> entrypoints,
								   Ecosystem ecosystem) {
	}

	/**
	 * 判断一个 mod jar 是否为 Quilt mod（以是否存在 quilt.mod.json 为准）。
	 *
	 * @param jar mod jar 路径
	 * @return true = 带 quilt.mod.json，按 Quilt 处理
	 */
	public static boolean isQuiltMod(Path jar) {
		try (JarFile jf = new JarFile(jar.toFile())) {
			return jf.getEntry(QUILT_MOD_JSON) != null;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * 识别并读取 mod 元数据。
	 *
	 * <p>流程：先看有没有 {@code quilt.mod.json}；有则按 Quilt 解析（id/version/entrypoints 取自
	 * {@code quilt_loader}，缺字段时回退到 {@code fabric.mod.json}）。没有则按纯 Fabric 解析顶层字段。
	 *
	 * @param jar mod jar 路径
	 * @return 解析出的元数据
	 * @throws IOException            读 jar 失败
	 * @throws IllegalStateException 缺少 fabric.mod.json / quilt.mod.json，或 JSON 无法解析
	 */
	public static QuiltModMetadata readMetadata(Path jar) throws IOException {
		try (JarFile jf = new JarFile(jar.toFile())) {
			ZipEntry quiltEntry = jf.getEntry(QUILT_MOD_JSON);
			ZipEntry fabricEntry = jf.getEntry(FABRIC_MOD_JSON);

			Map<String, Object> quiltRoot = quiltEntry != null ? readJson(jf, quiltEntry) : null;
			Map<String, Object> fabricRoot = fabricEntry != null ? readJson(jf, fabricEntry) : null;

			if (quiltRoot == null && fabricRoot == null) {
				throw new IllegalStateException(
						"既没有 quilt.mod.json 也没有 fabric.mod.json，不是 Fabric/Quilt mod: " + jar);
			}

			boolean isQuilt = quiltRoot != null;
			Ecosystem ecosystem = isQuilt ? Ecosystem.QUILT : Ecosystem.FABRIC;

			// quilt_loader 节点：Quilt 的 id/version/entrypoints 都挂在这里。
			Object quiltLoader = isQuilt ? quiltRoot.get("quilt_loader") : null;
			Map<String, Object> ql = asMap(quiltLoader);

			// id：优先 quilt_loader.id，回退 fabric.mod.json 顶层 id。
			String id = firstString(ql != null ? ql.get("id") : null,
					fabricRoot != null ? fabricRoot.get("id") : null);
			// version：优先 quilt_loader.version，回退 fabric 顶层 version。
			String version = firstString(ql != null ? ql.get("version") : null,
					fabricRoot != null ? fabricRoot.get("version") : null);
			// entrypoints：优先 quilt_loader.entrypoints，回退 fabric 顶层 entrypoints。
			Map<String, List<String>> entrypoints = normalizeEntrypoints(
					ql != null ? ql.get("entrypoints") : null,
					fabricRoot != null ? fabricRoot.get("entrypoints") : null);

			return new QuiltModMetadata(id, version, entrypoints, ecosystem);
		}
	}

	/**
	 * 把识别结果直接转成中立的 {@link ModCandidate}，供 catalog / 加载器消费。
	 *
	 * <p>JiJ（jar-in-jar）嵌套 jar 暂记为空列表——识别阶段只关心"这是谁"，嵌套 jar 的展开在
	 * Fabric 加载路径里按 Fabric 原生规则处理（与本类解耦）。
	 *
	 * @param jar mod jar 路径
	 * @return 带正确 Ecosystem 标记的 candidate
	 */
	public static ModCandidate toCandidate(Path jar) throws IOException {
		QuiltModMetadata meta = readMetadata(jar);
		return new ModCandidate(meta.id(), meta.version(), meta.ecosystem(), jar, List.of());
	}

	// ---- 内部工具 ----

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object o) {
		return o instanceof Map ? (Map<String, Object>) o : null;
	}

	/** 读 zip entry 并解析成对象图。 */
	private static Map<String, Object> readJson(JarFile jf, ZipEntry entry) throws IOException {
		try (var in = jf.getInputStream(entry)) {
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			Object parsed = MiniJson.parse(text);
			return asMap(parsed);
		}
	}

	/** 取第一个非 null 的字符串值（id / version 用）。 */
	private static String firstString(Object... candidates) {
		for (Object c : candidates) {
			if (c instanceof String s && !s.isEmpty()) return s;
		}
		return null;
	}

	/**
	 * 规范化 entrypoints：Fabric 允许每个组的值是单个字符串或字符串数组；Quilt 同理。
	 * 优先取第一个非 null 的来源（quilt_loader.entrypoints 优先）。
	 */
	private static Map<String, List<String>> normalizeEntrypoints(Object quiltEntrypoints, Object fabricEntrypoints) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		Object chosen = quiltEntrypoints != null ? quiltEntrypoints : fabricEntrypoints;
		Map<String, Object> groups = asMap(chosen);
		if (groups == null) return result;

		for (Map.Entry<String, Object> e : groups.entrySet()) {
			result.put(e.getKey(), toStringList(e.getValue()));
		}
		return result;
	}

	/** 把 entrypoint 组的值（单个 String 或 List）统一成 List&lt;String&gt;。 */
	@SuppressWarnings("unchecked")
	private static List<String> toStringList(Object value) {
		List<String> out = new ArrayList<>();
		if (value instanceof String s) {
			out.add(s);
		} else if (value instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> m) {
					// Fabric/Quilt 高级形式：{ "value": "com.example.Entry", "adapter": "kotlin" }
					Object v = m.get("value");
					if (v instanceof String sv) out.add(sv);
				} else if (o instanceof String s) {
					out.add(s);
				}
			}
		}
		return out;
	}
}
