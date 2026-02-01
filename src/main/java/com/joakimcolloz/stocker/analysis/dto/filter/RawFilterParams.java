package com.joakimcolloz.stocker.analysis.dto.filter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RawFilterParams implements FilterParams {
	private final JsonNode value;

	@JsonCreator
	public static RawFilterParams fromJson(JsonNode value) {
		return new RawFilterParams(value);
	}

	@JsonValue
	public JsonNode toJson() {
		return value;
	}
}
