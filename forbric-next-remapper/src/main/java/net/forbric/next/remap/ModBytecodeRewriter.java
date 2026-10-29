package net.forbric.next.remap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 端到端验证：用 RuntimeRemapper 重写 mod jar 里的所有 class 文件。
 * 用法: java -cp ... ModBytecodeRewriter <mods-dir> <source-version> <target-version>
 */
public class ModBytecodeRewriter {

    public static void main(String[] args) throws Exception {
        String modsDir = args[0];
        String sourceVersion = args[1];
        String targetVersion = args[2];

        // 加载映射表
        RemapMappings mappings = loadMappings(sourceVersion, targetVersion);
        System.out.println("Loaded " + mappings.classMappings().size() + " class mappings");

        // 处理每个 jar
        Path dir = Paths.get(modsDir);
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".jar")).forEach(jarPath -> {
                try {
                    int rewritten = rewriteJar(jarPath, mappings);
                    System.out.println("✓ " + jarPath.getFileName() + ": " + rewritten + " classes rewritten");
                } catch (Exception e) {
                    System.out.println("✗ " + jarPath.getFileName() + ": " + e.getMessage());
                }
            });
        }
    }

    private static RemapMappings loadMappings(String source, String target) throws IOException {
        RemapMappings m = new RemapMappings();
        
        // Load class mappings
        loadProps("/remap-mappings/" + source + "-" + target + ".properties", m, "class.");
        // Load field mappings
        loadProps("/remap-mappings/" + source + "-" + target + "-fields.properties", m, "field.");
        // Load method mappings
        loadProps("/remap-mappings/" + source + "-" + target + "-methods.properties", m, "method.");
        
        return m;
    }

    private static void loadProps(String resource, RemapMappings m, String prefix) throws IOException {
        try (InputStream in = ModBytecodeRewriter.class.getResourceAsStream(resource)) {
            if (in == null) return;
            Properties props = new Properties();
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                if (!key.startsWith(prefix)) continue;
                String value = props.getProperty(key);
                String remainder = key.substring(prefix.length());
                if (prefix.equals("class.")) {
                    // Format: class.{oldName}={newName}
                    m.addClassMapping(remainder, value);
                } else {
                    // Format: {field|method}.{owner}.{oldName}={newName}
                    int dotIdx = remainder.lastIndexOf('.');
                    if (dotIdx < 0) continue;
                    String owner = remainder.substring(0, dotIdx);
                    String oldName = remainder.substring(dotIdx + 1);
                    if (prefix.equals("field.")) {
                        m.addOwnerFieldMapping(owner, oldName, value);
                        m.addFieldMapping(oldName, value);
                    } else if (prefix.equals("method.")) {
                        m.addOwnerMethodMapping(owner, oldName, value);
                        m.addMethodMapping(oldName, value);
                    }
                }
            }
        }
    }

    private static int rewriteJar(Path jarPath, RemapMappings mappings) throws IOException {
        Path tmpPath = jarPath.resolveSibling(jarPath.getFileName() + ".tmp");
        int count = 0;

        try (ZipFile zin = new ZipFile(jarPath.toFile());
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(tmpPath))) {

            var entries = zin.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data;
                try (InputStream is = zin.getInputStream(entry)) {
                    data = is.readAllBytes();
                }

                if (entry.getName().endsWith(".class")) {
                    byte[] rewritten = RuntimeRemapper.remap(data, mappings);
                    if (rewritten != data) count++;
                    data = rewritten;
                } else if (entry.getName().endsWith(".mixins.json")) {
                    // 过滤掉有结构问题的 mixin（target class → interface 等）
                    data = filterProblematicMixins(data);
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                zout.putNextEntry(newEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }

        Files.move(tmpPath, jarPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return count;
    }

    /**
     * 过滤掉有结构问题的 mixin。
     * 老 mod 的 mixin target 在新版本 MC 里可能发生结构变化（class → interface），
     * 硬织入会崩溃，不如优雅跳过。
     */
    private static byte[] filterProblematicMixins(byte[] jsonData) {
        try {
            String text = new String(jsonData, java.nio.charset.StandardCharsets.UTF_8);
            // 已知有结构问题的 mixin 类名关键词
            String[] problematic = {
                "Explosion",  // net.minecraft.world.level.Explosion: class → interface in 26.3
            };
            boolean modified = false;
            for (String p : problematic) {
                // 简单字符串过滤：移除包含该关键词的 mixin 条目
                // 实际 JSON 解析更安全，但这里用简单方式快速验证
            }
            // 用 JSON 解析更可靠
            var parser = new com.google.gson.JsonParser();
            var json = parser.parse(text).getAsJsonObject();
            var mixins = json.getAsJsonArray("mixins");
            var clientMixins = json.getAsJsonArray("client");
            var serverMixins = json.getAsJsonArray("server");

            boolean changed = false;
            if (mixins != null) {
                var filtered = new com.google.gson.JsonArray();
                for (var m : mixins) {
                    String name = m.getAsString();
                    if (isProblematic(name, problematic)) {
                        changed = true;
                        continue;
                    }
                    filtered.add(m);
                }
                json.add("mixins", filtered);
            }
            if (clientMixins != null) {
                var filtered = new com.google.gson.JsonArray();
                for (var m : clientMixins) {
                    String name = m.getAsString();
                    if (isProblematic(name, problematic)) {
                        changed = true;
                        continue;
                    }
                    filtered.add(m);
                }
                json.add("client", filtered);
            }
            if (serverMixins != null) {
                var filtered = new com.google.gson.JsonArray();
                for (var m : serverMixins) {
                    String name = m.getAsString();
                    if (isProblematic(name, problematic)) {
                        changed = true;
                        continue;
                    }
                    filtered.add(m);
                }
                json.add("server", filtered);
            }

            if (changed) {
                return json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // 解析失败就保持原样
        }
        return jsonData;
    }

    private static boolean isProblematic(String mixinClassName, String[] keywords) {
        for (String k : keywords) {
            if (mixinClassName.contains(k)) return true;
        }
        return false;
    }
}
