package net.forbric.next.installer.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * NeoForbric dedicated server installer — CLI.
 *
 * <p>Usage:
 * <pre>
 *   java -jar neoforbric-server-installer.jar --mc 1.21.1 --dir /opt/mc-server [--accept-eula]
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
		// 服务端模式：默认 pure（纯 Forbric）；mixed = Forbric + Bukkit 插件混合端。
		String serverMode = "pure";

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--mc" -> mcVersion = args[++i];
				case "--dir" -> targetDir = Path.of(args[++i]);
				case "--accept-eula" -> acceptEula = true;
				case "--server-mode" -> serverMode = args[++i];
				case "--help", "-h" -> {
					printUsage();
					return;
				}
				default -> System.err.println("Unknown argument: " + args[i]);
			}
		}

		// 规范化并校验服务端模式取值。
		serverMode = serverMode.trim().toLowerCase();
		if (!serverMode.equals("pure") && !serverMode.equals("mixed")) {
			System.err.println("Unknown --server-mode: " + serverMode + " (expected pure or mixed)");
			System.exit(1);
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

		System.out.println("NeoForbric server installer");
		System.out.println("  MC version:  " + mcVersion);
		System.out.println("  Target dir:  " + targetDir);
		System.out.println("  NeoForge:    " + (entry.neoForgeVersion() != null ? entry.neoForgeVersion() : "N/A"));
		System.out.println("  Forge:       " + (entry.forgeVersion() != null ? entry.forgeVersion() : "N/A"));
		System.out.println("  Fabric API:  " + (entry.fabricVersion() != null ? entry.fabricVersion() : "N/A"));
		System.out.println("  Server mode: " + ("mixed".equals(serverMode) ? "mixed (Forbric + Bukkit)" : "pure (Forbric only)"));

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
			writeStartScript(targetDir, mcVersion, serverMode);
			writeStopScript(targetDir);
			writeConfig(targetDir, mcVersion);

			System.out.println("\nDone. Edit start.sh/start.bat to adjust JVM flags, then run it.");
		} catch (IOException e) {
			System.err.println("Install failed: " + e.getMessage());
			System.exit(1);
		}
	}

	private static void writeStartScript(Path dir, String mcVersion, String serverMode) throws IOException {
		// 安装器不自带 vanilla server.jar——用户需自行下载 MC server.jar 到本目录。
		// start.sh 用 classpath 方式拉起 Forbric launcher Main，而不是引用不存在的 fat jar。
		String shebang = "#!/usr/bin/env bash\n";
		String header =
				"# NeoForbric server start script for MC " + mcVersion + "\n" +
				"cd \"$(dirname \"$0\")\"\n" +
				"\n" +
				"# Pick java: prefer JAVA_HOME, fall back to PATH\n" +
				"if [ -n \"$JAVA_HOME\" ] && [ -x \"$JAVA_HOME/bin/java\" ]; then\n" +
				"\tJAVA=\"$JAVA_HOME/bin/java\"\n" +
				"else\n" +
				"\tJAVA=java\n" +
				"fi\n" +
				"\n" +
				"# NeoForbric does not bundle the vanilla server.jar.\n" +
				"# Download it from Mojang and place it next to this script, e.g.:\n" +
				"#   wget https://piston-data.mojang.com/.../server.jar\n" +
				"if [ ! -f server.jar ]; then\n" +
				"\techo \"ERROR: server.jar not found in $(pwd).\"\n" +
				"\techo \"Please download the vanilla Minecraft server.jar first (see README).\"\n" +
				"\texit 1\n" +
				"fi\n" +
				"\n" +
				"exec \"$JAVA\" -Xmx4G -Xms1G" +
				" -Dforbric.compatibilityPolicy=continue" +
				" -Dforbric.serverMode=" + serverMode +
				" -Dforbric.gameDir=\"$(pwd)\"" +
				" -cp \"server.jar:lib/*\"" +
				" net.forbric.next.launcher.Main nogui\n";

		Files.writeString(dir.resolve("start.sh"), shebang + header);
		dir.resolve("start.sh").toFile().setExecutable(true);

		String bat =
				"@echo off\r\n" +
				"rem NeoForbric server start script for MC " + mcVersion + "\r\n" +
				"cd /d \"%~dp0\"\r\n" +
				"\r\n" +
				"if defined JAVA_HOME (\r\n" +
				"\tset JAVA=%JAVA_HOME%\\bin\\java.exe\r\n" +
				") else (\r\n" +
				"\tset JAVA=java\r\n" +
				")\r\n" +
				"\r\n" +
				"if not exist server.jar (\r\n" +
				"\techo ERROR: server.jar not found in %CD%.\r\n" +
				"\techo Please download the vanilla Minecraft server.jar first.\r\n" +
				"\texit /b 1\r\n" +
				")\r\n" +
				"\r\n" +
				"\"%JAVA%\" -Xmx4G -Xms1G" +
				" -Dforbric.compatibilityPolicy=continue" +
				" -Dforbric.serverMode=" + serverMode +
				" -Dforbric.gameDir=\"%CD%\"" +
				" -cp \"server.jar;lib/*\"" +
				" net.forbric.next.launcher.Main nogui\r\n" +
				"pause\r\n";
		Files.writeString(dir.resolve("start.bat"), bat);

		System.out.println("  Scripts:     start.sh, start.bat written");
	}

	private static void writeStopScript(Path dir) throws IOException {
		Files.writeString(dir.resolve("stop.sh"),
				"#!/usr/bin/env bash\n" +
				"# Send 'stop' to the server's console (if running in a screen/tmux)\n" +
				"echo \"stop\"\n");
		dir.resolve("stop.sh").toFile().setExecutable(true);
	}

	private static void writeConfig(Path dir, String mcVersion) throws IOException {
		String toml = """
			# NeoForbric configuration
			# Generated by the server installer for MC %s

			[load]
			# "continue" = one mod failure doesn't crash the server (default)
			# "fail"     = stop boot on the first unmet dependency or entrypoint error
			compatibility-policy = "continue"

			[mods]
			# Extra mod directories to scan (default: ./mods)
			# extra-paths = ["./mods-optional"]

			[mixin]
			# Set to "off" to disable all guest mixins (debug)
			enabled = true
			""".formatted(mcVersion);
		Files.writeString(dir.resolve(".neoforbric.toml"), toml);
		System.out.println("  Config:      .neoforbric.toml written");
	}

	private static void printUsage() {
		System.out.println("Usage: java -jar neoforbric-server-installer.jar --mc <version> --dir <path> [--accept-eula] [--server-mode pure|mixed]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --mc <version>       MC version to install (e.g. 1.21.1)");
		System.out.println("  --dir <path>         Target server directory");
		System.out.println("  --accept-eula        Auto-write eula=true");
		System.out.println("  --server-mode <m>   pure (default) = Forbric only; mixed = Forbric + Bukkit plugins");
		System.out.println("  --help               Show this message");
		System.out.println();
		System.out.println("Supported MC versions: " + ServerVersionCatalog.all().keySet());
	}
}
