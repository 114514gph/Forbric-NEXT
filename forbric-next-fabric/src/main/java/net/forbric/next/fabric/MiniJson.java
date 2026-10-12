package net.forbric.next.fabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器（forbric-next-fabric 内部专用）。
 *
 * <p>为什么要自己写：loader / fabric 模块刻意不引入第三方 JSON 库（Gson/Jackson），
 * 以保持 skeleton 阶段零外部依赖。fabric.mod.json / quilt.mod.json 的结构很规整，
 * 一个递归下降解析器足够把 id / version / entrypoints 读出来。
 *
 * <p>支持的输出类型：
 * <ul>
 *   <li>对象 → {@code Map<String,Object>}（保留插入顺序）</li>
 *   <li>数组 → {@code List<Object>}</li>
 *   <li>字符串 → {@code String}</li>
 *   <li>数字 → {@code Number}（整数用 Long，小数用 Double）</li>
 *   <li>true/false → {@code Boolean}</li>
 *   <li>null → {@code null}</li>
 * </ul>
 *
 * <p>只做解析，不做格式化、不做校验。解析失败抛 {@link IllegalArgumentException}。
 */
final class MiniJson {

	private final String src;
	private int pos;

	private MiniJson(String src) {
		this.src = src;
	}

	/** 把整段 JSON 文本解析成对象图。 */
	static Object parse(String text) {
		MiniJson p = new MiniJson(text);
		p.skipWs();
		Object v = p.parseValue();
		p.skipWs();
		if (p.pos != p.src.length()) {
			throw new IllegalArgumentException("JSON: 末尾还有多余内容 at offset " + p.pos);
		}
		return v;
	}

	private Object parseValue() {
		skipWs();
		char c = peek();
		return switch (c) {
			case '{' -> parseObject();
			case '[' -> parseArray();
			case '"' -> parseString();
			case 't', 'f' -> parseBool();
			case 'n' -> parseNull();
			default -> parseNumber();
		};
	}

	private Map<String, Object> parseObject() {
		expect('{');
		Map<String, Object> map = new LinkedHashMap<>();
		skipWs();
		if (peek() == '}') { pos++; return map; }
		while (true) {
			skipWs();
			String key = parseString();
			skipWs();
			expect(':');
			Object val = parseValue();
			map.put(key, val);
			skipWs();
			char c = next();
			if (c == '}') break;
			if (c != ',') throw err("期望 ',' 或 '}'，实际 '" + c + "'");
		}
		return map;
	}

	private List<Object> parseArray() {
		expect('[');
		List<Object> list = new ArrayList<>();
		skipWs();
		if (peek() == ']') { pos++; return list; }
		while (true) {
			list.add(parseValue());
			skipWs();
			char c = next();
			if (c == ']') break;
			if (c != ',') throw err("期望 ',' 或 ']'，实际 '" + c + "'");
		}
		return list;
	}

	private String parseString() {
		expect('"');
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = next();
			if (c == '"') break;
			if (c == '\\') {
				char e = next();
				sb.append(switch (e) {
					case '"' -> '"';
					case '\\' -> '\\';
					case '/' -> '/';
					case 'b' -> '\b';
					case 'f' -> '\f';
					case 'n' -> '\n';
					case 'r' -> '\r';
					case 't' -> '\t';
					case 'u' -> (char) Integer.parseInt(src.substring(pos, pos + 4), 16);
					default -> e;
				});
				if (e == 'u') pos += 4;
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private Number parseNumber() {
		int start = pos;
		while (pos < src.length() && "-+0123456789.eE".indexOf(src.charAt(pos)) >= 0) pos++;
		String token = src.substring(start, pos);
		try {
			if (token.contains(".") || token.contains("e") || token.contains("E")) {
				return Double.parseDouble(token);
			}
			return Long.parseLong(token);
		} catch (NumberFormatException e) {
			throw err("非法数字: " + token);
		}
	}

	private Boolean parseBool() {
		if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
		if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
		throw err("非法布尔值");
	}

	private Object parseNull() {
		if (src.startsWith("null", pos)) { pos += 4; return null; }
		throw err("非法 null");
	}

	private char peek() {
		if (pos >= src.length()) throw err("意外的结尾");
		return src.charAt(pos);
	}

	private char next() {
		if (pos >= src.length()) throw err("意外的结尾");
		return src.charAt(pos++);
	}

	private void expect(char c) {
		skipWs();
		if (next() != c) throw err("期望 '" + c + "'");
	}

	private void skipWs() {
		while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
	}

	private IllegalArgumentException err(String msg) {
		return new IllegalArgumentException("JSON 解析失败: " + msg + " (offset " + pos + ")");
	}
}
