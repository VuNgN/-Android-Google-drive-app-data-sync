package com.example.googledrive

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.api.services.drive.Drive
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File as LocalFile

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityTest {
    private lateinit var activity: MainActivity
    private lateinit var driveHelper: DriveHelper
    private lateinit var mockSignInClient: SignInClient
    private lateinit var mockDrive: Drive
    private val testDispatcher = TestCoroutineDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(Identity)
        mockSignInClient = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)
        driveHelper = mockk(relaxed = true)
        every { Identity.getSignInClient(any()) } returns mockSignInClient

        activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .get()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
        unmockkAll()
    }

    @Test
    fun `test initialization`() {
        // Assert
        assert(activity.findViewById<Button>(R.id.signInButton) != null)
        assert(activity.findViewById<Button>(R.id.signOutButton) != null)
        assert(activity.findViewById<Button>(R.id.syncToDriveButton) != null)
        assert(activity.findViewById<Button>(R.id.syncFromDriveButton) != null)
        assert(activity.findViewById<Button>(R.id.selectFileButton) != null)
        assert(activity.findViewById<TextView>(R.id.statusTextView) != null)
        assert(activity.findViewById<TextView>(R.id.userInfoTextView) != null)
        assert(activity.findViewById<RecyclerView>(R.id.fileListRecyclerView) != null)
        assert(activity.findViewById<ProgressBar>(R.id.loadingProgressBar) != null)
        assert(activity.findViewById<TextView>(R.id.loadingTextView) != null)
    }

    @Test
    fun `test sign in button click`() {
        // Arrange
        val signInButton = activity.findViewById<Button>(R.id.signInButton)
        coEvery { driveHelper.signInWithGoogleButton(any()) } answers {
            firstArg<DriveHelper.OnSignInListener>().onSignInSuccess("test@example.com")
        }

        // Act
        signInButton.performClick()

        // Assert
        coVerify { driveHelper.signInWithGoogleButton(any()) }
        assert(activity.findViewById<TextView>(R.id.statusTextView).text.toString() == "Signed in successfully!")
        assert(activity.findViewById<TextView>(R.id.userInfoTextView).text.toString() == "Email: test@example.com")
    }

    @Test
    fun `test sign out button click`() {
        // Arrange
        val signOutButton = activity.findViewById<Button>(R.id.signOutButton)
        coEvery { driveHelper.signOut(any()) } answers {
            firstArg<DriveHelper.OnSignOutListener>().onSignOutSuccess()
        }

        // Act
        signOutButton.performClick()

        // Assert
        coVerify { driveHelper.signOut(any()) }
        assert(activity.findViewById<TextView>(R.id.statusTextView).text.toString() == "You have been signed out.")
    }

    @Test
    fun `test sync to drive button click`() {
        // Arrange
        val syncToDriveButton = activity.findViewById<Button>(R.id.syncToDriveButton)
        val mockAuthorizationResult = mockk<AuthorizationResult>()
        val mockPendingIntent = mockk<PendingIntent>()
        val mockIntentSender = mockk<IntentSender>()

        every { mockAuthorizationResult.hasResolution() } returns true
        every { mockAuthorizationResult.pendingIntent } returns mockPendingIntent
        every { mockPendingIntent.intentSender } returns mockIntentSender

        coEvery { driveHelper.checkDriveAuthorization(any()) } answers {
            firstArg<DriveHelper.OnCheckDriveAuthorizationListener>().onAuthorizationResult(mockAuthorizationResult)
        }

        // Act
        syncToDriveButton.performClick()

        // Assert
        coVerify { driveHelper.checkDriveAuthorization(any()) }
    }

    @Test
    fun `test sync from drive button click`() {
        // Arrange
        val syncFromDriveButton = activity.findViewById<Button>(R.id.syncFromDriveButton)
        val mockAuthorizationResult = mockk<AuthorizationResult>()
        val mockPendingIntent = mockk<PendingIntent>()
        val mockIntentSender = mockk<IntentSender>()

        every { mockAuthorizationResult.hasResolution() } returns true
        every { mockAuthorizationResult.pendingIntent } returns mockPendingIntent
        every { mockPendingIntent.intentSender } returns mockIntentSender

        coEvery { driveHelper.checkDriveAuthorization(any()) } answers {
            firstArg<DriveHelper.OnCheckDriveAuthorizationListener>().onAuthorizationResult(mockAuthorizationResult)
        }

        // Act
        syncFromDriveButton.performClick()

        // Assert
        coVerify { driveHelper.checkDriveAuthorization(any()) }
    }

    @Test
    fun `test select file button click`() {
        // Arrange
        val selectFileButton = activity.findViewById<Button>(R.id.selectFileButton)
        val mockUri = mockk<Uri>()
        val mockContentResolver = mockk<android.content.ContentResolver>()
        val mockCursor = mockk<android.database.Cursor>()

        every { activity.contentResolver } returns mockContentResolver
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns mockCursor
        every { mockCursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) } returns 0
        every { mockCursor.getString(any()) } returns "test.txt"
        every { mockCursor.moveToFirst() } returns true
        every { mockContentResolver.openInputStream(any()) } returns mockk(relaxed = true)

        // Act
        selectFileButton.performClick()

        // Assert
        // Verify that the file picker was launched
        // Note: We can't directly verify the ActivityResultLauncher, but we can verify the UI state
        assert(activity.findViewById<Button>(R.id.selectFileButton).isEnabled)
    }

    @Test
    fun `test show loading`() {
        // Act
        activity.showLoading("Test Loading")

        // Assert
        assert(activity.findViewById<ProgressBar>(R.id.loadingProgressBar).visibility == View.VISIBLE)
        assert(activity.findViewById<TextView>(R.id.loadingTextView).visibility == View.VISIBLE)
        assert(activity.findViewById<TextView>(R.id.loadingTextView).text.toString() == "Test Loading")
        assert(!activity.findViewById<Button>(R.id.syncToDriveButton).isEnabled)
        assert(!activity.findViewById<Button>(R.id.syncFromDriveButton).isEnabled)
        assert(!activity.findViewById<Button>(R.id.selectFileButton).isEnabled)
    }

    @Test
    fun `test hide loading`() {
        // Act
        activity.hideLoading()

        // Assert
        assert(activity.findViewById<ProgressBar>(R.id.loadingProgressBar).visibility == View.GONE)
        assert(activity.findViewById<TextView>(R.id.loadingTextView).visibility == View.GONE)
        assert(activity.findViewById<Button>(R.id.syncToDriveButton).isEnabled)
        assert(activity.findViewById<Button>(R.id.syncFromDriveButton).isEnabled)
        assert(activity.findViewById<Button>(R.id.selectFileButton).isEnabled)
    }

    @Test
    fun `test update status`() {
        // Act
        activity.updateStatus("Test Status")

        // Assert
        assert(activity.findViewById<TextView>(R.id.statusTextView).text.toString() == "Test Status")
    }
} 