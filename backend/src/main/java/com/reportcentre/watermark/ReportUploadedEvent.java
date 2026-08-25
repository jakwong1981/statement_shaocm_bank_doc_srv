package com.reportcentre.watermark;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportUploadedEvent implements Serializable {
    private String reportId;
    private String rawStoragePath;
    private String benchmarkTag;
    private String uploaderId;
}
