package com.example.googledrive

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.BeginSignInResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File as LocalFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DriveHelperTest {
    private lateinit var context: Context
    private lateinit var driveHelper: DriveHelper
    private lateinit var mockSignInClient: SignInClient
    private lateinit var mockDrive: Drive
    private lateinit var mockDriveFiles: Drive.Files
    private lateinit var mockDriveFilesList: Drive.Files.List
    private lateinit var mockDriveFilesCreate: Drive.Files.Create
    private lateinit var mockDriveFilesDelete: Drive.Files.Delete
    private lateinit var mockDriveFilesGet: Drive.Files.Get

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockSignInClient = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)
        mockDriveFiles = mockk(relaxed = true)
        mockDriveFilesList = mockk(relaxed = true)
        mockDriveFilesCreate = mockk(relaxed = true)
        mockDriveFilesDelete = mockk(relaxed = true)
        mockDriveFilesGet = mockk(relaxed = true)

        mockkObject(Identity)
        every { Identity.getSignInClient(any()) } returns mockSignInClient

        driveHelper = DriveHelper.Builder(context)
            .setApplicationName("TestApp")
            .build()
    }

    @Test
    fun `test sign in with Google success`() = runBlocking {
        // Arrange
        val mockBeginSignInResult = mockk<BeginSignInResult>()
        val mockPendingIntent = mockk<android.app.PendingIntent>()
        val mockIntentSender = mockk<android.content.IntentSender>()

        coEvery { mockSignInClient.beginSignIn(any()) } returns mockBeginSignInResult
        every { mockBeginSignInResult.pendingIntent } returns mockPendingIntent
        every { mockPendingIntent.intentSender } returns mockIntentSender

        var signInSuccess = false
        var signInEmail = ""

        // Act
        driveHelper.signInWithGoogleButton(object : DriveHelper.OnSignInListener {
            override fun onSignInSuccess(email: String) {
                signInSuccess = true
                signInEmail = email
            }

            override fun onSignInFailure(e: Exception) {
                // Not called in success case
            }
        })

        // Assert
        assert(signInSuccess)
        assert(signInEmail.isNotEmpty())
    }

    @Test
    fun `test sign in with Google failure`() = runBlocking {
        // Arrange
        coEvery { mockSignInClient.beginSignIn(any()) } throws Exception("Sign in failed")

        var signInFailure = false
        var errorMessage = ""

        // Act
        driveHelper.signInWithGoogleButton(object : DriveHelper.OnSignInListener {
            override fun onSignInSuccess(email: String) {
                // Not called in failure case
            }

            override fun onSignInFailure(e: Exception) {
                signInFailure = true
                errorMessage = e.message ?: ""
            }
        })

        // Assert
        assert(signInFailure)
        assert(errorMessage == "Sign in failed")
    }

    @Test
    fun `test sync to Drive success`() = runBlocking {
        // Arrange
        val localFiles = listOf(
            LocalFile("test1.txt"),
            LocalFile("test2.txt")
        )
        val driveFiles = listOf(
            File().setName("test1.txt").setId("id1"),
            File().setName("test3.txt").setId("id3")
        )

        every { mockDrive.files() } returns mockDriveFiles
        every { mockDriveFiles.list() } returns mockDriveFilesList
        every { mockDriveFilesList.execute() } returns com.google.api.services.drive.model.FileList().setFiles(driveFiles)
        every { mockDriveFiles.create(any(), any()) } returns mockDriveFilesCreate
        every { mockDriveFilesCreate.execute() } returns File()
        every { mockDriveFiles.delete(any()) } returns mockDriveFilesDelete
        every { mockDriveFilesDelete.execute() } returns Unit

        var deleteCalled = false
        var uploadCalled = false
        var keepCalled = false
        var errorCalled = false

        // Act
        driveHelper.syncToDrive(mockDrive, localFiles) { step, fileName, success, error ->
            when (step) {
                DriveHelper.SyncStep.DELETE -> deleteCalled = true
                DriveHelper.SyncStep.UPLOAD -> uploadCalled = true
                DriveHelper.SyncStep.KEEP -> keepCalled = true
                DriveHelper.SyncStep.ERROR -> errorCalled = true
                else -> {}
            }
        }

        // Assert
        assert(deleteCalled)
        assert(uploadCalled)
        assert(!errorCalled)
    }

    @Test
    fun `test sync from Drive success`() = runBlocking {
        // Arrange
        val localFiles = listOf(
            LocalFile("test1.txt"),
            LocalFile("test2.txt")
        )
        val driveFiles = listOf(
            File().setName("test1.txt").setId("id1"),
            File().setName("test3.txt").setId("id3")
        )
        val uploadDir = LocalFile("upload")

        every { mockDrive.files() } returns mockDriveFiles
        every { mockDriveFiles.list() } returns mockDriveFilesList
        every { mockDriveFilesList.execute() } returns com.google.api.services.drive.model.FileList().setFiles(driveFiles)
        every { mockDriveFiles.get(any()) } returns mockDriveFilesGet
        every { mockDriveFilesGet.executeMediaAsInputStream() } returns mockk(relaxed = true)

        var downloadCalled = false
        var keepCalled = false
        var errorCalled = false

        // Act
        driveHelper.syncFromDrive(mockDrive, localFiles, uploadDir) { step, fileName, success, error ->
            when (step) {
                DriveHelper.SyncStep.DOWNLOAD -> downloadCalled = true
                DriveHelper.SyncStep.KEEP -> keepCalled = true
                DriveHelper.SyncStep.ERROR -> errorCalled = true
                else -> {}
            }
        }

        // Assert
        assert(downloadCalled)
        assert(keepCalled)
        assert(!errorCalled)
    }

    @Test
    fun `test sign out success`() = runBlocking {
        // Arrange
        coEvery { mockSignInClient.signOut() } returns mockk()

        var signOutSuccess = false
        var signOutFailure = false

        // Act
        driveHelper.signOut(object : DriveHelper.OnSignOutListener {
            override fun onSignOutSuccess() {
                signOutSuccess = true
            }

            override fun onSignOutFailure(e: Exception) {
                signOutFailure = true
            }
        })

        // Assert
        assert(signOutSuccess)
        assert(!signOutFailure)
    }

    @Test
    fun `test sign out failure`() = runBlocking {
        // Arrange
        coEvery { mockSignInClient.signOut() } throws Exception("Sign out failed")

        var signOutSuccess = false
        var signOutFailure = false
        var errorMessage = ""

        // Act
        driveHelper.signOut(object : DriveHelper.OnSignOutListener {
            override fun onSignOutSuccess() {
                signOutSuccess = true
            }

            override fun onSignOutFailure(e: Exception) {
                signOutFailure = true
                errorMessage = e.message ?: ""
            }
        })

        // Assert
        assert(!signOutSuccess)
        assert(signOutFailure)
        assert(errorMessage == "Sign out failed")
    }
} 