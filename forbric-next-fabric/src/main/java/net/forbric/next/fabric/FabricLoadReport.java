package net.forbric.next.fabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.forbric.next.common.NextLog;
import net.forbric.next.fabric.FabricLoadOrder.Result.SkippedMod;
import net.forbric.next.fabric.FabricModDiscovery.DiscoveredFabricMod;

/**
 * Fabric 加载报告。
 *
 * <p>写到 {@code <gameDir>/.forbric-next/load-report.txt}，分三段：
 * <ol>
 *   <li>成功加载的 mod（id / 版本 / 生态系）</li>
 *   <li>entrypoint 执行失败的 mod（id / 组 / 类 / 原因）</li>
 *   <li>因缺前置被跳过的 mod（id / 缺哪些依赖）</li>
 * </ol>
 *
 * <p>报告是给人看的，不是给程序读的——启动出问题时用户第一时间打开这个文件定位。
 */
public final class FabricLoadReport {

	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final List<DiscoveredFabricMod> succeeded = new ArrayList<>();
	private final List<FabricEntrypointExecutor.EntrypointFailure> failures = new ArrayList<>();
	private final List<SkippedMod> skipped = new ArrayList<>();

	/** 记一个成功加载的 mod。 */
	public void addSuccess(DiscoveredFabricMod mod) {
		succeeded.add(mod);
	}

	/** 记一批 entrypoint 失败。 */
	public void addFailures(List<FabricEntrypointExecutor.EntrypointFailure> fs) {
		failures.addAll(fs);
	}

	/** 记一批因缺前置被跳过的 mod。 */
	public void addSkipped(List<SkippedMod> ss) {
		skipped.addAll(ss);
	}

	/**
	 * 把报告写到 {@code <gameDir>/.forbric-next/load-report.txt}。
	 *
	 * @param gameDir 游戏根目录
	 */
	public void write(Path gameDir) {
		Path reportDir = gameDir.resolve(".forbric-next");
		Path reportFile = reportDir.resolve("load-report.txt");
		try {
			Files.createDirectories(reportDir);
			StringBuilder sb = new StringBuilder();
			sb.append("Forbric-NEXT Fabric/Quilt 加载报告\n");
			sb.append("生成时间: ").append(LocalDateTime.now().format(TS)).append('\n');
			sb.append("========================================\n\n");

			sb.append("【成功加载】(").append(succeeded.size()).append(" 个)\n");
			for (DiscoveredFabricMod m : succeeded) {
				sb.append("  - ").append(m.id())
						.append(" @ ").append(m.version())
						.append(" [").append(m.ecosystem()).append(']');
				if (m.name() != null) sb.append("  (").append(m.name()).append(')');
				sb.append('\n');
			}
			sb.append('\n');

			sb.append("【entrypoint 执行失败】(").append(failures.size()).append(" 条)\n");
			for (FabricEntrypointExecutor.EntrypointFailure f : failures) {
				sb.append("  - mod=").append(f.modId())
						.append(" 组=").append(f.group())
						.append(" 类=").append(f.className())
						.append(" 原因=").append(f.error().toString()).append('\n');
			}
			sb.append('\n');

			sb.append("【因缺前置被跳过】(").append(skipped.size()).append(" 个)\n");
			for (SkippedMod s : skipped) {
				sb.append("  - ").append(s.mod().id())
						.append(" @ ").append(s.mod().version())
						.append(" 缺少: ").append(String.join(", ", s.missingDeps())).append('\n');
			}
			sb.append('\n');
			sb.append("========================================\n");
			sb.append("合计: 成功 ").append(succeeded.size())
					.append(" / 失败 ").append(failures.size())
					.append(" / 跳过 ").append(skipped.size()).append('\n');

			Files.writeString(reportFile, sb.toString(),
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			NextLog.info("FabricLoadReport: 报告已写入 {}", reportFile);
		} catch (IOException e) {
			NextLog.error("FabricLoadReport: 写报告失败: {}", e.toString());
		}
	}
}
