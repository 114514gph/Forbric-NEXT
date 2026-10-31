package net.neoforbric.version.impl;

import net.neoforbric.version.MinecraftVersion;
import net.neoforbric.version.VersionAdapter;

/**
 * Minecraft 1.21.x adapter (skeleton).
 *
 * <p>1.21.4 uses Mojmap, requires Java 21, and moved registry initialization to
 * the bootstrap region. NeoForge for 1.21 is the primary Forge lineage.
 */
public class V1_21_Adapter implements VersionAdapter {

	private static final MinecraftVersion VERSION = MinecraftVersion.of(
			"1.21.4", 1, 21, 4, "mojmap", 21);

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
			default -> throw new IllegalArgumentException("Unknown class key for 1.21: " + key);
		};
	}

	@Override
	public boolean hasEvent(String eventKey) {
		return switch (eventKey) {
			case "server_starting", "server_started", "server_stopping", "server_stopped" -> true;
			case "client_tick", "client_started", "registry_freeze", "datapack_load" -> true;
			default -> false;
		};
	}

	@Override
	public boolean isFunctional() {
		return false; // skeleton only
	}
}
