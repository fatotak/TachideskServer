package suwayomi.tachidesk.manga.impl

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ArchiveProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.FolderProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPaths
import suwayomi.tachidesk.manga.impl.util.lang.renameTo
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import java.io.File
import java.io.InputStream

object ChapterDownloadHelper {
    fun getImage(
        mangaId: Int,
        chapterId: Int,
        index: Int,
    ): Pair<InputStream, String> = provider(mangaId, chapterId).getImage().execute(index)

    fun getImageCount(
        mangaId: Int,
        chapterId: Int,
    ): Int = provider(mangaId, chapterId).getImageCount()

    fun delete(
        mangaId: Int,
        chapterId: Int,
    ): Boolean = provider(mangaId, chapterId).delete()

    suspend fun download(
        mangaId: Int,
        chapterId: Int,
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit,
    ): Boolean = provider(mangaId, chapterId).download().execute(download, scope, step)

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private fun provider(
        mangaId: Int,
        chapterId: Int,
    ): ChaptersFilesProvider<*> {
        val chapterDownloadPaths = getChapterDownloadPaths(mangaId, chapterId)
        val preferredPath = chapterDownloadPaths.first()
        val preferredPathCbz = "${preferredPath}.cbz"

        // find whatever exists, using order of chapterDownloadPaths as preference.
        for (cdl in chapterDownloadPaths) {
            val cdlCbz = "${cdl}.cbz"
            val cdlCbzFile = File(cdlCbz)
            if (cdlCbzFile.exists()) {
                return if ((cdlCbz != preferredPathCbz) && (cdlCbzFile.renameTo(preferredPathCbz))) {
                    ArchiveProvider(preferredPathCbz, mangaId, chapterId)
                } else {
                    ArchiveProvider(cdlCbz, mangaId, chapterId)
                }
            }

            val cdlFolder = File(cdl)
            if (File(cdl).exists()) {
                return if ((cdl != preferredPath) && (cdlFolder.renameTo(preferredPath))) {
                    FolderProvider(preferredPath, mangaId, chapterId)
                } else {
                    FolderProvider(cdl, mangaId, chapterId)
                }
            }
        }

        // file doesn't exist if we get here, so it's a new file.
        // preference of new files is the newest format (earliest entry in chapterDownloadPaths)
        return if (serverConfig.downloadAsCbz.value) {
            ArchiveProvider("${preferredPath}.cbz", mangaId, chapterId)
        } else {
            FolderProvider(preferredPath, mangaId, chapterId)
        }
    }

    fun getArchiveStreamWithSize(
        mangaId: Int,
        chapterId: Int,
    ): Pair<InputStream, Long> = provider(mangaId, chapterId).getAsArchiveStream()

    fun getCbzForDownload(
        chapterId: Int,
        markAsRead: Boolean?,
    ): Triple<InputStream, String, Long> {
        val (chapterData, mangaTitle) =
            transaction {
                val row =
                    (ChapterTable innerJoin MangaTable)
                        .select(ChapterTable.columns + MangaTable.columns)
                        .where { ChapterTable.id eq chapterId }
                        .firstOrNull() ?: throw IllegalArgumentException("ChapterId $chapterId not found")
                val chapter = ChapterTable.toDataClass(row)
                val title = row[MangaTable.title]
                Pair(chapter, title)
            }

        val fileName = "$mangaTitle - [${chapterData.scanlator}] ${chapterData.name}.cbz"

        val cbzFile = provider(chapterData.mangaId, chapterData.id).getAsArchiveStream()

        if (markAsRead == true) {
            Chapter.modifyChapter(
                chapterData.mangaId,
                chapterData.index,
                isRead = true,
                isBookmarked = null,
                markPrevRead = null,
                lastPageRead = null,
            )
        }

        return Triple(cbzFile.first, fileName, cbzFile.second)
    }
}
