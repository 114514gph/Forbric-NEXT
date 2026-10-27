package net.forbric.next.fabricapi.event;

import java.nio.file.Path;

/**
 * 服务端"开始停止（保存之前）"事件（{@code SERVER_STOPPING}）。
 *
 * <p>具体 record，不是 Proxy。
 *
 * @param serverDirectory 服务端运行目录
 */
public record ServerStoppingEvent(Path serverDirectory) {
}
