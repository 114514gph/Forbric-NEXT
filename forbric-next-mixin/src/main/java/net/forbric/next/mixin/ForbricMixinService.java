package net.forbric.next.mixin;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import net.forbric.next.common.NextLog;

/**
 * NeoForbric 对 Sponge Mixin 的真实封装。
 *
 * <p>职责（每一个方法都对应一次真实的 Mixin 调用，不是空壳）：
 * <ol>
 *   <li>{@link #init()} — 调 {@link MixinBootstrap#init()} 启动 Mixin 平台，
 *       拿到 {@link MixinEnvironment}，把 side 设成 SERVER、兼容级别设成 JAVA_21。</li>
 *   <li>{@link #registerConfig(ModMixinConfig)} — 读 mod jar 里的 {@code .mixins.json}
 *       并 {@code env.addConfiguration(name)} 注册进环境。</li>
 *   <li>{@link #weave(String, byte[])} — 取 {@link IMixinTransformer}，对一个待定义的类字节
 *       做织入；没有 mixin 命中时原样返回。mod classloader 在 defineClass 前调它。</li>
 * </ol>
 *
 * <p><b>关于依赖版本的坑（真实验证时踩到）：</b>
 * 官方 {@code org.spongepowered:mixin:0.8.5} 的 {@code CompatibilityLevel} 最高只到 JAVA_18，
 * 无法处理 MC 1.21.1 的 Java 21（class file 65）字节码。真实织入跑在 Fabric 维护的
 * {@code net.fabricmc:sponge-mixin:0.15.4+mixin.0.8.7} 上（包名仍是 {@code org.spongepowered.asm}，
 * API 完全一致，且支持 JAVA_21）。本类按两边共同的稳定 API 编写。
 */
public final class ForbricMixinService {

	private static ForbricMixinService instance;

	private MixinEnvironment environment;
	private IMixinTransformer transformer;
	private boolean initialized;

	private ForbricMixinService() {}

	/** 单例：MixinBootstrap 在一个 JVM 里只能 init 一次。 */
	public static synchronized ForbricMixinService getInstance() {
		if (instance == null) {
			instance = new ForbricMixinService();
		}
		return instance;
	}

	/**
	 * 启动 Mixin 平台。重复调用安全（第二次直接返回）。
	 *
	 * @return 环境对象
	 */
	public synchronized MixinEnvironment init() {
		if (initialized) {
			return environment;
		}
		NextLog.info("ForbricMixinService: 启动 MixinBootstrap ...");
		// MixinBootstrap.init() 会在当前 classloader 上装好 MixinService（JDK 用 system CL）。
		MixinBootstrap.init();

		this.environment = MixinEnvironment.getCurrentEnvironment();
		// dedicated server：side=SERVER，让 mixin 只加载 server/commmon 段。
		environment.setSide(MixinEnvironment.Side.SERVER);
		applyCompatibilityLevel();

		// 拿到真实 transformer。addConfiguration 之后它就开始能处理目标类。
		this.transformer = (IMixinTransformer) environment.getActiveTransformer();

		initialized = true;
		NextLog.info("ForbricMixinService: Mixin 环境就绪 side={} compat={} transformer={}",
				environment.getSide(), environment.getCompatibilityLevel(),
				transformer == null ? "null" : transformer.getClass().getName());
		return environment;
	}

	/**
	 * 把 mod jar 里的 mixin 配置注册进环境。
	 *
	 * @param cfg 解析好的 mixins.json
	 */
	public void registerConfig(ModMixinConfig cfg) {
		ensureInit();
		MixinConfigReader.logSummary(cfg);
		// 注意：配置文件名要能被 mixin 用 classloader 当资源读到（放 mod jar 根）。
		environment.addConfiguration(cfg.configName());
		NextLog.info("ForbricMixinService: 已注册 mixin 配置 -> {}", cfg.configName());
	}

	/**
	 * 对一个类的原始字节做 Mixin 织入。
	 *
	 * <p>由 mod classloader 在 defineClass 之前调用。如果没有任何 mixin 命中这个类，
	 * transformer 会原样返回字节数组（可能同一个数组，也可能是副本）。
	 *
	 * @param classInternalName 类的内部名（{@code net/minecraft/server/MinecraftServer}），
	 *                          或点号全限定名；mixin 两种都认
	 * @param basicClass        class 文件原始字节
	 * @return 织入后的字节（一定非 null；没命中则等于入参）
	 */
	public byte[] weave(String classInternalName, byte[] basicClass) {
		ensureInit();
		if (transformer == null) {
			return basicClass;
		}
		String transformedName = classInternalName.replace('/', '.');
		return transformer.transformClassBytes(classInternalName, transformedName, basicClass);
	}

	public MixinEnvironment environment() {
		ensureInit();
		return environment;
	}

	public IMixinTransformer transformer() {
		ensureInit();
		return transformer;
	}

	public boolean isInitialized() {
		return initialized;
	}

	private void ensureInit() {
		if (!initialized) {
			throw new IllegalStateException("ForbricMixinService 尚未 init()");
		}
	}

	/**
	 * 把兼容级别设成 JAVA_21。用 valueOf 反射式查找，避免编译期硬依赖 0.8.7 fork。
	 * 找不到就退回环境默认（打 WARN，不崩）。
	 */
	private void applyCompatibilityLevel() {
		try {
			MixinEnvironment.CompatibilityLevel level =
					MixinEnvironment.CompatibilityLevel.valueOf("JAVA_21");
			MixinEnvironment.setCompatibilityLevel(level);
			NextLog.info("ForbricMixinService: CompatibilityLevel = JAVA_21");
		} catch (IllegalArgumentException notIn085) {
			// 官方 0.8.5 没有 JAVA_21 —— 记录但不致命，真实运行用的是 0.8.7 fork。
			NextLog.warn("ForbricMixinService: 当前 mixin 没有 JAVA_21 级别（可能是官方 0.8.5），"
					+ "回退到默认级别={}", environment.getCompatibilityLevel());
		}
	}
}
