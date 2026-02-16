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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * This class is used to represent a response message from the Sarvam AI API.
 *
 * @author Sandeep Belgavi
 * @since 2026-02-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SarvamAiResponseMessage {

  private String content;

  @JsonProperty("tool_calls")
  private List<ToolCall> toolCalls;

  @JsonProperty("function_call")
  private Function functionCall;

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public List<ToolCall> getToolCalls() {
    return toolCalls;
  }

  public void setToolCalls(List<ToolCall> toolCalls) {
    this.toolCalls = toolCalls;
  }

  public Function getFunctionCall() {
    return functionCall;
  }

  public void setFunctionCall(Function functionCall) {
    this.functionCall = functionCall;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ToolCall {
    private String id;
    private String type;
    private Function function;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Function getFunction() {
      return function;
    }

    public void setFunction(Function function) {
      this.function = function;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Function {
    private String name;
    private String arguments;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getArguments() {
      return arguments;
    }

    public void setArguments(String arguments) {
      this.arguments = arguments;
    }
  }
}
