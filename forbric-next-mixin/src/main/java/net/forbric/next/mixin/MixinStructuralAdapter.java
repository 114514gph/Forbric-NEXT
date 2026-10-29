package net.forbric.next.mixin;

import java.util.*;

/**
 * Mixin 结构适配层。
 *
 * 老 mod 的 mixin target 在新版本 MC 里可能发生结构变化（class → interface、
 * 方法签名改变、字段移除）。这个类在 preflight 阶段扫描 mod 的 mixin config，
 * 把已知有结构问题的 mixin 过滤掉，并记录到 load-report。
 *
 * 这不是"移除 mixin"，而是"跨版本适配"——老 mixin 在新 MC 上已经语义失效，
 * 硬织入会崩溃，不如优雅跳过并记录。
 */
public class MixinStructuralAdapter {

    /**
     * 已知在 26.3 里发生结构变化的 target 类。
     * key = target 类的内部名（斜杠形式）
     * value = 变化描述
     */
    private static final Map<String, String> KNOWN_STRUCTURAL_CHANGES = new LinkedHashMap<>();

    static {
        // Explosion 从 class 变成了 interface
        KNOWN_STRUCTURAL_CHANGES.put("net/minecraft/world/level/Explosion",
                "class → interface in 26.3");
        // SharedConstants 的字段名变了（field_1125 → IS_RUNNING_IN_IDE）
        // 这个靠字节码重写处理，不在此列
    }

    /** 被跳过的 mixin 记录（load-report） */
    private final List<String> skippedMixins = new ArrayList<>();

    /**
     * 扫描 mixin config 的 mixin 列表，过滤掉已知有结构问题的。
     *
     * @param mixinClassName mixin 类名（如 "com.example.mixin.ExplosionMixin"）
     * @param targetInternalName 目标类内部名（斜杠形式，如 "net/minecraft/world/level/Explosion"）
     * @return true = 保留，false = 跳过
     */
    public boolean shouldSkip(String mixinClassName, String targetInternalName) {
        if (KNOWN_STRUCTURAL_CHANGES.containsKey(targetInternalName)) {
            String reason = KNOWN_STRUCTURAL_CHANGES.get(targetInternalName);
            skippedMixins.add(mixinClassName + " → " + targetInternalName + " (" + reason + ")");
            return true;
        }
        return false;
    }

    /** 获取被跳过的 mixin 列表（用于 load-report） */
    public List<String> getSkippedMixins() {
        return Collections.unmodifiableList(skippedMixins);
    }

    /** 添加一条新发现的结构变化（运行时动态补充） */
    public void registerStructuralChange(String targetInternalName, String reason) {
        KNOWN_STRUCTURAL_CHANGES.put(targetInternalName, reason);
    }
}
