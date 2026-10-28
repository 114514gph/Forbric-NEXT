package net.forbric.next.fabric;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.nio.file.StandardCopyOption;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.common.NextLog;

/**
 * Fabric/Quilt mod 发现器。
 *
 * <p>职责边界（与 0.3.0 的 bug 对照）：
 * <ul>
 *   <li>只扫描 {@code mods/} 目录下的 {@code .jar} 文件，逐个读 {@code fabric.mod.json} /
 *       {@code quilt.mod.json}。</li>
 *   <li>不碰 Forge 的 {@code mods.toml}，也不把 Fabric mod 塞进 Forge 的依赖图。</li>
 *   <li>识别出的每个 mod 都打上 {@link Ecosystem#FABRIC} 或 {@link Ecosystem#QUILT} 标签，
 *       但后续排序 / entrypoint 执行全部走 Fabric 那一套（Quilt 复用 Fabric 加载路径）。</li>
 * </ul>
 *
 * <p>解析失败的 jar（不是合法的 Fabric/Quilt mod、JSON 坏掉）会被跳过并打 WARN，
 * 不会让一个坏 jar 炸掉整个启动流程。
 */
public final class FabricModDiscovery {

	private FabricModDiscovery() {}

	/**
	 * 发现阶段产出的 mod 描述（比中立的 {@code ModCandidate} 多保留了 entrypoints / depends / name，
	 * 这些字段只在 Fabric 加载路径内部使用）。
	 *
	 * @param id           mod id
	 * @param version      版本字符串
	 * @param name          人类可读名称（可能为 null）
	 * @param ecosystem     FABRIC 或 QUILT
	 * @param jarPath       jar 在磁盘上的位置
	 * @param entrypoints   entrypoint 组名 → 入口类全限定名列表
	 * @param depends       mod id → 版本约束
	 * @param mixinConfigs  fabric.mod.json "mixins" 字段声明的 mixin config 文件名列表
	 * @param parentJar     外层 mod jar（JiJ 嵌套 mod 指向它的外层 jar；顶层 mod 为 null）
	 */
	public record DiscoveredFabricMod(
			String id,
			String version,
			String name,
			Ecosystem ecosystem,
			Path jarPath,
			Map<String, List<String>> entrypoints,
			Map<String, String> depends,
			List<String> mixinConfigs,
			Path parentJar
	) {
		/**
		 * 兼容旧调用的紧凑构造器：mixin configs 为空、顶层 mod（parentJar=null）。
		 * 新代码请用全参构造器并显式传 mixinConfigs / parentJar。
		 */
		public DiscoveredFabricMod(
				String id,
				String version,
				String name,
				Ecosystem ecosystem,
				Path jarPath,
				Map<String, List<String>> entrypoints,
				Map<String, String> depends) {
			this(id, version, name, ecosystem, jarPath, entrypoints, depends, List.of(), null);
		}
	}

	/**
	 * 扫描 mods 目录。
	 *
	 * @param modsDir mods 目录（可能不存在）
	 * @return 成功识别的 Fabric/Quilt mod 列表；目录不存在或没有 jar 时返回空列表
	 */
	public static List<DiscoveredFabricMod> discover(Path modsDir) {
		List<DiscoveredFabricMod> out = new ArrayList<>();
		if (modsDir == null || !Files.isDirectory(modsDir)) {
			NextLog.info("FabricModDiscovery: mods 目录不存在或不是目录: {}", modsDir);
			return out;
		}

		try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : jars) {
				try {
					QuiltModDetector.QuiltModMetadata meta = QuiltModDetector.readMetadata(jar);
					if (meta.id() == null || meta.id().isEmpty()) {
						NextLog.warn("FabricModDiscovery: jar 缺少 id，跳过: {}", jar);
						continue;
					}
					DiscoveredFabricMod mod = new DiscoveredFabricMod(
							meta.id(),
							meta.version() == null ? "0.0.0" : meta.version(),
							meta.name(),
							meta.ecosystem(),
							jar,
							meta.entrypoints() == null ? new LinkedHashMap<>() : meta.entrypoints(),
							meta.depends() == null ? new LinkedHashMap<>() : meta.depends(),
							meta.mixinConfigs() == null ? List.of() : meta.mixinConfigs(),
							null
					);
					out.add(mod);
					NextLog.info("  发现 {} mod: {} {} ({})",
							meta.ecosystem(), meta.id(), meta.version(), jar.getFileName());
					// mixin config 已读入 DiscoveredFabricMod；实际注册由 boot 阶段的
					// ForbricMixinService.registerConfig() 完成。
					if (!mod.mixinConfigs().isEmpty()) {
						NextLog.info("  mixin configs: {}", mod.mixinConfigs());
					}
					// JiJ：把这个外层 jar 里 META-INF/jars/*.jar 也当作嵌套 mod 注册。
					discoverNestedJars(jar, out);
				} catch (IOException | IllegalStateException e) {
					// 一个坏 jar 不影响其它 mod
					NextLog.warn("FabricModDiscovery: 跳过无法识别的 jar {}: {}", jar, e.getMessage());
				}
			}
		} catch (IOException e) {
			NextLog.error("FabricModDiscovery: 扫描 mods 目录失败: {}", e.toString());
		}
		return out;
	}

	/**
	 * 扫描一个外层 mod jar 内的 {@code META-INF/jars/*.jar}（JiJ，jar-in-jar）。
	 *
	 * <p><b>为什么必须做：</b>Fabric Loader 自己的 jar 里就嵌了 MixinExtras；如果不把嵌套 jar
	 * 也注册成 mod，{@code isModLoaded("mixinextras")} 会返回 false，ModMenu 等 mod 会崩。
	 * 每个嵌套 jar 读自己的 {@code fabric.mod.json} 后作为嵌套 mod 加进候选列表，
	 * {@code parentJar} 指向外层 jar。
	 *
	 * @param outerJar 外层 mod jar 路径
	 * @param out      候选列表（嵌套 mod 追加进来）
	 */
	private static void discoverNestedJars(Path outerJar, List<DiscoveredFabricMod> out) {
		try (JarFile outer = new JarFile(outerJar.toFile())) {
			Enumeration<JarEntry> entries = outer.entries();
			while (entries.hasMoreElements()) {
				JarEntry je = entries.nextElement();
				String name = je.getName();
				if (!name.startsWith("META-INF/jars/") || !name.endsWith(".jar")) continue;

				// 把嵌套 jar 落盘成临时文件，复用 readMetadata(Path) 读它的 fabric.mod.json。
				Path nested = Files.createTempFile("forbric-nested-", ".jar");
				nested.toFile().deleteOnExit();
				try (var in = outer.getInputStream(je)) {
					Files.copy(in, nested, StandardCopyOption.REPLACE_EXISTING);
				}
				try {
					QuiltModDetector.QuiltModMetadata meta = QuiltModDetector.readMetadata(nested);
					if (meta.id() == null || meta.id().isEmpty()) {
						NextLog.warn("FabricModDiscovery: 嵌套 jar 缺少 id，跳过: {} (inside {})",
								name, outerJar.getFileName());
						continue;
					}
					out.add(new DiscoveredFabricMod(
							meta.id(),
							meta.version() == null ? "0.0.0" : meta.version(),
							meta.name(),
							meta.ecosystem(),
							nested,
							meta.entrypoints() == null ? new LinkedHashMap<>() : meta.entrypoints(),
							meta.depends() == null ? new LinkedHashMap<>() : meta.depends(),
							meta.mixinConfigs() == null ? List.of() : meta.mixinConfigs(),
							outerJar
					));
					NextLog.info("  嵌套 mod: {} (inside {})", meta.id(), outerJar.getFileName());
				} catch (IOException | IllegalStateException inner) {
					NextLog.warn("FabricModDiscovery: 嵌套 jar 无法识别，跳过 {} (inside {}): {}",
							name, outerJar.getFileName(), inner.getMessage());
				}
			}
		} catch (IOException e) {
			NextLog.warn("FabricModDiscovery: 扫描 JiJ 嵌套 jar 失败 {}: {}", outerJar, e.getMessage());
		}
	}
}
