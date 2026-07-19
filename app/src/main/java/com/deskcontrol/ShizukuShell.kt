package com.deskcontrol

import rikka.shizuku.Shizuku
import android.content.pm.PackageManager
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuShell {
    data class Result(val exitCode: Int, val output: String, val error: String)

    fun isAlive(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Executes a command using Shizuku.
     * Centralized to ensure consistent reflection and error handling.
     */
    fun run(vararg args: String): Result {
        if (!isAlive()) {
            return Result(-1, "", "Shizuku not connected or permission denied")
        }

        return try {
            val process = newShizukuProcess(args)
                ?: return Result(-1, "", "Failed to create Shizuku process (reflection error)")
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val outThread = Thread {
                try {
                    var line: String?
                    while (outReader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                } catch (e: Exception) {}
            }
            
            val errThread = Thread {
                try {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        error.append(line).append("\n")
                    }
                } catch (e: Exception) {}
            }
            
            outThread.start()
            errThread.start()
            
            val exitCode = process.waitFor()
            outThread.join(500)
            errThread.join(500)
            
            Result(exitCode, output.toString().trim(), error.toString().trim())
        } catch (e: Throwable) {
            Result(-1, "", e.message ?: "unknown error during shizuku execution")
        }
    }

    /**
     * Special helper for 'settings' commands used for enabling accessibility.
     */
    fun runSettingsCommand(key: String, value: String): Result {
        return run("settings", "put", "secure", key, value)
    }

    private fun newShizukuProcess(args: Array<out String>): Process? {
        return try {
            // Shizuku.newProcess(args, env, workingDir)
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, args, null, null) as? Process
        } catch (e: NoSuchMethodException) {
            // Fallback for different library versions if necessary
            try {
                val method = Shizuku::class.java.methods.firstOrNull { 
                    it.name == "newProcess" && it.parameterTypes.size == 3 
                }
                method?.isAccessible = true
                method?.invoke(null, args, null, null) as? Process
            } catch (e2: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

