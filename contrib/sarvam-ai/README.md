# Sarvam AI Integration for ADK Java

This module provides:

- `SarvamAi` model wrapper for chat completions
- `SarvamAiToolset` function tools for:
  - text-to-speech
  - speech-to-text
  - translation
  - document digitization

## Usage

```java
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.sarvamai.SarvamAi;
import com.google.adk.models.sarvamai.SarvamAiConfig;
import com.google.adk.models.sarvamai.SarvamAiToolset;

String apiKey = System.getenv("SARVAM_API_KEY");

SarvamAiConfig config = new SarvamAiConfig(apiKey);
SarvamAi model = new SarvamAi("sarvam-m", config);
SarvamAiToolset sarvamToolset = new SarvamAiToolset(config);

LlmAgent agent = LlmAgent.builder()
    .name("sarvam-assistant")
    .model(model)
    .instruction("Use Sarvam tools when users ask for TTS, STT, translation, or document OCR.")
    .tools(
        sarvamToolset.textToSpeechTool(),
        sarvamToolset.speechToTextTool(),
        sarvamToolset.translateTool(),
        sarvamToolset.documentDigitizationTool())
    .build();
```

## Tool Names

- `sarvam_text_to_speech`
- `sarvam_speech_to_text`
- `sarvam_translate`
- `sarvam_digitize_document`
