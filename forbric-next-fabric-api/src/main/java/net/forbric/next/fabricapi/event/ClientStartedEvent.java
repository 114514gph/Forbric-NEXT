package net.forbric.next.fabricapi.event;

/**
 * 客户端"已启动、主窗口已显示"事件（{@code CLIENT_STARTED}）。
 *
 * <p>具体 record，不是 Proxy——携带窗口标题等上下文，mod 可直接读取。
 *
 * @param windowTitle 主窗口标题
 */
public record ClientStartedEvent(String windowTitle) {
}
