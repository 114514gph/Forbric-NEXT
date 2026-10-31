package net.neoforbric.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link RemapMappings} 行为测试。
 *
 * <p>覆盖：点号/斜杠归一、三张表的增查、properties 文件加载（含注释/前后缀）、未命中返回原值。
 */
class RemapMappingsTest {

	@Test
	void classMappingNormalizesDotsToSlashes() {
		RemapMappings m = new RemapMappings();
		// 点号形式存进去，应该归一成斜杠内部形式
		m.addClassMapping("com.example.old.Old", "com.example.new.New");
		assertTrue(m.classMappings().containsKey("com/example/old/Old"), "点号应转成斜杠 key");
		assertEquals("com/example/new/New", m.mapClass("com/example/old/Old"));
	}

	@Test
	void mapClassReturnsOriginalWhenUnmapped() {
		RemapMappings m = new RemapMappings();
		m.addClassMapping("a.B", "b.B");
		// 没映射的类原样返回
		assertEquals("some/Unmapped/Class", m.mapClass("some/Unmapped/Class"));
	}

	@Test
	void fieldAndMethodMappings() {
		RemapMappings m = new RemapMappings();
		m.addFieldMapping("field_123", "someField");
		m.addMethodMapping("method_456", "doThing");
		assertEquals("someField", m.mapField("field_123"));
		assertEquals("doThing", m.mapMethod("method_456"));
		assertEquals("unchanged", m.mapField("unchanged"));
	}

	@Test
	void loadsFromPropertiesFile(@TempDir Path tmp) throws IOException {
		Path props = tmp.resolve("map.properties");
		Files.writeString(props, """
				# 注释行
				class.com.example.old.Old=com.example.new.New
				class.net/minecraft/class_1=net/minecraft/Player
				field.field_1=namedField
				method.method_2=namedMethod
				""", StandardCharsets.UTF_8);

		RemapMappings m = new RemapMappings();
		m.loadFromProperties(props);

		assertEquals("com/example/new/New", m.mapClass("com/example/old/Old"));
		assertEquals("net/minecraft/Player", m.mapClass("net/minecraft/class_1"));
		assertEquals("namedField", m.mapField("field_1"));
		assertEquals("namedMethod", m.mapMethod("method_2"));
	}

	@Test
	void emptyMappingsLeavesClassUntouched() {
		RemapMappings m = new RemapMappings();
		assertEquals("a/b/C", m.mapClass("a/b/C"));
		assertTrue(m.classMappings().isEmpty());
	}
}
