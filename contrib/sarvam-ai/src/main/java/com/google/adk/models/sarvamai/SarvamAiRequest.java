/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.models.sarvamai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.JsonBaseModel;
import com.google.adk.models.LlmRequest;
import com.google.adk.tools.BaseTool;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class is used to create a request to the Sarvam AI API.
 *
 * @author Sandeep Belgavi
 * @since 2026-02-11
 */
public class SarvamAiRequest {
  private static final ObjectMapper OBJECT_MAPPER = JsonBaseModel.getMapper();

  private String model;
  private List<SarvamAiMessage> messages;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<ToolDefinition> tools;

  @JsonProperty("tool_choice")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Object toolChoice;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Double temperature;

  @JsonProperty("top_p")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Double topP;

  @JsonProperty("max_tokens")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer maxTokens;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Object stop;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer n;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer seed;

  @JsonProperty("frequency_penalty")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Double frequencyPenalty;

  @JsonProperty("presence_penalty")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Double presencePenalty;

  @JsonProperty("reasoning_effort")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String reasoningEffort;

  @JsonProperty("wiki_grounding")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean wikiGrounding;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean stream;

  public SarvamAiRequest(String model, LlmRequest llmRequest) {
    this(model, llmRequest, false);
  }

  public SarvamAiRequest(String model, LlmRequest llmRequest, boolean stream) {
    this.model = model;
    this.stream = stream;
    this.messages = new ArrayList<>();
    List<String> systemInstructions = llmRequest.getSystemInstructions();
    if (!systemInstructions.isEmpty()) {
      String systemContent = String.join("\n\n", systemInstructions);
      this.messages.add(new SarvamAiMessage("system", systemContent));
    }
    for (Content content : llmRequest.contents()) {
      // Map ADK/Gemini role "model" -> OpenAI-compatible role "assistant"
      String role = content.role().orElse("user");
      if ("model".equals(role)) {
        role = "assistant";
      }
      for (Part part : content.parts().orElse(Collections.emptyList())) {
        if (part.text().isPresent()) {
          this.messages.add(new SarvamAiMessage(role, part.text().get()));
        } else if (part.functionCall().isPresent()) {
          var functionCall = part.functionCall().get();
          SarvamAiResponseMessage.Function function = new SarvamAiResponseMessage.Function();
          function.setName(functionCall.name().orElse(""));
          function.setArguments(toJsonString(functionCall.args().orElse(ImmutableMap.of()), "{}"));

          SarvamAiResponseMessage.ToolCall toolCall = new SarvamAiResponseMessage.ToolCall();
          toolCall.setId(functionCall.id().orElse("call_" + System.currentTimeMillis()));
          toolCall.setType("function");
          toolCall.setFunction(function);

          this.messages.add(
              new SarvamAiMessage("assistant", null, null, null, List.of(toolCall), function));
        } else if (part.functionResponse().isPresent()) {
          var functionResponse = part.functionResponse().get();
          String responseBody =
              toJsonString(functionResponse.response().orElse(ImmutableMap.of()), "{}");
          // tool_call_id is required - generate if missing for tool loop matching
          String toolCallId =
              functionResponse
                  .id()
                  .filter(id -> !id.isBlank())
                  .orElse("call_" + System.currentTimeMillis());
          // Tool messages: only role, content, tool_call_id (pass null for name, toolCalls,
          // functionCall)
          this.messages.add(
              new SarvamAiMessage("tool", responseBody, toolCallId, null, null, null));
        }
      }
    }

    this.tools = buildToolDefinitions(llmRequest.tools());
    this.toolChoice = this.tools.isEmpty() ? null : "auto";

    // Map config from LlmRequest.config() (GenerateContentConfig)
    Optional<GenerateContentConfig> configOpt = llmRequest.config();
    if (configOpt.isPresent()) {
      GenerateContentConfig config = configOpt.get();
      config.temperature().ifPresent(t -> this.temperature = t.doubleValue());
      config.topP().ifPresent(t -> this.topP = t.doubleValue());
      config.maxOutputTokens().ifPresent(this::setMaxTokens);
      config
          .stopSequences()
          .filter(s -> !s.isEmpty())
          .ifPresent(
              stopSeq -> {
                if (stopSeq.size() == 1) {
                  this.stop = stopSeq.get(0);
                } else {
                  this.stop = stopSeq;
                }
              });
      config.frequencyPenalty().ifPresent(f -> this.frequencyPenalty = f.doubleValue());
      config.presencePenalty().ifPresent(p -> this.presencePenalty = p.doubleValue());
    }
  }

  public String getModel() {
    return model;
  }

  public List<SarvamAiMessage> getMessages() {
    return messages;
  }

  public List<ToolDefinition> getTools() {
    return tools;
  }

  public Object getToolChoice() {
    return toolChoice;
  }

  public Double getTemperature() {
    return temperature;
  }

  public Double getTopP() {
    return topP;
  }

  public Integer getMaxTokens() {
    return maxTokens;
  }

  public void setMaxTokens(Integer maxTokens) {
    this.maxTokens = maxTokens;
  }

  public Object getStop() {
    return stop;
  }

  public Integer getN() {
    return n;
  }

  public Integer getSeed() {
    return seed;
  }

  public Double getFrequencyPenalty() {
    return frequencyPenalty;
  }

  public Double getPresencePenalty() {
    return presencePenalty;
  }

  public String getReasoningEffort() {
    return reasoningEffort;
  }

  public Boolean getWikiGrounding() {
    return wikiGrounding;
  }

  public Boolean getStream() {
    return stream;
  }

  private static List<ToolDefinition> buildToolDefinitions(Map<String, BaseTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return Collections.emptyList();
    }
    List<ToolDefinition> toolDefinitions = new ArrayList<>();
    for (BaseTool baseTool : tools.values()) {
      Optional<FunctionDeclaration> functionDeclarationOpt = baseTool.declaration();
      if (functionDeclarationOpt.isEmpty()) {
        continue;
      }
      FunctionDeclaration declaration = functionDeclarationOpt.get();
      FunctionDefinition function = new FunctionDefinition();
      function.setName(declaration.name().orElse(baseTool.name()));
      function.setDescription(declaration.description().orElse(baseTool.description()));
      function.setParameters(buildParametersSchema(declaration.parameters()));

      ToolDefinition toolDefinition = new ToolDefinition();
      toolDefinition.setType("function");
      toolDefinition.setFunction(function);
      toolDefinitions.add(toolDefinition);
    }
    return toolDefinitions;
  }

  private static Map<String, Object> buildParametersSchema(Optional<Schema> parameters) {
    if (parameters.isEmpty()) {
      return Map.of("type", "object", "properties", Map.of());
    }

    Map<String, Object> schemaMap =
        OBJECT_MAPPER.convertValue(parameters.get(), new TypeReference<Map<String, Object>>() {});
    if (!schemaMap.containsKey("type")) {
      schemaMap.put("type", "object");
    }
    return normalizeSchemaTypesToJsonSchema(schemaMap);
  }

  /**
   * Recursively normalizes schema type values to JSON Schema lowercase (e.g. STRING -> string).
   * Sarvam API expects JSON Schema; ADK/Gemini use uppercase type names.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> normalizeSchemaTypesToJsonSchema(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return map;
    }
    Map<String, Object> result = new java.util.LinkedHashMap<>(map);
    if (result.containsKey("type")) {
      Object type = result.get("type");
      String typeStr = null;
      if (type instanceof String s) {
        typeStr = s;
      } else if (type instanceof Map<?, ?> typeMap && typeMap.containsKey("knownEnum")) {
        typeStr = String.valueOf(typeMap.get("knownEnum"));
      }
      if (typeStr != null) {
        result.put("type", toJsonSchemaType(typeStr));
      }
    }
    if (result.containsKey("properties")) {
      Object props = result.get("properties");
      if (props instanceof Map<?, ?> propsMap) {
        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : propsMap.entrySet()) {
          if (e.getValue() instanceof Map<?, ?> nested) {
            normalized.put(
                String.valueOf(e.getKey()),
                normalizeSchemaTypesToJsonSchema((Map<String, Object>) nested));
          } else {
            normalized.put(String.valueOf(e.getKey()), e.getValue());
          }
        }
        result.put("properties", normalized);
      }
    }
    if (result.containsKey("items")) {
      Object items = result.get("items");
      if (items instanceof Map<?, ?> itemsMap) {
        result.put("items", normalizeSchemaTypesToJsonSchema((Map<String, Object>) itemsMap));
      }
    }
    return result;
  }

  private static String toJsonSchemaType(String type) {
    if (type == null) return "string";
    return switch (type.toUpperCase()) {
      case "STRING" -> "string";
      case "NUMBER" -> "number";
      case "INTEGER" -> "integer";
      case "BOOLEAN" -> "boolean";
      case "ARRAY" -> "array";
      case "OBJECT" -> "object";
      case "NULL" -> "null";
      default -> type.toLowerCase();
    };
  }

  private static String toJsonString(Object value, String fallback) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value);
    } catch (Exception ignored) {
      return fallback;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ToolDefinition {
    private String type;
    private FunctionDefinition function;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public FunctionDefinition getFunction() {
      return function;
    }

    public void setFunction(FunctionDefinition function) {
      this.function = function;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class FunctionDefinition {
    private String name;
    private String description;
    private Map<String, Object> parameters;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Map<String, Object> getParameters() {
      return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
      this.parameters = parameters;
    }
  }
}
