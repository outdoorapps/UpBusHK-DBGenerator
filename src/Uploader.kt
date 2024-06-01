import Uploader.Companion.upload
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.StorageClient
import com.google.firebase.database.FirebaseDatabase
import org.apache.log4j.BasicConfigurator
import util.Utils.Companion.dbNameRegex
import util.Utils.Companion.execute
import util.Utils.Companion.extractVersionNumber
import util.Utils.Companion.getDatabaseFile
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class Uploader {
    companion object {
        private const val FIREBASE_CREDENTIALS = "secrets/upbushk-firebase-adminsdk-3obkp-773ff4b827.json"
        private const val DATABASE_URL = "https://upbushk-default-rtdb.asia-southeast1.firebasedatabase.app"
        private const val BUCKET_NAME = "upbushk.appspot.com"
        private const val CLIENT_DATABASE_INFO_PATH = "public_resources/client_database"

        fun upload(databaseFile: File, databaseVersion: String) {
            val serviceAccount = FileInputStream(FIREBASE_CREDENTIALS)
            val options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl(DATABASE_URL).setStorageBucket(BUCKET_NAME).build()
            FirebaseApp.initializeApp(options)

            val bucket = StorageClient.getInstance().bucket()
            val blobId = BlobId.of(bucket.name, databaseFile.name)
            val blobInfo =
                BlobInfo.newBuilder(blobId).setContentType(Files.probeContentType(databaseFile.toPath())).build()
            val precondition = if (bucket.storage.get(bucket.name, databaseFile.name) == null) {
                // For a target object that does not yet exist, set the DoesNotExist precondition.
                // This will cause the request to fail if the object is created before the request runs.
                Storage.BlobWriteOption.doesNotExist()
            } else {
                // If the destination already exists in your bucket, instead set a generation-match
                // precondition. This will cause the request to fail if the existing object's generation
                // changes before the request runs.
                Storage.BlobWriteOption.generationMatch(bucket.storage.get(bucket.name, databaseFile.name).generation)
            }

            // 1. Upload
            var uploadSucceeded = false
            execute("Uploading ${databaseFile.name}...", printOnNextLine = true) {
                try {
                    databaseFile.inputStream().use {
                        bucket.storage.createFrom(blobInfo, it, precondition)
                    }
                    uploadSucceeded = true
                } catch (e: Exception) {
                    println(e)
                }
                if (uploadSucceeded) println("- Upload ${databaseFile.name} succeeded")
            }

            // 2. Mark changes on realtime database
            var markChangesSucceeded = false
            if (uploadSucceeded) {
                execute("Marking database update on Firebase...", printOnNextLine = true) {
                    val ref = FirebaseDatabase.getInstance().getReference(CLIENT_DATABASE_INFO_PATH)
                    val values = mapOf("version" to databaseVersion, "min_compatible_app_version" to dbMinAppVersion)
                    val future = ref.updateChildrenAsync(values)
                    try {
                        future.get(10, TimeUnit.SECONDS)
                        markChangesSucceeded = true
                    } catch (e: Exception) {
                        println("- Marking database update on Firebase failed: $e")
                    }
                    if (markChangesSucceeded) println("- Marking database update on Firebase succeeded")
                }
            }

            // 3. Remove old database files
            var deleteSucceeded = false
            if (uploadSucceeded && markChangesSucceeded) {
                val filesToBeDeleted = mutableListOf<BlobId>()
                bucket.storage.list(bucket.name).values.forEach {
                    if (it.name.matches(dbNameRegex) && it.name != databaseFile.name) {
                        filesToBeDeleted.add(it.blobId)
                    }
                }

                if (filesToBeDeleted.isNotEmpty()) {
                    println("Previous database files to be deleted:")
                    filesToBeDeleted.forEach { println("- ${it.name}") }
                    execute("Deleting previous database files from Firebase...", printOnNextLine = true) {
                        try {
                            bucket.storage.delete(filesToBeDeleted)
                            deleteSucceeded = true
                        } catch (e: Exception) {
                            println(e)
                        }
                        if (deleteSucceeded) println("- Delete previous database files succeeded")
                    }
                } else {
                    deleteSucceeded = true
                }
            }

            if (uploadSucceeded && markChangesSucceeded && deleteSucceeded) println("All Firebase operations succeeded")
        }
    }
}

fun main() {
    BasicConfigurator.configure()
    val dbFile = getDatabaseFile()
    if (dbFile != null) {
        val version = extractVersionNumber(dbFile)
        println("Database version found: $version")
        upload(dbFile, version)
    } else {
        println("Database file not found, nothing is uploaded")
    }
}