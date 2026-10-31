package net.forbric.next.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 崩溃分析报告——写 {@code .neoforbric/crash-analysis.txt}。
 *
 * <p>原版 Forbric 的 crash-analysis.txt 是用户排障的第一入口。NEXT 对齐这个设计：
 * 崩溃时不只打日志，还生成一份结构化文件，包含时间、异常栈、当时加载到哪个 mod、
 * 已加载的 mod 列表。
 */
public final class CrashAnalysis {

	private CrashAnalysis() {}

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/**
	 * 写一份崩溃分析文件。
	 *
	 * @param gameDir   游戏目录（.neoforbric/ 在它下面）
	 * @param phase     崩溃时正在执行的阶段
	 * @param loadedMods 已成功加载的 mod 列表
	 * @param t         异常
	 */
	public static void write(Path gameDir, String phase, List<String> loadedMods, Throwable t) {
		try {
			Path dir = gameDir.resolve(".neoforbric");
			Files.createDirectories(dir);
			Path file = dir.resolve("crash-analysis.txt");

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);

			pw.println("NeoForbric Crash Report");
			pw.println("=" .repeat(50));
			pw.println("Time:    " + LocalDateTime.now().format(FMT));
			pw.println("Phase:   " + phase);
			pw.println("Mods loaded before crash: " + loadedMods.size());
			pw.println();
			pw.println("Loaded mods:");
			for (String m : loadedMods) {
				pw.println("  - " + m);
			}
			pw.println();
			pw.println("Exception: " + t.getClass().getName() + ": " + t.getMessage());
			pw.println("Stack trace:");
			t.printStackTrace(pw);

			Files.writeString(file, sw.toString());
			NextLog.error("崩溃分析已写入: {}", file);
		} catch (Exception e) {
			NextLog.error("写崩溃分析文件失败: {}", e.getMessage());
		}
	}
}
