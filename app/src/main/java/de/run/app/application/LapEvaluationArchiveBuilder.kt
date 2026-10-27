package de.run.app.application

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packages the explicitly requested LAP report into separate, readable topic files. */
class LapEvaluationArchiveBuilder {
    fun build(report: String): ByteArray {
        val sections = splitSections(report)
        val header = report.lineSequence().takeWhile { !it.startsWith("## ") }.joinToString("\n").trim()
        val files = linkedMapOf("00_Uebersicht.md" to overview(header, sections))
        sections.forEachIndexed { index, section ->
            val name = section.title.toFileName()
            files["%02d_%s.md".format(index + 1, name)] =
                "# ${section.title}\n\n${section.content}\n"
        }
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry("LAP-Auswertung/$name"))
                    zip.write(content.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private fun splitSections(report: String): List<ReportSection> {
        val sections = mutableListOf<ReportSection>()
        var currentTitle: String? = null
        val currentLines = mutableListOf<String>()
        report.lineSequence().forEach { line ->
            if (line.startsWith("## ")) {
                currentTitle?.let { sections += ReportSection(it, currentLines.joinToString("\n").trim()) }
                currentTitle = line.removePrefix("## ").trim()
                currentLines.clear()
            } else if (currentTitle != null) {
                currentLines += line
            }
        }
        currentTitle?.let { sections += ReportSection(it, currentLines.joinToString("\n").trim()) }
        return sections
    }

    private fun overview(header: String, sections: List<ReportSection>): String = buildString {
        appendLine("# LAP-Auswertungspaket")
        appendLine()
        if (header.isNotBlank()) {
            appendLine(header)
            appendLine()
        }
        appendLine("Dieses Paket wurde nur nach deiner ausdrücklichen Exportaktion erstellt.")
        appendLine("Es enthält den aktuellen Auswertungsstand; frühere Zustände und jede Interaktion werden nicht automatisch protokolliert.")
        appendLine("Gesundheits- und Trainingsdaten bleiben lokal, bis du diesen ZIP-Export selbst teilst.")
        appendLine()
        appendLine("## Dateien")
        sections.forEachIndexed { index, section ->
            appendLine("- %02d_%s.md — %s".format(index + 1, section.title.toFileName(), section.title))
        }
        appendLine()
        appendLine("## Grenzen der Prüffälle")
        appendLine("Debug-Prüffälle verwenden echte LAP-Regeln und prüfen ausgewählte Grenz- und Fehlwertfälle. Sie sind keine vollständige Prüfung aller möglichen Eingaben oder Abläufe.")
    }

    private fun String.toFileName(): String = lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "bereich" }

    private data class ReportSection(val title: String, val content: String)
}
