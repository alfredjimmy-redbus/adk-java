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

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<FunctionDefinition> functions;

  @JsonProperty("tool_choice")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String toolChoice;

  @JsonProperty("function_call")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String functionCall;

  public SarvamAiRequest(String model, LlmRequest llmRequest) {
    this.model = model;
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
          this.messages.add(
              new SarvamAiMessage(
                  "tool",
                  responseBody,
                  functionResponse.id().orElse(null),
                  functionResponse.name().orElse(null),
                  null,
                  null));
        }
      }
    }

    this.tools = buildToolDefinitions(llmRequest.tools());
    this.functions = this.tools.stream().map(ToolDefinition::getFunction).toList();
    this.toolChoice = this.tools.isEmpty() ? null : "auto";
    this.functionCall = this.functions.isEmpty() ? null : "auto";
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

  public String getToolChoice() {
    return toolChoice;
  }

  public List<FunctionDefinition> getFunctions() {
    return functions;
  }

  public String getFunctionCall() {
    return functionCall;
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
    return schemaMap;
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
