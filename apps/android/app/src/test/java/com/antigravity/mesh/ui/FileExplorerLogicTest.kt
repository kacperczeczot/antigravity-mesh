package com.antigravity.mesh.ui

import com.antigravity.mesh.data.FileItem
import com.antigravity.mesh.ui.screens.getParentDirectory
import com.antigravity.mesh.ui.screens.inferHomeDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileExplorerLogicTest {

    @Test
    fun testGetParentDirectory() {
        assertEquals("/Users", getParentDirectory("/Users/kacper"))
        assertEquals("/", getParentDirectory("/Users"))
        assertNull(getParentDirectory("/"))
        assertNull(getParentDirectory(""))
        assertEquals("..", getParentDirectory("."))
        assertEquals("/foo/bar", getParentDirectory("/foo/bar/baz"))
        assertEquals("C:\\Users", getParentDirectory("C:\\Users\\kacper"))
        assertEquals("C:\\", getParentDirectory("C:\\Users"))
        assertNull(getParentDirectory("C:\\"))
        assertNull(getParentDirectory("C:"))
    }

    @Test
    fun testInferHomeDirectory() {
        assertEquals("/Users/kacper", inferHomeDirectory("/Users/kacper/Developer/mesh"))
        assertEquals("/home/kacper", inferHomeDirectory("/home/kacper/Developer/mesh"))
        assertEquals("C:\\Users\\kacper", inferHomeDirectory("C:\\Users\\kacper\\Developer\\mesh"))
        assertEquals("C:/Users/kacper", inferHomeDirectory("C:/Users/kacper/Developer/mesh"))
        assertNull(inferHomeDirectory("/etc/nginx"))
        assertNull(inferHomeDirectory(""))
    }

    @Test
    fun testBackNavigationStateMachine() {
        // Model the back navigation logic:
        // 1. Preview open -> close preview
        // 2. Search active -> clear search
        // 3. historyStack > 1 -> pop history
        // 4. historyStack <= 1 -> exit (call onBack)
        
        var selectedFile: String? = "readme.md"
        var searchQuery = "test"
        var historyStack = listOf(".", "subdir", "subsubdir")
        var exited = false
        val onBack = { exited = true }

        fun stepBack() {
            if (selectedFile != null) {
                selectedFile = null
            } else if (searchQuery.isNotEmpty()) {
                searchQuery = ""
            } else if (historyStack.size > 1) {
                historyStack = historyStack.dropLast(1)
            } else {
                onBack()
            }
        }

        // 1. First back: closes file preview
        stepBack()
        assertNull(selectedFile)
        assertEquals("test", searchQuery)
        assertEquals(3, historyStack.size)
        assertFalse(exited)

        // 2. Second back: clears search
        stepBack()
        assertEquals("", searchQuery)
        assertEquals(3, historyStack.size)
        assertFalse(exited)

        // 3. Third back: steps back from subsubdir to subdir
        stepBack()
        assertEquals(listOf(".", "subdir"), historyStack)
        assertFalse(exited)

        // 4. Fourth back: steps back from subdir to root (.)
        stepBack()
        assertEquals(listOf("."), historyStack)
        assertFalse(exited)

        // 5. Fifth back: at root with empty history -> MUST EXIT!
        stepBack()
        assertTrue("Back button at root directory MUST call onBack and exit the screen!", exited)
    }

    @Test
    fun testFileSortingOrders() {
        val f1 = FileItem(name = "b.txt", size = 100, isDir = false, modified = 1000)
        val f2 = FileItem(name = "a.txt", size = 500, isDir = false, modified = 2000)
        val dir1 = FileItem(name = "z_folder", size = 0, isDir = true, modified = 500)
        val dir2 = FileItem(name = "a_folder", size = 0, isDir = true, modified = 3000)

        val items = listOf(f1, f2, dir1, dir2)

        // With foldersFirst = true and NAME_ASC
        val sorted1 = items.sortedWith(
            Comparator { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    return@Comparator if (a.isDirectory) -1 else 1
                }
                a.name.compareTo(b.name, ignoreCase = true)
            }
        )
        assertEquals(listOf("a_folder", "z_folder", "a.txt", "b.txt"), sorted1.map { it.name })

        // With foldersFirst = false and SIZE_DESC
        val sorted2 = items.sortedWith(
            Comparator { a, b ->
                b.size.compareTo(a.size)
            }
        )
        assertEquals(listOf("a.txt", "b.txt"), sorted2.filter { !it.isDirectory }.map { it.name })
    }

    @Test
    fun testHiddenFileFiltering() {
        val visible = FileItem(name = "main.rs", isDir = false)
        val hidden1 = FileItem(name = ".gitignore", isDir = false)
        val hidden2 = FileItem(name = ".github", isDir = true)
        val all = listOf(visible, hidden1, hidden2)

        // When hidden files are hidden by default
        val filteredDefault = all.filter { !it.name.startsWith(".") }
        assertEquals(listOf("main.rs"), filteredDefault.map { it.name })

        // When showHiddenFiles is true
        val filteredShown = all
        assertEquals(3, filteredShown.size)
    }

    @Test
    fun testSearchFiltering() {
        val f1 = FileItem(name = "App.kt", isDir = false)
        val f2 = FileItem(name = "ViewModel.kt", isDir = false)
        val f3 = FileItem(name = "app_icon.png", isDir = false)
        val all = listOf(f1, f2, f3)

        // Case-insensitive search
        val searchApp = all.filter { it.name.contains("app", ignoreCase = true) }
        assertEquals(listOf("App.kt", "app_icon.png"), searchApp.map { it.name })

        // Blank search query returns all
        val query = "   "
        val searchBlank = if (query.isBlank()) all else all.filter { it.name.contains(query, ignoreCase = true) }
        assertEquals(3, searchBlank.size)
    }
}
