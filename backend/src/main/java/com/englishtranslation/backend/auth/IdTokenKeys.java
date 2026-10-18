package com.englishtranslation.backend.auth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/** Where the public keys that sign a provider's ID tokens come from. Tests give their own key, so no network call. */
public interface IdTokenKeys {

    JWKSource<SecurityContext> keysFor(SocialProvider provider);
}
