package com.matyrobbrt.kaupenbot.apiimpl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.spongepowered.configurate.reference.WatchServiceListener

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.attribute.BasicFileAttributes

@Slf4j
@CompileStatic
class PluginLoader {
    private final WatchServiceListener fileWatch
    private final BasePluginRegistry plugins
    private final GroovyShell shell

    PluginLoader(WatchServiceListener fileWatch, BasePluginRegistry plugins, GroovyShell shell) {
        this.fileWatch = fileWatch
        this.plugins = plugins
        this.shell = shell
    }

    private final Map<Path, UUID> fileIds = [:]

    void track(Path directory, Path outPath) {
        log.debug('Tracking directory {}', directory)
        if (Files.exists(outPath))
            Files.walk(outPath).forEach { if (!Files.isDirectory(it)) Files.delete(it) }
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                file = file.toAbsolutePath()
                if (file.startsWith(outPath) || !file.fileName.toString().endsWith('groovy')) return FileVisitResult.CONTINUE
                final id = UUID.randomUUID()
                fileIds.put(file, id)
                runScript(file, id)
                return FileVisitResult.CONTINUE
            }
        })
        fileWatch.listenToDirectory(directory, {
            if (!(it.context() instanceof Path)) return
            Path path = directory.resolve(it.context() as Path).toAbsolutePath()
            if (path.startsWith(outPath) || !path.fileName.toString().endsWith('groovy')) return

            if (it.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                log.debug('Loading script {}', path)
                final id = UUID.randomUUID()
                fileIds.put(path, id)
                runScript(path, id)
            } else if (it.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                log.debug('Unloading deleted script {}', path)
                final id = fileIds.get(path)
                if (id !== null) {
                    fileIds.remove(path)
                    plugins.plugins.forEach { name, plugin ->
                        plugin.scriptUnloaded(id)
                    }
                }
            } else if (it.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                log.debug('Reloading script {}', path)
                final id = fileIds.get(path)
                if (id !== null) {
                    plugins.plugins.forEach { name, plugin ->
                        plugin.scriptUnloaded(id)
                    }
                    runScript(path, id)
                } else {
                    final actualId = UUID.randomUUID()
                    fileIds.put(path, actualId)
                    runScript(path, actualId)
                }
            }
        })
    }

    private void runScript(Path file, UUID uuid) {
        plugins.plugins.forEach { name, plugin ->
            plugin.currentScript = uuid
        }

        try (final reader = file.newReader()) {
            final parsed = shell.parse(reader)
            parsed.binding = new Binding(['plugins': plugins])
            parsed.run()
        } catch (Exception exception) {
            log.error('There was an exception trying to run script {}: ', file, exception)
            plugins.plugins.forEach { name, plugin ->
                plugin.scriptUnloaded(uuid)
            }
        }
    }
}
