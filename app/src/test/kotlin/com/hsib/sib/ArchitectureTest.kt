package com.hsib.sib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The privacy promise of this app is about structure, and structure can be tested. Sharing is the only part of S-IB that may
 * talk to a server, so the reader of notifications, the importer, backups and the database must never be able to reach it.
 * These tests read the source files and fail the build if that ever stops being true.
 */
class ArchitectureTest {
    private val root = File("src/main/kotlin/com/hsib/sib")

    private fun sources(): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun rel(f: File) = f.relativeTo(root).path.replace('\\', '/')

    private fun importsOf(f: File) = f.readLines().map { it.trim() }.filter { it.startsWith("import ") }

    @Test
    fun theSourcesAreFound() {
        assertTrue("run from the app module", sources().size > 40)
    }

    @Test
    fun onlyTheScreensAndTheContainerImportTheSharingPackage() {
        val offenders = sources().filter { f ->
            val path = rel(f)
            !path.startsWith("social/") && !path.startsWith("ui/") && path != "AppContainer.kt" &&
                importsOf(f).any { it.startsWith("import com.hsib.sib.social") }
        }.map { rel(it) }
        assertEquals("these files may not reach the sharing code: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun theNotificationReaderAndTheOtherReadersNeverMentionSharing() {
        val readers = listOf("platform/NotificationReader.kt", "platform/Ingest.kt", "platform/DocText.kt", "platform/Images.kt", "platform/Backup.kt")
        for (name in readers) {
            val f = File(root, name)
            assertTrue("$name should exist", f.exists())
            val text = f.readText()
            assertTrue("$name must not know about sharing", !text.contains("hsib.sib.social") && !text.contains("SocialRepository") && !text.contains(".social"))
        }
    }

    @Test
    fun networkLibrariesAreUsedOnlyInsideTheSharingPackage() {
        val network = listOf("java.net.", "javax.net.", "okhttp3", "com.google.firebase", "android.net.http", "io.grpc", "retrofit2", "java.nio.channels.SocketChannel", "androidx.credentials", "com.google.android.libraries.identity")
        val offenders = sources().filter { f ->
            !rel(f).startsWith("social/") && importsOf(f).any { imp -> network.any { imp.startsWith("import $it") } }
        }.map { rel(it) }
        assertEquals("these files use the network outside the sharing package: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun theSharingPackageDoesNotReachIntoTheReadersEither() {
        // Sharing is given a store and a settings object. It never imports what reads notifications or documents.
        val offenders = sources().filter { rel(it).startsWith("social/") }.filter { f ->
            importsOf(f).any { it.startsWith("import com.hsib.sib.platform") }
        }.map { rel(it) }
        assertEquals("sharing must not depend on the readers: $offenders", emptyList<String>(), offenders)
    }
}
