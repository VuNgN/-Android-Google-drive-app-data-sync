package com.example.googledrive

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * Helper class for Google Drive operations.
 * Handles authentication, file operations, and synchronization between local storage and Google Drive.
 */
class DriveHelper private constructor(
    private val context: Context,
    private val applicationName: String,
    private val webClientId: String
) {
    private var credentialManager: CredentialManager = CredentialManager.create(context)
    private var parentDataFolder = "appDataFolder"

    private fun setParentDataFolder(folderId: String) {
        parentDataFolder = folderId
    }

    /**
     * Checks if the user is authorized to access Google Drive.
     * If not authorized, requests authorization through the Google Sign-In flow.
     */
    fun checkDriveAuthorization(onCheckDriveAuthorizationListener: OnCheckDriveAuthorizationListener) {
        val requestedScopes: List<Scope> = listOf(Scope(DriveScopes.DRIVE_APPDATA))
        val authorizationRequest: AuthorizationRequest =
            AuthorizationRequest.builder().setRequestedScopes(requestedScopes).build()

        Identity.getAuthorizationClient(context).authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                onCheckDriveAuthorizationListener.onAuthorizationResult(authorizationResult)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to authorize", e)
                onCheckDriveAuthorizationListener.onAuthorizationFailure(e)
            }
    }

    /**
     * Handles the authorization result and builds a Drive instance.
     */
    fun handleDriveAuthorization(
        authorizationResult: AuthorizationResult, callback: (Drive) -> Unit = {}
    ) {
        Log.d(TAG, "Drive authorization successful: ${authorizationResult.accessToken}")
        val credential: Credential =
            GoogleCredential().setAccessToken(authorizationResult.accessToken)
        val drive = Drive.Builder(
            NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
        ).setApplicationName(applicationName).build()

        callback(drive)
    }

    /**
     * Initiates Google Sign-In process using Credential Manager.
     */
    suspend fun signInWithGoogleButton(onSignInListener: OnSignInListener) {
        val signInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(webClientId).setNonce(generateNonce()).build()

        val request =
            GetCredentialRequest.Builder().addCredentialOption(signInWithGoogleOption).build()

        try {
            Log.d(TAG, "Getting user info...")
            val result = credentialManager.getCredential(
                request = request,
                context = context,
            )
            withContext(Dispatchers.Main) {
                handleSignIn(result, onSignInListener)
            }
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Failed to get user info: ", e)
            withContext(Dispatchers.Main) {
                onSignInListener.onSignInFailure(e)
            }
        }
    }

    /**
     * Signs out the user and clears stored credentials.
     */
    suspend fun signOut(onSignOutListener: OnSignOutListener) {
        try {
            val request = ClearCredentialStateRequest()
            credentialManager.clearCredentialState(request)
            context.getSharedPreferences("aaaa", MODE_PRIVATE).edit {
                clear()
            }
            Log.d(TAG, "Signed out successfully.")
            withContext(Dispatchers.Main) {
                onSignOutListener.onSignOutSuccess()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out", e)
            withContext(Dispatchers.Main) {
                onSignOutListener.onSignOutFailure(e)
            }
        }
    }

    /**
     * Handles the sign-in result and processes user information.
     */
    private fun handleSignIn(result: GetCredentialResponse, onSignInListener: OnSignInListener) {
        result.credential.let { credential ->
            when (credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val googleIdTokenCredential =
                                GoogleIdTokenCredential.createFrom(credential.data)
                            val email = googleIdTokenCredential.id
                            context.getSharedPreferences("aaaa", MODE_PRIVATE).edit {
                                putString("email", email)
                            }
                            onSignInListener.onSignInSuccess(email)
                        } catch (e: GoogleIdTokenParsingException) {
                            Log.e(TAG, "Received an invalid google id token response", e)
                            onSignInListener.onSignInFailure(e)
                        }
                    }
                }
                else -> {
                    Log.e(TAG, "Unexpected type of credential")
                    onSignInListener.onSignInFailure(
                        Exception("Unexpected type of credential: ${credential.type}")
                    )
                }
            }
        }
    }

    /**
     * Generates a random nonce for Google Sign-In security.
     */
    private fun generateNonce(): String {
        val random = SecureRandom()
        val nonceBytes = ByteArray(16)
        random.nextBytes(nonceBytes)
        return Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /**
     * Compares local and Drive files to determine sync actions needed.
     */
    private suspend fun compareLocalAndDriveFiles(
        drive: Drive,
        localFiles: List<java.io.File>
    ): SyncDiff {
        val driveFiles = drive.files().list()
            .setSpaces(parentDataFolder)
            .setFields("files(id, name, modifiedTime)")
            .execute()
            .files

        val localFileMap = localFiles.associateBy { it.name }
        val driveFileMap = driveFiles.associateBy { it.name }

        val filesToUpload = mutableListOf<java.io.File>()
        val filesToDelete = mutableListOf<File>()
        val filesToKeep = mutableListOf<File>()

        // Check files to upload (new or modified)
        localFiles.forEach { localFile ->
            val driveFile = driveFileMap[localFile.name]
            if (driveFile == null) {
                filesToUpload.add(localFile)
            } else {
                val localModified = localFile.lastModified()
                val driveModified = driveFile.modifiedTime?.value ?: 0
                if (localModified > driveModified) {
                    filesToUpload.add(localFile)
                } else {
                    filesToKeep.add(driveFile)
                }
            }
        }

        // Check files to delete (not in local)
        driveFiles.forEach { driveFile ->
            if (!localFileMap.containsKey(driveFile.name)) {
                filesToDelete.add(driveFile)
            }
        }

        return SyncDiff(filesToUpload, filesToDelete, filesToKeep)
    }

    /**
     * Synchronizes local files to Google Drive.
     * Only deletes files not in local and uploads new/modified files.
     */
    suspend fun syncToDrive(
        drive: Drive,
        localFiles: List<java.io.File>,
        onProgress: (step: SyncStep, fileName: String?, success: Boolean, error: Exception?) -> Unit
    ) {
        try {
            val diff = compareLocalAndDriveFiles(drive, localFiles)

            // Delete files not in local
            diff.filesToDelete.forEach { driveFile ->
                try {
                    drive.files().delete(driveFile.id).execute()
                    onProgress(SyncStep.DELETE, driveFile.name, true, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting file from Drive: ${driveFile.name}", e)
                    onProgress(SyncStep.DELETE, driveFile.name, false, e)
                }
            }

            // Upload new or modified files
            diff.filesToUpload.forEach { localFile ->
                try {
                    val uri = Uri.fromFile(localFile)
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: localFile.inputStream()
                    val fileContent = inputStream.use { it.readBytes() }
                    val fileMetadata = File()
                    fileMetadata.setName(localFile.name)
                    fileMetadata.setParents(listOf(parentDataFolder))
                    val mediaContent =
                        ByteArrayContent.fromString("application/octet-stream", String(fileContent))
                    drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
                    onProgress(SyncStep.UPLOAD, localFile.name, true, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload file: ${localFile.name}", e)
                    onProgress(SyncStep.UPLOAD, localFile.name, false, e)
                }
            }

            // Report unchanged files
            diff.filesToKeep.forEach { driveFile ->
                onProgress(SyncStep.KEEP, driveFile.name, true, null)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during sync to Drive", e)
            onProgress(SyncStep.ERROR, null, false, e)
        }
    }

    /**
     * Synchronizes files from Google Drive to local storage.
     * Only downloads new or modified files from Drive.
     */
    suspend fun syncFromDrive(
        drive: Drive,
        localFiles: List<java.io.File>,
        downloadDir: java.io.File,
        onProgress: (step: SyncStep, fileName: String, success: Boolean, error: Exception?) -> Unit
    ) {
        try {
            val localFileMap = localFiles.associateBy { it.name }
            val driveFiles = drive.files().list()
                .setSpaces(parentDataFolder)
                .setFields("files(id, name, modifiedTime)")
                .execute()
                .files

            driveFiles.forEach { driveFile ->
                val localFile = localFileMap[driveFile.name]
                val shouldDownload = when {
                    localFile == null -> true // New file on Drive
                    (driveFile.modifiedTime?.value
                        ?: 0) > localFile.lastModified() -> true // File modified on Drive
                    else -> false // Files are identical
                }

                if (shouldDownload) {
                    try {
                        val outputFile = java.io.File(downloadDir, driveFile.name)
                        drive.files().get(driveFile.id).executeMediaAsInputStream()
                            .use { inputStream ->
                                outputFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        onProgress(SyncStep.DOWNLOAD, driveFile.name, true, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download file: ${driveFile.name}", e)
                        onProgress(SyncStep.DOWNLOAD, driveFile.name, false, e)
                    }
                } else {
                    onProgress(SyncStep.KEEP, driveFile.name, true, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync from Drive", e)
            onProgress(SyncStep.ERROR, "", false, e)
        }
    }

    interface OnCheckDriveAuthorizationListener {
        fun onAuthorizationResult(authorizationResult: AuthorizationResult)
        fun onAuthorizationFailure(e: Exception)
    }

    interface OnSignInListener {
        fun onSignInSuccess(email: String)
        fun onSignInFailure(e: Exception)
    }

    interface OnSignOutListener {
        fun onSignOutSuccess()
        fun onSignOutFailure(e: Exception)
    }

    enum class SyncStep {
        DELETE,     // Delete file from Drive
        UPLOAD,     // Upload file to Drive
        KEEP,       // Keep file unchanged
        DOWNLOAD,   // Download file from Drive
        ERROR       // Error during sync
    }

    data class SyncDiff(
        val filesToUpload: List<java.io.File>, // New or modified local files
        val filesToDelete: List<File>, // Files on Drive not in local
        val filesToKeep: List<File> // Files identical in both locations
    )

    companion object {
        private const val TAG = "DriveHelper"
    }

    class Builder(private val context: Context) {
        private var parentDataFolder: String = "appDataFolder"
        private var applicationName: String = "AppName"
        private var webClientId: String = ""

        fun setParentDataFolder(folderId: String): Builder {
            this.parentDataFolder = folderId
            return this
        }

        fun setApplicationName(appName: String): Builder {
            this.applicationName = appName
            return this
        }

        fun setWebClientId(clientId: String): Builder {
            this.webClientId = clientId
            return this
        }

        fun build(): DriveHelper {
            require(webClientId.isNotEmpty()) { "Web Client ID must be set" }
            val driveHelper = DriveHelper(context, applicationName, webClientId)
            driveHelper.setParentDataFolder(parentDataFolder)
            return driveHelper
        }
    }
}