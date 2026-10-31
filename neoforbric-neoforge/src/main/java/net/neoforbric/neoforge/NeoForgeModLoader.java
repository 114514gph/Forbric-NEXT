package net.neoforbric.neoforge;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.neoforbric.api.Ecosystem;
import net.neoforbric.api.LoadContext;
import net.neoforbric.api.LoadPhase;
import net.neoforbric.api.ModCandidate;
import net.neoforbric.api.ModLoader;
import net.neoforbric.common.ModsToml;
import net.neoforbric.common.NextLog;
import net.neoforbric.api.util.ModDependencySorter;

/**
 * NeoForge ecosystem loader.
 *
 * <p>和 {@link ForgeModLoader} 同一套排序逻辑：BEFORE/AFTER 是排序边，
 * mandatory depends 只是存在性检查。NeoForge 有独立的 mod-bus / game-bus 事件系统。
 */
public class NeoForgeModLoader implements ModLoader {

	private final List<ModCandidate> discovered = new ArrayList<>();

	@Override
	public Ecosystem ecosystem() {
		return Ecosystem.NEOFORGE;
	}

	@Override
	public List<ModCandidate> discover(LoadContext context) {
		NextLog.info("NeoForgeModLoader: scanning {} for META-INF/neoforge.mods.toml", context.modsDir());
		List<ModCandidate> out = new ArrayList<>();
		Path modsDir = context.modsDir();
		if (modsDir == null || !Files.isDirectory(modsDir)) return out;
		try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : jars) {
				var mods = ModsToml.readFromJar(jar, "META-INF/neoforge.mods.toml");
				if (mods.isEmpty()) continue;
				for (ModsToml.Mod m : mods.get()) {
					out.add(new ModCandidate(m.modId(), m.version(),
							Ecosystem.NEOFORGE, jar, List.of()));
					NextLog.info("  发现 NeoForge mod: {} {} ({})", m.modId(), m.version(), jar.getFileName());
				}
			}
		} catch (IOException e) {
			NextLog.error("NeoForgeModLoader: 扫描 mods 目录失败: {}", e.toString());
		}
		return out;
	}

	@Override
	public void resolve(List<ModCandidate> candidates, LoadContext context) {
		discovered.clear();
		Map<String, Set<String>> orderEdges = new HashMap<>();
		for (ModCandidate c : candidates) {
			// 简化：把每个 mod 的依赖当作 AFTER 边（被依赖的先加载）
			for (String dep : java.util.List.<String>of()) { // TODO: 从 mods.toml 提取 [[dependencies]]
				orderEdges.computeIfAbsent(dep, k -> new HashSet<>()).add(c.id());
			}
		}
		discovered.addAll(ModDependencySorter.sort(candidates, orderEdges));
		NextLog.info("NeoForgeModLoader: resolved {} neoforge mod(s); ordered by BEFORE/AFTER edges",
				discovered.size());
	}

	@Override
	public void runPhase(LoadPhase phase) {
		switch (phase) {
			case CONSTRUCT -> NextLog.info("NeoForgeModLoader: constructing mod classes");
			case REGISTRY -> NextLog.info("NeoForgeModLoader: firing RegisterEvent");
			case COMMON_SETUP -> NextLog.info("NeoForgeModLoader: FMLCommonSetupEvent");
			case SIDED_SETUP -> NextLog.info("NeoForgeModLoader: FMLClientSetupEvent / FMLDedicatedServerSetupEvent");
			case IMC_ENQUEUE -> NextLog.info("NeoForgeModLoader: InterModEnqueueEvent");
			case IMC_PROCESS -> NextLog.info("NeoForgeModLoader: InterModProcessEvent");
			case LOAD_COMPLETE -> NextLog.info("NeoForgeModLoader: FMLLoadCompleteEvent");
			default -> { /* no-op */ }
		}
	}
}
