package suwayomi.tachidesk.manga.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga

class ChapterNameTest : ApplicationTest() {
    @Test
    fun testChapterName() {
    }

    @Test
    fun testChapterCbzPath() {
        val mangaId = createLibraryManga("CbzTest")
        createChapters(mangaId, 10, false)

        runBlocking {
            val chapterList = Chapter.getChapterList(mangaId)

            for (chapter in chapterList) {
                val chapterCbzPath = getChapterCbzPath(mangaId, chapter.id)
                println(chapterCbzPath)
            }
        }
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterTable,
            CategoryMangaTable,
            MangaTable,
            CategoryTable,
        )
    }
}
