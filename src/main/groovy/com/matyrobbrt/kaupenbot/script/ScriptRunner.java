package com.matyrobbrt.kaupenbot.script;

import com.matyrobbrt.kaupenbot.KaupenBot;
import com.matyrobbrt.kaupenbot.util.Constants;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.util.Eval;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ScriptRunner {
    public static final GroovyShell SHELL = new GroovyShell(configureCompiler(new CompilerConfiguration()));

    static {
        Constants.getEXECUTOR().scheduleAtFixedRate(() -> SHELL.getClassLoader().clearCache(), 0, 1, TimeUnit.DAYS);
    }

    public static void run(Map<String, ?> arguments, String script) {
        InvokerHelper.createScript(
                SHELL.getClassLoader().parseClass(script),
                new Binding(arguments)
        ).run();
    }

    private static CompilerConfiguration configureCompiler(CompilerConfiguration configuration) {
        {
            final var imports = new ImportCustomizer();
            imports.addStarImports("net.dv8tion.jda.api");
            configuration.addCompilationCustomizers(imports);
        }

        {
            final var secure = new SecureASTCustomizer();
            secure.setDisallowedReceiversClasses(List.of(
                    KaupenBot.class, GroovyShell.class, Eval.class,
                    Script.class, Path.class, File.class,
                    System.class
            ));
            configuration.addCompilationCustomizers(secure);
        }

        return configuration;
    }
}
