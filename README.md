# CoreAssistant

A privacy-focused, lightweight LLM chat application for Android, powered by the Google Gemini API.

## 🚀 Features

- **Gemini AI Chat**: Engage in intelligent conversations using various Gemini models (Flash, Pro, etc.).
- **Auto-Save Drafts**: Never lose what you type. Your message drafts are saved at short intervals for every conversation.
- **Multimodal Support**: Send images and files along with your text prompts for visual analysis and document processing.
- **Model Thinking**: View the model's internal "thought" process to understand how it arrived at an answer.
- **Google Grounding**: Enable real-time search grounding to get up-to-date information with verifiable sources.
- **Privacy & Security**:
    - **Encrypted Database**: All chat history and drafts are stored locally using SQLCipher encryption.
    - **Screenshot Protection**: Block screenshots and hide app content in the recent apps switcher.
    - **Auto-Clear History**: Optional setting to automatically delete all conversations when the app is closed.
- **Customization**:
    - Choose from different Gemini models.
    - Adjust "Thinking Level" for supported models.
    - Personalize your name for the AI.

## 🛠️ Setup

1. Obtain a **Gemini API Key** from [Google AI Studio](https://aistudio.google.com/).
2. Enter your API key and your name in the app's initial setup screen.
3. Start chatting!

## 🏗️ Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI**: [Jetpack Compose](https://developer.android.com/compose)
- **Local Storage**: [Room](https://developer.android.com/training/data-storage/room) with [SQLCipher](https://www.zetetic.net/sqlcipher/) (Encrypted)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Markdown**: [Multiplatform Markdown Renderer](https://github.com/mikepenz/Multiplatform-Markdown-Renderer)
- **AI**: [Google Gemini API](https://ai.google.dev/)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
