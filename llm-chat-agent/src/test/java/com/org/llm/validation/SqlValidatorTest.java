package com.org.llm.validation;

import com.org.llm.exception.SqlValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    @DisplayName("Sanitize strips markdown code fences and a trailing semicolon")
    @Test
    void sanitizeStripsFencesAndTrailingSemicolon() {
        assertThat(validator.sanitize("```sql\nSELECT 1 FROM text2sql_orders;\n```"))
                .isEqualTo("SELECT 1 FROM text2sql_orders");
    }

    @DisplayName("Sanitize rejects blank SQL input with a validation exception")
    @Test
    void sanitizeRejectsBlankSql() {
        assertThatExceptionOfType(SqlValidationException.class).isThrownBy(() -> validator.sanitize("  "));
    }

    @DisplayName("ValidateReadOnly accepts a SELECT joining only allow-listed tables")
    @Test
    void validateReadOnlyAcceptsSelectOnAllowedTables() {
        String sql = "SELECT o.id FROM text2sql_orders o JOIN text2sql_customers c ON c.id = o.customer_id";
        assertThat(validator.validateReadOnly(sql)).isEqualTo(sql);
    }

    @DisplayName("ValidateReadOnly rejects mutating statements like DELETE")
    @Test
    void validateReadOnlyRejectsMutatingStatements() {
        assertThatExceptionOfType(SqlValidationException.class)
                .isThrownBy(() -> validator.validateReadOnly("DELETE FROM text2sql_orders"));
    }

    @DisplayName("ValidateReadOnly rejects a forbidden keyword smuggled after a SELECT statement")
    @Test
    void validateReadOnlyRejectsForbiddenKeywordInsideSelect() {
        assertThatExceptionOfType(SqlValidationException.class)
                .isThrownBy(() -> validator.validateReadOnly("SELECT 1 FROM text2sql_orders; DROP TABLE x"));
    }

    @DisplayName("ValidateReadOnly rejects queries against tables outside the allow list")
    @Test
    void validateReadOnlyRejectsTablesOutsideAllowList() {
        assertThatExceptionOfType(SqlValidationException.class)
                .isThrownBy(() -> validator.validateReadOnly("SELECT * FROM api_keys"));
    }

    @DisplayName("EnforceLimit appends a LIMIT clause when the query has none")
    @Test
    void enforceLimitAppendsLimitWhenMissing() {
        assertThat(validator.enforceLimit("SELECT * FROM text2sql_orders", 50))
                .isEqualTo("SELECT * FROM text2sql_orders LIMIT 50");
    }

    @DisplayName("EnforceLimit leaves existing LIMIT clauses and COUNT queries unchanged")
    @Test
    void enforceLimitKeepsExistingLimitAndCountQueries() {
        assertThat(validator.enforceLimit("SELECT * FROM text2sql_orders LIMIT 5", 50))
                .isEqualTo("SELECT * FROM text2sql_orders LIMIT 5");
        assertThat(validator.enforceLimit("SELECT COUNT(*) FROM text2sql_orders", 50))
                .isEqualTo("SELECT COUNT(*) FROM text2sql_orders");
    }

    @DisplayName("Prepare runs the full sanitize, validate, and limit-enforcement pipeline")
    @Test
    void prepareRunsFullPipeline() {
        assertThat(validator.prepare("```sql\nSELECT * FROM text2sql_products;\n```", 25))
                .isEqualTo("SELECT * FROM text2sql_products LIMIT 25");
    }
}
