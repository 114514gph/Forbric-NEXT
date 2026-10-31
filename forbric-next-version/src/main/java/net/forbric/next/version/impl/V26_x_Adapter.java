package net.forbric.next.version.impl;

import net.forbric.next.version.MinecraftVersion;
import net.forbric.next.version.VersionAdapter;

/**
 * Minecraft 26.x adapter (target version, matches Forbric 0.3.0's 26.2).
 *
 * <p>This is the primary target. It uses Mojmap, requires Java 21+ (the game itself needs 25),
 * and is the version NeoForbric will fully support first.
 */
public class V26_x_Adapter implements VersionAdapter {

	private static final MinecraftVersion VERSION = MinecraftVersion.of(
			"26.2", 26, 2, 0, "mojmap", 21);

	@Override
	public MinecraftVersion version() {
		return VERSION;
	}

	@Override
	public String resolveClassName(String key) {
		return switch (key) {
			case "server" -> "net.minecraft.server.MinecraftServer";
			case "client" -> "net.minecraft.client.Minecraft";
			case "world" -> "net.minecraft.world.level.Level";
			case "block" -> "net.minecraft.world.level.block.Block";
			case "item" -> "net.minecraft.world.item.Item";
			default -> throw new IllegalArgumentException("Unknown class key for 26.x: " + key);
		};
	}

	@Override
	public boolean hasEvent(String eventKey) {
		// 26.x has the full event set
		return true;
	}

	@Override
	public boolean isFunctional() {
		return true; // primary target
	}
}
