package net.forbric.next.common;

/**
 * Central log facade. Routes to stdout with simple formatting — no external logging dependency.
 */
public final class NextLog {
	private NextLog() {}

	public static void info(String msg, Object... args) {
		System.out.println("[Forbric-NEXT] " + format(msg, args));
	}

	public static void warn(String msg, Object... args) {
		System.out.println("[Forbric-NEXT][WARN] " + format(msg, args));
	}

	public static void error(String msg, Object... args) {
		System.err.println("[Forbric-NEXT][ERROR] " + format(msg, args));
	}

	private static String format(String msg, Object... args) {
		if (args.length == 0) return msg;
		try {
			return String.format(msg.replace("{}", "%s"), args);
		} catch (Exception e) {
			return msg;
		}
	}
}
