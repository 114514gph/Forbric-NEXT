package net.neoforbric.forge;

import java.util.HashMap;
import java.util.Map;

import net.neoforbric.common.NextLog;

/**
 * Forge registry event system.
 *
 * <p>Forge fires RegisterEvent during the registry phase, and mods register
 * their blocks, items, entities, etc. by listening to specific register events.
 *
 * <p>This implementation provides a simple registry map and fires
 * RegisterEvent for each registry type.
 */
public class ForgeRegistry {

    /** Registry name -> (resource location -> registered object) */
    private final Map<String, Map<String, Object>> registries = new HashMap<>();

    private final ForgeEventBus eventBus;

    public ForgeRegistry(ForgeEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Fire the RegisterEvent for all known registry types.
     * Called during the REGISTRY lifecycle phase.
     */
    public void fireRegisterEvents() {
        NextLog.info("ForgeRegistry: firing RegisterEvent for {} registry types", registries.size());

        // Fire RegisterEvent for blocks
        eventBus.post(new RegisterEvent("block", getOrCreate("block")));

        // Fire RegisterEvent for items
        eventBus.post(new RegisterEvent("item", getOrCreate("item")));

        // Fire RegisterEvent for entity types
        eventBus.post(new RegisterEvent("entity_type", getOrCreate("entity_type")));

        // Fire RegisterEvent for biomes
        eventBus.post(new RegisterEvent("biome", getOrCreate("biome")));

        // Fire RegisterEvent for menu screens
        eventBus.post(new RegisterEvent("menu", getOrCreate("menu")));

        // Fire RegisterEvent for sound events
        eventBus.post(new RegisterEvent("sound_event", getOrCreate("sound_event")));

        NextLog.info("ForgeRegistry: registration complete");
    }

    private Map<String, Object> getOrCreate(String name) {
        return registries.computeIfAbsent(name, k -> new HashMap<>());
    }

    /**
     * Get a registered object by registry name and resource location.
     */
    public Object get(String registryName, String resourceLocation) {
        Map<String, Object> reg = registries.get(registryName);
        return reg == null ? null : reg.get(resourceLocation);
    }

    /**
     * Total number of registered entries across all registries.
     */
    public int totalRegistered() {
        return registries.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * RegisterEvent - fired during registry phase.
     * Mods listen to this event and call event.register(...) to add their objects.
     */
    public static class RegisterEvent {
        private final String registryName;
        private final Map<String, Object> registry;

        public RegisterEvent(String registryName, Map<String, Object> registry) {
            this.registryName = registryName;
            this.registry = registry;
        }

        public String getRegistryName() { return registryName; }

        /**
         * Register an object with the given resource location (e.g. "mymod:myblock").
         */
        public void register(String resourceLocation, Object object) {
            registry.put(resourceLocation, object);
            NextLog.debug("ForgeRegistry: registered {} -> {} in {}",
                resourceLocation, object.getClass().getSimpleName(), registryName);
        }
    }
}
