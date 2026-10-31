package net.neoforbric.version.impl;

import net.neoforbric.version.MinecraftVersion;
import net.neoforbric.version.VersionAdapter;

/**
 * Minecraft 1.20.x adapter (skeleton).
 *
 * <p>1.20.1 is the most widely-modded version. It uses Mojmap (for Fabric) or MCP (Forge),
 * requires Java 17, and introduced the {@code Registry.FREEZE} event.
 */
public class V1_20_Adapter implements VersionAdapter {

	private static final MinecraftVersion VERSION = MinecraftVersion.of(
			"1.20.1", 1, 20, 1, "mojmap", 17);

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
			default -> throw new IllegalArgumentException("Unknown class key for 1.20: " + key);
		};
	}

	@Override
	public boolean hasEvent(String eventKey) {
		return switch (eventKey) {
			case "server_starting", "server_started", "server_stopping", "server_stopped" -> true;
			case "client_tick", "client_started", "registry_freeze" -> true;
			default -> false;
		};
	}

	@Override
	public boolean isFunctional() {
		return false; // skeleton only
	}
}
