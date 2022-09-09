package com.matyrobbrt.kaupenbot.util

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import io.leangen.geantyref.TypeToken
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.ConfigurateException
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.reference.ConfigurationReference
import org.spongepowered.configurate.reference.ValueReference
import org.spongepowered.configurate.reference.WatchServiceListener

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.util.function.Consumer

@Slf4j
@CompileStatic
class ConfigurateUtils {
    public static final WatchServiceListener CONFIG_WATCH_SERVICE = WatchServiceListener.builder()
            .threadFactory {
                new Thread(it, 'ConfigListener').tap {
                    it.daemon = true
                }
            }
            .build()

    /**
     * Loads a config, and sets any new values that are not present.
     *
     * @param loader        the loader to use
     * @param configPath    the path of the config
     * @param configSetter  a consumer which will set the new config in the case of reloads
     * @param configClass   the class of the config
     * @param defaultConfig the default config
     * @param <T>           the type of the config
     * @return a reference to the configuration node and the configuration reference
     */
    static <T> Configuration<T, CommentedConfigurationNode> loadConfig(HoconConfigurationLoader loader, Path configPath, Consumer<T> configSetter, Class<T> configClass, T defaultConfig) throws ConfigurateException {
        final var configSerializer = Objects.requireNonNull(loader.defaultOptions().serializers().get(configClass))
        final var type = TypeToken.get(configClass).getType()

        if (!Files.exists(configPath)) {
            try {
                final var node = loader.loadToReference()
                Files.createDirectories(configPath.getParent())
                Files.createFile(configPath)
                configSerializer.serialize(type, defaultConfig, node.node())
                node.save()
            } catch (Exception e) {
                throw new ConfigurateException(e)
            }
        }

        final var configRef = loader.loadToReference()

        final var inMemoryNode = CommentedConfigurationNode
                .root(loader.defaultOptions())
        configSerializer.serialize(type, defaultConfig, inMemoryNode)
        configRef.node().mergeFrom(inMemoryNode)
        configRef.save()

        final configLoader = { WatchEvent<?> event ->
            try {
                configRef.load()
                configSetter.accept(configRef.referenceTo(configClass).get())
                log.warn('Reloaded config {}!', configPath)
            } catch (ConfigurateException e) {
                log.error('Exception while trying to reload config {}!', configPath, e)
            }
        }

        CONFIG_WATCH_SERVICE.listenToFile(configPath, configLoader)

        return new Configuration<>(config: configRef, value: configRef.referenceTo(configClass))
    }
}
@TupleConstructor
class Configuration<T, C extends ConfigurationNode> {
    ConfigurationReference<C> config
    ValueReference<T, C> value
}
