package net.forbric.next.mixin.testmod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.forbric.next.mixin.hook.ForbricHooks;

/**
 * 真·测试 mixin：target 是 vanilla server 里的 {@code net.minecraft.server.MinecraftServer}。
 *
 * <p>用 {@code targets = "..."} 字符串指定目标，编译期不需要把 server jar 拉进 compile classpath。
 *
 * <p>织入点：所有构造器 {@code <init>} 的 TAIL。构造器名 {@code <init>} 在字节码里稳定，
 * 不受成员混淆影响。命中后回调 {@link ForbricHooks#onServerConstructed()}（hooks 在 mixin 包外）。
 */
@Mixin(targets = "net.minecraft.server.MinecraftServer")
public class ForbricTestMixin {

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void forbric$afterServerConstruct(CallbackInfo ci) {
		ForbricHooks.onServerConstructed();
	}
}
