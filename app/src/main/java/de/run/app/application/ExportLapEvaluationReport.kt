package de.run.app.application

import de.run.app.domain.repository.EvaluationReportRepository

class ExportLapEvaluationReport(
    private val reports: EvaluationReportRepository,
    private val archiveBuilder: LapEvaluationArchiveBuilder = LapEvaluationArchiveBuilder()
) {
    suspend operator fun invoke(uri: String, report: String): Boolean =
        reports.export(uri, archiveBuilder.build(report))
}
