package com.urbansidequest.backend.config;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class PostgresTextArrayTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            List<String> parameter,
            JdbcType jdbcType
    ) throws SQLException {
        Array array = preparedStatement.getConnection().createArrayOf("text", parameter.toArray());
        preparedStatement.setArray(index, array);
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return this.toList(resultSet.getArray(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return this.toList(resultSet.getArray(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return this.toList(callableStatement.getArray(columnIndex));
    }

    private List<String> toList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        if (value instanceof String[] strings) {
            return Arrays.asList(strings);
        }
        return List.of();
    }
}
