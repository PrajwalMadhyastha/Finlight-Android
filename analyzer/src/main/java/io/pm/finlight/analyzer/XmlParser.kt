package io.pm.finlight.analyzer

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object XmlParser {
    fun parse(xmlContent: String): List<DatasetSmsEntry> {
        val entries = mutableListOf<DatasetSmsEntry>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val inputSource = InputSource(StringReader(xmlContent))
            val doc = builder.parse(inputSource)
            
            val smsNodes = doc.getElementsByTagName("sms")
            
            for (i in 0 until smsNodes.length) {
                val node = smsNodes.item(i)
                if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val element = node as Element
                    val address = element.getAttribute("address") ?: ""
                    val date = element.getAttribute("date") ?: "0"
                    val body = element.getAttribute("body") ?: ""
                    
                    entries.add(DatasetSmsEntry(
                        address = address,
                        date = date,
                        body = body
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entries
    }
}
