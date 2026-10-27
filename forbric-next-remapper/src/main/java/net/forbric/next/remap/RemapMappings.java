package net.forbric.next.remap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时重映射映射表（内存态）。
 *
 * <p>三张表：
 * <ul>
 *   <li>{@link #classMappings} — 老类名 → 新类名（JVM 内部形式，斜杠分隔，例如
 *       {@code net/minecraft/class_1234} → {@code net/minecraft/new/Name}）。</li>
 *   <li>{@link #fieldMappings} — 老字段名 → 新字段名（骨架阶段不带 owner，TODO 补 owner 作用域）。</li>
 *   <li>{@link #methodMappings} — 老方法名 → 新方法名（同上）。</li>
 * </ul>
 *
 * <p>类名统一以 <b>JVM 内部形式</b>（斜杠）存储；调用方传点号形式也会自动归一。
 * 这是整个重映射层唯一的"真源"，{@link RuntimeRemapper} 只读不写。
 */
public final class RemapMappings {

	/** 类名映射：老内部名 → 新内部名。 */
	private final Map<String, String> classMappings = new LinkedHashMap<>();
	/** 字段名映射：老字段名 → 新字段名。 */
	private final Map<String, String> fieldMappings = new LinkedHashMap<>();
	/** 方法名映射：老方法名 → 新方法名。 */
	private final Map<String, String> methodMappings = new LinkedHashMap<>();

	/** 新建空映射表。 */
	public RemapMappings() {
	}

	// ---- 增删 ----

	/**
	 * 加一条类映射。点号形式会自动转成斜杠内部形式。
	 *
	 * @param oldName 老类名（点号或斜杠形式均可）
	 * @param newName 新类名（点号或斜杠形式均可）
	 */
	public void addClassMapping(String oldName, String newName) {
		classMappings.put(toInternal(oldName), toInternal(newName));
	}

	/**
	 * 加一条字段映射。
	 *
	 * @param oldName 老字段名
	 * @param newName 新字段名
	 */
	public void addFieldMapping(String oldName, String newName) {
		fieldMappings.put(oldName, newName);
	}

	/**
	 * 加一条方法映射。
	 *
	 * @param oldName 老方法名
	 * @param newName 新方法名
	 */
	public void addMethodMapping(String oldName, String newName) {
		methodMappings.put(oldName, newName);
	}

	// ---- 查询 ----

	/**
	 * 查类映射；没有映射时返回原名字。
	 *
	 * @param internalName 老类的 JVM 内部名（斜杠形式）
	 * @return 新内部名；未命中返回入参本身
	 */
	public String mapClass(String internalName) {
		return classMappings.getOrDefault(internalName, internalName);
	}

	/** 查字段映射；未命中返回原名。 */
	public String mapField(String oldName) {
		return fieldMappings.getOrDefault(oldName, oldName);
	}

	/** 查方法映射；未命中返回原名。 */
	public String mapMethod(String oldName) {
		return methodMappings.getOrDefault(oldName, oldName);
	}

	// ---- 只读视图 ----

	/** 返回类映射的只读视图（老内部名 → 新内部名）。 */
	public Map<String, String> classMappings() {
		return java.util.Collections.unmodifiableMap(classMappings);
	}

	/** 返回字段映射的只读视图。 */
	public Map<String, String> fieldMappings() {
		return java.util.Collections.unmodifiableMap(fieldMappings);
	}

	/** 返回方法映射的只读视图。 */
	public Map<String, String> methodMappings() {
		return java.util.Collections.unmodifiableMap(methodMappings);
	}

	// ---- 加载 ----

	/**
	 * 从 properties 文件加载映射。
	 *
	 * <p>约定：行格式 {@code 前缀.老名=新名}，前缀决定进哪张表：
	 * <ul>
	 *   <li>{@code class.old=...} → 类映射（old/new 都自动转内部形式）</li>
	 *   <li>{@code field.old=...} → 字段映射</li>
	 *   <li>{@code method.old=...} → 方法映射</li>
	 * </ul>
	 * {@code #} 开头的行是注释，空行跳过。
	 *
	 * @param file properties 文件路径
	 * @throws IOException 读文件失败
	 */
	public void loadFromProperties(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			loadFromInputStream(in);
		}
	}

	/**
	 * 从输入流加载映射（格式同 {@link #loadFromProperties(Path)}）。
	 *
	 * @param in 输入流，调用方负责关闭
	 * @throws IOException 读流失败
	 */
	public void loadFromInputStream(InputStream in) throws IOException {
		String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		for (String rawLine : text.split("\\r?\\n")) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			int eq = line.indexOf('=');
			if (eq <= 0) continue;
			String key = line.substring(0, eq).trim();
			String value = line.substring(eq + 1).trim();
			if (key.startsWith("class.")) {
				addClassMapping(key.substring("class.".length()), value);
			} else if (key.startsWith("field.")) {
				addFieldMapping(key.substring("field.".length()), value);
			} else if (key.startsWith("method.")) {
				addMethodMapping(key.substring("method.".length()), value);
			}
			// 其它前缀忽略，未来可扩展（例如 classByOwner.）
		}
	}

	// ---- 工具 ----

	/**
	 * 把点号形式或斜杠形式的类名统一成 JVM 内部形式（斜杠）。
	 * 已经是斜杠形式的原样返回。
	 */
	static String toInternal(String name) {
		return name == null ? null : name.replace('.', '/');
	}

	@Override
	public String toString() {
		return "RemapMappings{class=" + classMappings.size()
				+ ", field=" + fieldMappings.size()
				+ ", method=" + methodMappings.size() + "}";
	}
}
