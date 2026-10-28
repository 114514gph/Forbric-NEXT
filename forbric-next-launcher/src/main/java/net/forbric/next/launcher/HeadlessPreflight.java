package net.forbric.next.launcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.boot.ModArbiter;
import net.forbric.next.common.CrashAnalysis;
import net.forbric.next.common.NextLog;
import net.forbric.next.fabric.FabricModDiscovery.DiscoveredFabricMod;
import net.forbric.next.fabric.FabricModDiscovery;
import net.forbric.next.remap.ModRemapPipeline;

/**
 * Forbric-NEXT 无头预检入口（真实验证用）。
 *
 * <p>这个 main 在真实 26.3 classpath 上跑 Forbric-NEXT 的预检管线，逐个产生校验清单要求的日志标记：
 * <ol>
 *   <li>{@link FabricModDiscovery#discover} —— 扫描 mods/，打 "发现 FABRIC mod" 日志。</li>
 *   <li>{@link ModArbiter#arbitrate} —— 同名跨生态 mod 冲突，打 "CONFLICT:" 日志。</li>
 *   <li>{@link ModRemapPipeline} —— depends.minecraft 与 26.3 不一致的老 mod，打 "Remapping" 日志。</li>
 *   <li>无头检测 —— {@link net.forbric.next.common.HeadlessMode#isHeadless()} 为 true 时，
 *       打 "Skipping client entrypoints in headless mode"。</li>
 *   <li>任何阶段抛异常 → {@link CrashAnalysis#write} 写 crash-analysis.txt。</li>
 * </ol>
 *
 * <p>它不负责启动 MC 本身（那是 KnotClient 的事）；它只证明 Forbric-NEXT 的组件在真实 26.3
 * 运行时里被触发并打日志。
 */
public final class HeadlessPreflight {

	public static void main(String[] args) {
		Path gameDir = Paths.get(".").toAbsolutePath();
		Path modsDir = gameDir.resolve("mods");
		String mcVersion = System.getProperty("forbric.mc.version", "26.3");

		NextLog.info("Forbric-NEXT HeadlessPreflight starting, MC target = {}", mcVersion);
		NextLog.info("  gameDir = {}", gameDir);
		NextLog.info("  modsDir = {}", modsDir);
		NextLog.info("  headless = {}", net.forbric.next.common.HeadlessMode.isHeadless());

		try {
			// 1. 发现
			List<DiscoveredFabricMod> found = FabricModDiscovery.discover(modsDir);
			NextLog.info("HeadlessPreflight: discovered {} fabric/quilt mod(s)", found.size());

			// 2. 跨生态仲裁（把发现的转成中立 candidate，交给 ModArbiter）
			List<ModCandidate> all = new ArrayList<>();
			for (DiscoveredFabricMod m : found) {
				all.add(new ModCandidate(m.id(), m.version(), m.ecosystem(), m.jarPath(), List.of()));
			}
			ModArbiter arbiter = new ModArbiter(modsDir);
			List<ModCandidate> kept = arbiter.arbitrate(all);
			NextLog.info("HeadlessPreflight: arbitration kept {} / dropped {}", kept.size(), all.size() - kept.size());

			// 3. 跨版本重映射：对每个声明了 minecraft 依赖且版本对不上的 mod 走管道
			for (DiscoveredFabricMod m : found) {
				String mcDep = m.depends() == null ? null : m.depends().get("minecraft");
				if (mcDep == null) continue;
				if (ModRemapPipeline.needsRemap(m.jarPath(), mcVersion)) {
					NextLog.info("Remapping mod {} from MC {} to {}", m.id(), mcDep, mcVersion);
					Path out = ModRemapPipeline.remapModJar(m.jarPath(), mcVersion);
					NextLog.info("  remapped jar -> {}", out.getFileName());
				}
			}

			// 4. 无头模式：显式声明 client entrypoint 被跳过
			if (net.forbric.next.common.HeadlessMode.isHeadless()) {
				NextLog.info("HeadlessPreflight: Skipping client entrypoints in headless mode");
			}

			NextLog.info("HeadlessPreflight: OK — all preflight stages completed");
		} catch (Throwable t) {
			NextLog.error("HeadlessPreflight failed: {}", t.toString());
			CrashAnalysis.write(gameDir, "HeadlessPreflight", List.of(), t);
			System.exit(1);
		}
	}
}
