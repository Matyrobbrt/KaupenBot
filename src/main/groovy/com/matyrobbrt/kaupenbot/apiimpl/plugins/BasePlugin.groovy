package com.matyrobbrt.kaupenbot.apiimpl.plugins

import com.matyrobbrt.kaupenbot.api.Plugin
import groovy.transform.CompileStatic

@CompileStatic
abstract class BasePlugin implements Plugin {
    protected final ThreadLocal<UUID> currentScript = new ThreadLocal<>()

    @Override
    final void setCurrentScript(UUID uuid) {
        currentScript.set(uuid)
    }
}
