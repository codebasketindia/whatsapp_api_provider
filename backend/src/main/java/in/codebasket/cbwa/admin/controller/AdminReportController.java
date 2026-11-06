package in.codebasket.cbwa.admin.controller;

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
@Tag(name = "Admin - Dashboards & reports")
@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private final DashboardService dashboards;
    private final ReportService reports;
    private final ReportExporter exporter;

    @GetMapping("/api/admin/dashboard")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_VIEW')")
    public ApiResponse<AdminDashboardDto> adminDashboard() {
        return ApiResponse.ok(dashboards.admin());
    }

    @GetMapping("/api/admin/dashboard/chart")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_VIEW')")
    public ApiResponse<List<?>> adminChart(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "messages") String metric) {
        return ApiResponse.ok(dashboards.adminChart(from, to, metric));
    }

    @GetMapping("/api/admin/reports/partners")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<?> partners(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "json") String format,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        if (isJson(format)) {
            return ResponseEntity.ok(ApiResponse.ok(reports.partners(from, to, page, size == null ? 20 : size)));
        }
        return exporter.export(ReportTables.partners(reports.partners(from, to, null, null).content(), ReportTables.period(from, to)),
                format, "partner-report");
    }

    @GetMapping("/api/admin/reports/categories")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<?> categories(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Long partnerId,
                                        @RequestParam(defaultValue = "json") String format) {
        var rows = reports.categories(from, to, partnerId);
        return isJson(format) ? ResponseEntity.ok(ApiResponse.ok(rows))
                : exporter.export(ReportTables.categories(rows, ReportTables.period(from, to)), format, "category-report");
    }

    @GetMapping("/api/admin/reports/countries")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<?> countries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) Long partnerId,
                                       @RequestParam(defaultValue = "json") String format) {
        var rows = reports.countries(from, to, partnerId);
        return isJson(format) ? ResponseEntity.ok(ApiResponse.ok(rows))
                : exporter.export(ReportTables.countries(rows, ReportTables.period(from, to)), format, "country-report");
    }

    @GetMapping("/api/admin/reports/revenue")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<?> revenue(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "day") String groupBy,
                                     @RequestParam(defaultValue = "json") String format) {
        var rows = reports.revenue(from, to, groupBy);
        return isJson(format) ? ResponseEntity.ok(ApiResponse.ok(rows))
                : exporter.export(ReportTables.revenue(rows, ReportTables.period(from, to)), format, "revenue-report");
    }

    @GetMapping("/api/admin/reports/reconciliation")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<?> reconciliation(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) Long wabaId,
                                            @RequestParam(defaultValue = "json") String format) {
        var rows = reports.reconciliation(from, to, wabaId);
        return isJson(format) ? ResponseEntity.ok(ApiResponse.ok(rows))
                : exporter.export(ReportTables.reconciliation(rows, ReportTables.period(from, to)), format, "meta-reconciliation");
    }

    private static boolean isJson(String format) {
        return format == null || format.isBlank() || "json".equalsIgnoreCase(format);
    }
}
