package net.forbric.next.fabricapi.event;

/**
 * 客户端"正在停止"事件（{@code CLIENT_STOPPING}）。
 *
 * <p>当前没有需要携带的上下文，仍是一个具体 record（标记类型），
 * 以后加字段不会破坏 mod 的 {@code instanceof} / {@code equals} 语义。
 */
public record ClientStoppingEvent() {
}
