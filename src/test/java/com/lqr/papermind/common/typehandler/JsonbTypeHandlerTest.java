package com.lqr.papermind.common.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JsonbTypeHandlerTest {

    private final JsonbTypeHandler handler = new JsonbTypeHandler();

    @Test
    void setNonNullParameterShouldSerializeJavaTimeValuesAsIsoStrings() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignmentStatus", "SUBMITTED");
        snapshot.put("submittedAt", OffsetDateTime.parse("2026-06-10T21:20:30+08:00"));
        snapshot.put("reviewedAt", LocalDateTime.parse("2026-06-10T21:20:30"));
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, snapshot, JdbcType.OTHER);

        ArgumentCaptor<Object> parameterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(statement).setObject(org.mockito.ArgumentMatchers.eq(1), parameterCaptor.capture());
        PGobject jsonObject = (PGobject) parameterCaptor.getValue();
        assertThat(jsonObject.getType()).isEqualTo("jsonb");
        assertThat(jsonObject.getValue())
                .contains("\"submittedAt\":\"2026-06-10T21:20:30+08:00\"")
                .contains("\"reviewedAt\":\"2026-06-10T21:20:30\"")
                .doesNotContain("[2026,6,10");
    }
}
