# CoreAssistant

A privacy-focused, feature-rich LLM chat application for Android, powered by the Google Gemini API.

## Features

- **Gemini AI Chat**: Engage in intelligent conversations using various Gemini models (Flash, Pro, etc.).
- **Voice Assistant**: Integrated voice interaction service. Set CoreAssistant as your default digital assistant to trigger it with a long-press of the power button or "Hey Google".
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
- **Enhanced Chat UI**:
    - Full Markdown rendering for AI responses.
    - Text-to-Speech (TTS) for reading messages aloud.
    - One-tap "Copy to Clipboard" for all messages.

## Setup

1. Obtain a **Gemini API Key** from the [Google AI Studio](https://aistudio.google.com/).
2. Enter your API key in the app's setup screen or settings.
3. (Optional) Set CoreAssistant as your **Default Digital Assistant** in Android Settings for the best voice experience.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Networking**: Retrofit & OkHttp
- **Local Storage**: Room with SQLCipher (Encrypted)
- **Image Loading**: Coil
- **Markdown**: Multiplatform Markdown Renderer
- **AI**: Google Gemini API

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
