package com.matyrobbrt.kaupenbot.apiimpl

import com.matyrobbrt.kaupenbot.api.Plugin
import com.matyrobbrt.kaupenbot.api.PluginRegistry
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

@CompileStatic
class BasePluginRegistry implements PluginRegistry {
    @PackageScope
    final Map<String, Plugin> plugins = [:]

    @Override
    <Z extends Plugin> Z get(Class<Z> pluginClass) {
        return (Z) plugins.find { pluginClass.isInstance(it.value) }?.value
    }

    @Override
    <Z extends Plugin> Z named(String name) {
        return (Z) plugins[name]
    }

    @Override
    void registerPlugin(String name, Plugin plugin) {
        plugins[name] = plugin
    }

}