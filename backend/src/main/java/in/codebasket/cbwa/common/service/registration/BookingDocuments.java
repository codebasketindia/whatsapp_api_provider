package in.codebasket.cbwa.common.service.registration;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.entity.RegistrationForm;
import in.codebasket.cbwa.common.entity.RegistrationItem;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Booking invoice (no GST) and the all-passes PDF. Built-in PDF fonts have no ₹ sign, so "Rs." is used. */
@Component
public class BookingDocuments {

    private static final Color NAVY = new Color(0x1E, 0x2A, 0x44);
    private static final Color GOLD = new Color(0xC9, 0xA2, 0x4B);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    public byte[] invoice(Partner partner, RegistrationForm form, Registration r, List<RegistrationItem> items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, NAVY);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font white = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

        PdfPTable head = new PdfPTable(new float[] {3, 2});
        head.setWidthPercentage(100);
        PdfPCell left = cell(Rectangle.NO_BORDER);
        left.addElement(new Paragraph(partner.getBusinessName(), title));
        left.addElement(new Paragraph(join(partner.getAddressLine1(), partner.getAddressLine2(), partner.getCity(), partner.getState(), partner.getPincode()), small));
        left.addElement(new Paragraph(join(partner.getMobile() == null ? null : "Phone: " + partner.getMobile(), partner.getEmail()), small));
        head.addCell(left);
        PdfPCell right = cell(Rectangle.NO_BORDER);
        Paragraph inv = new Paragraph("INVOICE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, GOLD));
        inv.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(inv);
        for (String line : new String[] {"Invoice no: " + r.getInvoiceNo(), "Booking no: " + r.getPassNo(),
                "Date: " + DATE.format(r.getPaidAt() != null ? r.getPaidAt() : r.getCreatedAt())}) {
            Paragraph p = new Paragraph(line, normal);
            p.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(p);
        }
        head.addCell(right);
        doc.add(head);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Billed to", small));
        doc.add(new Paragraph(r.getName() + "   ·   +" + r.getPhone(), bold));
        doc.add(new Paragraph(form.getTitle(), normal));
        doc.add(new Paragraph(" "));

        PdfPTable t = new PdfPTable(new float[] {0.6f, 5, 2, 2, 2});
        t.setWidthPercentage(100);
        for (String h : new String[] {"#", "Package", "MRP", "Discount", "Amount"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, white));
            c.setBackgroundColor(NAVY);
            c.setPadding(6);
            if (!h.equals("Package") && !h.equals("#")) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(c);
        }
        int i = 1;
        BigDecimal mrp = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (RegistrationItem it : items) {
            BigDecimal disc = it.getMrp().subtract(it.getPrice());
            t.addCell(pad(new Phrase(String.valueOf(i++), normal), false));
            t.addCell(pad(new Phrase(it.getName() + (it.getPassNo() == null ? "" : "\nPass ID: " + it.getPassNo()), normal), false));
            t.addCell(pad(new Phrase(rs(it.getMrp()), normal), true));
            t.addCell(pad(new Phrase(disc.signum() > 0 ? "- " + rs(disc) : "-", normal), true));
            t.addCell(pad(new Phrase(rs(it.getPrice()), bold), true));
            mrp = mrp.add(it.getMrp());
            total = total.add(it.getPrice());
        }
        doc.add(t);

        PdfPTable sum = new PdfPTable(new float[] {7, 2.5f});
        sum.setWidthPercentage(100);
        for (String[] row : new String[][] {{"Total MRP", rs(mrp)}, {"You saved", rs(mrp.subtract(total))}, {"Amount paid", rs(total)}}) {
            boolean last = row[0].equals("Amount paid");
            PdfPCell a = pad(new Phrase(row[0], last ? bold : normal), true);
            PdfPCell b = pad(new Phrase(row[1], last ? bold : normal), true);
            a.setBorder(Rectangle.NO_BORDER);
            b.setBorder(Rectangle.NO_BORDER);
            sum.addCell(a);
            sum.addCell(b);
        }
        doc.add(sum);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Payment: " + (r.getRazorpayPaymentId() == null ? "Free registration" : "Online (Razorpay) · " + r.getRazorpayPaymentId()
                + (r.getPaymentMethod() == null ? "" : " · " + r.getPaymentMethod())), normal));
        doc.add(new Paragraph("No GST or other tax is charged on this invoice.", small));
        doc.add(new Paragraph("This is a computer-generated invoice and does not need a signature.", small));
        doc.close();
        return out.toByteArray();
    }

    /** One page per pass image, for printing. */
    public byte[] passes(List<byte[]> jpgs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(doc, out);
        doc.open();
        try {
            for (byte[] jpg : jpgs) {
                Image img = Image.getInstance(jpg);
                img.scaleToFit(PageSize.A4.getWidth() - 60, PageSize.A4.getHeight() - 60);
                img.setAlignment(Element.ALIGN_CENTER);
                doc.add(img);
                doc.newPage();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Pass PDF could not be created", e);
        }
        doc.close();
        return out.toByteArray();
    }

    private static PdfPCell cell(int border) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        return c;
    }

    private static PdfPCell pad(Phrase p, boolean right) {
        PdfPCell c = new PdfPCell(p);
        c.setPadding(6);
        c.setBorderColor(new Color(0xE5, 0xE7, 0xEB));
        if (right) {
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        return c;
    }

    static String rs(BigDecimal v) {
        return "Rs. " + new java.text.DecimalFormat("#,##,##0.00").format(v);
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                sb.append(sb.length() == 0 ? "" : ", ").append(p.trim());
            }
        }
        return sb.toString();
    }
}
