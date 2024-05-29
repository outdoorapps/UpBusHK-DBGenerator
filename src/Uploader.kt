import Uploader.Companion.upload
import com.beust.klaxon.Klaxon
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.StorageClient
import com.google.firebase.database.FirebaseDatabase
import data.Database
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.log4j.BasicConfigurator
import org.tukaani.xz.XZInputStream
import util.Utils
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit


class Uploader {
    companion object {
        private const val FIREBASE_CREDENTIALS = "secrets/upbushk-firebase-adminsdk-3obkp-773ff4b827.json"
        private const val DATABASE_URL = "https://upbushk-default-rtdb.asia-southeast1.firebasedatabase.app"
        private const val BUCKET_NAME = "upbushk.appspot.com"
        private const val PUBLIC_RESOURCES_PATH = "public_resources"

        fun upload(databaseFile: File, clientDatabaseVersion: String) {
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
                Storage.BlobWriteOption.generationMatch(
                    bucket.storage.get(bucket.name, databaseFile.name).generation
                )
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

            Utils.execute("Marking the new client database version...") {
                val ref = FirebaseDatabase.getInstance().getReference(PUBLIC_RESOURCES_PATH)
                val version = mapOf("client_database_version" to clientDatabaseVersion)
                // todo add min app version compatible
                val future = ref.setValueAsync(version)
                try {
                    future.get(10, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    println("Marking new database version failed: $e")
                }
            }
        }
    }
}

fun main() {
    var version: String? = null
    BasicConfigurator.configure()
    Utils.execute("Loading RSDatabase...") {
        val dbFile = File(Utils.getArchivePath())
        dbFile.inputStream().use { input ->
            XZInputStream(input).use { xzInput ->
                TarArchiveInputStream(xzInput).use { tarStream ->
                    tarStream.nextTarEntry
                    val jsonString = tarStream.bufferedReader().use { it.readText() }
                    val data = Klaxon().parse<Database>(jsonString)
                    version = data?.version
                }
            }
        }
    }

    if (version != null) {
        println("Uploading database version: $version")
        upload(File(Utils.getArchivePath()), version!!)
    } else {
        println("Version is null, not uploading")
    }
}