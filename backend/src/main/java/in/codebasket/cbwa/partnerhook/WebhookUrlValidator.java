package in.codebasket.cbwa.partnerhook;

import in.codebasket.cbwa.common.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF protection for partner webhook URLs: https only and the host must resolve to public
 * addresses (no loopback, private, link-local, CGNAT, multicast or IPv6 ULA ranges). Checked when
 * the URL is saved and again before every delivery (DNS may change).
 *
 * cbwa.partner-webhooks.allow-insecure=true (tests / local development only) allows http and
 * private hosts so a local receiver can be used.
 */
@Component
public class WebhookUrlValidator {

    private final boolean allowInsecure;

    public WebhookUrlValidator(@Value("${cbwa.partner-webhooks.allow-insecure:false}") boolean allowInsecure) {
        this.allowInsecure = allowInsecure;
    }

    public String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw AppException.field("url", "Webhook URL is required");
        }
        String url = raw.trim();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw AppException.field("url", "Enter a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"https".equals(scheme) && !(allowInsecure && "http".equals(scheme))) {
            throw AppException.field("url", "Webhook URL must start with https://");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw AppException.field("url", "Enter a valid URL");
        }
        if (!allowInsecure) {
            checkPublicHost(uri.getHost());
        }
        return url;
    }

    /** True when the URL may be called now (re-check at delivery time). */
    public boolean isDeliverable(String url) {
        try {
            validate(url);
            return true;
        } catch (AppException e) {
            return false;
        }
    }

    private static void checkPublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw AppException.field("url", "The host name could not be found");
        }
        for (InetAddress a : addresses) {
            if (isPrivate(a)) {
                throw AppException.field("url", "Webhook URL must point to a public internet address");
            }
        }
    }

    static boolean isPrivate(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0 || first == 10 || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        if (a instanceof Inet6Address) {
            int first = b[0] & 0xff;
            boolean ula = (first & 0xfe) == 0xfc;
            boolean mappedV4 = b[10] == (byte) 0xff && b[11] == (byte) 0xff && allZero(b, 0, 10);
            if (mappedV4) {
                try {
                    return isPrivate(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
            return ula;
        }
        return false;
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
