package net.forbric.next.version.impl;

import net.forbric.next.version.MinecraftVersion;
import net.forbric.next.version.VersionAdapter;

/**
 * Minecraft 1.18.x adapter (skeleton).
 *
 * <p>1.18 uses official MCP mappings (not Mojmap), requires Java 17, and has the old
 * {@code World}/{@code MinecraftServer} class names before the 1.19 renames.
 */
public class V1_18_Adapter implements VersionAdapter {

	private static final MinecraftVersion VERSION = MinecraftVersion.of(
			"1.18.2", 1, 18, 2, "mcp", 17);

	@Override
	public MinecraftVersion version() {
		return VERSION;
	}

	@Override
	public String resolveClassName(String key) {
		return switch (key) {
			case "server" -> "net.minecraft.server.MinecraftServer";
			case "client" -> "net.minecraft.client.Minecraft";
			case "world" -> "net.minecraft.world.World";
			case "block" -> "net.minecraft.world.level.block.Block";
			case "item" -> "net.minecraft.world.item.Item";
			default -> throw new IllegalArgumentException("Unknown class key for 1.18: " + key);
		};
	}

	@Override
	public boolean hasEvent(String eventKey) {
		return switch (eventKey) {
			case "server_starting", "server_started", "server_stopping", "server_stopped" -> true;
			case "client_tick", "client_started" -> true;
			default -> false;
		};
	}

	@Override
	public boolean isFunctional() {
		return false; // skeleton only
	}
}
