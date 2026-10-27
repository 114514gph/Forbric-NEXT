package net.forbric.next.fabricapi.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Minimal event bus mimicking Fabric API's event model.
 *
 * <p>Fabric API events are each a singleton object mods register listeners on, and the
 * game-side code invokes via {@code invoker()}. Forbric-NEXT provides this minimal
 * implementation so a mod calling {@code ServerLifecycleEvents.SERVER_STARTED.register(hook)}
 * works without the user installing the full fabric-api jar.
 *
 * <p>This is intentionally tiny: no phases, no cancellable events, no event groups.
 * It covers the 90% case (lifecycle + simple callbacks). Richer events can be added later.
 *
 * @param <T> the listener type
 */
public final class Event<T> {

	private final List<T> listeners = new CopyOnWriteArrayList<>();
	private final Function<Iterable<T>, T> invokerFactory;

	private Event(Function<Iterable<T>, T> invokerFactory) {
		this.invokerFactory = invokerFactory;
	}

	/** Create an event where the invoker is a single combined listener. */
	public static <T> Event<T> ofCombiner(Function<Iterable<T>, T> combiner) {
		return new Event<>(combiner);
	}

	/** Create a simple runnable event (zero-arg hooks). */
	public static Event<Runnable> ofRunnable() {
		// 根因修复：每个 listener 单独 try/catch 隔离。
		// 之前是 `for (Runnable r : listeners) r.run();`——一个回调抛异常会直接中断整个分发，
		// 排在它后面的所有 mod 回调都收不到事件。现在改成记录错误后继续下一个，
		// 一个坏 mod 不再炸掉整条事件总线。
		return new Event<>(listeners -> () -> {
			int index = 0;
			for (Runnable r : listeners) {
				try {
					r.run();
				} catch (Throwable t) {
					net.forbric.next.common.NextLog.error(
							"Event: 第 {} 个监听器执行失败，已隔离并继续分发: {}", index, t.toString());
				}
				index++;
			}
		});
	}

	/**
	 * Create an event whose listeners take a concrete event object (zero Proxy).
	 *
	 * <p>与 {@link #ofRunnable()} 并列：当事件需要携带上下文（游戏目录、窗口标题……）时用这个工厂，
	 * 监听器是 {@code Consumer<T>}。invoker 是一个 {@code Consumer<T>}，把事件对象逐个 fan-out
	 * 给所有监听器，且每个监听器单独 try/catch 隔离（和 runnable 版同样的容错语义）。
	 *
	 * <p><b>为什么不用 {@code java.lang.reflect.Proxy} 做事件上下文：</b>Proxy 类的
	 * {@code equals()} 不对、接口加新方法会抛 {@link UnsupportedOperationException}、mod 里
	 * {@code instanceof} 检查也会失败。这里一律用具体 record / class 作为事件类型，彻底避开这些坑。
	 */
	public static <T> Event<Consumer<T>> ofConsumer() {
		return new Event<>(listeners -> event -> {
			int index = 0;
			for (Consumer<T> l : listeners) {
				try {
					l.accept(event);
				} catch (Throwable t) {
					net.forbric.next.common.NextLog.error(
							"Event: 第 {} 个监听器执行失败，已隔离并继续分发: {}", index, t.toString());
				}
				index++;
			}
		});
	}

	/** Register a listener. */
	public void register(T listener) {
		listeners.add(listener);
	}

	/** The combined invoker that fans out to all registered listeners. */
	public T invoker() {
		return invokerFactory.apply(listeners);
	}

	/** Number of registered listeners (diagnostics). */
	public int listenerCount() {
		return listeners.size();
	}

	/** Clear all listeners (for shutdown). */
	public void clear() {
		listeners.clear();
	}
}
