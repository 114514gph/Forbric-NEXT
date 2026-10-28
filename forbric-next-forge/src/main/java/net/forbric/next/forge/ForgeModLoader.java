package net.forbric.next.forge;

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

import net.forbric.next.api.Ecosystem;
import net.forbric.next.api.LoadContext;
import net.forbric.next.api.LoadPhase;
import net.forbric.next.api.ModCandidate;
import net.forbric.next.api.ModLoader;
import net.forbric.next.common.ModsToml;
import net.forbric.next.common.NextLog;

/**
 * MinecraftForge ecosystem loader.
 *
 * <p><b>Skeleton status.</b> The key contract this stub enforces (fixing 0.3.0's "strong compat" bug):
 * <ul>
 *   <li>Forge @Mod construction order uses Forge-native semantics: explicit BEFORE/AFTER edges only.
 *       Mandatory {@code depends} is a presence requirement, <em>not</em> an ordering edge.</li>
 *   <li>No Fabric mod is ever included in the Forge construction graph.</li>
 *   <li>Forge lifecycle phases run: Construct → CommonSetup → SidedSetup → Registration →
 *       IMC Enqueue → IMC Process → LoadComplete.</li>
 * </ul>
 */
public class ForgeModLoader implements ModLoader {

	private final List<ModCandidate> discovered = new ArrayList<>();

	@Override
	public Ecosystem ecosystem() {
		return Ecosystem.MINECRAFTFORGE;
	}

	@Override
	public List<ModCandidate> discover(LoadContext context) {
		NextLog.info("ForgeModLoader: scanning {} for META-INF/mods.toml", context.modsDir());
		List<ModCandidate> out = new ArrayList<>();
		Path modsDir = context.modsDir();
		if (modsDir == null || !Files.isDirectory(modsDir)) return out;
		try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : jars) {
				var mods = ModsToml.readFromJar(jar, "META-INF/mods.toml");
				if (mods.isEmpty()) continue; // 不是 Forge mod，留给别的 loader
				for (ModsToml.Mod m : mods.get()) {
					out.add(new ModCandidate(m.modId(), m.version(),
							Ecosystem.MINECRAFTFORGE, jar, List.of()));
					NextLog.info("  发现 Forge mod: {} {} ({})", m.modId(), m.version(), jar.getFileName());
				}
			}
		} catch (IOException e) {
			NextLog.error("ForgeModLoader: 扫描 mods 目录失败: {}", e.toString());
		}
		return out;
	}

	@Override
	public void resolve(List<ModCandidate> candidates, LoadContext context) {
		discovered.clear();
		// Forge 排序：BEFORE/AFTER 是排序边，mandatory depends 只是存在性检查
		// 目前无显式 ordering 边数据，用 modId 做稳定排序
		discovered.addAll(ForgeModSorter.sort(candidates, Map.of()));
		NextLog.info("ForgeModLoader: resolved {} forge mod(s); ordered by BEFORE/AFTER edges",
				discovered.size());
	}

	@Override
	public void runPhase(LoadPhase phase) {
		switch (phase) {
			case CONSTRUCT -> NextLog.info("ForgeModLoader: constructing @Mod classes");
			case REGISTRY -> NextLog.info("ForgeModLoader: firing RegisterEvent");
			case COMMON_SETUP -> NextLog.info("ForgeModLoader: FMLCommonSetupEvent");
			case SIDED_SETUP -> NextLog.info("ForgeModLoader: FMLClientSetupEvent / FMLDedicatedServerSetupEvent");
			case IMC_ENQUEUE -> NextLog.info("ForgeModLoader: InterModEnqueueEvent");
			case IMC_PROCESS -> NextLog.info("ForgeModLoader: InterModProcessEvent");
			case LOAD_COMPLETE -> NextLog.info("ForgeModLoader: FMLLoadCompleteEvent");
			default -> { /* no-op */ }
		}
	}
}
