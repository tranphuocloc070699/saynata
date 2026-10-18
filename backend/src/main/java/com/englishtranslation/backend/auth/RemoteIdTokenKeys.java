package com.englishtranslation.backend.auth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The real keys: Google's and Apple's JWKS addresses. Nimbus fetches each set on first use (not at start-up), keeps
 * it in memory, and fetches again when a token names a key it does not know (the providers rotate their keys).
 */
@Component
public class RemoteIdTokenKeys implements IdTokenKeys {

    private final Map<SocialProvider, JWKSource<SecurityContext>> sources = new EnumMap<>(SocialProvider.class);

    public RemoteIdTokenKeys(SocialAuthProperties properties) {
        for (SocialProvider provider : SocialProvider.values()) {
            String url = properties.jwksUrlFor(provider);
            try {
                sources.put(provider, JWKSourceBuilder.<SecurityContext>create(URI.create(url).toURL())
                        .retrying(true)
                        .build());
            } catch (MalformedURLException | IllegalArgumentException e) {
                throw new IllegalStateException("Bad JWKS address for " + provider + ": " + url, e);
            }
        }
    }

    @Override
    public JWKSource<SecurityContext> keysFor(SocialProvider provider) {
        return sources.get(provider);
    }
}
