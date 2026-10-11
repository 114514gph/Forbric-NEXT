package net.forbric.next.installer.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Forbric-NEXT dedicated server installer — CLI.
 *
 * <p>Usage:
 * <pre>
 *   java -jar forbric-next-server-installer.jar --mc 1.21.1 --dir /opt/mc-server [--accept-eula]
 * </pre>
 *
 * <p>This is a skeleton: it validates the version, creates the target directory, and writes
 * launch scripts. The actual server jar download comes later.
 */
public class ServerInstallerCLI {

	public static void main(String[] args) {
		String mcVersion = null;
		Path targetDir = null;
		boolean acceptEula = false;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--mc" -> mcVersion = args[++i];
				case "--dir" -> targetDir = Path.of(args[++i]);
				case "--accept-eula" -> acceptEula = true;
				case "--help", "-h" -> {
					printUsage();
					return;
				}
				default -> System.err.println("Unknown argument: " + args[i]);
			}
		}

		if (mcVersion == null || targetDir == null) {
			printUsage();
			System.exit(1);
		}

		ServerVersionCatalog.Entry entry = ServerVersionCatalog.get(mcVersion);
		if (entry == null) {
			System.err.println("Unknown MC version: " + mcVersion);
			System.err.println("Supported: " + ServerVersionCatalog.all().keySet());
			System.exit(1);
		}

		if (!entry.exists()) {
			System.err.println("WARNING: MC " + mcVersion + " does not exist as of 2026-10. (" + entry.notes() + ")");
			System.err.println("Proceeding anyway per explicit request.");
		}

		System.out.println("Forbric-NEXT server installer");
		System.out.println("  MC version:  " + mcVersion);
		System.out.println("  Target dir:  " + targetDir);
		System.out.println("  NeoForge:    " + (entry.neoForgeVersion() != null ? entry.neoForgeVersion() : "N/A"));
		System.out.println("  Forge:       " + (entry.forgeVersion() != null ? entry.forgeVersion() : "N/A"));
		System.out.println("  Fabric API:  " + (entry.fabricVersion() != null ? entry.fabricVersion() : "N/A"));

		try {
			Files.createDirectories(targetDir);

			// EULA
			if (acceptEula) {
				Files.writeString(targetDir.resolve("eula.txt"), "eula=true\n");
				System.out.println("  EULA:        accepted");
			} else {
				System.out.println("  EULA:        NOT accepted (pass --accept-eula)");
			}

			// Launch scripts
			writeStartScript(targetDir, mcVersion);
			writeStopScript(targetDir);

			System.out.println("\nDone. Edit start.sh/start.bat to adjust JVM flags, then run it.");
		} catch (IOException e) {
			System.err.println("Install failed: " + e.getMessage());
			System.exit(1);
		}
	}

	private static void writeStartScript(Path dir, String mcVersion) throws IOException {
		String javaCmd = "java -Xmx4G -Xms1G -jar forbric-next-server-" + mcVersion + ".jar nogui";

		Files.writeString(dir.resolve("start.sh"),
				"#!/usr/bin/env bash\n" +
				"# Forbric-NEXT server start script for MC " + mcVersion + "\n" +
				"cd \"$(dirname \"$0\")\"\n" +
				javaCmd + "\n");
		dir.resolve("start.sh").toFile().setExecutable(true);

		Files.writeString(dir.resolve("start.bat"),
				"@echo off\r\n" +
				"rem Forbric-NEXT server start script for MC " + mcVersion + "\r\n" +
				"cd /d \"%~dp0\"\r\n" +
				javaCmd + "\r\n" +
				"pause\r\n");

		System.out.println("  Scripts:     start.sh, start.bat written");
	}

	private static void writeStopScript(Path dir) throws IOException {
		Files.writeString(dir.resolve("stop.sh"),
				"#!/usr/bin/env bash\n" +
				"# Send 'stop' to the server's console (if running in a screen/tmux)\n" +
				"echo \"stop\"\n");
		dir.resolve("stop.sh").toFile().setExecutable(true);
	}

	private static void printUsage() {
		System.out.println("Usage: java -jar forbric-next-server-installer.jar --mc <version> --dir <path> [--accept-eula]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --mc <version>    MC version to install (e.g. 1.21.1)");
		System.out.println("  --dir <path>      Target server directory");
		System.out.println("  --accept-eula     Auto-write eula=true");
		System.out.println("  --help            Show this message");
		System.out.println();
		System.out.println("Supported MC versions: " + ServerVersionCatalog.all().keySet());
	}
}
