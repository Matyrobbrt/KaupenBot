package com.matyrobbrt.kaupenbot.api

import groovy.transform.CompileStatic

@CompileStatic
interface PluginRegistry {
    default <Z extends Plugin> Z getAt(Class<Z> pluginClass) {
        return get(pluginClass)
    }
    default Plugin propertyMissing(String name) {
        return named(name)
    }

    abstract <Z extends Plugin> Z get(Class<Z> pluginClass)
    abstract <Z extends Plugin> Z named(String name)

    abstract void registerPlugin(String name, Plugin plugin)
}

@CompileStatic
interface Plugin {
    void setCurrentScript(UUID uuid)

    void scriptUnloaded(UUID id)
}