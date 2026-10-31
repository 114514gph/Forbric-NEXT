package net.neoforbric.forge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.neoforbric.common.NextLog;

/**
 * Forge-style event bus.
 *
 * <p>Forge has two event buses:
 * <ul>
 *   <li><b>MinecraftForge.EVENT_BUS</b> - game events (player join, block break, etc.)</li>
 *   <li><b>FMLJavaModLoadingContext.get().getModEventBus()</b> - mod lifecycle events (registry, setup)</li>
 * </ul>
 *
 * <p>This implementation merges them into one bus for simplicity,
 * since we're running on a compat layer anyway.
 *
 * <p>Event handlers are methods annotated with @SubscribeEvent that take
 * a single event parameter. When an event is fired, all matching handlers
 * are called in registration order.
 */
public class ForgeEventBus {

    /** event class -> list of (target object, handler method) */
    private final Map<Class<?>, List<Subscriber>> subscribers = new HashMap<>();

    /**
     * Register all @SubscribeEvent methods on a mod instance to this bus.
     */
    public void register(Object modInstance) {
        List<Method> handlers = ForgeAnnotationScanner.findSubscribeEventMethods(modInstance.getClass());
        for (Method m : handlers) {
            if (m.getParameterCount() != 1) {
                NextLog.warn("ForgeEventBus: {}#{} has @SubscribeEvent but {} params, skipping",
                    modInstance.getClass().getSimpleName(), m.getName(), m.getParameterCount());
                continue;
            }
            Class<?> eventType = m.getParameterTypes()[0];
            m.setAccessible(true);
            subscribers.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(new Subscriber(modInstance, m));
            NextLog.debug("ForgeEventBus: registered {}#{} for event {}",
                modInstance.getClass().getSimpleName(), m.getName(), eventType.getSimpleName());
        }
    }

    /**
     * Fire an event to all registered subscribers.
     *
     * @param event the event object to dispatch
     */
    public void post(Object event) {
        Class<?> eventClass = event.getClass();
        // Also check superclasses and interfaces (Forge events have inheritance)
        List<Subscriber> all = new ArrayList<>();
        for (Map.Entry<Class<?>, List<Subscriber>> e : subscribers.entrySet()) {
            if (e.getKey().isAssignableFrom(eventClass)) {
                all.addAll(e.getValue());
            }
        }
        if (all.isEmpty()) return;

        NextLog.debug("ForgeEventBus: firing {} to {} subscriber(s)",
            eventClass.getSimpleName(), all.size());

        for (Subscriber s : all) {
            try {
                s.method.invoke(s.target, event);
            } catch (Exception e) {
                NextLog.warn("ForgeEventBus: handler {}#{} failed: {}",
                    s.target.getClass().getSimpleName(), s.method.getName(), e.getMessage());
            }
        }
    }

    /**
     * Check if there are any subscribers for a given event type.
     */
    public boolean hasSubscribers(Class<?> eventType) {
        for (Class<?> c : subscribers.keySet()) {
            if (c.isAssignableFrom(eventType)) return true;
        }
        return false;
    }

    /**
     * Total number of registered handlers.
     */
    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    private record Subscriber(Object target, Method method) {}
}
