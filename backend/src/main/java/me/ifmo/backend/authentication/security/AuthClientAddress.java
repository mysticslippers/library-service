package me.ifmo.backend.authentication.security;

import jakarta.servlet.http.HttpServletRequest;
import me.ifmo.backend.authentication.configuration.AuthProtectionProperties;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class AuthClientAddress {

    private final List<IpAddressMatcher> proxies;

    public AuthClientAddress(AuthProtectionProperties properties) {
        proxies = properties.getTrustedProxies().stream().map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = canonical(request.getRemoteAddr());

        if (proxies.stream().anyMatch(proxy -> proxy.matches(peer))) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null)
                return canonical(realIp.strip());
        }

        return peer;
    }

    private String canonical(String value) {
        try {
            if (value == null || value.length() > 64)
                throw new UnknownHostException();

            if (value.contains(":"))
                return canonicalIpv6(value);

            return canonicalIpv4(value);
        } catch (UnknownHostException | NumberFormatException exception) {
            throw new BusinessRuleException("Invalid client address");
        }
    }

    private String canonicalIpv6(String value) throws UnknownHostException {
        if (!value.matches("[0-9a-fA-F:.]+"))
            throw new UnknownHostException();

        return InetAddress.getByName(value).getHostAddress();
    }

    private String canonicalIpv4(String value) throws UnknownHostException {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4)
            throw new UnknownHostException();

        byte[] bytes = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("[0-9]{1,3}"))
                throw new UnknownHostException();

            int octet = Integer.parseInt(parts[i]);
            if (octet > 255)
                throw new UnknownHostException();

            bytes[i] = (byte) octet;
        }

        return InetAddress.getByAddress(bytes).getHostAddress();
    }
}
