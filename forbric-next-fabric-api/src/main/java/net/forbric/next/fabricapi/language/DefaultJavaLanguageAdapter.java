package net.forbric.next.fabricapi.language;

/**
 * The default Java language adapter. Handles:
 * <ul>
 *   <li>{@code com.example.Mod} — plain class, default constructor</li>
 *   <li>{@code com.example.Mod::FIELD} — static field on the class</li>
 *   <li>{@code com.example.Mod::method} — static no-arg method returning the instance</li>
 * </ul>
 */
public final class DefaultJavaLanguageAdapter implements LanguageAdapter {

	public static final String NAME = "default";

	@Override
	public String name() {
		return NAME;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T create(String value, Class<T> type, ClassLoader loader) throws Exception {
		int sep = value.indexOf("::");
		String className = sep < 0 ? value : value.substring(0, sep);
		Class<?> cls = Class.forName(className, true, loader);

		if (sep < 0) {
			return type.cast(cls.getDeclaredConstructor().newInstance());
		}

		String member = value.substring(sep + 2);
		try {
			// Static field access (Kotlin object INSTANCE, ModClient::INSTANCE, etc.)
			java.lang.reflect.Field field = cls.getDeclaredField(member);
			field.setAccessible(true);
			return type.cast(field.get(null));
		} catch (NoSuchFieldException ignored) {
			// Fall through to static method
			java.lang.reflect.Method method = cls.getDeclaredMethod(member);
			method.setAccessible(true);
			return (T) method.invoke(null);
		}
	}
}
