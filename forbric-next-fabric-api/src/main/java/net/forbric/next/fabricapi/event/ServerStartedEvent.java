package net.forbric.next.fabricapi.event;

import java.nio.file.Path;

/**
 * 服务端"已加载所有 dimension、开始接受连接"事件（{@code SERVER_STARTED}）。
 *
 * <p>具体 record，不是 Proxy——带具体上下文（服务端目录），mod 里可直接解构。
 *
 * @param serverDirectory 服务端运行目录
 */
public record ServerStartedEvent(Path serverDirectory) {
}
