package com.matyrobbrt.kaupenbot.common.util

import groovy.transform.CompileStatic

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@CompileStatic
final class Constants {
    static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2) {
        new Thread(it, 'TimedTasks').tap { it.daemon = true }
    }
}
