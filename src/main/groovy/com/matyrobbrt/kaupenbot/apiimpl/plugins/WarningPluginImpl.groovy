package com.matyrobbrt.kaupenbot.apiimpl.plugins

import com.matyrobbrt.kaupenbot.api.ModerationAction
import com.matyrobbrt.kaupenbot.api.plugins.WarningsPlugin
import com.matyrobbrt.kaupenbot.api.util.Warning
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor

@CompileStatic
class WarningPluginImpl extends BasePlugin implements WarningsPlugin {
    private final Map<Integer, ScriptAction> actions = [:]

    @Override
    void scriptUnloaded(UUID id) {
        actions.removeAll { it.value.scriptId == id }
    }

    @Override
    void addWarnAction(int number, ModerationAction<Warning> action) {
        actions[number] = new ScriptAction(currentScript.get(), action)
    }

    @Override
    ModerationAction<Warning> getAction(int warnNumber) {
        return actions[warnNumber]?.action
    }

    @CompileStatic
    @TupleConstructor
    static final class ScriptAction {
        UUID scriptId
        ModerationAction<Warning> action
    }
}
