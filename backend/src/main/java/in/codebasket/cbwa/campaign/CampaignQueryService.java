package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.campaign.dto.CampaignDtos.CampaignDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.RecipientDto;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.contact.TabularFile;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** CP7-CP10, CP12: campaign lists, detail, recipients and downloadable report. */
@Service
@RequiredArgsConstructor
public class CampaignQueryService {

    private static final DateTimeFormatter IST = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(TimeUtil.IST);
    private static final List<String> REPORT_HEADER = List.of("Row", "Phone", "Status", "Skip reason", "Error", "Message ID",
            "Sent at (IST)", "Delivered at (IST)", "Read at (IST)", "Price (INR)");

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;
    private final NamedParameterJdbcTemplate jdbc;

    /** partnerId null = admin (optionally filtered by filterPartnerId). */
    @Transactional(readOnly = true)
    public PageResponse<CampaignDto> list(Long partnerId, Long filterPartnerId, String status, String search,
                                          LocalDate from, LocalDate to, Integer page, Integer size) {
        Specification<Campaign> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            Long p = partnerId != null ? partnerId : filterPartnerId;
            if (p != null) {
                ps.add(cb.equal(root.get("partnerId"), p));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), CampaignStatus.valueOf(status)));
            }
            if (search != null && !search.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("name")), "%" + search.trim().toLowerCase() + "%"));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeUtil.startOfDayIst(from)));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), TimeUtil.endOfDayIstExclusive(to)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Pageable pageable = Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        boolean admin = partnerId == null;
        return PageResponse.of(campaignRepository.findAll(spec, pageable), c -> campaignService.toDto(c, admin));
    }

    @Transactional(readOnly = true)
    public CampaignDto get(Long partnerId, Long id) {
        Campaign c = partnerId == null
                ? campaignRepository.findById(id).orElseThrow(() -> in.codebasket.cbwa.common.exception.AppException.notFound("Campaign"))
                : campaignService.owned(partnerId, id);
        return campaignService.toDto(c, partnerId == null);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipientDto> recipients(Long partnerId, Long id, String status, String search, Integer page, Integer size) {
        Campaign c = campaignService.owned(partnerId, id);
        Pageable pageable = Paging.of(page, size);
        StringBuilder where = new StringBuilder(" from campaign_recipients r left join messages m on m.id = r.message_id where r.campaign_id = :cid");
        MapSqlParameterSource params = new MapSqlParameterSource("cid", c.getId());
        if (status != null && !status.isBlank()) {
            where.append(" and r.status = :st");
            params.addValue("st", RecipientStatus.valueOf(status).name());
        }
        if (search != null && !search.isBlank()) {
            where.append(" and r.phone like :q");
            params.addValue("q", "%" + search.replaceAll("[^0-9]", "") + "%");
        }
        Long total = jdbc.queryForObject("select count(*)" + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<RecipientDto> content = jdbc.query("select r.id, r.row_no, r.phone, r.status, r.skip_reason, r.error_message, m.uid, r.updated_at"
                        + where + " order by r.row_no, r.id limit :limit offset :offset", params,
                (rs, i) -> new RecipientDto(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant()));
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) Math.ceil(t / (double) pageable.getPageSize()));
    }

    public record Report(String filename, String contentType, byte[] body) {
    }

    @Transactional(readOnly = true)
    public Report report(Long partnerId, Long id, String format) {
        Campaign c = campaignService.owned(partnerId, id);
        List<List<String>> rows = new ArrayList<>();
        jdbc.query("""
                        select r.row_no, r.phone, r.status, r.skip_reason, r.error_message, m.uid, m.sent_at, m.delivered_at, m.read_at, m.price
                        from campaign_recipients r left join messages m on m.id = r.message_id
                        where r.campaign_id = :cid order by r.row_no, r.id
                        """, new MapSqlParameterSource("cid", c.getId()),
                rs -> {
                    BigDecimal price = rs.getBigDecimal(10);
                    rows.add(List.of(String.valueOf(rs.getInt(1)), nz(rs.getString(2)), nz(rs.getString(3)), nz(rs.getString(4)),
                            nz(rs.getString(5)), nz(rs.getString(6)), ist(rs.getTimestamp(7)), ist(rs.getTimestamp(8)),
                            ist(rs.getTimestamp(9)), price == null ? "" : price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
                });
        String base = "campaign-" + c.getId() + "-report";
        if ("csv".equalsIgnoreCase(format)) {
            return new Report(base + ".csv", "text/csv", TabularFile.toCsv(REPORT_HEADER, rows));
        }
        return new Report(base + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(c, rows));
    }

    private static byte[] xlsx(Campaign c, List<List<String>> rows) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Recipients");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Campaign: " + c.getName());
            Row header = sheet.createRow(2);
            for (int i = 0; i < REPORT_HEADER.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(REPORT_HEADER.get(i));
                cell.setCellStyle(bold);
            }
            int r = 3;
            for (List<String> row : rows) {
                Row xr = sheet.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    xr.createCell(i).setCellValue(row.get(i));
                }
            }
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the report", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String ist(Timestamp ts) {
        return ts == null ? "" : IST.format(ts.toInstant());
    }
}
