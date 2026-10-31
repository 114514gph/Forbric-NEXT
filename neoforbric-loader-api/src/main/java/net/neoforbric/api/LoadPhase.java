package net.neoforbric.api;

/**
 * Lifecycle phases the boot module drives across all loaders.
 *
 * <p>The order here is the canonical between-ecosystem order. Each {@link ModLoader} decides
 * internally which of its own sub-steps run in each phase; the boot module never inspects that.
 *
 * <ul>
 *   <li>{@link #PRE_LAUNCH} — before game bootstrap; mixin configs registered.
 *       <b>顺序原则：</b>本阶段必须先跑完 vanilla {@code Bootstrap.validate()} 等价物
 *       （DefaultAttributes 等默认值就绪），再触发任何 mod 事件。它本就声明在
 *       {@link #REGISTRY} 之前，顺序不要调换。</li>
 *   <li>{@link #CONSTRUCT} — mod classes constructed; FMLConstructModEvent equivalent</li>
 *   <li>{@link #REGISTRY} — registry unfreeze window; Fabric main entrypoints run here
 *       alongside Forge RegisterEvent (both must touch registries in the same window)</li>
 *   <li>{@link #COMMON_SETUP} — FMLCommonSetupEvent equivalent</li>
 *   <li>{@link #SIDED_SETUP} — client/server specific setup</li>
 *   <li>{@link #IMC_ENQUEUE} / {@link #IMC_PROCESS} — inter-mod communication</li>
 *   <li>{@link #LOAD_COMPLETE} — all mods loaded</li>
 *   <li>{@link #GAME_STARTED} — world/server fully running</li>
 * </ul>
 */
public enum LoadPhase {
	PRE_LAUNCH,
	CONSTRUCT,
	REGISTRY,
	COMMON_SETUP,
	SIDED_SETUP,
	IMC_ENQUEUE,
	IMC_PROCESS,
	LOAD_COMPLETE,
	GAME_STARTED
}
