package app.murmure.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Cycle 1 — A1 : les règles d'extraction excluent tous les domaines, cloud et transfert. */
class DataExtractionRulesTest {
  private val domains = listOf(
    "root", "file", "database", "sharedpref", "external",
    "device_root", "device_file", "device_database", "device_sharedpref"
  )

  private fun rulesFile(): File {
    val candidates = listOf(
      File("src/main/res/xml/murmure_data_extraction_rules.xml"),
      File("modules/murmure-native/android/src/main/res/xml/murmure_data_extraction_rules.xml")
    )
    return candidates.first { it.exists() }
  }

  @Test fun everyDomainIsExcludedForCloudAndDeviceTransfer() {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rulesFile())
    for (section in listOf("cloud-backup", "device-transfer")) {
      val nodes = doc.getElementsByTagName(section)
      assertTrue("section $section manquante", nodes.length == 1)
      val excludes = (nodes.item(0) as org.w3c.dom.Element).getElementsByTagName("exclude")
      val excluded = (0 until excludes.length).map { i ->
        val e = excludes.item(i) as org.w3c.dom.Element
        assertTrue(e.getAttribute("path") == ".")
        e.getAttribute("domain")
      }.toSet()
      assertTrue("$section : ${domains - excluded}", excluded.containsAll(domains))
      assertTrue("$section ne doit rien inclure", (nodes.item(0) as org.w3c.dom.Element).getElementsByTagName("include").length == 0)
    }
  }
}
