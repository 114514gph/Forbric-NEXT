package net.neoforbric.remap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link RuntimeRemapper} 行为测试。
 *
 * <p>用系统 JavaCompiler 真编一个引用老类的测试类，再跑重映射，断言：
 * <ul>
 *   <li>重写后的 class 不再包含老类名（CONSTANT_Class + 描述符里都没有）；</li>
 *   <li>包含新类名；</li>
 *   <li>class 文件魔数 {@code CAFEBABE} 不变（还是合法 class 头）；</li>
 *   <li>空映射表时原样返回。</li>
 * </ul>
 */
class RuntimeRemapperTest {

	/**
	 * 编译一段源码到输出目录，返回生成的 class 字节。
	 *
	 * @param srcDir   源码根目录
	 * @param outDir   class 输出目录
	 * @param relative 要读的 class 相对路径（例如 {@code com/example/old/UsesOld.class}）
	 */
	private static byte[] compileAndRead(Path srcDir, Path outDir, String relative) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "当前 JRE 必须带 javac（测试用 JDK 跑）");
		try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
			fm.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, List.of(outDir.toFile()));
			fm.setLocation(javax.tools.StandardLocation.SOURCE_PATH, List.of(srcDir.toFile()));
			var units = fm.getJavaFileObjectsFromFiles(
					Files.walk(srcDir).filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList());
			JavaCompiler.CompilationTask task = compiler.getTask(null, fm, null,
					List.of("-g:none"), null, units);
			assertTrue(task.call(), "编译失败");
		}
		return Files.readAllBytes(outDir.resolve(relative));
	}

	@Test
	void remapsClassNameInConstantPoolAndDescriptor(@TempDir Path tmp) throws IOException {
		Path src = tmp.resolve("src");
		Path out = tmp.resolve("out");
		Files.createDirectories(src.resolve("com/example/old"));
		Files.createDirectories(out);

		// 老类 stub
		Files.writeString(src.resolve("com/example/old/Old.java"),
				"package com.example.old; public class Old {}");
		// 引用老类的类：方法返回 Old → 同时生成 CONSTANT_Class 和描述符 L...;
		Files.writeString(src.resolve("com/example/old/UsesOld.java"),
				"package com.example.old; public class UsesOld { public Old make() { return new Old(); } }");

		byte[] original = compileAndRead(src, out, "com/example/old/UsesOld.class");

		RemapMappings mappings = new RemapMappings();
		mappings.addClassMapping("com.example.old.Old", "com.example.new.New");

		byte[] remapped = RuntimeRemapper.remap(original, mappings);

		String oldInternal = "com/example/old/Old";
		String newInternal = "com/example/new/New";
		byte[] oldBytes = oldInternal.getBytes(StandardCharsets.UTF_8);
		byte[] newBytes = newInternal.getBytes(StandardCharsets.UTF_8);

		// 老名字应该在原始字节里出现（ sanity check）
		assertTrue(indexOf(original, oldBytes) >= 0, "原始 class 应包含老类名");

		// 重写后：老名字彻底消失，新名字出现
		assertFalse(indexOf(remapped, oldBytes) >= 0, "重写后不应再包含老类名（CONSTANT_Class + 描述符都应被替换）");
		assertTrue(indexOf(remapped, newBytes) >= 0, "重写后应包含新类名");

		// 魔数不变
		assertArrayEquals(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
				Arrays.copyOfRange(remapped, 0, 4), "class 魔数必须保持 CAFEBABE");
	}

	@Test
	void emptyMappingsReturnsOriginalBytes(@TempDir Path tmp) throws IOException {
		Path src = tmp.resolve("src");
		Path out = tmp.resolve("out");
		Files.createDirectories(src.resolve("com/example/x"));
		Files.createDirectories(out);
		Files.writeString(src.resolve("com/example/x/Hello.java"),
				"package com.example.x; public class Hello {}");

		byte[] original = compileAndRead(src, out, "com/example/x/Hello.class");
		byte[] remapped = RuntimeRemapper.remap(original, new RemapMappings());

		// 空映射表应原样返回（省一次拷贝）
		assertArrayEquals(original, remapped);
	}

	@Test
	void nonClassBytesAreReturnedAsIs() {
		byte[] garbage = "not a class file".getBytes(StandardCharsets.UTF_8);
		RemapMappings m = new RemapMappings();
		m.addClassMapping("a.B", "b.B");
		// 魔数不对 → 原样返回，不崩
		assertArrayEquals(garbage, RuntimeRemapper.remap(garbage, m));
	}

	/** 在 haystack 里找 needle 的起始下标，没找到返回 -1。 */
	private static int indexOf(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) continue outer;
			}
			return i;
		}
		return -1;
	}
}
