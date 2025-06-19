package com.example.googledrive

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File as LocalFile

/**
 * Main activity for Google Drive integration app.
 * Handles file synchronization between local storage and Google Drive.
 */
class MainActivity : AppCompatActivity() {
    // UI Components
    private lateinit var driveHelper: DriveHelper
    private lateinit var signInButton: Button
    private lateinit var signOutButton: Button
    private lateinit var syncToDriveButton: Button
    private lateinit var syncFromDriveButton: Button
    private lateinit var selectFileButton: Button
    private lateinit var viewLogButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var userInfoTextView: TextView
    private lateinit var fileListRecyclerView: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var loadingTextView: TextView
    private lateinit var loadingLayout: LinearLayout

    // Data
    private val localFiles = MutableLiveData<List<LocalFileInfo>>(emptyList())
    private var uri: Uri? = null
    private lateinit var uploadDir: LocalFile

    /**
     * Data class representing a local file with its metadata
     */
    data class LocalFileInfo(
        val name: String,
        val path: String,
        val lastModified: Long
    )

    // Activity result launchers
    private val startActivityForResult = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val authorizationResult =
                Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data)
            handleDriveAuthorization(authorizationResult)
        } else {
            Log.e(TAG, "Authorization failed or was canceled")
            updateUi(isSignedIn = false)
        }
    }

    private val pickMediaForSync = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Log.d(TAG, "Selected file URI: $uri")
            this.uri = uri
            saveFileLocally(uri)
        } else {
            Log.d(TAG, "No file selected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        setupWindowInsets()
        initializeApp()
    }

    /**
     * Sets up window insets for edge-to-edge display
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Initializes the app components and directories
     */
    private fun initializeApp() {
        // Initialize upload directory
        uploadDir = LocalFile(filesDir, UPLOAD_DIR_NAME)
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }

        driveHelper = DriveHelper.Builder(this)
            .setApplicationName(getString(R.string.app_name))
            .setWebClientId(WEB_CLIENT_ID)
            .build()

        initializeViews()
        setupClickListeners()
        setupRecyclerView()
        loadLocalFiles()
        checkSignInStatus()
    }

    /**
     * Checks if user is already signed in
     */
    private fun checkSignInStatus() {
        val email = getSharedPreferences("aaaa", MODE_PRIVATE).getString("email", null)
        if (email != null) {
            statusTextView.text = "Signed in successfully!"
            userInfoTextView.text = "Email: $email"
            updateUi(isSignedIn = true)
        } else {
            statusTextView.text = "Not signed in"
            userInfoTextView.text = ""
            updateUi(isSignedIn = false)
        }
    }

    /**
     * Initializes all UI components
     */
    private fun initializeViews() {
        signInButton = findViewById(R.id.signInButton)
        signOutButton = findViewById(R.id.signOutButton)
        syncToDriveButton = findViewById(R.id.syncToDriveButton)
        syncFromDriveButton = findViewById(R.id.syncFromDriveButton)
        selectFileButton = findViewById(R.id.selectFileButton)
        viewLogButton = findViewById(R.id.viewLogButton)
        statusTextView = findViewById(R.id.statusTextView)
        userInfoTextView = findViewById(R.id.userInfoTextView)
        fileListRecyclerView = findViewById(R.id.fileListRecyclerView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        loadingTextView = findViewById(R.id.loadingTextView)
        loadingLayout = findViewById(R.id.loadingOverlay)
    }

    /**
     * Sets up RecyclerView with file adapter
     */
    private fun setupRecyclerView() {
        fileAdapter = FileAdapter { localFile ->
            deleteLocalFile(localFile)
        }
        fileListRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fileAdapter
        }

        localFiles.observe(this) { files ->
            fileAdapter.submitList(files)
        }
    }

    /**
     * Sets up click listeners for all buttons
     */
    private fun setupClickListeners() {
        selectFileButton.setOnClickListener {
            pickMediaForSync.launch("*/*")
        }

        syncToDriveButton.setOnClickListener {
            lifecycleScope.launch {
                syncToDrive()
            }
        }

        syncFromDriveButton.setOnClickListener {
            lifecycleScope.launch {
                syncFromDrive()
            }
        }

        signInButton.setOnClickListener {
            lifecycleScope.launch {
                signInWithGoogle()
            }
        }

        signOutButton.setOnClickListener {
            lifecycleScope.launch {
                signOut()
            }
        }

        viewLogButton.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    /**
     * Shows loading indicator with custom message
     */
    private fun showLoading(message: String = "Syncing...") {
        runOnUiThread {
            loadingTextView.text = message
            loadingLayout.visibility = View.VISIBLE
            syncToDriveButton.isEnabled = false
            syncFromDriveButton.isEnabled = false
            selectFileButton.isEnabled = false
        }
    }

    /**
     * Hides loading indicator and re-enables buttons
     */
    private fun hideLoading() {
        runOnUiThread {
            loadingLayout.visibility = View.GONE
            syncToDriveButton.isEnabled = true
            syncFromDriveButton.isEnabled = true
            selectFileButton.isEnabled = true
        }
    }

    /**
     * Updates status message on UI
     */
    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
        }
    }

    /**
     * Handles Google Drive authorization result
     */
    private fun handleDriveAuthorization(authorizationResult: AuthorizationResult) {
        driveHelper.handleDriveAuthorization(authorizationResult) { drive: Drive ->
            lifecycleScope.launch(Dispatchers.IO) {
                showLoading("Uploading files to Drive...")
                try {
                    val localFileList = localFiles.value?.map { LocalFile(it.path) } ?: emptyList()
                    driveHelper.syncToDrive(
                        drive,
                        localFileList
                    ) { step, fileName, success, error ->
                        when (step) {
                            DriveHelper.SyncStep.DELETE -> {
                                if (success) {
                                    Log.d(TAG, "Deleted file from Drive: $fileName")
                                } else {
                                    Log.e(TAG, "Failed to delete file: $fileName", error)
                                }
                            }

                            DriveHelper.SyncStep.UPLOAD -> {
                                if (success) {
                                    Log.d(TAG, "Uploaded file to Drive: $fileName")
                                } else {
                                    Log.e(TAG, "Failed to upload file: $fileName", error)
                                }
                            }

                            DriveHelper.SyncStep.KEEP -> {
                                Log.d(TAG, "Kept file on Drive: $fileName")
                            }

                            DriveHelper.SyncStep.ERROR -> {
                                Log.e(TAG, "Error during sync: ${error?.message}")
                                updateStatus("Sync failed: ${error?.message}")
                            }

                            else -> {
                                Log.w(TAG, "Unknown sync step: $step")
                            }
                        }
                    }
                    hideLoading()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during sync to Drive", e)
                    hideLoading()
                    updateStatus("Sync failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Handles downloading files from Google Drive
     */
    private fun handleDriveDownload(authorizationResult: AuthorizationResult) {
        driveHelper.handleDriveAuthorization(authorizationResult) { drive: Drive ->
            lifecycleScope.launch(Dispatchers.IO) {
                showLoading("Downloading files from Drive...")
                try {
                    val localFileList = localFiles.value?.map { LocalFile(it.path) } ?: emptyList()
                    driveHelper.syncFromDrive(
                        drive,
                        localFileList,
                        uploadDir
                    ) { step, fileName, success, error ->
                        when (step) {
                            DriveHelper.SyncStep.DOWNLOAD -> {
                                if (success) {
                                    Log.d(TAG, "Downloaded file from Drive: $fileName")
                                } else {
                                    Log.e(TAG, "Failed to download file: $fileName", error)
                                }
                            }

                            DriveHelper.SyncStep.KEEP -> {
                                Log.d(TAG, "Kept file: $fileName")
                            }

                            DriveHelper.SyncStep.ERROR -> {
                                Log.e(TAG, "Error during sync: ${error?.message}")
                                updateStatus("Sync failed: ${error?.message}")
                            }

                            else -> {
                                Log.w(TAG, "Unknown sync step: $step")
                            }
                        }
                    }
                    loadLocalFiles() // Reload local files after sync
                    hideLoading()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during sync from Drive", e)
                    hideLoading()
                    updateStatus("Sync failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Saves selected file to local storage
     */
    private fun saveFileLocally(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val fileName = getFileName(uri)
                val localFile = LocalFile(uploadDir, fileName)

                inputStream?.use { input ->
                    localFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val fileInfo = LocalFileInfo(
                    name = fileName,
                    path = localFile.absolutePath,
                    lastModified = localFile.lastModified()
                )

                runOnUiThread {
                    val currentList = localFiles.value?.toMutableList() ?: mutableListOf()
                    currentList.add(fileInfo)
                    localFiles.value = currentList
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving file locally", e)
                updateStatus("Error saving file: ${e.message}")
            }
        }
    }

    /**
     * Deletes a local file and updates the UI
     */
    private fun deleteLocalFile(fileInfo: LocalFileInfo) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentList = localFiles.value?.toMutableList() ?: mutableListOf()
                Log.d(TAG, "Attempting to delete file: ${fileInfo.name}")

                val file = LocalFile(fileInfo.path)
                if (file.delete()) {
                    // Create new list without the deleted file
                    val newList = currentList.filter { it.path != fileInfo.path }
                    // Update UI with new list
                    localFiles.postValue(newList)
                    Log.d(TAG, "Successfully deleted file: ${fileInfo.name}")
                    Log.d(TAG, "New list size: ${newList.size}")
                } else {
                    Log.e(TAG, "Failed to delete file: ${fileInfo.name}")
                    updateStatus("Failed to delete file: ${fileInfo.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting file", e)
                updateStatus("Error deleting file: ${e.message}")
            }
        }
    }

    /**
     * Loads all files from local storage
     */
    private fun loadLocalFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val files = uploadDir.listFiles()
                val newList = mutableListOf<LocalFileInfo>()
                files?.forEach { file ->
                    newList.add(
                        LocalFileInfo(
                            name = file.name,
                            path = file.absolutePath,
                            lastModified = file.lastModified()
                        )
                    )
                }
                runOnUiThread {
                    localFiles.value = newList
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local files", e)
            }
        }
    }

    /**
     * Initiates sync to Google Drive
     */
    private suspend fun syncToDrive() {
        driveHelper.checkDriveAuthorization(object : DriveHelper.OnCheckDriveAuthorizationListener {
            override fun onAuthorizationResult(authorizationResult: AuthorizationResult) {
                if (authorizationResult.hasResolution()) {
                    val pendingIntent: PendingIntent? = authorizationResult.pendingIntent
                    if (pendingIntent == null) {
                        Log.e(TAG, "PendingIntent is null, cannot start Authorization UI")
                        return
                    }
                    val intentSenderRequest: IntentSenderRequest =
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    try {
                        startActivityForResult.launch(intentSenderRequest)
                    } catch (e: SendIntentException) {
                        Log.e(TAG, "Couldn't start Authorization UI: " + e.localizedMessage)
                        updateStatus("Authorization failed: ${e.localizedMessage}")
                    }
                } else {
                    handleDriveAuthorization(authorizationResult)
                }
            }

            override fun onAuthorizationFailure(e: Exception) {
                Log.e(TAG, "Failed to authorize", e)
                updateStatus("Authorization failed: ${e.localizedMessage}")
            }
        })
    }

    /**
     * Initiates sync from Google Drive
     */
    private suspend fun syncFromDrive() {
        driveHelper.checkDriveAuthorization(object : DriveHelper.OnCheckDriveAuthorizationListener {
            override fun onAuthorizationResult(authorizationResult: AuthorizationResult) {
                if (authorizationResult.hasResolution()) {
                    val pendingIntent: PendingIntent? = authorizationResult.pendingIntent
                    if (pendingIntent == null) {
                        Log.e(TAG, "PendingIntent is null, cannot start Authorization UI")
                        return
                    }
                    val intentSenderRequest: IntentSenderRequest =
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    try {
                        startActivityForResult.launch(intentSenderRequest)
                    } catch (e: SendIntentException) {
                        Log.e(TAG, "Couldn't start Authorization UI: " + e.localizedMessage)
                        updateStatus("Authorization failed: ${e.localizedMessage}")
                    }
                } else {
                    handleDriveDownload(authorizationResult)
                }
            }

            override fun onAuthorizationFailure(e: Exception) {
                Log.e(TAG, "Failed to authorize", e)
                updateStatus("Authorization failed: ${e.localizedMessage}")
            }
        })
    }

    /**
     * Handles Google Sign In process
     */
    private suspend fun signInWithGoogle() {
        driveHelper.signInWithGoogleButton(object : DriveHelper.OnSignInListener {
            override fun onSignInSuccess(email: String) {
                statusTextView.text = "Signed in successfully!"
                userInfoTextView.text = "Email: $email"
                updateUi(isSignedIn = true)
            }

            override fun onSignInFailure(e: Exception) {
                statusTextView.text = "Failed to sign in: ${e.message}"
                updateUi(isSignedIn = false)
            }
        })
    }

    /**
     * Handles sign out process
     */
    private suspend fun signOut() {
        driveHelper.signOut(object : DriveHelper.OnSignOutListener {
            override fun onSignOutSuccess() {
                statusTextView.text = "You have been signed out."
                updateUi(isSignedIn = false)
            }

            override fun onSignOutFailure(e: Exception) {
                statusTextView.text = "Failed to sign out: ${e.message}"
            }
        })
    }

    /**
     * Updates UI based on sign in status
     */
    private fun updateUi(isSignedIn: Boolean) {
        if (isSignedIn) {
            signInButton.visibility = View.GONE
            syncToDriveButton.visibility = View.VISIBLE
            syncFromDriveButton.visibility = View.VISIBLE
            signOutButton.visibility = View.VISIBLE
            userInfoTextView.visibility = View.VISIBLE
            selectFileButton.visibility = View.VISIBLE
            fileListRecyclerView.visibility = View.VISIBLE
        } else {
            signInButton.visibility = View.VISIBLE
            syncToDriveButton.visibility = View.GONE
            syncFromDriveButton.visibility = View.GONE
            signOutButton.visibility = View.GONE
            userInfoTextView.visibility = View.GONE
            selectFileButton.visibility = View.VISIBLE
            fileListRecyclerView.visibility = View.VISIBLE
            userInfoTextView.text = ""
        }
    }

    /**
     * Gets file name from URI
     */
    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: "unknown_file"
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val UPLOAD_DIR_NAME = "uploaded_files"
        private const val WEB_CLIENT_ID =
            "138324410819-nrpe25634p9l8q6cn725e8apbvg9p8jq.apps.googleusercontent.com"
    }
}