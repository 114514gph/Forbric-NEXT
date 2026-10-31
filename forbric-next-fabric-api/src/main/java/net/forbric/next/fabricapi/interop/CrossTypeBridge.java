package net.forbric.next.fabricapi.interop;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.forbric.next.api.Ecosystem;
import net.forbric.next.common.NextLog;

/**
 * 物品 / 流体的跨系桥（Forge ↔ Fabric）。
 *
 * <p><b>为什么全程 {@link Object} + 反射：</b>Forge 的 ItemStack 和 Fabric(yarn) 的 ItemStack
 * 是两套不同映射的类，编译期谁也不认识谁。NeoForbric 作为中间层不能把任何一侧的 API
 * 编进自己的字节码，否则就被绑死在某一侧。所以桥接只做两件事：
 * <ol>
 *   <li><b>快照（读）</b>：用反射从一侧的 stack 里抽出中立三元组
 *       {@code (registryId, count)}，不依赖任何一侧的具体类型。</li>
 *   <li><b>重建（写）</b>：把快照交给目标侧注册的 factory，反射构造目标侧 stack。</li>
 * </ol>
 *
 * <p>"按 id 找 item"和"用 item+count 造 stack"这两步两侧不一样，做成可注册 SPI：
 * {@link #registerItemStackFactory}。没注册时桥接打 WARN 返回 null，不抛 NPE。
 */
public final class CrossTypeBridge {

	private CrossTypeBridge() {}

	/** ecosystem → (itemId → item 对象)。运行时由对应 loader 注入。 */
	private static final Map<Ecosystem, Function<String, Object>> ITEM_RESOLVERS = new ConcurrentHashMap<>();
	private static final Map<Ecosystem, Function<String, Object>> FLUID_RESOLVERS = new ConcurrentHashMap<>();
	/** ecosystem → ((item, count) → targetStack)。 */
	private static final Map<Ecosystem, BiFunction<Object, Integer, Object>> ITEM_STACK_FACTORIES =
			new ConcurrentHashMap<>();
	private static final Map<Ecosystem, BiFunction<Object, Integer, Object>> FLUID_STACK_FACTORIES =
			new ConcurrentHashMap<>();

	/** 中立快照：与两侧都解耦的中间表示。 */
	public record StackSnapshot(String registryId, int count, Kind kind) {
		public enum Kind { ITEM, FLUID }
	}

	/** 注入某一侧"按 registry id 解析 item"的函数。 */
	public static void registerItemResolver(Ecosystem eco, Function<String, Object> resolver) {
		ITEM_RESOLVERS.put(eco, resolver);
	}

	/** 注入某一侧"按 registry id 解析 fluid"的函数。 */
	public static void registerFluidResolver(Ecosystem eco, Function<String, Object> resolver) {
		FLUID_RESOLVERS.put(eco, resolver);
	}

	/** 注入某一侧"用 item + count 造一个 ItemStack"的工厂。 */
	public static void registerItemStackFactory(Ecosystem eco, BiFunction<Object, Integer, Object> factory) {
		ITEM_STACK_FACTORIES.put(eco, factory);
	}

	/** 注入某一侧"用 fluid + amount 造一个 FluidStack"的工厂。 */
	public static void registerFluidStackFactory(Ecosystem eco, BiFunction<Object, Integer, Object> factory) {
		FLUID_STACK_FACTORIES.put(eco, factory);
	}

	// ------------------------------------------------------------------
	// ItemStack 桥
	// ------------------------------------------------------------------

	/** 反射读任意一侧 ItemStack 的 (id, count) 中立快照。 */
	public static StackSnapshot snapshotItemStack(Object stack) {
		if (stack == null) return null;
		int count = (int) callNoArg(stack, "getCount");
		Object item = callNoArg(stack, "getItem");
		String id = registryIdOf(item);
		NextLog.info("CrossTypeBridge: 快照 ItemStack id={} count={}", id, count);
		return new StackSnapshot(id, count, StackSnapshot.Kind.ITEM);
	}

	/** Forge ItemStack → Fabric ItemStack（返回 Object，调用侧强转）。 */
	public static Object forgeToFabricItemStack(Object forgeStack) {
		return rebuildItem(snapshotItemStack(forgeStack), Ecosystem.FABRIC);
	}

	/** Fabric ItemStack → Forge ItemStack。 */
	public static Object fabricToForgeItemStack(Object fabricStack) {
		return rebuildItem(snapshotItemStack(fabricStack), Ecosystem.MINECRAFTFORGE);
	}

	// ------------------------------------------------------------------
	// FluidStack 桥
	// ------------------------------------------------------------------

	/** 反射读任意一侧 FluidStack 的 (fluidId, amount) 中立快照。 */
	public static StackSnapshot snapshotFluidStack(Object fluidStack) {
		if (fluidStack == null) return null;
		int amount = (int) callNoArg(fluidStack, "getAmount");
		Object fluid = callNoArg(fluidStack, "getFluid");
		String id = registryIdOf(fluid);
		NextLog.info("CrossTypeBridge: 快照 FluidStack id={} amount={}", id, amount);
		return new StackSnapshot(id, amount, StackSnapshot.Kind.FLUID);
	}

	public static Object forgeToFabricFluidStack(Object forgeFluid) {
		return rebuildFluid(snapshotFluidStack(forgeFluid), Ecosystem.FABRIC);
	}

	public static Object fabricToForgeFluidStack(Object fabricFluid) {
		return rebuildFluid(snapshotFluidStack(fabricFluid), Ecosystem.MINECRAFTFORGE);
	}

	// ------------------------------------------------------------------
	// 内部
	// ------------------------------------------------------------------

	private static Object rebuildItem(StackSnapshot snap, Ecosystem target) {
		Function<String, Object> resolver = ITEM_RESOLVERS.get(target);
		BiFunction<Object, Integer, Object> factory = ITEM_STACK_FACTORIES.get(target);
		if (resolver == null || factory == null) {
			NextLog.warn("CrossTypeBridge: 目标侧 {} 未注册 item resolver/factory，无法重建", target);
			return null;
		}
		Object item = resolver.apply(snap.registryId());
		if (item == null) {
			NextLog.warn("CrossTypeBridge: {} 在 {} 侧找不到 item", snap.registryId(), target);
			return null;
		}
		return factory.apply(item, snap.count());
	}

	private static Object rebuildFluid(StackSnapshot snap, Ecosystem target) {
		Function<String, Object> resolver = FLUID_RESOLVERS.get(target);
		BiFunction<Object, Integer, Object> factory = FLUID_STACK_FACTORIES.get(target);
		if (resolver == null || factory == null) {
			NextLog.warn("CrossTypeBridge: 目标侧 {} 未注册 fluid resolver/factory，无法重建", target);
			return null;
		}
		Object fluid = resolver.apply(snap.registryId());
		if (fluid == null) return null;
		return factory.apply(fluid, snap.count());
	}

	/** 反射调无参方法（getCount/getItem/getFluid/getAmount），沿父类链找。 */
	private static Object callNoArg(Object target, String methodName) {
		Class<?> c = target.getClass();
		while (c != Object.class) {
			try {
				Method m = c.getDeclaredMethod(methodName);
				m.setAccessible(true);
				return m.invoke(target);
			} catch (NoSuchMethodException e) {
				c = c.getSuperclass();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("反射调用 " + methodName + " 失败: " + e.getMessage(), e);
			}
		}
		throw new IllegalStateException("在 " + target.getClass() + " 上找不到无参方法 " + methodName);
	}

	/** 反射取 item/fluid 的 registry id（优先 getRegistryName()，退回 toString）。 */
	private static String registryIdOf(Object itemOrFluid) {
		if (itemOrFluid == null) return "minecraft:air";
		try {
			Object loc = callNoArg(itemOrFluid, "getRegistryName");
			if (loc != null) return loc.toString();
		} catch (IllegalArgumentException | IllegalStateException notPresent) {
			// 某些映射没有 getRegistryName，退回 toString
		}
		return itemOrFluid.getClass().getName() + "@" + itemOrFluid.hashCode();
	}
}
