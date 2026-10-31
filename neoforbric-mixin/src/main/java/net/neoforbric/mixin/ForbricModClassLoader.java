package net.neoforbric.mixin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.neoforbric.common.NextLog;

/**
 * mods/ 目录的隔离类加载器。
 *
 * <p><b>类加载委托模型（parent-first，和真正的 mod 加载器一致）：</b>
 * <pre>
 *   loadClass(name)
 *     ├─ 1. parent（system CL，持有 vanilla server.jar + MC 库）先尝试
 *     │      → 所有 vanilla 类 / JDK 类都由 parent 解析，mod 碰不到它们的字节
 *     └─ 2. parent 找不到 → findClass(name) 自己从 mods/*.jar 里读字节
 *            → defineClass 前先过 {@link ForbricMixinService#weave} 织入
 * </pre>
 *
 * <p>这样天然满足隔离要求：
 * <ul>
 *   <li>mod 里的类和 vanilla 类同名也不会冲突——vanilla 永远走 parent，mod 自定义类只在本 CL。</li>
 *   <li>mod 代码里 {@code import net.minecraft.server.MinecraftServer} 能解析到 parent 加载的
 *       vanilla 类（parent-first 保证）。</li>
 * </ul>
 *
 * <p><b>诚实的边界：</b>target 是 vanilla 类（如 MinecraftServer）的 mixin，要在 vanilla 类被
 * defineClass 之前织入；那一步发生在 parent 层。本 CL 只负责把 mod jar 里的类织掉，
 * 以及对外暴露 {@link ForbricMixinService} 供验证流程直接对 vanilla 字节做织入。
 */
public class ForbricModClassLoader extends URLClassLoader {

	static {
		ClassLoader.registerAsParallelCapable();
	}

	private final List<Path> modJars;
	private final ForbricMixinService mixin;

	/**
	 * @param modJars mods 目录下要加载的 mod jar
	 * @param parent  父类加载器；真实启动时传 system CL（它的 classpath 上有 vanilla server.jar）
	 * @param mixin   mixin 服务（可空；为空时只做隔离不织入，便于单测）
	 */
	public ForbricModClassLoader(List<Path> modJars, ClassLoader parent, ForbricMixinService mixin) {
		super("forbric-mod-cl", toUrls(modJars), parent);
		this.modJars = List.copyOf(modJars);
		this.mixin = mixin;
		NextLog.info("ForbricModClassLoader: 初始化，挂载 {} 个 mod jar，parent={}",
				modJars.size(), parent == null ? "null" : parent.getClass().getName());
		for (Path jar : modJars) {
			NextLog.info("  mod jar: {}", jar);
		}
	}

	/** 便捷工厂：从 mods 目录扫描所有 .jar。 */
	public static ForbricModClassLoader fromModsDir(Path modsDir, ClassLoader parent, ForbricMixinService mixin)
			throws IOException {
		List<Path> jars = new ArrayList<>();
		if (modsDir != null && Files.isDirectory(modsDir)) {
			try (var stream = Files.newDirectoryStream(modsDir, "*.jar")) {
				for (Path p : stream) jars.add(p);
			}
		}
		return new ForbricModClassLoader(jars, parent, mixin);
	}

	/**
	 * parent 找不到时，从 mods/*.jar 读字节并 defineClass。
	 * defineClass 前过 mixin 织入。
	 */
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		// 用资源流从自己的 URL 里读 .class
		String resource = name.replace('.', '/') + ".class";
		try (InputStream in = getResourceAsStreamRaw(resource)) {
			if (in == null) {
				throw new ClassNotFoundException(name);
			}
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			in.transferTo(buf);
			byte[] bytes = buf.toByteArray();

			// 织入（如果挂了 mixin 服务）。mod 自己的类一般没有 mixin 命中，原样返回。
			if (mixin != null && mixin.isInitialized()) {
				bytes = mixin.weave(name, bytes);
			}

			Class<?> defined = defineClass(name, bytes, 0, bytes.length);
			NextLog.info("ForbricModClassLoader: 定义 mod 类 {} ({} bytes, cl={})",
					name, bytes.length, getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this)));
			return defined;
		} catch (IOException e) {
			throw new ClassNotFoundException("读取 mod class 失败: " + name, e);
		}
	}

	/** 只从本 CL 自己的 URL 读资源（不委托 parent），避免误命中 vanilla 同名资源。 */
	private InputStream getResourceAsStreamRaw(String resource) {
		URL u = findResource(resource);
		if (u == null) return null;
		try {
			return u.openStream();
		} catch (IOException e) {
			return null;
		}
	}

	public List<Path> modJars() {
		return modJars;
	}

	private static URL[] toUrls(List<Path> jars) {
		List<URL> urls = new ArrayList<>(jars.size());
		for (Path jar : jars) {
			try {
				urls.add(jar.toUri().toURL());
			} catch (MalformedURLException e) {
				NextLog.warn("ForbricModClassLoader: 非法 jar 路径 {}: {}", jar, e.getMessage());
			}
		}
		return urls.toArray(new URL[0]);
	}
}
