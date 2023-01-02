package com.matyrobbrt.kaupenbot.gradle

import com.mattmalec.pterodactyl4j.PteroBuilder
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

@CompileStatic
abstract class UploadFileToPterodactyl extends DefaultTask {
    @Input abstract Property<String> getServerURL()
    @Input abstract Property<String> getServerID()
    @Input abstract Property<String> getApiToken()

    @InputFile abstract RegularFileProperty getFileToUpload()
    @Input abstract Property<String> getFilePath()

    @TaskAction
    void run() {
        final client = PteroBuilder.createClient(serverURL.get(), apiToken.get())
        final server = client.retrieveServerByIdentifier(serverID.get()).execute(false)

        logger.info("Server information: name - %s, ID - %s", server.name, server.identifier)
        final path = filePath.get().split('/')

        server.stop()
                .flatMap {
                    server.retrieveDirectory(path.size() == 1 ? '/' : path.dropRight(1).join('/'))
                        .flatMap {
                            it.upload().addFile(fileToUpload.get().asFile.getBytes(), path.last())
                        }
                }
                .flatMap { server.start() }
                .onErrorMap {
                    logger.error('Encountered exception uploading file: ', it)
                    return null
                }
                .execute(false)

        logger.warn('Uploaded file and restarted server!')
    }
}
