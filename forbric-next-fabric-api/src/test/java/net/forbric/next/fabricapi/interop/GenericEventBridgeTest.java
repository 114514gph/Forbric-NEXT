package net.forbric.next.fabricapi.interop;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.forbric.next.fabricapi.event.ServerLifecycleEvents;

/**
 * {@link GenericEventBridge} 的行为测试。
 *
 * <p>验证事件桥的分发语义：
 * <ol>
 *   <li>注册一个 onServerStarted 回调，fire 后被调用。</li>
 *   <li>注册多个回调，fire 后全部被调用。</li>
 *   <li>某个回调抛异常，不影响其它回调继续执行。</li>
 * </ol>
 *
 * <p>注意：{@link ServerLifecycleEvents#SERVER_STARTED} 是静态单例，每个测试前后都 clear，
 * 避免测试之间串状态。
 */
class GenericEventBridgeTest {

	@BeforeEach
	@AfterEach
	void reset() {
		ServerLifecycleEvents.SERVER_STARTED.clear();
	}

	@Test
	void singleCallbackIsInvokedOnFire() {
		boolean[] called = {false};
		GenericEventBridge.onServerStarted(() -> called[0] = true);

		GenericEventBridge.fireServerStarted();

		assertTrue(called[0], "注册的 onServerStarted 回调应被 fire 触发");
	}

	@Test
	void multipleCallbacksAreAllInvoked() {
		AtomicInteger count = new AtomicInteger();
		GenericEventBridge.onServerStarted(count::incrementAndGet);
		GenericEventBridge.onServerStarted(count::incrementAndGet);
		GenericEventBridge.onServerStarted(count::incrementAndGet);

		GenericEventBridge.fireServerStarted();

		assertEquals(3, count.get(), "三个回调都应被调用");
	}

	@Test
	void throwingCallbackDoesNotPreventOthers() {
		AtomicInteger goodHooksRan = new AtomicInteger();

		// 第一个正常回调
		GenericEventBridge.onServerStarted(goodHooksRan::incrementAndGet);
		// 中间一个会抛异常的回调
		GenericEventBridge.onServerStarted(() -> {
			throw new IllegalStateException("我是个坏掉的回调");
		});
		// 第三个正常回调——关键：它必须仍然被执行
		GenericEventBridge.onServerStarted(goodHooksRan::incrementAndGet);

		// fire 本身不应该把异常冒出去（事件总线自己隔离了坏回调）
		assertDoesNotThrow(GenericEventBridge::fireServerStarted);

		assertEquals(2, goodHooksRan.get(),
				"抛异常的回调应被隔离，排在它前后的两个正常回调都要跑");
	}

	@Test
	void registerBlocksEventAlsoFansOut() {
		// 顺带验证 bridge 自带的注册阶段事件
		AtomicInteger count = new AtomicInteger();
		GenericEventBridge.onRegisterBlocks(count::incrementAndGet);
		GenericEventBridge.onRegisterBlocks(count::incrementAndGet);

		GenericEventBridge.fireRegisterBlocks();

		assertEquals(2, count.get());
	}
}
