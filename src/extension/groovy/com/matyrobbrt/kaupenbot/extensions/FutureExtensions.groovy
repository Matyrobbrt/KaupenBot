package com.matyrobbrt.kaupenbot.extensions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class FutureExtensions {
    static <T> CompletableFuture<T> exceptionHandling(CompletableFuture<T> self) {
        self.whenComplete((res, t) -> {
            if (t != null) {
                log.error('Encountered exception executing completable future: ', t)
            }
        })
    }
}
