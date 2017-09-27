package utils

import java.io.File

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.text.PDFTextStripper

import scala.collection.JavaConverters._

object PDFUtil {
  def main(args: Array[String]): Unit = {
    val doc = PDDocument.load(new File("f:/p3-2.pdf"))
    val totalPages = doc.getNumberOfPages
    val items = doc.getDocumentCatalog.getDocumentOutline.children().asScala.flatMap( i => getCatalogs("/", i))
    val scannedItems =
      items.filter(_._3 >= 0).sliding(2).toList.map(_.toList).map{ t =>
        val endPage = if (t(0)._3 == t(1)._3) { t(0)._3 } else { t(1)._3 - 1 }
        t(0).copy(_4 = endPage)
      }
    (scannedItems ::: List(items.last.copy(_4 = totalPages))).foreach{ t =>
      println(t._2)
      println(getText(doc, t._3, t._4))
      println("=============================================================================")

    }

  }

  // (parentCatalogPath, currentCatalog, fromPage, toPage, content)
  def getCatalogs(parentCatalogPath: String, item: PDOutlineItem): List[(String, String, Int, Int, String)] = {
    val current =
      if (item.getDestination.isInstanceOf[PDPageDestination]) {
        (parentCatalogPath, item.getTitle, item.getDestination.asInstanceOf[PDPageDestination].retrievePageNumber + 1, 0, "")
      } else if (item.getAction.isInstanceOf[PDActionGoTo]){
        val dst = item.getAction.asInstanceOf[PDActionGoTo].getDestination
        if (dst.isInstanceOf[PDPageDestination]) {
          (parentCatalogPath, item.getTitle, dst.asInstanceOf[PDPageDestination].retrievePageNumber + 1, 0, "")
        } else {
          (parentCatalogPath, item.getTitle, -1, 0, "")
        }
      } else {
        (parentCatalogPath, item.getTitle, -1, 0, "")
      }

    current :: item.children().asScala.toList.flatMap(i => getCatalogs(s"${parentCatalogPath}/${item.getTitle}".replaceFirst("//", "/"), i))
  }

  def getText(doc: PDDocument, startPage: Int, endPage: Int): String = {
    val stripper = new PDFTextStripper
    stripper.setStartPage(startPage)
    stripper.setEndPage(endPage)
    stripper.getText(doc)
  }

}
