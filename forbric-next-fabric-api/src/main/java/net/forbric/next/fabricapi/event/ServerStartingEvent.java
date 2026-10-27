package net.forbric.next.fabricapi.event;

import java.nio.file.Path;

/**
 * 服务端"即将开始 tick"事件（{@code SERVER_STARTING}）。
 *
 * <p>具体 record，不是 Proxy / 匿名接口实现——mod 可以安全地 {@code instanceof} 判定、
 * 用 record 自动生成的 {@code equals/hashCode/toString}。
 *
 * @param serverDirectory 服务端运行目录（gameDir 的服务端侧）
 */
public record ServerStartingEvent(Path serverDirectory) {
}
