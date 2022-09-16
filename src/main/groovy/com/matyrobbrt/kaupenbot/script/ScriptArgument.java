package com.matyrobbrt.kaupenbot.script;

import groovy.lang.Closure;
import groovy.lang.GroovyInterceptable;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import groovy.lang.Tuple;
import net.dv8tion.jda.api.requests.RestAction;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jdbi.v3.core.internal.MemoizingSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ScriptArgument extends GroovyObjectSupport implements GroovyInterceptable {
    private final Map<String, Object> properties = new HashMap<>();
    private final Map<String, List<MethodInvoker>> methods = new HashMap<>();

    // region internal
    @Override
    public Object invokeMethod(String name, Object args) {
        final CallContext context;
        if (args instanceof Object[] asArray) {
            context = createContext(asArray);
        } else if (args instanceof Tuple<?> tuple) {
            context = createContext(tuple.toArray());
        } else {
            context = createContext(new Object[]{args});
        }
        for (final var method : findMethods(name, context.getArgs().length)) {
            try {
                return method.call(context);
            } catch (ClassCastException ignored) {}
        }
        throw new MissingMethodException(name, Object.class, context.getArgs());
    }

    @Override
    public Object getProperty(String propertyName) {
        final var prop = properties.get(propertyName);
        if (prop == null) {
            final var getMethods = findMethods("get" + StringGroovyMethods.capitalize(propertyName), 0);
            if (!getMethods.isEmpty()) {
                return getMethods.get(0).call(createContext(new Object[0]));
            }
            final var isMethods = findMethods("is" + StringGroovyMethods.capitalize(propertyName), 0);
            if (!isMethods.isEmpty()) {
                return isMethods.get(0).call(createContext(new Object[0]));
            }
            throw new MissingPropertyException(propertyName, Object.class);
        }
        return prop;
    }

    private CallContext createContext(Object[] arguments) {
        return new CallContext() {
            @Override
            public Object[] getArgs() {
                return arguments;
            }

            @Override
            public <Z> ArgumentExpectation<Z> expectArg(int index, Class<Z> type) {
                return new ArgumentExpectation<>() {
                    private final List<Expectation<?, Z>> expectations = new ArrayList<>();
                    {
                        when(type, Function.identity());
                    }

                    @Override
                    public List<Expectation<?, Z>> getExpectations() {
                        return expectations;
                    }

                    @Override
                    public <Z1> ArgumentExpectation<Z> when(Class<Z1> type, Function<Z1, Z> function) {
                        this.expectations.add(new Expectation<>(type, function));
                        return this;
                    }

                    @Override
                    public <Z1> ArgumentExpectation<Z> flatWhen(ArgumentExpectation<Z1> expectation, Function<Z1, Z> function) {
                        expectation.getExpectations().forEach(exp -> expectations.add(new Expectation<>(
                                exp.type(), obj -> function.apply(exp.apply(obj))
                        )));
                        return this;
                    }

                    @Override
                    public @NotNull Z orElse(Supplier<Z> orElse) {
                        return Objects.requireNonNullElseGet(get(), orElse);
                    }

                    @Override
                    public Z get() {
                        if (index >= arguments.length) {
                            return null;
                        }
                        final var arg = arguments[index];
                        for (final Expectation<?, Z> exc : expectations) {
                            if (exc.test(arg)) return exc.apply(arg);
                        }
                        return null;
                    }
                };
            }
        };
    }

    private List<MethodInvoker> findMethods(String name, int argumentCount) {
        final var methods = new ArrayList<MethodInvoker>();
        final var mth = this.methods.computeIfAbsent(name, it -> new ArrayList<>());
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mth.size(); i++) {
            final var mt = mth.get(i);
            if (mt.parameterCount() < 0 || mt.parameterCount() == argumentCount) {
                methods.add(mt);
            }
        }
        return methods;
    }

    @Override
    public void setProperty(String propertyName, Object newValue) {
        throw new UnsupportedOperationException();
    }
    // endregion

    private ScriptArgument() {}
    public static ScriptArgument make() {
        return new ScriptArgument();
    }

    public ScriptArgument addProperty(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public ScriptArgument addProperty(String name, Closure<?> value) {
        return addMethod("get" + StringGroovyMethods.capitalize(name), value);
    }

    public ScriptArgument addCachedProperty(String name, Supplier<?> value) {
        return addMethod("get" + StringGroovyMethods.capitalize(name), MemoizingSupplier.of(value));
    }

    public ScriptArgument set(String name, Object value) {
        return addProperty(name, value);
    }

    public ScriptArgument addMethod(String name, Closure<?> method) {
        methods.computeIfAbsent(name, k -> new ArrayList<>()).add(new MethodInvoker() {
            @Override
            public Object call(CallContext args) {
                return method.call(args.getArgs());
            }

            @Override
            public int parameterCount() {
                return method.getMaximumNumberOfParameters();
            }
        });
        return this;
    }

    public ScriptArgument addMethod(String name, int expectedArguments, Function<CallContext, Object> function) {
        methods.computeIfAbsent(name, k -> new ArrayList<>()).add(new MethodInvoker() {
            @Override
            public Object call(CallContext args) {
                return function.apply(args);
            }

            @Override
            public int parameterCount() {
                return expectedArguments;
            }
        });
        return this;
    }

    public ScriptArgument addVoidMethod(String name, int expectedArguments, Consumer<CallContext> function) {
        return addMethod(name, expectedArguments, ctx -> {
            function.accept(ctx);
            return null;
        });
    }

    public ScriptArgument addMethod(String name, Supplier<?> supplier) {
        return addMethod(name, 0, ctx -> supplier.get());
    }

    public ScriptArgument addRestAction(String name, Supplier<RestAction<?>> supplier) {
        return addMethod(name, () -> supplier.get().complete());
    }

    public interface MethodInvoker {
        Object call(CallContext args);

        default int parameterCount() {
            return -1;
        }
    }

    public interface CallContext {
        Object[] getArgs();

        <Z> ArgumentExpectation<Z> expectArg(int index, Class<Z> type);

        default String string(int index) {
            if (index > getArgs().length) return "";
            return getArgs()[index].toString();
        }

        default <T> Stream<T> stream(IntFunction<T> getter) {
            return IntStream.range(0, getArgs().length).mapToObj(getter);
        }
    }

    public interface ArgumentExpectation<T> extends Supplier<T> {
        <Z> ArgumentExpectation<T> when(Class<Z> type, Function<Z, T> function);
        <Z> ArgumentExpectation<T> flatWhen(ArgumentExpectation<Z> expectation, Function<Z, T> function);

        @NotNull
        T orElse(Supplier<T> orElse);

        @Nullable
        @Override
        T get();

        List<Expectation<?, T>> getExpectations();
    }

    @SuppressWarnings("unchecked")
    record Expectation<A, B>(Class<A> type, Function<A, B> function) implements Predicate<Object>, Function<Object, B> {
        @Override
        public B apply(Object o) {
            return Expectation.this.function.apply((A) o);
        }

        @Override
        public boolean test(Object o) {
            return Expectation.this.type.isInstance(o);
        }
    }
}
