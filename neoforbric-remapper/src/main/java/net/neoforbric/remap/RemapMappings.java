package net.neoforbric.remap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时重映射映射表（内存态）。
 *
 * <p>四张表：
 * <ul>
 *   <li>{@link #classMappings} — 老类名 → 新类名（JVM 内部形式，斜杠分隔）。</li>
 *   <li>{@link #fieldMappings} — 全局字段名映射（无 owner，fallback）。</li>
 *   <li>{@link #methodMappings} — 全局方法名映射（无 owner，fallback）。</li>
 *   <li>{@link #ownerFieldMappings} — owner 作用域字段映射：{@code "老Owner.老Field" → 新Field}。</li>
 *   <li>{@link #ownerMethodMappings} — owner 作用域方法映射：{@code "老Owner.老Method" → 新Method}。</li>
 * </ul>
 *
 * <p>查询优先级：先查 owner 作用域，没命中再查全局 fallback。
 */
public final class RemapMappings {

	private final Map<String, String> classMappings = new LinkedHashMap<>();
	private final Map<String, String> fieldMappings = new LinkedHashMap<>();
	private final Map<String, String> methodMappings = new LinkedHashMap<>();
	private final Map<String, String> ownerFieldMappings = new HashMap<>();
	private final Map<String, String> ownerMethodMappings = new HashMap<>();

	public RemapMappings() {}

	// ---- 类映射 ----

	public void addClassMapping(String oldName, String newName) {
		classMappings.put(toInternal(oldName), toInternal(newName));
	}

	public String mapClass(String internalName) {
		return classMappings.getOrDefault(internalName, internalName);
	}

	// ---- 全局字段/方法（fallback） ----

	public void addFieldMapping(String oldName, String newName) {
		fieldMappings.put(oldName, newName);
	}

	public void addMethodMapping(String oldName, String newName) {
		methodMappings.put(oldName, newName);
	}

	// ---- Owner 作用域 ----

	/**
	 * 加一条 owner 作用域字段映射。
	 *
	 * @param ownerInternal 老 owner 类的 JVM 内部名（斜杠形式）
	 * @param oldName       老字段名
	 * @param newName       新字段名
	 */
	public void addOwnerFieldMapping(String ownerInternal, String oldName, String newName) {
		ownerFieldMappings.put(ownerInternal + "." + oldName, newName);
	}

	/**
	 * 加一条 owner 作用域方法映射。
	 *
	 * @param ownerInternal 老 owner 类的 JVM 内部名（斜杠形式）
	 * @param oldName       老方法名
	 * @param newName       新方法名
	 */
	public void addOwnerMethodMapping(String ownerInternal, String oldName, String newName) {
		ownerMethodMappings.put(ownerInternal + "." + oldName, newName);
	}

	/** 查字段映射（全局 fallback，不查 owner 作用域）。 */
	public String mapField(String oldName) {
		return fieldMappings.getOrDefault(oldName, oldName);
	}

	/** 查方法映射（全局 fallback，不查 owner 作用域）。 */
	public String mapMethod(String oldName) {
		return methodMappings.getOrDefault(oldName, oldName);
	}

	/** 查字段映射：先 owner 作用域，再全局 fallback。 */
	public String mapField(String ownerInternal, String oldName) {
		String scoped = ownerFieldMappings.get(ownerInternal + "." + oldName);
		if (scoped != null) return scoped;
		return mapField(oldName);
	}

	/** 查方法映射：先 owner 作用域，再全局 fallback。 */
	public String mapMethod(String ownerInternal, String oldName) {
		String scoped = ownerMethodMappings.get(ownerInternal + "." + oldName);
		if (scoped != null) return scoped;
		return mapMethod(oldName);
	}

	// ---- 只读视图 ----

	public Map<String, String> classMappings() { return Collections.unmodifiableMap(classMappings); }
	public Map<String, String> fieldMappings() { return Collections.unmodifiableMap(fieldMappings); }
	public Map<String, String> methodMappings() { return Collections.unmodifiableMap(methodMappings); }

	// ---- 加载 ----

	/**
	 * 从 properties 文件加载映射。
	 *
	 * <p>行格式 {@code 前缀.键=值}：
	 * <ul>
	 *   <li>{@code class.old=...} → 类映射</li>
	 *   <li>{@code field.old=...} → 全局字段映射</li>
	 *   <li>{@code method.old=...} → 全局方法映射</li>
	 *   <li>{@code field.owner.OldOwner.oldField=newField} → owner 作用域字段映射</li>
	 *   <li>{@code method.owner.OldOwner.oldMethod=newMethod} → owner 作用域方法映射</li>
	 * </ul>
	 */
	public void loadFromProperties(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			loadFromInputStream(in);
		}
	}

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
			} else if (key.startsWith("field.owner.")) {
				// field.owner.OwnerName.fieldName
				String rest = key.substring("field.owner.".length());
				int dot = rest.lastIndexOf('.');
				if (dot > 0) {
					addOwnerFieldMapping(toInternal(rest.substring(0, dot)), rest.substring(dot + 1), value);
				}
			} else if (key.startsWith("method.owner.")) {
				String rest = key.substring("method.owner.".length());
				int dot = rest.lastIndexOf('.');
				if (dot > 0) {
					addOwnerMethodMapping(toInternal(rest.substring(0, dot)), rest.substring(dot + 1), value);
				}
			} else if (key.startsWith("field.")) {
				addFieldMapping(key.substring("field.".length()), value);
			} else if (key.startsWith("method.")) {
				addMethodMapping(key.substring("method.".length()), value);
			}
		}
	}

	// ---- 工具 ----

	static String toInternal(String name) {
		return name == null ? null : name.replace('.', '/');
	}

	@Override
	public String toString() {
		return "RemapMappings{class=" + classMappings.size()
				+ ", field=" + fieldMappings.size() + "(+owner:" + ownerFieldMappings.size() + ")"
				+ ", method=" + methodMappings.size() + "(+owner:" + ownerMethodMappings.size() + ")}";
	}

	// 避免 import 麻烦
	private static class Collections {
		static <K, V> Map<K, V> unmodifiableMap(Map<K, V> m) {
			return java.util.Collections.unmodifiableMap(m);
		}
	}
}
