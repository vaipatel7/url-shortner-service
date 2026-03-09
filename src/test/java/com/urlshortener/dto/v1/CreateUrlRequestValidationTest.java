package com.urlshortener.dto.v1;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CreateUrlRequest} Bean Validation constraints.
 *
 * Exercises the validator directly — no HTTP stack required. This ensures
 * that constraint annotations on the DTO are correct independently of the
 * JAX-RS layer that triggers them at runtime.
 */
class CreateUrlRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // -------------------------------------------------------------------------
    // longUrl — max 2048 characters
    // -------------------------------------------------------------------------

    @Test
    void longUrl_exactly2048Characters_passesValidation() {
        String url = "https://example.com/" + "a".repeat(2048 - "https://example.com/".length());
        assertThat(url).hasSize(2048);

        Set<ConstraintViolation<CreateUrlRequest>> violations = validate(url, null, null);

        assertThat(violations).isEmpty();
    }

    @Test
    void longUrl_2049Characters_failsValidation() {
        String url = "https://example.com/" + "a".repeat(2049 - "https://example.com/".length() + 1);
        assertThat(url.length()).isGreaterThan(2048);

        Set<ConstraintViolation<CreateUrlRequest>> violations = validate(url, null, null);

        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("longUrl") &&
                v.getMessage().contains("2048"));
    }

    @Test
    void longUrl_2049Characters_violationIsOnLongUrlField() {
        String url = "https://" + "x".repeat(2042);
        assertThat(url).hasSize(2050);

        Set<ConstraintViolation<CreateUrlRequest>> violations = validate(url, null, null);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("longUrl");
    }

    @Test
    void longUrl_typicalUrl_passesValidation() {
        Set<ConstraintViolation<CreateUrlRequest>> violations =
                validate("https://example.com/some/path?q=1", null, null);

        assertThat(violations).isEmpty();
    }

    // -------------------------------------------------------------------------
    // longUrl — existing constraints still enforced
    // -------------------------------------------------------------------------

    @Test
    void longUrl_blank_failsNotBlank() {
        Set<ConstraintViolation<CreateUrlRequest>> violations = validate("   ", null, null);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("longUrl");
    }

    @Test
    void longUrl_null_failsNotBlank() {
        Set<ConstraintViolation<CreateUrlRequest>> violations = validate(null, null, null);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("longUrl");
    }

    @Test
    void longUrl_missingScheme_failsPatternConstraint() {
        Set<ConstraintViolation<CreateUrlRequest>> violations =
                validate("example.com/path", null, null);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("longUrl");
    }

    @Test
    void longUrl_ftpScheme_failsPatternConstraint() {
        Set<ConstraintViolation<CreateUrlRequest>> violations =
                validate("ftp://example.com/file.txt", null, null);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("longUrl");
    }

    // -------------------------------------------------------------------------
    // alias — size 6–20 (unchanged, regression guard)
    // -------------------------------------------------------------------------

    @Test
    void alias_5Characters_failsSizeConstraint() {
        Set<ConstraintViolation<CreateUrlRequest>> violations =
                validate("https://example.com", null, "short");

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("alias");
    }

    @Test
    void alias_null_passesValidation() {
        Set<ConstraintViolation<CreateUrlRequest>> violations =
                validate("https://example.com", null, null);

        assertThat(violations).isEmpty();
    }

    @Test
    void alias_20Characters_passesValidation() {
        Set<ConstraintViolation<CreateUrlRequest>> violations =
                validate("https://example.com", null, "a".repeat(20));

        assertThat(violations).isEmpty();
    }

    @Test
    void alias_21Characters_failsSizeConstraint() {
        Set<ConstraintViolation<CreateUrlRequest>> violations =
                validate("https://example.com", null, "a".repeat(21));

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("alias");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Set<ConstraintViolation<CreateUrlRequest>> validate(String longUrl, String domain, String alias) {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setLongUrl(longUrl);
        req.setDomain(domain);
        req.setAlias(alias);
        return validator.validate(req);
    }
}
