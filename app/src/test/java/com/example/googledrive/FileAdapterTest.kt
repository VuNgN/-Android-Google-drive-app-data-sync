package com.example.googledrive

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileAdapterTest {
    private lateinit var adapter: FileAdapter
    private lateinit var mockOnItemClickListener: (Int) -> Unit
    private lateinit var mockViewHolder: FileAdapter.FileViewHolder
    private lateinit var mockView: View
    private lateinit var mockFileNameTextView: TextView

    @Before
    fun setup() {
        mockOnItemClickListener = mockk(relaxed = true)
        adapter = FileAdapter(mockOnItemClickListener)
        mockView = mockk(relaxed = true)
        mockFileNameTextView = mockk(relaxed = true)
        mockViewHolder = FileAdapter.FileViewHolder(mockView)

        every { mockView.findViewById<TextView>(R.id.fileNameTextView) } returns mockFileNameTextView
    }

    @Test
    fun `test onCreateViewHolder`() {
        // Arrange
        val mockParent = mockk<RecyclerView>()
        val mockInflater = mockk<LayoutInflater>()
        val mockItemView = mockk<View>()

        every { mockParent.context } returns mockk(relaxed = true)
        every { mockParent.context.getSystemService(android.content.Context.LAYOUT_INFLATER_SERVICE) } returns mockInflater
        every { mockInflater.inflate(R.layout.item_file, mockParent, false) } returns mockItemView

        // Act
        val viewHolder = adapter.onCreateViewHolder(mockParent, 0)

        // Assert
        assert(viewHolder is FileAdapter.FileViewHolder)
    }

    @Test
    fun `test onBindViewHolder`() {
        // Arrange
        val fileInfo = MainActivity.LocalFileInfo(
            name = "test.txt",
            path = "/path/to/test.txt",
            lastModified = 1234567890
        )
        adapter.submitList(listOf(fileInfo))

        // Act
        adapter.onBindViewHolder(mockViewHolder, 0)

        // Assert
        verify { mockFileNameTextView.text = "test.txt" }
    }

    @Test
    fun `test onItemClick`() {
        // Arrange
        val fileInfo = MainActivity.LocalFileInfo(
            name = "test.txt",
            path = "/path/to/test.txt",
            lastModified = 1234567890
        )
        adapter.submitList(listOf(fileInfo))

        // Act
        adapter.onItemClick(0)

        // Assert
        verify { mockOnItemClickListener.invoke(0) }
    }

    @Test
    fun `test getItemCount`() {
        // Arrange
        val fileList = listOf(
            MainActivity.LocalFileInfo("file1.txt", "/path/to/file1.txt", 1234567890),
            MainActivity.LocalFileInfo("file2.txt", "/path/to/file2.txt", 1234567891)
        )

        // Act
        adapter.submitList(fileList)

        // Assert
        assert(adapter.itemCount == 2)
    }

    @Test
    fun `test submitList with null`() {
        // Act
        adapter.submitList(null)

        // Assert
        assert(adapter.itemCount == 0)
    }

    @Test
    fun `test submitList with empty list`() {
        // Act
        adapter.submitList(emptyList())

        // Assert
        assert(adapter.itemCount == 0)
    }
} 