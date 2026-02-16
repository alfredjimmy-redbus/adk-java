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
import java.util.List;

/**
 * This class is used to represent a message from the Sarvam AI API.
 *
 * @author Sandeep Belgavi
 * @since 2026-02-11
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SarvamAiMessage {

  private String role;
  private String content;

  @JsonProperty("tool_call_id")
  private String toolCallId;

  private String name;

  @JsonProperty("tool_calls")
  private List<SarvamAiResponseMessage.ToolCall> toolCalls;

  @JsonProperty("function_call")
  private SarvamAiResponseMessage.Function functionCall;

  public SarvamAiMessage(String role, String content) {
    this.role = role;
    this.content = content;
  }

  public SarvamAiMessage(
      String role,
      String content,
      String toolCallId,
      String name,
      List<SarvamAiResponseMessage.ToolCall> toolCalls,
      SarvamAiResponseMessage.Function functionCall) {
    this.role = role;
    this.content = content;
    this.toolCallId = toolCallId;
    this.name = name;
    this.toolCalls = toolCalls;
    this.functionCall = functionCall;
  }

  public String getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public String getToolCallId() {
    return toolCallId;
  }

  public String getName() {
    return name;
  }

  public List<SarvamAiResponseMessage.ToolCall> getToolCalls() {
    return toolCalls;
  }

  public SarvamAiResponseMessage.Function getFunctionCall() {
    return functionCall;
  }
}
