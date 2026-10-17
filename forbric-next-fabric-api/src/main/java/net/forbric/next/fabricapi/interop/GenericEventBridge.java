package net.forbric.next.fabricapi.interop;

import net.forbric.next.fabricapi.event.ClientLifecycleEvents;
import net.forbric.next.fabricapi.event.Event;
import net.forbric.next.fabricapi.event.ServerLifecycleEvents;

/**
 * 通用接缝层（Generic Interop）。
 *
 * <p><b>设计动机：</b>0.3.0 里 Forbric 为每个 mod（Carpet / Create / C2ME ...）打一个补丁分支，
 * 加一个 {@code if modId.equals(...)}。NEXT 反其道而行——暴露一组与具体 mod 无关的通用回调，
 * 任何 mod / 适配层都通过这组接口注册钩子，游戏事件发生时统一触发。
 *
 * <p>本类是纯静态门面：
 * <ul>
 *   <li>客户端 / 服务端生命周期回调 → 委托给已有的
 *       {@link ClientLifecycleEvents} / {@link ServerLifecycleEvents}，避免双份总线。</li>
 *   <li>方块 / 物品注册阶段 → 本类自己持有 {@link Event} 单例，由 boot 在注册窗口触发。</li>
 * </ul>
 *
 * <p>没有任何 per-mod 分支，没有硬编码 mod id。
 */
public final class GenericEventBridge {

	private GenericEventBridge() {}

	/** 方块注册阶段事件（注册窗口 unfreeze 时触发）。 */
	public static final Event<Runnable> REGISTER_BLOCKS = Event.ofRunnable();
	/** 物品注册阶段事件。 */
	public static final Event<Runnable> REGISTER_ITEMS = Event.ofRunnable();

	// ---- 客户端生命周期（委托到 ClientLifecycleEvents）----

	/** 客户端启动完成（主窗口已显示）。 */
	public static void onClientStarted(Runnable hook) {
		ClientLifecycleEvents.CLIENT_STARTED.register(hook);
	}

	/** 客户端即将关闭。 */
	public static void onClientStopping(Runnable hook) {
		ClientLifecycleEvents.CLIENT_STOPPING.register(hook);
	}

	// ---- 服务端生命周期（委托到 ServerLifecycleEvents）----

	/** 服务端启动完成（已加载所有 dimension、开始接受连接）。 */
	public static void onServerStarted(Runnable hook) {
		ServerLifecycleEvents.SERVER_STARTED.register(hook);
	}

	/** 服务端即将停止（保存之前）。 */
	public static void onServerStopping(Runnable hook) {
		ServerLifecycleEvents.SERVER_STOPPING.register(hook);
	}

	/** 服务端完全停止。 */
	public static void onServerStopped(Runnable hook) {
		ServerLifecycleEvents.SERVER_STOPPED.register(hook);
	}

	// ---- 注册阶段 ----

	/** 方块注册阶段回调。 */
	public static void onRegisterBlocks(Runnable hook) {
		REGISTER_BLOCKS.register(hook);
	}

	/** 物品注册阶段回调。 */
	public static void onRegisterItems(Runnable hook) {
		REGISTER_ITEMS.register(hook);
	}

	// ---- 触发点（由 boot / 游戏侧调用，mod 不直接调）----

	/** 触发所有方块注册回调。 */
	public static void fireRegisterBlocks() {
		REGISTER_BLOCKS.invoker().run();
	}

	/** 触发所有物品注册回调。 */
	public static void fireRegisterItems() {
		REGISTER_ITEMS.invoker().run();
	}

	/** 触发所有"服务端启动完成"回调。 */
	public static void fireServerStarted() {
		ServerLifecycleEvents.SERVER_STARTED.invoker().run();
	}

	/** 触发所有"服务端即将停止"回调。 */
	public static void fireServerStopping() {
		ServerLifecycleEvents.SERVER_STOPPING.invoker().run();
	}

	/** 触发所有"服务端完全停止"回调。 */
	public static void fireServerStopped() {
		ServerLifecycleEvents.SERVER_STOPPED.invoker().run();
	}
}
