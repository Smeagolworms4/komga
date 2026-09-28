package org.gotson.komga.oracle.infrastructure.metadata.comicrack

import io.mockk.every
import io.mockk.mockk
import org.apache.commons.validator.routines.ISBNValidator
import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaFile
import org.gotson.komga.domain.model.MetadataPatchTarget
import org.gotson.komga.domain.service.BookAnalyzer
import org.gotson.komga.infrastructure.metadata.comicrack.ComicInfoProvider
import org.gotson.komga.infrastructure.metadata.comicrack.computeSeriesFromSeriesAndVolume
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.metadata.MetadataSamples

class ComicInfoProviderOracleTest : OracleTest() {
  private val withComicInfo = BookWithMedia(MetadataSamples.book(), Media(files = listOf(MediaFile("ComicInfo.xml"))))

  private fun provider(content: ByteArray): ComicInfoProvider {
    val analyzer = mockk<BookAnalyzer> { every { getFileContent(any(), "ComicInfo.xml") } returns content }
    return ComicInfoProvider(bookAnalyzer = analyzer, isbnValidator = ISBNValidator(true))
  }

  private fun xml(body: String) = "<?xml version=\"1.0\"?><ComicInfo>$body</ComicInfo>".toByteArray()

  override fun cases() {
    val cases = MetadataSamples.xmlCases
    func("getBookMetadataFromBook") {
      for ((id, cls, content) in cases) case("$cls #$id") { provider(content).getBookMetadataFromBook(withComicInfo) }
      case("no ComicInfo.xml in media") { provider(xml("<Title>T</Title>")).getBookMetadataFromBook(BookWithMedia(MetadataSamples.book(), Media(files = listOf(MediaFile("comicinfo.xml"))))) }
    }
    func("getSeriesMetadataFromBook") {
      for ((id, cls, content) in cases) {
        for (append in listOf(true, false)) case("$cls #$id (append volume $append)") { provider(content).getSeriesMetadataFromBook(withComicInfo, append) }
      }
      case("no ComicInfo.xml in media") { provider(xml("<Series>S</Series>")).getSeriesMetadataFromBook(BookWithMedia(MetadataSamples.book(), Media()), true) }
    }
    func("shouldLibraryHandlePatch") {
      for ((name, library) in MetadataSamples.libraries) {
        for (target in MetadataPatchTarget.entries) case("$name, $target") { provider(ByteArray(0)).shouldLibraryHandlePatch(library, target) }
      }
    }
    // private: through getBookMetadataFromBook
    func("getComicInfo") {
      case("no files") { provider(xml("<Title>T</Title>")).getBookMetadataFromBook(BookWithMedia(MetadataSamples.book(), Media())) }
      case("analyzer error") {
        val analyzer = mockk<BookAnalyzer> { every { getFileContent(any(), any()) } throws IllegalStateException("boom") }
        ComicInfoProvider(bookAnalyzer = analyzer, isbnValidator = ISBNValidator(true)).getBookMetadataFromBook(withComicInfo)
      }
      case("empty content") { provider(ByteArray(0)).getBookMetadataFromBook(withComicInfo) }
      case("not xml") { provider("not xml".toByteArray()).getBookMetadataFromBook(withComicInfo) }
      case("empty root") { provider(xml("")).getBookMetadataFromBook(withComicInfo) }
      case("other root name") { provider("<Other><Title>T</Title></Other>".toByteArray()).getBookMetadataFromBook(withComicInfo) }
    }
    // private: through getBookMetadataFromBook
    func("splitWithRole") {
      for (v in listOf("", " ", "A", "A,B", " A , B ,, ", ",", "A;B", "A, ,B", "Doe, John", "  ,  ,  ")) {
        case("'$v'") {
          provider(xml("<Writer>$v</Writer><Penciller>$v</Penciller><Inker>$v</Inker><Colorist>$v</Colorist><Letterer>$v</Letterer><CoverArtist>$v</CoverArtist><Editor>$v</Editor><Translator>$v</Translator>"))
            .getBookMetadataFromBook(withComicInfo)
            ?.authors
        }
      }
    }
    func("computeSeriesFromSeriesAndVolume") {
      for (s in listOf(null, "", " ", "Batman", " Batman ", "X (1)")) {
        for (v in listOf(null, 0, 1, 2, -1, 2020, Int.MAX_VALUE)) case("'$s' volume $v") { computeSeriesFromSeriesAndVolume(s, v) }
      }
    }
  }
}
