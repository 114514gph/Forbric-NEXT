package net.neoforbric.api.bukkit;

import net.neoforbric.api.ClassLoadingPolicy;

/**
 * 混合端的插件类加载隔离策略 SPI。
 *
 * <p><b>为什么不另起一套枚举：</b>Forbric 已经有 {@link ClassLoadingPolicy.Delegate}
 * （PARENT_FIRST / CHILD_FIRST / GAME）。为避免和历史上 Forbric 0.3.0 的手写
 * {@code DelegationPolicy} 前缀表产生第二套平行的隔离规则，本接口<em>直接复用</em>
 * {@link ClassLoadingPolicy.Delegate} 作为返回值——隔离判定只有一张表，不会出现两套口径。
 *
 * <p><b>混合端的隔离规则（Mohist/Arclight 的通用做法）：</b>
 * <ul>
 *   <li>{@code org.bukkit.**, net.minecraft.** 等 API / 游戏类} → {@link ClassLoadingPolicy.Delegate#PARENT_FIRST}
 *       （插件和 mod 共用同一份 API 与游戏类，否则会出现类身份冲突）。</li>
 *   <li>插件自身的实现类（plugin.yml 主包及其依赖）→ {@link ClassLoadingPolicy.Delegate#CHILD_FIRST}
 *       （每个 Bukkit 插件有自己的隔离类加载器，插件之间默认互相不可见，和 Bukkit 原生一致）。</li>
 *   <li>Forge/Fabric mod 的类 → 仍由各生态系自己的 {@link ClassLoadingPolicy} 决定，
 *       本策略只在"插件 vs. 全局"这一层做裁决，不去干涉 mod 内部的委派。</li>
 * </ul>
 */
public interface PluginClassLoaderStrategy {

	/**
	 * 裁决某个类名在插件类加载器里应该如何委派。
	 *
	 * @param className 要加载的类的全限定名
	 * @return 委派方式（复用统一的 {@link ClassLoadingPolicy.Delegate}）
	 */
	ClassLoadingPolicy.Delegate delegate(String className);
}
