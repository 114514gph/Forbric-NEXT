package net.neoforbric.remap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * 测试辅助：用系统 javac 编译源码目录到输出目录。
 *
 * <p>只给本包测试用，避免每个测试类都重复写一遍 JavaCompiler 样板。
 */
final class JavaCompileHelper {

	private JavaCompileHelper() {}

	static void compile(Path srcDir, Path outDir) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("当前 JRE 不带 javac，测试需要用 JDK 跑");
		}
		Files.createDirectories(outDir);
		try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
			fm.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, List.of(outDir.toFile()));
			fm.setLocation(javax.tools.StandardLocation.SOURCE_PATH, List.of(srcDir.toFile()));
			var units = fm.getJavaFileObjectsFromFiles(
					Files.walk(srcDir)
							.filter(p -> p.toString().endsWith(".java"))
							.map(Path::toFile)
							.toList());
			JavaCompiler.CompilationTask task = compiler.getTask(null, fm, null,
					List.of("-g:none"), null, units);
			if (!task.call()) {
				throw new IOException("编译失败: " + srcDir);
			}
		}
	}
}
