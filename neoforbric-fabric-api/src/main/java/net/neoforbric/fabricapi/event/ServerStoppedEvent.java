package net.neoforbric.fabricapi.event;

import java.nio.file.Path;

/**
 * 服务端"已完全停止"事件（{@code SERVER_STOPPED}）。
 *
 * <p>具体 record，不是 Proxy。
 *
 * @param serverDirectory 服务端运行目录
 */
public record ServerStoppedEvent(Path serverDirectory) {
}
