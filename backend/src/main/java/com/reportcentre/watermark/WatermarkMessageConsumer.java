package com.reportcentre.watermark;

import com.reportcentre.entity.Report;
import com.reportcentre.entity.enums.ReportStatus;
import com.reportcentre.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WatermarkMessageConsumer {

    private final ReportRepository reportRepository;
    private final WatermarkEngine watermarkEngine;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void handleReportUploaded(Map<String, String> message) {
        String reportId = message.get("reportId");
        String rawStoragePath = message.get("rawStoragePath");
        String benchmarkTag = message.get("benchmarkTag");
        String uploaderId = message.get("uploaderId");

        log.info("Processing watermark for report: {}", reportId);

        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            log.error("Report not found: {}", reportId);
            return;
        }

        try {
            report.setStatus(ReportStatus.PROCESSING);
            reportRepository.save(report);

            WatermarkEngine.WatermarkResult result = watermarkEngine.process(
                    rawStoragePath, benchmarkTag, uploaderId);

            report.setStatus(ReportStatus.READY);
            report.setWatermarkedStoragePath(result.watermarkedPath());
            report.setChecksumSha256(result.checksum());
            report.setPageCount(result.pageCount());
            reportRepository.save(report);

            log.info("Watermark completed for report: {}, {} pages", reportId, result.pageCount());

        } catch (Exception e) {
            log.error("Watermark failed for report: {}", reportId, e);
            report.setStatus(ReportStatus.FAILED);
            report.setErrorReason(e.getMessage());
            reportRepository.save(report);
        }
    }
}
