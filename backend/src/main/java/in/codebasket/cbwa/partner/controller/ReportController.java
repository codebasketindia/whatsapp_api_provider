package in.codebasket.cbwa.partner.controller;

import in.codebasket.cbwa.common.service.report.DashboardService;
import in.codebasket.cbwa.common.service.report.ReportExporter;
import in.codebasket.cbwa.common.service.report.ReportService;
import in.codebasket.cbwa.common.service.report.ReportTables;

import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.dto.DashboardDtos.AdminDashboardDto;
import in.codebasket.cbwa.common.dto.DashboardDtos.PartnerDashboardDto;
import in.codebasket.cbwa.common.dto.DashboardDtos.PartnerPoint;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** DB1–DB4 dashboards and RP1–RP6 reports (format=json | xlsx | pdf, csv for RP6). */
@Tag(name = "Dashboards & reports")
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final DashboardService dashboards;
    private final ReportService reports;
    private final ReportExporter exporter;

    @GetMapping("/api/partner/dashboard")
    @PreAuthorize("hasRole('PARTNER')")
    public ApiResponse<PartnerDashboardDto> partnerDashboard() {
        return ApiResponse.ok(dashboards.partner(SecurityUtils.currentPartnerId()));
    }

    @GetMapping("/api/partner/dashboard/chart")
    @PreAuthorize("hasRole('PARTNER')")
    public ApiResponse<List<PartnerPoint>> partnerChart(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(dashboards.partnerChart(SecurityUtils.currentPartnerId(), from, to));
    }

    @GetMapping("/api/partner/reports/usage")
    @PreAuthorize("hasRole('PARTNER')")
    public ResponseEntity<?> usage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(defaultValue = "day") String groupBy,
                                   @RequestParam(defaultValue = "json") String format) {
        var rows = reports.usage(SecurityUtils.currentPartnerId(), from, to, groupBy);
        return isJson(format) ? ResponseEntity.ok(ApiResponse.ok(rows))
                : exporter.export(ReportTables.usage(rows, groupBy, ReportTables.period(from, to)), format, "usage-report");
    }

    private static boolean isJson(String format) {
        return format == null || format.isBlank() || "json".equalsIgnoreCase(format);
    }
}
