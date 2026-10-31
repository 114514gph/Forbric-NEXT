package net.neoforbric.forge;

import net.neoforbric.common.NextLog;
import net.neoforbric.remap.RuntimeRemapper;
import net.neoforbric.remap.RemapMappings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

/**
 * ClassLoader that remaps Forge mod class references from 1.20.1 Mojang names
 * to 26.3 Mojang names using RuntimeRemapper (proper constant pool parsing).
 *
 * This replaces the naive string-replacement approach with correct bytecode remapping.
 */
public class RemappingForgeClassLoader extends URLClassLoader {

    private final RemapMappings mappings = new RemapMappings();

    public RemappingForgeClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        loadMappings();
    }

    private void loadMappings() {
        try (InputStream is = getClass().getResourceAsStream(
                "/remap-mappings/forge-named-1.20.1-26.3.properties")) {
            if (is == null) {
                NextLog.warn("RemappingForgeClassLoader: mapping file not found");
                return;
            }
            Properties props = new Properties();
            props.load(is);
            int count = 0;
            for (String key : props.stringPropertyNames()) {
                // key format: old/internal/Name=new/internal/Name
                String newName = props.getProperty(key);
                mappings.addClassMapping(key, newName);
                count++;
            }
            NextLog.info("RemappingForgeClassLoader: loaded {} class mappings", count);
        } catch (IOException e) {
            NextLog.warn("RemappingForgeClassLoader: failed to load mappings: {}", e.getMessage());
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String classPath = name.replace('.', '/') + ".class";

        URL resource = findResource(classPath);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }

        byte[] classBytes;
        try (InputStream is = resource.openStream()) {
            classBytes = readAll(is);
        } catch (IOException e) {
            throw new ClassNotFoundException("Cannot read " + name, e);
        }

        // Apply proper constant pool remapping
        byte[] remapped = RuntimeRemapper.remap(classBytes, mappings);

        return defineClass(name, remapped, 0, remapped.length);
    }

    private byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        return bos.toByteArray();
    }
}
