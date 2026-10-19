package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.common.exception.AppException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Validates IP whitelist entries: IPv4 / IPv6 literals only (no host names, no CIDR). */
public final class IpAddressValidator {

    private static final Pattern IPV4 = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    public static final int MAX_ENTRIES = 20;

    private IpAddressValidator() {
    }

    public static List<String> normalise(List<String> ips) {
        if (ips == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : ips) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String ip = raw.trim();
            if (!isIpLiteral(ip)) {
                throw AppException.field("ipWhitelist", ip + " is not a valid IPv4 or IPv6 address");
            }
            out.add(ip);
        }
        if (out.size() > MAX_ENTRIES) {
            throw AppException.field("ipWhitelist", "At most " + MAX_ENTRIES + " IP addresses are allowed");
        }
        return new ArrayList<>(out);
    }

    public static boolean isIpLiteral(String ip) {
        if (IPV4.matcher(ip).matches()) {
            return true;
        }
        if (ip.contains(":") && IPV6_CHARS.matcher(ip).matches()) {
            try {
                // no DNS lookup happens for literals containing ':'
                InetAddress.getByName(ip);
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
        return false;
    }
}
