package net.forbric.next.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证 {@link ForbricModClassLoader} 的隔离语义：
 * <ul>
 *   <li>mod 类由本 CL 定义；</li>
 *   <li>vanilla 类由 parent（持有 vanilla 的 CL）定义；</li>
 *   <li>mod 里引用 vanilla 类能解析到 parent 那份，不冲突。</li>
 * </ul>
 * 测试里用 JDK 编译器现场编译两个微型类当"vanilla / mod"。
 */
class ForbricModClassLoaderTest {

	@TempDir
	Path tmp;

	@Test
	void isolatesModClassesAndDelegatesVanillaToParent() throws Exception {
		Path vanillaOut = Files.createDirectories(tmp.resolve("vanilla"));
		Path modOut = Files.createDirectories(tmp.resolve("mod"));

		compile(vanillaOut, "vanilla.VanillaApi", """
				package vanilla;
				public class VanillaApi {
					public static String message() { return "vanilla-ok"; }
				}
				""", "");

		compile(modOut, "mod.ModMain", """
				package mod;
				import vanilla.VanillaApi;
				public class ModMain {
					public static String greet() { return "mod says: " + VanillaApi.message(); }
					public static ClassLoader vanillaLoader() { return VanillaApi.class.getClassLoader(); }
				}
				""", vanillaOut.toString());

		// parent CL 只挂 vanilla；mod CL 挂 mod，parent=vanilla CL
		URL[] vanillaUrls = { vanillaOut.toUri().toURL() };
		URLClassLoader vanillaCl = new URLClassLoader("vanilla-cl", vanillaUrls,
				ClassLoader.getPlatformClassLoader());

		ForbricModClassLoader modCl =
				new ForbricModClassLoader(List.of(modOut), vanillaCl, null);

		// mod 类由 modCl 定义
		Class<?> modMain = Class.forName("mod.ModMain", true, modCl);
		assertEquals(modCl, modMain.getClassLoader(), "mod.ModMain 必须由 ForbricModClassLoader 定义");

		// mod 能解析并调用 parent 的 vanilla 类
		Object result = modMain.getMethod("greet").invoke(null);
		assertEquals("mod says: vanilla-ok", result);

		// VanillaApi 由 parent(vanillaCl) 加载，不是 modCl
		Object vanillaLoader = modMain.getMethod("vanillaLoader").invoke(null);
		assertEquals(vanillaCl, vanillaLoader, "VanillaApi 应由 parent vanilla-cl 加载");

		// parent-first：modCl 看到的 vanilla 类和 parent 那份是同一个 Class 对象（隔离成立）
		Class<?> vanillaFromParent = Class.forName("vanilla.VanillaApi", true, vanillaCl);
		Class<?> vanillaFromModCl = Class.forName("vanilla.VanillaApi", true, modCl);
		assertTrue(vanillaFromParent == vanillaFromModCl, "parent-first 下应是同一个 Class 对象");
	}

	/** 把一段源码编译到输出目录。fqcn 形如 {@code mod.ModMain}；extraCp 为额外 classpath（可空）。 */
	private void compile(Path outputDir, String fqcn, String source, String extraCp) {
		try {
			Path srcDir = Files.createDirectories(tmp.resolve("src"));
			Path srcFile = srcDir.resolve(fqcn.replace('.', '/') + ".java");
			Files.createDirectories(srcFile.getParent());
			Files.writeString(srcFile, source);

			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			String cp = extraCp == null || extraCp.isEmpty()
					? outputDir.toString()
					: outputDir.toString() + java.io.File.pathSeparator + extraCp;
			List<String> options = List.of("-d", outputDir.toString(), "-cp", cp);
			var fileObject = new SimpleJavaFileObject(srcFile.toUri(),
					javax.tools.JavaFileObject.Kind.SOURCE) {
				@Override public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return source;
				}
			};
			boolean ok = compiler.getTask(null, null, null, options, null, List.of(fileObject)).call();
			if (!ok) throw new IllegalStateException("编译失败: " + fqcn);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
