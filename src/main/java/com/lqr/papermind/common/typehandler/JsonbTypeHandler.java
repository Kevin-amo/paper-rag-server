package com.lqr.papermind.common.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL json/jsonb 字段与 Java Object/Map/List 之间的通用转换。
 */
@MappedJdbcTypes({JdbcType.OTHER, JdbcType.VARCHAR})
@MappedTypes({Object.class})
public class JsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 将 Java 对象序列化为 PostgreSQL jsonb 参数。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(toJson(parameter));
        ps.setObject(i, jsonObject);
    }

    /**
     * 按列名读取可为空的 JSON 字段。
     */
    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getObject(columnName));
    }

    /**
     * 按列索引读取可为空的 JSON 字段。
     */
    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getObject(columnIndex));
    }

    /**
     * 从存储过程调用结果中读取可为空的 JSON 字段。
     */
    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getObject(columnIndex));
    }

    /**
     * 将数据库返回的 JSON 文本反序列化为 Java 对象。
     */
    private Object parseJson(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(String.valueOf(value), Object.class);
        } catch (JsonProcessingException ex) {
            throw new SQLException("JSON 字段反序列化失败", ex);
        }
    }

    /**
     * 将 Java 对象序列化为 JSON 文本。
     */
    private String toJson(Object value) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new SQLException("JSON 字段序列化失败", ex);
        }
    }
}
