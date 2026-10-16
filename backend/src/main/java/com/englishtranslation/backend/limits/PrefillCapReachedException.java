package com.englishtranslation.backend.limits;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** The prefill run spent its money cap. Only a prefill run can get this; the runner stops and keeps the item pending. */
public class PrefillCapReachedException extends ApiException {

    public PrefillCapReachedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "prefill_cap_reached", "The prefill run reached its money cap.");
    }
}
