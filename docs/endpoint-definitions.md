
Backtest endpoint.
POST /api/analysis/backtest

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://stocker.local/schemas/BacktestRequest.json",
  "title": "BacktestRequest",
  "type": "object",
  "additionalProperties": false,
  "required": ["entryConditions", "exitRule", "startTimestamp", "endTimestamp"],
  "properties": {
    "entryConditions": {
      "type": "array",
      "minItems": 1,
      "items": { "$ref": "#/$defs/FilterRequest" }
    },
    "exitRule": { "$ref": "#/$defs/ExitRule" },
    "startTimestamp": { "type": "integer", "exclusiveMinimum": 0 },
    "endTimestamp": { "type": "integer", "exclusiveMinimum": 0 },
    "symbols": {
      "type": "array",
      "items": { "type": "string" }
    },
    "startingCapital": { "type": "number" },
    "entryRule": { "$ref": "#/$defs/EntryRule" },
    "exitConditions": {
      "type": "array",
      "items": { "$ref": "#/$defs/FilterRequest" }
    }
  },
  "$defs": {
    "ExitRule": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "stopLossPercent": { "type": ["number", "null"] },
        "takeProfitPercent": { "type": ["number", "null"] },
        "maxHoldingDays": { "type": ["integer", "null"] },
        "trailingStopLoss": { "type": ["boolean", "null"] }
      },
      "anyOf": [
        { "required": ["stopLossPercent"] },
        { "required": ["takeProfitPercent"] },
        { "required": ["maxHoldingDays"] }
      ],
      "allOf": [
        {
          "if": {
            "properties": { "trailingStopLoss": { "const": true } },
            "required": ["trailingStopLoss"]
          },
          "then": { "required": ["stopLossPercent"] }
        }
      ]
    },

    "EntryRule": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "entryMode": {
          "type": ["string", "null"],
          "enum": ["NEXT_BAR_OPEN", "LIMIT_ORDER", null]
        },
        "limitOffsetPercent": { "type": ["number", "null"] },
        "penetrationPercent": { "type": ["number", "null"] },
        "maxBarsToFill": { "type": ["integer", "null"] },
        "limitDirection": {
          "type": ["string", "null"],
          "enum": ["ABOVE", "BELOW", null]
        }
      }
    },

    "FilterRequest": {
      "type": "object",
      "additionalProperties": false,
      "required": ["type"],
      "properties": {
        "type": {
          "type": ["string", "null"],
          "enum": [
            "RSI",
            "HIGH_VOLUME",
            "MACD",
            "PATTERN",
            "MA_CROSSOVER",
            null
          ]
        },
        "params": {
          "oneOf": [
            { "$ref": "#/$defs/RsiFilterParams" },
            { "$ref": "#/$defs/HighVolumeFilterParams" },
            { "$ref": "#/$defs/MacdFilterParams" },
            { "$ref": "#/$defs/PatternFilterParams" },
            { "$ref": "#/$defs/MovingAverageFilterParams" },
            { "$ref": "#/$defs/RawFilterParams" },
            { "type": "null" }
          ]
        }
      },
      "allOf": [
        {
          "if": { "properties": { "type": { "const": "RSI" } }, "required": ["type"] },
          "then": { "properties": { "params": { "$ref": "#/$defs/RsiFilterParams" } } }
        },
        {
          "if": { "properties": { "type": { "const": "HIGH_VOLUME" } }, "required": ["type"] },
          "then": { "properties": { "params": { "$ref": "#/$defs/HighVolumeFilterParams" } } }
        },
        {
          "if": { "properties": { "type": { "const": "MACD" } }, "required": ["type"] },
          "then": { "properties": { "params": { "$ref": "#/$defs/MacdFilterParams" } } }
        },
        {
          "if": { "properties": { "type": { "const": "PATTERN" } }, "required": ["type"] },
          "then": { "properties": { "params": { "$ref": "#/$defs/PatternFilterParams" } } }
        },
        {
          "if": { "properties": { "type": { "const": "MA_CROSSOVER" } }, "required": ["type"] },
          "then": { "properties": { "params": { "$ref": "#/$defs/MovingAverageFilterParams" } } }
        }
      ]
    },

    "RsiFilterParams": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "threshold": { "type": ["number", "null"] },
        "condition": {
          "type": ["string", "null"],
          "enum": ["above", "below", null]
        }
      }
    },

    "HighVolumeFilterParams": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "percentageAbove": { "type": ["integer", "null"] }
      }
    },

    "MacdFilterParams": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "condition": { "type": ["string", "null"] },
        "fastPeriod": { "type": ["integer", "null"] },
        "slowPeriod": { "type": ["integer", "null"] },
        "signalPeriod": { "type": ["integer", "null"] }
      }
    },

    "PatternFilterParams": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "pattern": { "type": ["string", "null"] }
      }
    },

    "MovingAverageFilterParams": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "period": { "type": ["integer", "null"] },
        "maType": {
          "type": ["string", "null"],
          "enum": ["SMA", "EMA", null]
        },
        "condition": {
          "type": ["string", "null"],
          "enum": [
            "price_above_ma",
            "price_below_ma",
            "price_crosses_above_ma",
            "price_crosses_below_ma",
            null
          ]
        }
      }
    },

    "RawFilterParams": {
      "description": "Fallback for unknown filter types; accepts any JSON value under params",
      "type": ["object", "array", "string", "number", "integer", "boolean", "null"]
    }
  }
}
```

JSON definition: GET /api/analysis/indicators/supported response
This endpoint returns: List<SupportedFilterInfo>

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://stocker.local/schemas/IndicatorsSupportedResponse.json",
  "title": "IndicatorsSupportedResponse",
  "type": "array",
  "items": { "$ref": "#/$defs/SupportedFilterInfo" },
  "$defs": {
    "SupportedFilterInfo": {
      "type": "object",
      "additionalProperties": false,
      "required": ["type", "name", "description", "params"],
      "properties": {
        "type": {
          "type": "string",
          "enum": ["RSI", "MACD"]
        },
        "name": { "type": "string" },
        "description": { "type": "string" },
        "params": {
          "type": "object",
          "additionalProperties": { "type": "string" },
          "required": ["symbol", "startTimestamp", "endTimestamp"],
          "properties": {
            "symbol": { "type": "string" },
            "startTimestamp": { "type": "string" },
            "endTimestamp": { "type": "string" },

            "period": { "type": "string" },

            "fastPeriod": { "type": "string" },
            "slowPeriod": { "type": "string" },
            "signalPeriod": { "type": "string" }
          }
        }
      }
    }
  }
}
```

JSON definition: GET /api/analysis/backtest/supported response
This endpoint returns: Map<String, Object> with keys entryRule, exitRule, filters, patterns.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://stocker.local/schemas/BacktestSupportedResponse.json",
  "title": "BacktestSupportedResponse",
  "type": "object",
  "additionalProperties": false,
  "required": ["entryRule", "exitRule", "filters", "patterns"],
  "properties": {
    "entryRule": {
      "type": "object",
      "additionalProperties": false,
      "required": ["entryMode", "limitDirection"],
      "properties": {
        "entryMode": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": ["NEXT_BAR_OPEN", "LIMIT_ORDER"]
          }
        },
        "limitDirection": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": ["BELOW", "ABOVE"]
          }
        }
      }
    },

    "exitRule": {
      "type": "object",
      "additionalProperties": false,
      "required": ["fields", "notes"],
      "properties": {
        "fields": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": [
              "stopLossPercent",
              "takeProfitPercent",
              "maxHoldingDays",
              "trailingStopLoss"
            ]
          }
        },
        "notes": {
          "type": "array",
          "items": { "type": "string" }
        }
      }
    },

    "filters": {
      "type": "array",
      "items": { "$ref": "#/$defs/SupportedFilterInfo" }
    },

    "patterns": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Pattern codes from PatternType.supportedPatterns().code"
    }
  },

  "$defs": {
    "SupportedFilterInfo": {
      "type": "object",
      "additionalProperties": false,
      "required": ["type", "name", "description", "params"],
      "properties": {
        "type": {
          "type": "string",
          "enum": ["HIGH_VOLUME", "RSI", "MACD", "PATTERN", "MA_CROSSOVER"]
        },
        "name": { "type": "string" },
        "description": { "type": "string" },
        "params": {
          "type": "object",
          "additionalProperties": { "type": "string" },
          "description": "Map of parameter name -> human-readable description"
        }
      }
    }
  }
}
```
