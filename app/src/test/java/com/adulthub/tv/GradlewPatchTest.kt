package com.adulthub.tv

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GradlewPatchTest {
    @Test
    fun `gradlew scripts retain required local patches`() {
        val rootDir = File(".").canonicalFile.let {
            if (File(it, "gradlew").isFile) it else it.parentFile
        }
        val gradlew = File(rootDir, "gradlew")
        val gradlewBat = File(rootDir, "gradlew.bat")

        assertTrue("gradlew must exist", gradlew.isFile)
        assertTrue("gradlew.bat must exist", gradlewBat.isFile)

        val gradlewText = gradlew.readText()
        val gradlewBatText = gradlewBat.readText()

        assertTrue(
            "gradlew must contain JAVA_HOME auto-discovery",
            gradlewText.contains("""export JAVA_HOME="${'$'}_candidate""""),
        )
        assertTrue(
            "gradlew must enforce UTF-8 file encoding",
            gradlewText.contains("-Dfile.encoding=UTF-8"),
        )
        assertTrue(
            "gradlew.bat must enforce UTF-8 file encoding",
            gradlewBatText.contains("-Dfile.encoding=UTF-8"),
        )
    }
}
