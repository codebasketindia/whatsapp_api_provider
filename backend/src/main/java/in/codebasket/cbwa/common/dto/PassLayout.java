package in.codebasket.cbwa.common.dto;

/**
 * Where the name, mobile number and (optional) photo are written on the pass design. Boxes are
 * fractions (0..1) of the design's width/height so the same layout works at any image size.
 */
public record PassLayout(Box name, Box mobile, Box photo, Box passNo, String textColor, String fontFamily) {

    public record Box(double x, double y, double w, double h) {
    }

    /** Calibrated for the built-in "Harmain Ziyarat Pass" design (1599 × 2000 px). */
    public static PassLayout harmainDefault() {
        double W = 1599;
        double H = 2000;
        return new PassLayout(
                new Box(536 / W, 1194 / H, 737 / W, 61 / H),
                new Box(536 / W, 1278 / H, 737 / W, 60 / H),
                new Box(694 / W, 951 / H, 208 / W, 168 / H),
                null,
                "#0B5A2E", null);
    }
}
