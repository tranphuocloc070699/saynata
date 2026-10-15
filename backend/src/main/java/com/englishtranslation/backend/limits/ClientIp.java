package com.englishtranslation.backend.limits;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The IP of the client, read the same way as the admin login limit. Behind Caddy, Tomcat's remote-ip valve
 * (server.tomcat.remoteip.*) replaces the address with X-Forwarded-For, but only when the request comes from
 * a loopback or private proxy. The backend port is on loopback only, so a client cannot fake the header.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
