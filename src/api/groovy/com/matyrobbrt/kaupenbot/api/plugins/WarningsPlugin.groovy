package com.matyrobbrt.kaupenbot.api.plugins

import com.matyrobbrt.kaupenbot.api.ModerationAction
import com.matyrobbrt.kaupenbot.api.Plugin
import com.matyrobbrt.kaupenbot.api.util.Warning
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable

@CompileStatic
interface WarningsPlugin extends Plugin {
    void addWarnAction(int warnNumber, ModerationAction<Warning> action)

    @Nullable
    ModerationAction<Warning> getAction(int warnNumber)
}