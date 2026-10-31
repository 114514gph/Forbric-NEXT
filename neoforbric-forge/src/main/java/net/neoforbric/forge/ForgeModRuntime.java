package net.neoforbric.forge;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import net.neoforbric.api.ModCandidate;
import net.neoforbric.common.NextLog;

/**
 * Forge mod runtime: @Mod class loading + lifecycle scheduling + event bus + registry.
 *
 * <p>Full implementation:
 * <ul>
 *   <li>Scans mod jars for @Mod annotated classes</li>
 *   <li>Instantiates mod main classes with isolated classloaders</li>
 *   <li>Fires lifecycle events in correct order:
 *       ConstructionEvent → RegisterEvent → SetupEvent →
 *       InterModEnqueueEvent → InterModProcessEvent → LoadCompleteEvent</li>
 *   <li>ForgeEventBus dispatches events to @SubscribeEvent handlers</li>
 *   <li>ForgeRegistry handles block/item/entity registration</li>
 * </ul>
 */
public class ForgeModRuntime {

    private final List<Object> modInstances = new ArrayList<>();
    private final List<URLClassLoader> classLoaders = new ArrayList<>();
    private final ForgeEventBus eventBus = new ForgeEventBus();
    private final ForgeRegistry registry = new ForgeRegistry(eventBus);
    private final List<ModCandidate> candidates = new ArrayList<>();

    /**
     * Discover and instantiate all @Mod classes from a candidate jar.
     */
    public void loadMod(ModCandidate candidate) {
        candidates.add(candidate);
        NextLog.info("ForgeModRuntime: loading mod {} from {}", candidate.id(), candidate.jarPath());

        // Step 1: Scan for @Mod classes
        List<String> modClasses = ForgeAnnotationScanner.findModClasses(candidate.jarPath());
        if (modClasses.isEmpty()) {
            NextLog.warn("ForgeModRuntime: no @Mod class found in {}, skipping", candidate.jarPath());
            return;
        }

        // Step 2: Create remapping classloader and instantiate each @Mod class
        try {
            URL[] urls = { candidate.jarPath().toUri().toURL() };
            RemappingForgeClassLoader cl = new RemappingForgeClassLoader(urls, ForgeModRuntime.class.getClassLoader());
            classLoaders.add(cl);

            for (String className : modClasses) {
                try {
                    Class<?> modClass = Class.forName(className, true, cl);
                    Constructor<?> ctor = modClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object instance = ctor.newInstance();
                    modInstances.add(instance);

                    // Step 3: Register all @SubscribeEvent methods to the event bus
                    eventBus.register(instance);

                    NextLog.info("ForgeModRuntime: instantiated {} ({} event handlers)",
                        className, ForgeAnnotationScanner.findSubscribeEventMethods(modClass).size());
                } catch (Throwable e) {
                    NextLog.warn("ForgeModRuntime: failed to instantiate {}: {}", className, e.toString());
                }
            }
        } catch (Exception e) {
            NextLog.warn("ForgeModRuntime: failed to create classloader for {}: {}",
                candidate.id(), e.getMessage());
        }
    }

    /**
     * Fire the ConstructionEvent (FMLConstructModEvent equivalent).
     * Called when mod classes are first constructed.
     */
    public void fireConstructionEvent() {
        NextLog.info("ForgeModRuntime: firing ConstructionEvent ({} mods)", modInstances.size());
        eventBus.post(new ConstructionEvent(modInstances));
    }

    /**
     * Fire registry events (RegisterEvent for blocks, items, entities, etc.).
     */
    public void fireRegistryEvents() {
        NextLog.info("ForgeModRuntime: firing registry events");
        registry.fireRegisterEvents();
    }

    /**
     * Fire the SetupEvent (FMLCommonSetupEvent equivalent).
     */
    public void fireSetupEvent() {
        NextLog.info("ForgeModRuntime: firing SetupEvent");
        eventBus.post(new SetupEvent("common"));
    }

    /**
     * Fire the SidedSetupEvent (FMLClientSetupEvent / FMLDedicatedServerSetupEvent).
     */
    public void fireSidedSetupEvent(boolean isClient) {
        NextLog.info("ForgeModRuntime: firing SidedSetupEvent ({})", isClient ? "client" : "server");
        eventBus.post(new SetupEvent(isClient ? "client" : "server"));
    }

    /**
     * Fire InterModEnqueueEvent - mods send IMC messages to other mods.
     */
    public void fireInterModEnqueueEvent() {
        NextLog.info("ForgeModRuntime: firing InterModEnqueueEvent");
        eventBus.post(new InterModEvent("enqueue"));
    }

    /**
     * Fire InterModProcessEvent - IMC messages are delivered.
     */
    public void fireInterModProcessEvent() {
        NextLog.info("ForgeModRuntime: firing InterModProcessEvent");
        eventBus.post(new InterModEvent("process"));
    }

    /**
     * Fire LoadCompleteEvent - all mods fully loaded.
     */
    public void fireLoadCompleteEvent() {
        NextLog.info("ForgeModRuntime: firing LoadCompleteEvent");
        eventBus.post(new LoadCompleteEvent(modInstances.size(), registry.totalRegistered()));
    }

    /**
     * Run the full Forge lifecycle in correct order.
     */
    public void runFullLifecycle() {
        fireConstructionEvent();
        fireRegistryEvents();
        fireSetupEvent();
        fireSidedSetupEvent(true); // client side
        fireInterModEnqueueEvent();
        fireInterModProcessEvent();
        fireLoadCompleteEvent();
    }

    /** Shutdown all mod classloaders. */
    public void shutdown() {
        for (URLClassLoader cl : classLoaders) {
            try { cl.close(); } catch (Exception ignored) {}
        }
        classLoaders.clear();
        modInstances.clear();
        candidates.clear();
    }

    public int modCount() { return modInstances.size(); }
    public int handlerCount() { return eventBus.subscriberCount(); }
    public int registeredCount() { return registry.totalRegistered(); }
    public ForgeEventBus eventBus() { return eventBus; }
    public ForgeRegistry registry() { return registry; }

    // ---- Lifecycle event classes ----

    public static class ConstructionEvent {
        public final List<Object> mods;
        public ConstructionEvent(List<Object> mods) { this.mods = mods; }
    }

    public static class SetupEvent {
        public final String side; // "common", "client", "server"
        public SetupEvent(String side) { this.side = side; }
    }

    public static class InterModEvent {
        public final String phase; // "enqueue", "process"
        public InterModEvent(String phase) { this.phase = phase; }
    }

    public static class LoadCompleteEvent {
        public final int modCount;
        public final int registeredCount;
        public LoadCompleteEvent(int modCount, int registeredCount) {
            this.modCount = modCount;
            this.registeredCount = registeredCount;
        }
    }
}
