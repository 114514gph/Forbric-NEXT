package net.forbric.next.remap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import net.forbric.next.common.NextLog;

/**
 * 极简 class 文件常量池重写器（不依赖 ASM / tiny-remapper）。
 *
 * <p><b>为什么不直接用 ASM：</b>JDK 自带的 {@code jdk.internal.org.objectweb.asm} 是内部 API，
 * 默认不开放给应用类路径；引入第三方 ASM 又违背"骨架零外部依赖"的约束。class 文件的常量池
 * 结构本身是规整的，重映射老 mod 最关键的一件事——把 <b>类名引用</b> 指向新名字——只需要
 * 读写 CONSTANT_Utf8 条目，没必要跑完整的栈帧分析。
 *
 * <p><b>重写范围（骨架阶段）：</b>
 * <ul>
 *   <li>类名整体替换：CONSTANT_Class 指向的 CONSTANT_Utf8 条目（例如
 *       {@code com/example/old/Old} → {@code com/example/new/New}）。</li>
 *   <li>描述符内替换：方法/字段描述符里的 {@code Lcom/example/old/Old;} 子串
 *       （例如 {@code (Lcom/example/old/Old;)V} → {@code (Lcom/example/new/New;)V}）。</li>
 * </ul>
 *
 * <p><b>留 TODO：</b>
 * <ul>
 *   <li>字段名 / 方法名的重写需要 owner 类解析（先确定 CONSTANT_Fieldref/Methodref 的 owner，
 *       再去 NameAndType 里改 name 索引），骨架阶段只改类名，字段/方法映射表读了但不写回字节码。</li>
 *   <li>泛型签名（Signature 属性）里的类名引用、注解里的类名、invokedynamic 的
 *       BootstrapMethods 参数，都不在当前最小改写范围内。</li>
 * </ul>
 *
 * <p><b>安全保证：</b>常量池条目数量和索引不变（只改 Utf8 条目里的字节内容与长度），
 * 类文件后半段（access_flags / this_class / fields / methods / attributes）按原字节拷贝，
 * 引用的常量池索引全部仍然有效。解析失败时原样返回输入，绝不写坏 class。
 */
public final class RuntimeRemapper {

	private RuntimeRemapper() {}

	// ---- class 文件常量池 tag 常量 ----
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
	 * 对一个 class 文件字节数组做类名重映射。
	 *
	 * @param classBytes 原始 class 文件字节（不能是 jar / 其它格式）
	 * @param mappings   映射表（只读）
	 * @return 重映射后的 class 字节；若映射表为空或解析失败，返回原数组
	 */
	public static byte[] remap(byte[] classBytes, RemapMappings mappings) {
		if (classBytes == null || classBytes.length < 10) return classBytes;
		// 映射表里没有类映射就没必要动 class
		if (mappings == null || mappings.classMappings().isEmpty()) return classBytes;

		try {
			return remapInternal(classBytes, mappings);
		} catch (RuntimeException e) {
			// 解析失败：原样返回，不写坏 class，打一条 WARN 让上层知道
			NextLog.warn("RuntimeRemapper: class 常量池解析失败，跳过重写（{}）", e.getMessage());
			return classBytes;
		}
	}

	private static byte[] remapInternal(byte[] in, RemapMappings mappings) {
		// ---- 1. 读头 ----
		// u4 magic, u2 minor, u2 major, u2 constant_pool_count
		int pos = 0;
		int magic = readU4(in, pos); pos += 4;
		if (magic != 0xCAFEBABE) return in; // 不是 class 文件，别硬来
		pos += 4; // minor(2) + major(2)
		int cpCount = readU2(in, pos); pos += 2;
		if (cpCount <= 1) return in;

		// ---- 2. 逐条解析常量池 ----
		// spans[i] = [tag, payloadStart, payloadEnd]，索引从 1 开始；long/double 第二槽为 null
		int[][][] spans = new int[cpCount][][];
		// Utf8 条目解析出的原始字符串（仅 tag==1 的索引有值）
		String[] utf8Strings = new String[cpCount];

		for (int i = 1; i < cpCount; i++) {
			int tag = in[pos++] & 0xFF;
			int payloadStart = pos;
			switch (tag) {
				case TAG_UTF8 -> {
					int len = readU2(in, pos); pos += 2;
					byte[] raw = new byte[len];
					System.arraycopy(in, pos, raw, 0, len);
					pos += len;
					utf8Strings[i] = new String(raw, StandardCharsets.UTF_8);
				}
				case TAG_LONG, TAG_DOUBLE -> pos += 8;
				case TAG_INTEGER, TAG_FLOAT -> pos += 4;
				case TAG_CLASS, TAG_STRING, TAG_METHOD_TYPE, TAG_MODULE, TAG_PACKAGE -> pos += 2;
				case TAG_FIELDREF, TAG_METHODREF, TAG_INTERFACE_METHODREF,
						TAG_NAME_AND_TYPE, TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> pos += 4;
				case TAG_METHOD_HANDLE -> pos += 3; // u1 kind + u2 index
				default -> throw new IllegalStateException("未知常量池 tag: " + tag + " at cp #" + i);
			}
			spans[i] = new int[][]{{tag, payloadStart, pos}};
			// long/double 占两个槽位，下一个索引直接跳过并占位
			if (tag == TAG_LONG || tag == TAG_DOUBLE) {
				i++;
				spans[i] = null;
			}
		}

		int cpEnd = pos;

		// ---- 3. 计算每个 Utf8 条目重写后的字符串 ----
		Map<String, String> classMap = mappings.classMappings();
		String[] rewritten = new String[cpCount];
		for (int i = 1; i < cpCount; i++) {
			String s = utf8Strings[i];
			if (s == null) continue;
			rewritten[i] = applyClassMappings(s, classMap);
		}

		// ---- 4. 重序列化常量池 ----
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.length + 64);
		// 头：magic(4) + minor(2) + major(2) 原样拷贝
		out.writeBytes(java.util.Arrays.copyOfRange(in, 0, 8));
		writeU2(out, cpCount);
		for (int i = 1; i < cpCount; i++) {
			int[][] span = spans[i];
			if (span == null) continue; // long/double 的第二个空槽
			int tag = span[0][0];
			int payloadStart = span[0][1];
			int payloadEnd = span[0][2];
			out.write(tag);
			if (tag == TAG_UTF8) {
				// Utf8 条目：用重写后的字符串重写 payload（u2 length + bytes）
				byte[] b = rewritten[i].getBytes(StandardCharsets.UTF_8);
				writeU2(out, b.length);
				out.writeBytes(b);
			} else {
				// 非 Utf8：payload 原样拷贝（里面存的是 u2/u4 索引，不动）
				out.writeBytes(java.util.Arrays.copyOfRange(in, payloadStart, payloadEnd));
			}
		}

		// ---- 5. 类文件后半段（常量池之后）原样拷贝 ----
		out.writeBytes(java.util.Arrays.copyOfRange(in, cpEnd, in.length));
		return out.toByteArray();
	}

	/**
	 * 对一个 Utf8 字符串应用类名映射。
	 *
	 * <p>两种命中：
	 * <ol>
	 *   <li>整串等于老内部名（CONSTANT_Class 的 name）→ 整串替换。</li>
	 *   <li>串里包含 {@code L老内部名;}（字段/方法描述符、数组类型）→ 子串替换。</li>
	 * </ol>
	 */
	private static String applyClassMappings(String s, Map<String, String> classMap) {
		String result = s;
		for (Map.Entry<String, String> e : classMap.entrySet()) {
			String oldInternal = e.getKey();
			String newInternal = e.getValue();
			if (oldInternal.equals(result)) {
				// 整串就是一个类名引用
				result = newInternal;
			} else {
				// 描述符 / 数组里的 L老; 形式
				String needle = "L" + oldInternal + ";";
				String replacement = "L" + newInternal + ";";
				if (result.contains(needle)) {
					result = result.replace(needle, replacement);
				}
			}
		}
		return result;
	}

	// ---- class 文件是大端 ----
	private static int readU2(byte[] b, int off) {
		return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
	}

	private static int readU4(byte[] b, int off) {
		return ((b[off] & 0xFF) << 24)
				| ((b[off + 1] & 0xFF) << 16)
				| ((b[off + 2] & 0xFF) << 8)
				| (b[off + 3] & 0xFF);
	}

	private static void writeU2(ByteArrayOutputStream out, int v) {
		out.write((v >>> 8) & 0xFF);
		out.write(v & 0xFF);
	}
}
