package com.matyrobbrt.kaupenbot.logparser.parsers

import com.matyrobbrt.kaupenbot.logparser.Log
import com.matyrobbrt.kaupenbot.logparser.LogException
import com.matyrobbrt.kaupenbot.logparser.LogParser
import com.matyrobbrt.kaupenbot.logparser.ResultHandler
import groovy.transform.CompileStatic
import groovy.transform.stc.POJO

import java.util.function.BiFunction
import java.util.function.Predicate

@CompileStatic
static ExceptionParser "default"() {
    return new ExceptionParser().tap {
        parser({ it.theException.contains('mods.toml missing metadata for modid') }) { ex, log, ln, handler ->
            final modId = ex.exceptionStr[1].replace('mods.toml missing metadata for modid', '').trim()
            handler.appendIssue(embed {
                addField 'Cause', "There is no mod configured in the `mods.toml` with the mod ID of your `@Mod`'s main class.", true
                addField 'Possible solution', """
Check that the mod ID declared in your mod main class (the class annotated with `@Mod`) matches the one in your `mods.toml`.
Make sure that your `MODID` field (if you have one) also contains the correct ID.
""".trim(), false
            }, "Missing metadata for mod `$modId`")
        }
        parser({ it.exceptionStr[0] == 'java.lang.NullPointerException' && it.exceptionStr[1].startsWith('Registry Object not present:') }) { ex, log, ln, handler ->
            final obj = ex.exceptionStr[1].drop('Registry Object not present:'.length()).trim()
            handler.appendIssue(embed {
                if (obj == 'minecraft:milk') {
                    addField 'Cause', "Registry object named `$obj` is not registered or is not yet available.", true
                    addField 'Possible solution', """
Make sure that you're not calling `RegistryObject#get` too early (before `FMLCommonSetupEvent` is fired).
That includes passing references of objects to others via constructors. Use a `Supplier` instead.

Another possible cause is, if it's a custom object registered via `DeferredRegister#register`, make sure your DeferredRegister is registered to your mod bus, during mod construction, like so:
```java
ITEMS.register(modEventBus);
```
""".trim(), false
                }
            }, "Registry object `$obj` not found")
        }
    }
}

@CompileStatic
class ExceptionParser implements LogParser {
    private final List<Parser> parsers = []

    @Override
    void handle(Log log, BiFunction<Integer, Integer, String> gistLineGetter, ResultHandler handler) {
        log.exceptions.each {
            parsers.each { parser ->
                if (parser.exceptionTest.test(it)) {
                    parser.onMatch.call(it, log, gistLineGetter, handler)
                }
            }
        }
    }

    void parser(Predicate<LogException> exceptionTest, OnMatch onMatch) {
        this.parsers.add(new Parser(exceptionTest, onMatch))
    }

    @POJO
    @CompileStatic
    static final class Parser {
        final Predicate<LogException> exceptionTest
        final OnMatch onMatch

        Parser(Predicate<LogException> exceptionTest, OnMatch onMatch) {
            this.exceptionTest = exceptionTest
            this.onMatch = onMatch
        }
    }

    @CompileStatic
    static interface OnMatch {
        void call(LogException ex, Log log, BiFunction<Integer, Integer, String> ln, ResultHandler handler)
    }
}
