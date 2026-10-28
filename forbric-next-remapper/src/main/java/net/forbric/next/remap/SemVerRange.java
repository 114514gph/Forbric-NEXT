package net.forbric.next.remap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabric 风格的 SemVer 范围解析器。
 *
 * <p>支持的语法（对齐 fabric-loader 的 SemanticVersionImpl）：
 * <ul>
 *   <li>{@code 1.20.4} — 精确匹配</li>
 *   <li>{@code >=1.20.4} / {@code <=1.20.4} / {@code >1.20.4} / {@code <1.20.4}</li>
 *   <li>{@code ~1.20.4} — 兼容版本（~1.20.4 = >=1.20.4 <1.21.0）</li>
 *   <li>{@code *} 或 {@code 1.20.x} — 通配符</li>
 *   <li>{@code 1.20.4 - 1.21.1} — 范围</li>
 *   <li>{@code ||} — 或（多组范围）</li>
 * </ul>
 */
public final class SemVerRange {

	private final String raw;
	private final Range[] ranges;

	private SemVerRange(String raw, Range[] ranges) {
		this.raw = raw;
		this.ranges = ranges;
	}

	// ---- 数据模型 ----

	private record Version(int major, int minor, int patch, String preRelease) {
		static Version parse(String s) {
			s = s.trim();
			String pre = "";
			int dash = s.indexOf('-');
			if (dash >= 0) {
				pre = s.substring(dash + 1);
				s = s.substring(0, dash);
			}
			String[] parts = s.split("\\.");
			int major = parts.length > 0 ? parseIntSafe(parts[0]) : 0;
			int minor = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
			int patch = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
			return new Version(major, minor, patch, pre);
		}

		private static int parseIntSafe(String s) {
			try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
			catch (Exception e) { return 0; }
		}

		int compareTo(Version o) {
			int c = Integer.compare(major, o.major);
			if (c != 0) return c;
			c = Integer.compare(minor, o.minor);
			if (c != 0) return c;
			c = Integer.compare(patch, o.patch);
			if (c != 0) return c;
			if (preRelease.isEmpty() && !o.preRelease.isEmpty()) return 1;
			if (!preRelease.isEmpty() && o.preRelease.isEmpty()) return -1;
			return preRelease.compareTo(o.preRelease);
		}
	}

	private enum Op { EQ, GTE, LTE, GT, LT, RANGE, ANY, WILDCARD }

	private record Range(Op op, Version low, Version high, String wildcardKey) {
		boolean test(Version v) {
			return switch (op) {
				case EQ -> low != null && v.compareTo(low) == 0;
				case GTE -> low != null && v.compareTo(low) >= 0;
				case LTE -> high != null && v.compareTo(high) <= 0;
				case GT -> low != null && v.compareTo(low) > 0;
				case LT -> high != null && v.compareTo(high) < 0;
				case RANGE -> low != null && high != null
						&& v.compareTo(low) >= 0 && v.compareTo(high) <= 0;
				case ANY -> true;
				case WILDCARD -> {
					if (wildcardKey == null) yield true;
					String[] parts = wildcardKey.replace(".*", ".x").split("\\.");
					int wMajor = parts.length > 0 ? parseIntSafe(parts[0]) : -1;
					int wMinor = parts.length > 1 ? parseIntSafe(parts[1]) : -1;
					yield (wMajor < 0 || v.major == wMajor) && (wMinor < 0 || v.minor == wMinor);
				}
			};
		}
		private static int parseIntSafe(String s) {
			try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return -1; }
		}
	}

	// ---- 解析入口 ----

	public static SemVerRange parse(String range) {
		if (range == null || range.isBlank()) return new SemVerRange("*", new Range[]{new Range(Op.ANY, null, null, null)});

		String raw = range.trim();
		// 或条件
		String[] orParts = raw.split("\\|\\|");
		Range[] ranges = new Range[orParts.length];
		for (int i = 0; i < orParts.length; i++) {
			ranges[i] = parseSingle(orParts[i].trim());
		}
		return new SemVerRange(raw, ranges);
	}

	private static Range parseSingle(String s) {
		// 通配符
		if (s.equals("*") || s.endsWith("x") || s.endsWith("X") || s.endsWith(".*")) {
			return new Range(Op.WILDCARD, null, null, s);
		}
		// 范围 a - b
		Matcher rangeMatch = Pattern.compile("(.+?)\\s*-\\s*(.+)").matcher(s);
		if (rangeMatch.matches() && !s.startsWith(">") && !s.startsWith("<")) {
			Version low = Version.parse(rangeMatch.group(1));
			Version high = Version.parse(rangeMatch.group(2));
			return new Range(Op.RANGE, low, high, null);
		}
		// 比较符
		if (s.startsWith(">=")) return new Range(Op.GTE, Version.parse(s.substring(2)), null, null);
		if (s.startsWith("<=")) return new Range(Op.LTE, null, Version.parse(s.substring(2)), null);
		if (s.startsWith(">"))  return new Range(Op.GT, Version.parse(s.substring(1)), null, null);
		if (s.startsWith("<"))  return new Range(Op.LT, null, Version.parse(s.substring(1)), null);
		// ~ 兼容：~1.20.4 = [1.20.4, 1.21.0)
		if (s.startsWith("~")) {
			Version v = Version.parse(s.substring(1));
			Version ceiling = new Version(v.major, v.minor + 1, 0, "");
			return new Range(Op.RANGE, v, ceiling, null);
		}
		// 精确
		return new Range(Op.EQ, Version.parse(s), null, null);
	}

	// ---- 匹配 ----

	/** 判断给定版本号是否满足这个范围。 */
	public boolean test(String version) {
		Version v = Version.parse(version);
		for (Range r : ranges) {
			if (r.test(v)) return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return raw;
	}
}
