# CoreAssistant

A privacy-focused, feature-rich LLM chat application for Android, powered by the Google Gemini API.

## Features

- **Gemini AI Chat**: Engage in intelligent conversations using various Gemini models (Flash, Pro, etc.).
- **Voice Assistant**: Integrated voice interaction service using Sherpa-ONNX for local STT/TTS. Set CoreAssistant as your default digital assistant to trigger it with a long-press of the power button or "Hey Google".
- **Local Model Management**:
    - **Download Manager**: Models are downloaded and extracted directly within the app's setup screen to keep the initial APK size small.
    - **Integrity Checks**: All model downloads are verified using SHA-256 checksums.
    - **Dynamic Reloading**: Voice models can be switched in settings and are reloaded instantly without restarting the app.
- **Multimodal Support**: Send images and files along with your text prompts for visual analysis and document processing.
- **Model Thinking**: View the model's internal "thought" process for supported models to understand how it arrived at an answer.
- **Google Grounding**: Enable real-time search grounding to get up-to-date information and verifiable sources/links in responses.
- **Privacy & Security**:
    - **Encrypted Database**: All chat history is stored locally using SQLCipher encryption.
    - **Screenshot Protection**: Option to block screenshots and hide app content in the recent apps switcher.
    - **Auto-Clear History**: Optional setting to automatically delete all conversations when the app is closed.
- **Customization**:
    - Choose from different Gemini models.
    - Adjust "Thinking Level" for models that support it.
    - Personalize your name for the AI.
    - Configure the maximum number of conversations to keep.
- **Performance Optimized**: Models are loaded in a specific sequence (TTS -> VAD -> STT) to ensure the interface is responsive as quickly as possible.

## Setup

1. Obtain a **Gemini API Key** from the [Google AI Studio](https://aistudio.google.com/).
    - **Privacy Tip**: If you upgrade to **Tier 1** (by adding a credit card) in AI Studio, Google will not use your prompts or responses to train their models.
2. Enter your API key in the app's setup screen.
3. Use the **Download Manager** in the setup screen to fetch the required Sherpa-ONNX models.
4. (Optional) Set CoreAssistant as your **Default Digital Assistant** in Android Settings for the best voice experience.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Local AI**: Sherpa-ONNX (Whisper for STT, VITS for TTS)
- **Networking**: Retrofit & OkHttp
- **Local Storage**: Room with SQLCipher (Encrypted)
- **Image Loading**: Coil
- **Markdown**: Multiplatform Markdown Renderer
- **AI**: Google Gemini API

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
