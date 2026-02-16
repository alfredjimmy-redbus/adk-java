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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sarvam API-backed toolset (TTS, STT, Translate, Document Digitization).
 *
 * <p>Designed to share the same API key used by {@link SarvamAi} so end users do not need separate
 * per-tool configuration.
 */
public class SarvamAiToolset {
  private static final String DEFAULT_API_BASE_URL = "https://api.sarvam.ai";
  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
  private static final MediaType OCTET_STREAM_MEDIA_TYPE =
      MediaType.get("application/octet-stream");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final OkHttpClient httpClient;
  private final String apiKey;
  private final String apiBaseUrl;

  public SarvamAiToolset(SarvamAiConfig config) {
    this(config, DEFAULT_API_BASE_URL);
  }

  public SarvamAiToolset(SarvamAiConfig config, String apiBaseUrl) {
    this.httpClient = new OkHttpClient();
    this.apiKey = requireNonEmpty(config.getApiKey(), "apiKey");
    this.apiBaseUrl = requireNonEmpty(apiBaseUrl, "apiBaseUrl");
  }

  /** Returns all Sarvam function tools. */
  public List<FunctionTool> allTools() {
    return List.of(
        textToSpeechTool(), speechToTextTool(), translateTool(), documentDigitizationTool());
  }

  public FunctionTool textToSpeechTool() {
    return FunctionTool.create(this, "convertTextToSpeech");
  }

  public FunctionTool speechToTextTool() {
    return FunctionTool.create(this, "convertSpeechToText");
  }

  public FunctionTool translateTool() {
    return FunctionTool.create(this, "translateText");
  }

  /** Doc digitization can run for longer due to async job polling. */
  public FunctionTool documentDigitizationTool() {
    return FunctionTool.create(this, "digitizeDocument", false, true);
  }

  @Schema(
      name = "sarvam_text_to_speech",
      description =
          "Converts text to speech using Sarvam API. Returns base64 WAV audio in the response.")
  public Map<String, Object> convertTextToSpeech(
      @Schema(name = "text", description = "Text to synthesize") String text,
      @Schema(
              name = "target_language_code",
              description = "Target language code, e.g. hi-IN, en-IN, ta-IN")
          String targetLanguageCode,
      @Schema(
              name = "speaker",
              description = "Voice name to use for synthesis. Default: manisha",
              optional = true)
          String speaker) {
    try {
      String resolvedSpeaker = isBlank(speaker) ? "manisha" : speaker;
      Map<String, Object> body =
          Map.of(
              "text", text,
              "target_language_code", targetLanguageCode,
              "speaker", resolvedSpeaker);

      Request request =
          subscriptionKeyRequestBuilder(apiBaseUrl + "/text-to-speech")
              .post(RequestBody.create(OBJECT_MAPPER.writeValueAsString(body), JSON_MEDIA_TYPE))
              .build();

      try (Response response = httpClient.newCall(request).execute()) {
        String responseBody = readBody(response.body());
        if (!response.isSuccessful()) {
          return error("text_to_speech_failed", response.code(), responseBody);
        }

        JsonNode json = OBJECT_MAPPER.readTree(responseBody);
        JsonNode audios = json.path("audios");
        if (!audios.isArray() || audios.isEmpty()) {
          return error("text_to_speech_failed", response.code(), "No audio returned by Sarvam API");
        }

        Map<String, Object> result = success();
        result.put("request_id", json.path("request_id").asText("unknown"));
        result.put("audio_base64", audios.get(0).asText(""));
        result.put("target_language_code", targetLanguageCode);
        result.put("speaker", resolvedSpeaker);
        return result;
      }
    } catch (Exception e) {
      return error("text_to_speech_failed", 500, e.getMessage());
    }
  }

  @Schema(
      name = "sarvam_speech_to_text",
      description =
          "Converts speech to text using Sarvam API. Uses uploaded audio from toolContext if"
              + " present, otherwise reads base64 audio input.")
  public Map<String, Object> convertSpeechToText(
      @Schema(
              name = "audio_data_base64",
              description = "Optional base64-encoded audio data (wav/mp3/ogg/webm/flac).",
              optional = true)
          String audioDataBase64,
      @Schema(
              name = "language_code",
              description = "Optional language code. Default: unknown",
              optional = true)
          String languageCode,
      @Schema(name = "toolContext") ToolContext toolContext) {
    try {
      UploadedBinary audio = resolveAudioInput(audioDataBase64, toolContext);
      if (audio == null || audio.bytes.length == 0) {
        return error(
            "speech_to_text_failed",
            400,
            "No audio input provided. Upload audio or pass audio_data_base64.");
      }

      String resolvedLanguage = isBlank(languageCode) ? "unknown" : languageCode;
      MultipartBody multipartBody =
          new MultipartBody.Builder()
              .setType(MultipartBody.FORM)
              .addFormDataPart(
                  "file",
                  audio.fileName,
                  RequestBody.create(audio.bytes, MediaType.get(audio.mimeType)))
              .addFormDataPart("language_code", resolvedLanguage)
              .build();

      Request request =
          subscriptionKeyRequestBuilder(apiBaseUrl + "/speech-to-text").post(multipartBody).build();

      try (Response response = httpClient.newCall(request).execute()) {
        String responseBody = readBody(response.body());
        if (!response.isSuccessful()) {
          return error("speech_to_text_failed", response.code(), responseBody);
        }
        JsonNode json = OBJECT_MAPPER.readTree(responseBody);
        Map<String, Object> result = success();
        result.put("request_id", json.path("request_id").asText("unknown"));
        result.put("transcript", json.path("transcript").asText(""));
        result.put("language_code", json.path("language_code").asText(resolvedLanguage));
        return result;
      }
    } catch (Exception e) {
      return error("speech_to_text_failed", 500, e.getMessage());
    }
  }

  @Schema(
      name = "sarvam_translate",
      description = "Translates text using Sarvam API. Source language defaults to auto.")
  public Map<String, Object> translateText(
      @Schema(name = "input", description = "Text to translate") String input,
      @Schema(name = "target_language_code", description = "Target language code") String target,
      @Schema(
              name = "source_language_code",
              description = "Source language code. Default: auto",
              optional = true)
          String source) {
    try {
      String resolvedSource = isBlank(source) ? "auto" : source;

      Map<String, Object> body =
          Map.of(
              "input", input,
              "source_language_code", resolvedSource,
              "target_language_code", target);

      Request request =
          subscriptionKeyRequestBuilder(apiBaseUrl + "/translate")
              .post(RequestBody.create(OBJECT_MAPPER.writeValueAsString(body), JSON_MEDIA_TYPE))
              .build();

      try (Response response = httpClient.newCall(request).execute()) {
        String responseBody = readBody(response.body());
        if (!response.isSuccessful()) {
          return error("translate_failed", response.code(), responseBody);
        }

        JsonNode json = OBJECT_MAPPER.readTree(responseBody);
        Map<String, Object> result = success();
        result.put("request_id", json.path("request_id").asText("unknown"));
        result.put("translated_text", json.path("translated_text").asText(""));
        result.put(
            "source_language_code", json.path("source_language_code").asText(resolvedSource));
        result.put("target_language_code", target);
        return result;
      }
    } catch (Exception e) {
      return error("translate_failed", 500, e.getMessage());
    }
  }

  @Schema(
      name = "sarvam_digitize_document",
      description =
          "Digitizes an uploaded document (PDF/image) using Sarvam API and returns extracted text.")
  public Map<String, Object> digitizeDocument(
      @Schema(
              name = "language",
              description = "Document language code. Default: auto",
              optional = true)
          String language,
      @Schema(
              name = "output_format",
              description = "Output format: md or html. Default: md",
              optional = true)
          String outputFormat,
      @Schema(
              name = "file_data_base64",
              description = "Optional base64-encoded file bytes (PDF/image).",
              optional = true)
          String fileDataBase64,
      @Schema(
              name = "file_name",
              description = "Optional filename to use for upload.",
              optional = true)
          String fileName,
      @Schema(name = "mime_type", description = "Optional MIME type.", optional = true)
          String mimeType,
      @Schema(name = "toolContext") ToolContext toolContext) {
    try {
      UploadedBinary file = resolveDocumentInput(fileDataBase64, fileName, mimeType, toolContext);
      if (file == null || file.bytes.length == 0) {
        return error(
            "digitize_document_failed",
            400,
            "No document input provided. Upload a PDF/image or pass file_data_base64.");
      }

      String resolvedLanguage = isBlank(language) ? "auto" : language;
      String resolvedOutputFormat = isBlank(outputFormat) ? "md" : outputFormat.toLowerCase();
      if ("markdown".equals(resolvedOutputFormat)) {
        resolvedOutputFormat = "md";
      }

      String jobId = createDigitizationJob(resolvedLanguage, resolvedOutputFormat);
      String uploadUrl = createDigitizationUploadUrl(jobId, file.fileName);
      uploadDigitizationFile(uploadUrl, file.bytes, file.mimeType);
      startDigitizationJob(jobId);

      JsonNode finalStatus = waitForDigitizationCompletion(jobId, 60, 5000);
      String finalState = finalStatus.path("job_state").asText("Unknown");
      if (!"Completed".equalsIgnoreCase(finalState)) {
        return error(
            "digitize_document_failed",
            500,
            "Job did not complete. State: "
                + finalState
                + ", error: "
                + finalStatus.path("error_message").asText(""));
      }

      Map<String, String> downloadUrls = fetchDigitizationDownloadUrls(jobId);
      StringBuilder fullContent = new StringBuilder();
      for (String key : downloadUrls.keySet()) {
        String content = downloadUrlContent(downloadUrls.get(key));
        if (fullContent.length() > 0) {
          fullContent.append("\n\n---\n\n");
        }
        fullContent.append(content);
      }

      String extracted = fullContent.toString();
      Map<String, Object> result = success();
      result.put("job_id", jobId);
      result.put("job_state", finalState);
      result.put(
          "content",
          extracted.length() > 5000
              ? extracted.substring(0, 5000) + "\n\n... [truncated]"
              : extracted);
      result.put("content_length", extracted.length());
      result.put("output_format", resolvedOutputFormat);
      return result;
    } catch (Exception e) {
      return error("digitize_document_failed", 500, e.getMessage());
    }
  }

  private String createDigitizationJob(String language, String outputFormat) throws IOException {
    Map<String, Object> jobParameters = new HashMap<>();
    jobParameters.put("output_format", outputFormat);
    if (!"auto".equals(language)) {
      jobParameters.put("language", language);
    }
    Map<String, Object> body = Map.of("job_parameters", jobParameters);

    Request request =
        subscriptionKeyRequestBuilder(apiBaseUrl + "/doc-digitization/job/v1")
            .post(RequestBody.create(OBJECT_MAPPER.writeValueAsString(body), JSON_MEDIA_TYPE))
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      String responseBody = readBody(response.body());
      if (!response.isSuccessful()) {
        throw new IOException(
            "Failed to create digitization job. HTTP " + response.code() + ": " + responseBody);
      }
      JsonNode json = OBJECT_MAPPER.readTree(responseBody);
      String jobId = json.path("job_id").asText("");
      if (jobId.isEmpty()) {
        throw new IOException("Digitization job_id missing in response.");
      }
      return jobId;
    }
  }

  private String createDigitizationUploadUrl(String jobId, String fileName) throws IOException {
    Map<String, Object> body = Map.of("job_id", jobId, "files", List.of(fileName));

    Request request =
        subscriptionKeyRequestBuilder(apiBaseUrl + "/doc-digitization/job/v1/upload-files")
            .post(RequestBody.create(OBJECT_MAPPER.writeValueAsString(body), JSON_MEDIA_TYPE))
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      String responseBody = readBody(response.body());
      if (!response.isSuccessful()) {
        throw new IOException(
            "Failed to get upload URL. HTTP " + response.code() + ": " + responseBody);
      }
      JsonNode json = OBJECT_MAPPER.readTree(responseBody).path("upload_urls");
      if (!json.fields().hasNext()) {
        throw new IOException("No upload_urls present in digitization response.");
      }
      return json.fields().next().getValue().asText("");
    }
  }

  private void uploadDigitizationFile(String uploadUrl, byte[] data, String mimeType)
      throws IOException {
    Request request =
        new Request.Builder()
            .url(uploadUrl)
            .put(
                RequestBody.create(
                    data, mimeType == null ? OCTET_STREAM_MEDIA_TYPE : MediaType.get(mimeType)))
            .addHeader("x-ms-blob-type", "BlockBlob")
            .addHeader("Content-Type", mimeType == null ? "application/octet-stream" : mimeType)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to upload document bytes. HTTP " + response.code());
      }
    }
  }

  private void startDigitizationJob(String jobId) throws IOException {
    Request request =
        subscriptionKeyRequestBuilder(apiBaseUrl + "/doc-digitization/job/v1/" + jobId + "/start")
            .post(RequestBody.create(new byte[0], null))
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      String responseBody = readBody(response.body());
      if (!response.isSuccessful()) {
        throw new IOException(
            "Failed to start digitization job. HTTP " + response.code() + ": " + responseBody);
      }
    }
  }

  private JsonNode waitForDigitizationCompletion(String jobId, int maxAttempts, int sleepMs)
      throws IOException, InterruptedException {
    JsonNode latestStatus = OBJECT_MAPPER.createObjectNode();
    for (int i = 0; i < maxAttempts; i++) {
      Thread.sleep(sleepMs);
      Request request =
          subscriptionKeyRequestBuilder(
                  apiBaseUrl + "/doc-digitization/job/v1/" + jobId + "/status")
              .get()
              .build();

      try (Response response = httpClient.newCall(request).execute()) {
        String responseBody = readBody(response.body());
        if (!response.isSuccessful()) {
          throw new IOException(
              "Failed polling digitization status. HTTP " + response.code() + ": " + responseBody);
        }
        latestStatus = OBJECT_MAPPER.readTree(responseBody);
        String state = latestStatus.path("job_state").asText("Unknown");
        if ("Completed".equalsIgnoreCase(state) || "Failed".equalsIgnoreCase(state)) {
          return latestStatus;
        }
      }
    }
    return latestStatus;
  }

  private Map<String, String> fetchDigitizationDownloadUrls(String jobId) throws IOException {
    Request request =
        subscriptionKeyRequestBuilder(
                apiBaseUrl + "/doc-digitization/job/v1/" + jobId + "/download-files")
            .post(RequestBody.create(new byte[0], null))
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      String responseBody = readBody(response.body());
      if (!response.isSuccessful()) {
        throw new IOException(
            "Failed to fetch digitization download URLs. HTTP "
                + response.code()
                + ": "
                + responseBody);
      }

      JsonNode urlsNode = OBJECT_MAPPER.readTree(responseBody).path("download_urls");
      Map<String, String> urls = new HashMap<>();
      urlsNode
          .fields()
          .forEachRemaining(entry -> urls.put(entry.getKey(), entry.getValue().asText("")));
      if (urls.isEmpty()) {
        throw new IOException("No download URLs returned by digitization API.");
      }
      return urls;
    }
  }

  private String downloadUrlContent(String url) throws IOException {
    Request request = new Request.Builder().url(url).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      String body = readBody(response.body());
      if (!response.isSuccessful()) {
        throw new IOException("Failed to download digitized content. HTTP " + response.code());
      }
      return body;
    }
  }

  private UploadedBinary resolveAudioInput(String base64Audio, ToolContext toolContext) {
    if (!isBlank(base64Audio)) {
      return new UploadedBinary(
          Base64.getDecoder().decode(base64Audio), "uploaded_audio.wav", "audio/wav");
    }

    Optional<UploadedBinary> uploaded = extractUploadedBinary(toolContext, "audio/");
    return uploaded.orElse(null);
  }

  private UploadedBinary resolveDocumentInput(
      String fileDataBase64, String fileName, String mimeType, ToolContext toolContext) {
    if (!isBlank(fileDataBase64)) {
      String resolvedMimeType = isBlank(mimeType) ? "application/pdf" : mimeType;
      String resolvedFileName = isBlank(fileName) ? guessFileName(resolvedMimeType) : fileName;
      return new UploadedBinary(
          Base64.getDecoder().decode(fileDataBase64), resolvedFileName, resolvedMimeType);
    }

    Optional<UploadedBinary> uploadedPdf = extractUploadedBinary(toolContext, "application/pdf");
    if (uploadedPdf.isPresent()) {
      return uploadedPdf.get();
    }
    Optional<UploadedBinary> uploadedImage = extractUploadedBinary(toolContext, "image/");
    return uploadedImage.orElse(null);
  }

  private Optional<UploadedBinary> extractUploadedBinary(
      ToolContext toolContext, String mimePrefix) {
    Optional<Content> userContent = toolContext.userContent();
    if (userContent.isEmpty() || userContent.get().parts().isEmpty()) {
      return Optional.empty();
    }

    for (Part part : userContent.get().parts().get()) {
      if (part.inlineData().isEmpty()) {
        continue;
      }
      Blob blob = part.inlineData().get();
      String mimeType = blob.mimeType().orElse("");
      if (!mimeType.startsWith(mimePrefix)) {
        continue;
      }
      byte[] bytes = blob.data().orElse(new byte[0]);
      if (bytes.length == 0) {
        continue;
      }
      String fileName = guessFileName(mimeType);
      return Optional.of(new UploadedBinary(bytes, fileName, mimeType));
    }
    return Optional.empty();
  }

  private Request.Builder subscriptionKeyRequestBuilder(String url) {
    return new Request.Builder()
        .url(url)
        .addHeader("api-subscription-key", apiKey)
        .addHeader("Content-Type", "application/json");
  }

  private static String readBody(ResponseBody body) throws IOException {
    return body == null ? "" : body.string();
  }

  private static String guessFileName(String mimeType) {
    if (mimeType == null) {
      return "document.bin";
    }
    if (mimeType.contains("pdf")) {
      return "document.pdf";
    }
    if (mimeType.contains("png")) {
      return "document.png";
    }
    if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
      return "document.jpg";
    }
    if (mimeType.contains("tiff")) {
      return "document.tiff";
    }
    if (mimeType.contains("bmp")) {
      return "document.bmp";
    }
    if (mimeType.contains("webm")) {
      return "audio.webm";
    }
    if (mimeType.contains("ogg")) {
      return "audio.ogg";
    }
    if (mimeType.contains("mp3") || mimeType.contains("mpeg")) {
      return "audio.mp3";
    }
    return "document.bin";
  }

  private static String requireNonEmpty(String value, String field) {
    if (isBlank(value)) {
      throw new IllegalArgumentException(field + " must not be empty.");
    }
    return value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static Map<String, Object> success() {
    Map<String, Object> result = new HashMap<>();
    result.put("status", "success");
    return result;
  }

  private static Map<String, Object> error(String errorCode, int httpStatus, String message) {
    Map<String, Object> result = new HashMap<>();
    result.put("status", "error");
    result.put("error_code", errorCode);
    result.put("http_status", httpStatus);
    result.put("message", message == null ? "" : message);
    return result;
  }

  private static final class UploadedBinary {
    private final byte[] bytes;
    private final String fileName;
    private final String mimeType;

    private UploadedBinary(byte[] bytes, String fileName, String mimeType) {
      this.bytes = bytes;
      this.fileName = fileName;
      this.mimeType = mimeType;
    }
  }
}
