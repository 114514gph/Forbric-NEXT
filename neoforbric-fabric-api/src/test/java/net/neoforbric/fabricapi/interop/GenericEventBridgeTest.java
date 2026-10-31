package net.neoforbric.fabricapi.interop;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.neoforbric.fabricapi.event.ServerLifecycleEvents;
import net.neoforbric.fabricapi.event.ServerStartedEvent;

/**
 * {@link GenericEventBridge} 的行为测试。
 *
 * <p>验证事件桥的分发语义（事件上下文是具体 record，回调是 Consumer）：
 * <ol>
 *   <li>注册一个 onServerStarted 回调，fire 后被调用，且能拿到事件上下文。</li>
 *   <li>注册多个回调，fire 后全部被调用。</li>
 *   <li>某个回调抛异常，不影响其它回调继续执行。</li>
 * </ol>
 *
 * <p>注意：{@link ServerLifecycleEvents#SERVER_STARTED} 是静态单例，每个测试前后都 clear，
 * 避免测试之间串状态。
 */
class GenericEventBridgeTest {

	private static final Path GAME_DIR = Paths.get(".").toAbsolutePath();

	@BeforeEach
	@AfterEach
	void reset() {
		ServerLifecycleEvents.SERVER_STARTED.clear();
	}

	@Test
	void singleCallbackIsInvokedOnFireAndReceivesContext() {
		ServerStartedEvent[] seen = new ServerStartedEvent[1];
		GenericEventBridge.onServerStarted(e -> seen[0] = e);

		GenericEventBridge.fireServerStarted(GAME_DIR);

		assertTrue(seen[0] != null, "注册的 onServerStarted 回调应被 fire 触发");
		// 关键：回调拿到的就是我们 fire 时传进去的那个具体事件对象（Proxy 时代 instanceof/传递都有坑）
		assertSame(GAME_DIR, seen[0].serverDirectory(),
				"事件上下文应原样传到监听器，且是具体 record");
	}

	@Test
	void multipleCallbacksAreAllInvoked() {
		AtomicInteger count = new AtomicInteger();
		GenericEventBridge.onServerStarted(e -> count.incrementAndGet());
		GenericEventBridge.onServerStarted(e -> count.incrementAndGet());
		GenericEventBridge.onServerStarted(e -> count.incrementAndGet());

		GenericEventBridge.fireServerStarted(GAME_DIR);

		assertEquals(3, count.get(), "三个回调都应被调用");
	}

	@Test
	void throwingCallbackDoesNotPreventOthers() {
		AtomicInteger goodHooksRan = new AtomicInteger();

		// 第一个正常回调
		GenericEventBridge.onServerStarted(e -> goodHooksRan.incrementAndGet());
		// 中间一个会抛异常的回调
		GenericEventBridge.onServerStarted(e -> {
			throw new IllegalStateException("我是个坏掉的回调");
		});
		// 第三个正常回调——关键：它必须仍然被执行
		GenericEventBridge.onServerStarted(e -> goodHooksRan.incrementAndGet());

		// fire 本身不应该把异常冒出去（事件总线自己隔离了坏回调）
		assertDoesNotThrow(() -> GenericEventBridge.fireServerStarted(GAME_DIR));

		assertEquals(2, goodHooksRan.get(),
				"抛异常的回调应被隔离，排在它前后的两个正常回调都要跑");
	}

	@Test
	void registerBlocksEventAlsoFansOut() {
		// 顺带验证 bridge 自带的注册阶段事件（无上下文，仍是 Runnable）
		AtomicInteger count = new AtomicInteger();
		GenericEventBridge.onRegisterBlocks(count::incrementAndGet);
		GenericEventBridge.onRegisterBlocks(count::incrementAndGet);

		GenericEventBridge.fireRegisterBlocks();

		assertEquals(2, count.get());
	}
}
