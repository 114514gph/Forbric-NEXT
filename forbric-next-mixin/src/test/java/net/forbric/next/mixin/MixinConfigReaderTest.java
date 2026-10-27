package net.forbric.next.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** MixinConfigReader 的纯解析单测（不需要 MC 环境）。 */
class MixinConfigReaderTest {

	private static final String SAMPLE = """
			{
			  "required": true,
			  "package": "net.forbric.test.mixin",
			  "compatibilityLevel": "JAVA_21",
			  "mixins": ["CommonA", "CommonB"],
			  "server": ["ServerOnly"],
			  "client": ["ClientOnly"],
			  "plugin": "net.forbric.test.MixinPlugin",
			  "refmap": "forbric-test.refmap.json"
			}
			""";

	@Test
	void parsesStandardKeys() {
		ModMixinConfig cfg = MixinConfigReader.parse("forbric-test.mixins.json", "/tmp/x.jar", SAMPLE);
		assertEquals("net.forbric.test.mixin", cfg.mixinPackage());
		assertEquals(2, cfg.commonMixins().size());
		assertTrue(cfg.commonMixins().contains("CommonA"));
		assertEquals(1, cfg.serverMixins().size());
		assertEquals(1, cfg.clientMixins().size());
		assertEquals("net.forbric.test.MixinPlugin", cfg.pluginClassName());
		assertEquals("forbric-test.refmap.json", cfg.refmapName());
		assertEquals("JAVA_21", cfg.compatibilityLevel());
	}

	@Test
	void serverActiveMixinsMergeCommonAndServer() {
		ModMixinConfig cfg = MixinConfigReader.parse("c.json", "/tmp/x.jar", SAMPLE);
		var active = cfg.activeMixinsForServer();
		assertEquals(3, active.size()); // CommonA + CommonB + ServerOnly
		assertTrue(active.contains("ClientOnly") == false);
	}

	@Test
	void missingPackageIsRejected() {
		String bad = "{ \"mixins\": [] }";
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> MixinConfigReader.parse("bad.json", "/tmp/x.jar", bad));
	}
}
