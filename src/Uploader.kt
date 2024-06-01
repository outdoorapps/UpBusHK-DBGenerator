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
import util.Utils
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
            Utils.execute("Uploading ${databaseFile.name}...") {
                try {
                    databaseFile.inputStream().use {
                        bucket.storage.createFrom(blobInfo, it, precondition)
                    }
                } catch (e: Exception) {
                    println(e)
                }
            }

            Utils.execute("Marking database update on Firebase...") {
                val ref = FirebaseDatabase.getInstance().getReference(CLIENT_DATABASE_INFO_PATH)
                val values = mapOf("version" to databaseVersion, "min_compatible_app_version" to dbMinAppVersion)
                val future = ref.updateChildrenAsync(values)
                try {
                    future.get(10, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    println("Marking database update on Firebase failed: $e")
                }
            }
        }
    }
}

fun main() {
    // todo select the latest version to upload and cleanup order versions
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