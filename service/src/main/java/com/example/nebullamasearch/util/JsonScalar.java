package com.example.nebullamasearch.util;

import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class JsonScalar {

  public static final GraphQLScalarType JSON =
      GraphQLScalarType.newScalar()
          .name("JSON")
          .description("Arbitrary JSON value")
          .coercing(
              new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object dataFetcherResult) {
                  return dataFetcherResult;
                }

                @Override
                public Object parseValue(Object input) {
                  return input;
                }

                @Override
                public Object parseLiteral(Object input) {
                  return parseLiteralValue((Value<?>) input);
                }

                private Object parseLiteralValue(Value<?> value) {
                  if (value instanceof NullValue) return null;
                  if (value instanceof BooleanValue bv) return bv.isValue();
                  if (value instanceof IntValue iv) return iv.getValue().intValueExact();
                  if (value instanceof FloatValue fv) return fv.getValue().doubleValue();
                  if (value instanceof StringValue sv) return sv.getValue();
                  if (value instanceof ArrayValue av) {
                    return av.getValues().stream()
                        .map(this::parseLiteralValue)
                        .collect(Collectors.toList());
                  }
                  if (value instanceof ObjectValue ov) {
                    final Map<String, Object> map = new LinkedHashMap<>();
                    for (ObjectField field : ov.getObjectFields()) {
                      map.put(field.getName(), parseLiteralValue(field.getValue()));
                    }
                    return map;
                  }
                  throw new IllegalArgumentException(
                      "Unsupported literal type: " + value.getClass());
                }
              })
          .build();

  private JsonScalar() {}
}
