package com.englishtranslation.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** TASK-79: when Tomcat refuses a file that is over spring.servlet.multipart.max-file-size, the admin gets a simple 413. */
class TooBigUploadAnswerTest {

    @Test
    void springsOwnLimitBecomes413WithASimpleMessage() {
        ApiExceptionHandler handler = new ApiExceptionHandler(DataSize.ofMegabytes(25));

        ProblemDetail problem = handler.handleTooBig(new MaxUploadSizeExceededException(26L * 1024 * 1024));

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getDetail()).isEqualTo("That file is too big. The limit is 25 MB.");
        assertThat(problem.getProperties()).containsEntry("code", "file_too_large");
    }
}
