package in.codebasket.cbwa.invoice;

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
import in.codebasket.cbwa.common.util.MoneyUtil;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Renders a GST tax invoice / receipt as PDF (OpenPDF). Pure function of its inputs. */
public final class InvoicePdfRenderer {

    public record Company(String name, String address, String gstin, String stateCode, String email, String phone, byte[] logo) {
    }

    private static final Color GREEN = new Color(0x12, 0x8C, 0x7E);
    private static final Color LIGHT = new Color(0xF3, 0xF4, 0xF6);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private InvoicePdfRenderer() {
    }

    public static byte[] render(Invoice inv, Company company, String sacCode, BigDecimal gstPercent, String description) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, GREEN);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            PdfPTable head = new PdfPTable(new float[]{3, 2});
            head.setWidthPercentage(100);
            PdfPCell left = noBorder();
            if (company.logo() != null) {
                try {
                    Image logo = Image.getInstance(company.logo());
                    logo.scaleToFit(120, 50);
                    left.addElement(logo);
                } catch (Exception ignored) {
                    // a broken logo must never block invoicing
                }
            }
            left.addElement(new Paragraph(nz(company.name()), bold));
            if (company.address() != null) {
                left.addElement(new Paragraph(company.address(), normal));
            }
            if (company.gstin() != null) {
                left.addElement(new Paragraph("GSTIN: " + company.gstin(), normal));
            }
            if (company.email() != null || company.phone() != null) {
                left.addElement(new Paragraph(String.join("  ", nz(company.email()), nz(company.phone())).trim(), normal));
            }
            head.addCell(left);
            PdfPCell right = noBorder();
            boolean taxInvoice = inv.getCgstAmount().signum() > 0 || inv.getSgstAmount().signum() > 0 || inv.getIgstAmount().signum() > 0;
            Paragraph t = new Paragraph(taxInvoice ? "TAX INVOICE" : "INVOICE", title);
            t.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(t);
            Paragraph meta = new Paragraph("Invoice No: " + inv.getInvoiceNo() + "\nDate: " + DATE.format(inv.getInvoiceDate())
                    + (inv.getPlaceOfSupply() == null ? "" : "\nPlace of supply: " + inv.getPlaceOfSupply()), normal);
            meta.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(meta);
            head.addCell(right);
            doc.add(head);

            doc.add(new Paragraph(" "));
            Paragraph bill = new Paragraph("Bill to", bold);
            doc.add(bill);
            doc.add(new Paragraph(inv.getBillingName(), normal));
            if (inv.getBillingAddress() != null && !inv.getBillingAddress().isBlank()) {
                doc.add(new Paragraph(inv.getBillingAddress(), normal));
            }
            if (inv.getGstin() != null) {
                doc.add(new Paragraph("GSTIN: " + inv.getGstin(), normal));
            }
            doc.add(new Paragraph(" "));

            PdfPTable items = new PdfPTable(new float[]{5, 2, 2});
            items.setWidthPercentage(100);
            items.addCell(header("Description", bold));
            items.addCell(header("SAC", bold));
            items.addCell(header("Amount (INR)", bold));
            items.addCell(cell(description, normal, Element.ALIGN_LEFT));
            items.addCell(cell(nz(sacCode), normal, Element.ALIGN_CENTER));
            items.addCell(cell(money(inv.getTaxableAmount()), normal, Element.ALIGN_RIGHT));
            addTotalRow(items, "Taxable value", inv.getTaxableAmount(), normal);
            String half = gstPercent == null ? "" : " @ " + gstPercent.divide(new BigDecimal("2")).stripTrailingZeros().toPlainString() + "%";
            if (inv.getCgstAmount().signum() > 0) {
                addTotalRow(items, "CGST" + half, inv.getCgstAmount(), normal);
                addTotalRow(items, "SGST" + half, inv.getSgstAmount(), normal);
            }
            if (inv.getIgstAmount().signum() > 0) {
                addTotalRow(items, "IGST" + (gstPercent == null ? "" : " @ " + gstPercent.stripTrailingZeros().toPlainString() + "%"),
                        inv.getIgstAmount(), normal);
            }
            addTotalRow(items, "Total", inv.getTotalAmount(), bold);
            doc.add(items);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Amount in words: " + AmountInWords.rupees(inv.getTotalAmount()), normal));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("This is a computer generated invoice and does not need a signature.", small));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Invoice PDF generation failed", e);
        }
    }

    private static void addTotalRow(PdfPTable t, String label, BigDecimal value, Font f) {
        PdfPCell l = cell(label, f, Element.ALIGN_RIGHT);
        l.setColspan(2);
        t.addCell(l);
        t.addCell(cell(money(value), f, Element.ALIGN_RIGHT));
    }

    private static PdfPCell header(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(LIGHT);
        c.setPadding(6);
        return c;
    }

    private static PdfPCell cell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        return c;
    }

    private static PdfPCell noBorder() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static String money(BigDecimal v) {
        return MoneyUtil.rupees(v).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
