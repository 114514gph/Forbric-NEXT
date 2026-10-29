package net.forbric.next.remap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.forbric.next.common.NextLog;

/**
 * class 文件常量池重写器（不依赖 ASM / tiny-remapper）。
 *
 * <p><b>重写范围：</b>
 * <ul>
 *   <li>类名整体替换：CONSTANT_Class 指向的 CONSTANT_Utf8 条目。</li>
 *   <li>描述符内替换：方法/字段描述符里的 {@code L老内部名;} 子串。</li>
 *   <li>字段名重写：通过 CONSTANT_Fieldref → NameAndType → Utf8 链路，
 *       结合 owner 类名做 owner 作用域查询。</li>
 *   <li>方法名重写：同上，走 CONSTANT_Methodref / InterfaceMethodref 链路。</li>
 * </ul>
 *
 * <p><b>安全保证：</b>常量池条目数量和索引不变（只改 Utf8 条目里的字节内容与长度），
 * 类文件后半段按原字节拷贝。解析失败时原样返回输入，绝不写坏 class。
 */
public final class RuntimeRemapper {

	private RuntimeRemapper() {}

	private static final int TAG_UTF8 = 1;
	private static final int TAG_INTEGER = 3;
	private static final int TAG_FLOAT = 4;
	private static final int TAG_LONG = 5;
	private static final int TAG_DOUBLE = 6;
	private static final int TAG_CLASS = 7;
	private static final int TAG_STRING = 8;
	private static final int TAG_FIELDREF = 9;
	private static final int TAG_METHODREF = 10;
	private static final int TAG_INTERFACE_METHODREF = 11;
	private static final int TAG_NAME_AND_TYPE = 12;
	private static final int TAG_METHOD_HANDLE = 15;
	private static final int TAG_METHOD_TYPE = 16;
	private static final int TAG_DYNAMIC = 17;
	private static final int TAG_INVOKE_DYNAMIC = 18;
	private static final int TAG_MODULE = 19;
	private static final int TAG_PACKAGE = 20;

	/**
	 * 对一个 class 文件字节数组做完整重映射（类名 + 字段名 + 方法名）。
	 *
	 * @param classBytes 原始 class 文件字节
	 * @param mappings   映射表（只读）
	 * @return 重映射后的 class 字节；解析失败返回原数组
	 */
	public static byte[] remap(byte[] classBytes, RemapMappings mappings) {
		if (classBytes == null || classBytes.length < 10) return classBytes;
		if (mappings == null) return classBytes;
		if (mappings.classMappings().isEmpty()
				&& mappings.fieldMappings().isEmpty()
				&& mappings.methodMappings().isEmpty()) return classBytes;

		try {
			return remapInternal(classBytes, mappings);
		} catch (RuntimeException e) {
			NextLog.error("RuntimeRemapper: class 常量池解析失败，跳过重写。"
					+ " size={} bytes, exception={}: {}", classBytes.length, e.getClass().getName(), e.getMessage());
			// 打印完整堆栈到 stderr，方便定位哪个 class 文件、哪一行解析失败
			e.printStackTrace();
			return classBytes;
		}
	}

	private static byte[] remapInternal(byte[] in, RemapMappings mappings) {
		int pos = 0;
		int magic = readU4(in, pos); pos += 4;
		if (magic != 0xCAFEBABE) return in;
		pos += 4; // minor + major
		int cpCount = readU2(in, pos); pos += 2;
		if (cpCount <= 1) return in;

		// 常量池条目元数据
		int[] tags = new int[cpCount];
		int[] payloadStart = new int[cpCount];
		int[] payloadEnd = new int[cpCount];
		String[] utf8Strings = new String[cpCount];
		// 非 Utf8 条目的 payload 数据（u2/u4 索引值）
		int[][] refData = new int[cpCount][]; // [index1, index2] for two-ref entries

		for (int i = 1; i < cpCount; i++) {
			int tag = in[pos++] & 0xFF;
			tags[i] = tag;
			payloadStart[i] = pos;
			switch (tag) {
				case TAG_UTF8 -> {
					int len = readU2(in, pos); pos += 2;
					byte[] raw = new byte[len];
					System.arraycopy(in, pos, raw, 0, len);
					pos += len;
					utf8Strings[i] = new String(raw, StandardCharsets.UTF_8);
				}
				case TAG_LONG, TAG_DOUBLE -> {
					pos += 8;
					payloadEnd[i] = pos; // 先设当前条目的 end
					i++; // 跳过第二个槽位
					tags[i] = -1;
					payloadStart[i] = pos;
					payloadEnd[i] = pos;
				}
				case TAG_INTEGER, TAG_FLOAT -> pos += 4;
				case TAG_CLASS, TAG_STRING, TAG_METHOD_TYPE, TAG_MODULE, TAG_PACKAGE -> {
					refData[i] = new int[]{ readU2(in, pos) };
					pos += 2;
				}
				case TAG_FIELDREF, TAG_METHODREF, TAG_INTERFACE_METHODREF,
						TAG_NAME_AND_TYPE, TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> {
					refData[i] = new int[]{ readU2(in, pos), readU2(in, pos + 2) };
					pos += 4;
				}
				case TAG_METHOD_HANDLE -> pos += 3;
				default -> throw new IllegalStateException("未知 tag: " + tag);
			}
			payloadEnd[i] = pos;
		}

		int cpEnd = pos;
		Map<String, String> classMap = mappings.classMappings();

		// ---- 第一步：类名 Utf8 重写 ----
		Map<Integer, String> utf8Overrides = new HashMap<>();
		for (int i = 1; i < cpCount; i++) {
			String s = utf8Strings[i];
			if (s == null) continue;
			String rewritten = applyClassMappings(s, classMap);
			if (!rewritten.equals(s)) {
				utf8Overrides.put(i, rewritten);
			}
		}

		// ---- 第一步半：全局字段/方法名 Utf8 重写 ----
		// 扫描所有 Utf8 条目，凡是精确匹配老字段/方法名的就重写。
		// 这样 mixin @Shadow 字段名、字段表里的字段名都会被重写。
		// 风险极低：field_xxxx / method_xxxx 这种命名不会作为普通字符串出现。
		java.util.Set<String> knownFields = mappings.fieldMappings().keySet();
		java.util.Set<String> knownMethods = mappings.methodMappings().keySet();
		for (int i = 1; i < cpCount; i++) {
			if (utf8Overrides.containsKey(i)) continue; // 已经被类名重写了
			String s = utf8Strings[i];
			if (s == null) continue;
			// 精确匹配字段名或方法名
			if (knownFields.contains(s)) {
				utf8Overrides.put(i, mappings.mapField(s));
			} else if (knownMethods.contains(s)) {
				utf8Overrides.put(i, mappings.mapMethod(s));
			}
		}

		// ---- 第二步：字段/方法名重写（带 owner 作用域） ----
		// 收集 owner 类名映射后的结果，用于 owner 作用域查询
		for (int i = 1; i < cpCount; i++) {
			if (tags[i] != TAG_FIELDREF && tags[i] != TAG_METHODREF && tags[i] != TAG_INTERFACE_METHODREF) continue;
			if (refData[i] == null || refData[i].length < 2) continue;

			int classIdx = refData[i][0];
			int nameAndTypeIdx = refData[i][1];
			if (tags[classIdx] != TAG_CLASS || refData[classIdx] == null) continue;
			if (tags[nameAndTypeIdx] != TAG_NAME_AND_TYPE || refData[nameAndTypeIdx] == null) continue;

			int classNameIdx = refData[classIdx][0]; // CONSTANT_Class → name_index
			int fieldMethodNameIdx = refData[nameAndTypeIdx][0]; // NameAndType → name_index

			String ownerOld = utf8Strings[classNameIdx];
			String ownerNew = utf8Overrides.getOrDefault(classNameIdx, ownerOld);
			String memberOld = utf8Strings[fieldMethodNameIdx];
			if (memberOld == null || ownerNew == null) continue;

			// 查 owner 作用域映射
			String memberNew;
			if (tags[i] == TAG_FIELDREF) {
				memberNew = mappings.mapField(ownerNew, memberOld);
			} else {
				memberNew = mappings.mapMethod(ownerNew, memberOld);
			}
			if (!memberNew.equals(memberOld)) {
				utf8Overrides.put(fieldMethodNameIdx, memberNew);
			}
		}

		// ---- 重序列化常量池 ----
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.length + 64);
		out.writeBytes(java.util.Arrays.copyOfRange(in, 0, 8));
		writeU2(out, cpCount);
		for (int i = 1; i < cpCount; i++) {
			if (tags[i] == -1) continue; // long/double 第二槽
			out.write(tags[i]);
			if (tags[i] == TAG_UTF8) {
				String s = utf8Overrides.getOrDefault(i, utf8Strings[i]);
				byte[] b = s.getBytes(StandardCharsets.UTF_8);
				writeU2(out, b.length);
				out.writeBytes(b);
			} else {
				out.writeBytes(java.util.Arrays.copyOfRange(in, payloadStart[i], payloadEnd[i]));
			}
		}

		out.writeBytes(java.util.Arrays.copyOfRange(in, cpEnd, in.length));
		return out.toByteArray();
	}

	private static String applyClassMappings(String s, Map<String, String> classMap) {
		String result = s;
		// 多 pass 直到没有更多变化，确保映射链完整解析
		// 例如：class_1927 → Explosion → ServerExplosion 需要两次 pass
		for (int pass = 0; pass < 3; pass++) {
			boolean changed = false;
			for (Map.Entry<String, String> e : classMap.entrySet()) {
				String oldInternal = e.getKey();
				String newInternal = e.getValue();
				if (oldInternal.equals(result)) {
					result = newInternal;
					changed = true;
				} else {
					String needle = "L" + oldInternal + ";";
					if (result.contains(needle)) {
						result = result.replace(needle, "L" + newInternal + ";");
						changed = true;
					}
				}
			}
			if (!changed) break;
		}
		return result;
	}

	private static int readU2(byte[] b, int off) {
		return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
	}

	private static int readU4(byte[] b, int off) {
		return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
				| ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
	}

	private static void writeU2(ByteArrayOutputStream out, int v) {
		out.write((v >>> 8) & 0xFF);
		out.write(v & 0xFF);
	}
}
