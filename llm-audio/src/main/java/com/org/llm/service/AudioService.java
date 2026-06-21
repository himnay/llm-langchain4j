package com.org.llm.service;

import com.openai.client.OpenAIClient;
import com.openai.core.http.HttpResponse;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.org.llm.model.StoredAudio;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

@Service
@AllArgsConstructor
public class AudioService {

    private static final Path AUDIO_DIR = Path.of(System.getProperty("java.io.tmpdir"), "langchain4j-audio");

    private final AudioTranscriptionModel transcriptionModel;
    /**
     * LangChain4j has no text-to-speech abstraction — used only for {@link #textToSpeech}.
     */
    private final OpenAIClient openAiClient;

    public StoredAudio store(MultipartFile file) {
        try {
            Files.createDirectories(AUDIO_DIR);

            String fileId = UUID.randomUUID().toString();
            // keep only the last path segment of the client-supplied name — prevents path traversal
            String safeName = Path.of(Objects.requireNonNullElse(file.getOriginalFilename(), "audio"))
                    .getFileName().toString();
            String storedFileName = fileId + "_" + safeName;
            Path targetPath = AUDIO_DIR.resolve(storedFileName);

            Files.copy(file.getInputStream(), targetPath);

            return new StoredAudio(fileId, file.getOriginalFilename(), storedFileName,
                    file.getContentType(), file.getSize());

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store audio file", e);
        }
    }

    public String speechToText(String storedFileName) {
        byte[] audioBytes;
        try {
            audioBytes = Files.readAllBytes(AUDIO_DIR.resolve(storedFileName));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read stored audio file: " + storedFileName, e);
        }

        Audio audio = Audio.builder().binaryData(audioBytes).build();
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder(audio).build();
        AudioTranscriptionResponse response = transcriptionModel.transcribe(request);
        return response.text();
    }

    public byte[] textToSpeech(String text) {
        SpeechCreateParams params = SpeechCreateParams.builder()
                .input(text)
                .model("tts-1") // tts-1, tts-1-hd
                .voice("echo") // alloy, echo, fable, onyx, nova, shimmer
                .build();

        try (HttpResponse response = openAiClient.audio().speech().create(params);
             InputStream body = response.body()) {
            return body.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read TTS audio response", e);
        }
    }
}
