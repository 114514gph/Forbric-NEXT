package net.neoforbric.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.neoforbric.installer.InstallerVersionCatalog.CatalogEntry;

/**
 * {@link InstallerVersionCatalog} 的行为测试。
 *
 * <p>验证：
 * <ol>
 *   <li>6 个 MC 版本都在目录里；</li>
 *   <li>1.21.11 标记 exists=true（之前标错过成 false）；</li>
 *   <li>Forge / NeoForge 版本号在该版本支持时非空。</li>
 * </ol>
 */
class InstallerVersionCatalogTest {

	/** 目录里应有的 6 个 MC 版本。 */
	private static final Set<String> EXPECTED = Set.of(
			"1.18.2", "1.19.2", "1.20.4", "1.21.1", "1.21.11", "26.3");

	@Test
	void allSixMcVersionsArePresent() {
		Map<String, CatalogEntry> all = InstallerVersionCatalog.all();
		assertEquals(EXPECTED.size(), all.size(), "目录应有 6 个版本");
		assertTrue(all.keySet().containsAll(EXPECTED), "6 个预期版本都应在目录里: " + all.keySet());
	}

	@Test
	void version1_21_11MarkedAsExisting() {
		CatalogEntry e = InstallerVersionCatalog.get("1.21.11");
		assertNotNull(e);
		assertTrue(e.exists(), "1.21.11 必须标记 exists=true（之前误标过 false）");
	}

	@Test
	void loaderVersionsAreNonEmptyWhereClaimed() {
		Map<String, CatalogEntry> all = InstallerVersionCatalog.all();
		for (CatalogEntry e : all.values()) {
			// fabric 版本号每个条目都必须有
			assertNotNull(e.fabricVersion(), e.mcVersion() + " 应有 fabric 版本号");
			assertFalse(e.fabricVersion().isBlank(), e.mcVersion() + " 的 fabric 版本号不应为空");
		}

		// Forge 支持的版本（1.18.2 ~ 1.21.11）必须有 forgeVersion
		for (String v : new String[] {"1.18.2", "1.19.2", "1.20.4", "1.21.1", "1.21.11"}) {
			CatalogEntry e = all.get(v);
			assertNotNull(e, v + " 应在目录里");
			assertNotNull(e.forgeVersion(), v + " 应支持 Forge，forgeVersion 不应为空");
			assertFalse(e.forgeVersion().isBlank(), v + " 的 forgeVersion 不应为空串");
		}

		// NeoForge 从 1.20.4 开始有
		for (String v : new String[] {"1.20.4", "1.21.1", "1.21.11", "26.3"}) {
			CatalogEntry e = all.get(v);
			assertNotNull(e, v + " 应在目录里");
			assertNotNull(e.neoForgeVersion(), v + " 应支持 NeoForge，neoForgeVersion 不应为空");
			assertFalse(e.neoForgeVersion().isBlank(), v + " 的 neoForgeVersion 不应为空串");
		}
	}
}
