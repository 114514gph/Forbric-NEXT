package net.forbric.next.mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器（只服务于 {@code .mixins.json}）。
 *
 * <p>为什么不直接用 Gson：neoforbric-mixin 是织入层，必须保持零第三方依赖，
 * 否则会把 Gson 拖进 mod 的 classpath 污染隔离边界。mixins.json 结构很固定
 * （对象 + 字符串数组 + 少量字符串/布尔值），手写一个递归下降解析器足够。
 *
 * <p>支持：对象 {@code {}}、数组 {@code []}、字符串（含 {@code \n \" \\} 转义）、
 * {@code true/false/null}、数字（这里只取字符串形式，不做数值计算）。
 */
final class MixinJson {

	private final String s;
	private int i;

	private MixinJson(String s) { this.s = s; }

	/** 解析整个 JSON 文本为 Map（顶层必须是对象）。 */
	static Map<String, Object> parseObject(String text) {
		MixinJson p = new MixinJson(text);
		p.ws();
		Object v = p.value();
		if (!(v instanceof Map<?, ?> m)) {
			throw new IllegalStateException("mixins.json 顶层不是 JSON 对象");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> out = (Map<String, Object>) m;
		return out;
	}

	private Object value() {
		ws();
		char c = peek();
		return switch (c) {
			case '{' -> object();
			case '[' -> array();
			case '"' -> string();
			case 't' -> { lit("true"); yield Boolean.TRUE; }
			case 'f' -> { lit("false"); yield Boolean.FALSE; }
			case 'n' -> { lit("null"); yield null; }
			default -> number();
		};
	}

	private Map<String, Object> object() {
		expect('{');
		Map<String, Object> m = new LinkedHashMap<>();
		ws();
		if (peek() == '}') { i++; return m; }
		while (true) {
			ws();
			String key = string();
			ws();
			expect(':');
			m.put(key, value());
			ws();
			char c = peek();
			if (c == ',') { i++; continue; }
			if (c == '}') { i++; break; }
			throw new IllegalStateException("对象里期望 , 或 }，遇到: " + c);
		}
		return m;
	}

	private List<Object> array() {
		expect('[');
		List<Object> list = new ArrayList<>();
		ws();
		if (peek() == ']') { i++; return list; }
		while (true) {
			list.add(value());
			ws();
			char c = peek();
			if (c == ',') { i++; continue; }
			if (c == ']') { i++; break; }
			throw new IllegalStateException("数组里期望 , 或 ]，遇到: " + c);
		}
		return list;
	}

	private String string() {
		expect('"');
		StringBuilder sb = new StringBuilder();
		while (i < s.length()) {
			char c = s.charAt(i++);
			if (c == '"') return sb.toString();
			if (c == '\\') {
				char e = s.charAt(i++);
				sb.append(switch (e) {
					case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r';
					case '"' -> '"';  case '\\' -> '\\'; case '/' -> '/';
					default -> e;
				});
			} else {
				sb.append(c);
			}
		}
		throw new IllegalStateException("字符串未闭合");
	}

	private String number() {
		int start = i;
		while (i < s.length() && ",}] \n\r\t".indexOf(s.charAt(i)) < 0) i++;
		return s.substring(start, i);
	}

	private void lit(String word) {
		if (!s.startsWith(word, i)) throw new IllegalStateException("期望字面量 " + word);
		i += word.length();
	}

	private void expect(char c) {
		if (peek() != c) throw new IllegalStateException("期望 '" + c + "'，实际 '" + peek() + "'");
		i++;
	}

	private char peek() {
		if (i >= s.length()) throw new IllegalStateException("JSON 意外结束");
		return s.charAt(i);
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
	}
}
