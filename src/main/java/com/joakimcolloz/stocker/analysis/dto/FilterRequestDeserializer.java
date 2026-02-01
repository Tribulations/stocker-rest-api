package com.joakimcolloz.stocker.analysis.dto;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class FilterRequestDeserializer extends StdDeserializer<FilterRequest> {
	public FilterRequestDeserializer() {
		super(FilterRequest.class);
	}

	@Override
	public FilterRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		ObjectMapper mapper = (ObjectMapper) p.getCodec();
		JsonNode node = mapper.readTree(p);

		JsonNode typeNode = node.get("type");
		String type = typeNode == null || typeNode.isNull() ? null : typeNode.asText();

		JsonNode paramsNode = node.get("params");
		FilterParams params;

		if ("RSI".equals(type)) {
			params = paramsNode == null || paramsNode.isNull()
					? null
					: mapper.treeToValue(paramsNode, RsiFilterParams.class);
		} else if ("HIGH_VOLUME".equals(type)) {
			params = paramsNode == null || paramsNode.isNull()
					? null
					: mapper.treeToValue(paramsNode, HighVolumeFilterParams.class);
		} else {
			params = new RawFilterParams(paramsNode);
		}

		FilterRequest result = new FilterRequest();
		result.setType(type);
		result.setParams(params);
		return result;
	}
}
