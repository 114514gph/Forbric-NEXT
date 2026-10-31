package net.neoforbric.forge;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.neoforbric.common.NextLog;

/**
 * Scans a Forge mod JAR for classes annotated with {@code @Mod}.
 *
 * <p>Forge's original approach: mods declare their main class in mods.toml,
 * or it's auto-discovered by scanning for @Mod annotations.
 * This scanner reads the raw class file bytes and looks for the annotation
 * string in the constant pool, avoiding full class loading.
 */
public class ForgeAnnotationScanner {

    /**
     * Find all classes in the jar that have the @Mod annotation.
     *
     * @param jarPath path to the mod jar
     * @return list of fully-qualified class names with @Mod
     */
    public static List<String> findModClasses(java.nio.file.Path jarPath) {
        List<String> result = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                if (hasModAnnotation(jar, entry)) {
                    String className = entry.getName()
                        .replace('/', '.')
                        .replace(".class", "");
                    result.add(className);
                    NextLog.debug("ForgeAnnotationScanner: @Mod found in {}", className);
                }
            }
        } catch (IOException e) {
            NextLog.warn("ForgeAnnotationScanner: failed to scan {}: {}", jarPath, e.getMessage());
        }
        return result;
    }

    /**
     * Check if a class file has the @Mod annotation by scanning the constant pool
     * for the string "Mod" in annotation attributes.
     *
     * <p>This is a lightweight check - we don't fully parse the class file,
     * just look for the annotation descriptor string in the raw bytes.
     */
    private static boolean hasModAnnotation(JarFile jar, JarEntry entry) throws IOException {
        try (DataInputStream dis = new DataInputStream(jar.getInputStream(entry))) {
            // Skip magic (4) + minor (2) + major (2) = 8 bytes
            dis.readInt();
            dis.readUnsignedShort();
            dis.readUnsignedShort();

            // Read constant pool count
            int cpCount = dis.readUnsignedShort();

            // Scan constant pool for Utf8 entries containing "Lnet/minecraftforge/fml/common/Mod;"
            // or "Lnet/neoforged/fml/common/Mod;"
            for (int i = 1; i < cpCount; i++) {
                int tag = dis.readUnsignedByte();
                switch (tag) {
                    case 1 -> { // CONSTANT_Utf8
                        int length = dis.readUnsignedShort();
                        byte[] bytes = dis.readNBytes(length);
                        String s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        if (s.contains("fml/common/Mod") || s.contains("neoforged/fml/common/Mod")) {
                            return true;
                        }
                    }
                    case 3, 4 -> dis.readInt(); // Integer, Float
                    case 5, 6 -> { dis.readLong(); i++; } // Long, Double (take 2 slots)
                    case 7, 8 -> dis.readUnsignedShort(); // Class, String
                    case 9, 10, 11, 12 -> dis.readInt(); // Fieldref, Methodref, InterfaceMethodref, NameAndType
                    case 15 -> { dis.readUnsignedByte(); dis.readUnsignedShort(); } // MethodHandle
                    case 16 -> dis.readUnsignedShort(); // MethodType
                    case 17, 18 -> dis.readInt(); // Dynamic, InvokeDynamic
                    case 19, 20 -> dis.readInt(); // Module, Package
                    default -> {
                        NextLog.warn("Unknown constant pool tag {} at index {} in {}", tag, i, entry.getName());
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Find all methods annotated with @SubscribeEvent in a mod class.
     * These are event handlers that need to be registered to the event bus.
     */
    public static List<Method> findSubscribeEventMethods(Class<?> modClass) {
        List<Method> result = new ArrayList<>();
        for (Method m : modClass.getDeclaredMethods()) {
            for (Annotation a : m.getAnnotations()) {
                String typeName = a.annotationType().getName();
                if (typeName.contains("SubscribeEvent")) {
                    result.add(m);
                    NextLog.debug("ForgeAnnotationScanner: @SubscribeEvent on {}.{}",
                        modClass.getSimpleName(), m.getName());
                    break;
                }
            }
        }
        return result;
    }
}
