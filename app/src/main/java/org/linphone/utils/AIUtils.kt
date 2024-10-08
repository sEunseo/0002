package org.linphone.utils

import androidx.annotation.AnyThread
import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding
import com.google.cloud.speech.v1.SpeechClient
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIUtils {
    companion object {
        private const val TAG = "[AI Utils]"
        val config = generationConfig {
            maxOutputTokens = 200
            stopSequences = listOf("red")
            temperature = 0.9f
            topK = 16
            topP = 0.1f
        }

        val generativeModel = Firebase.vertexAI.generativeModel(
            "gemini-1.5-pro",
            generationConfig = config
        )

        @AnyThread
        suspend fun generateText(filePath: String): String {
            return withContext(Dispatchers.IO) {
                var transcript = ""

                // Google Cloud Speech-to-Text 클라이언트 초기화
                SpeechClient.create().use { speechClient ->

                    // 오디오 파일 불러오기
                    val audioBytes = Files.readAllBytes(Paths.get(filePath))
                    val audio = RecognitionAudio.newBuilder()
                        .setContent(com.google.protobuf.ByteString.copyFrom(audioBytes))
                        .build()

                    // 요청 구성 설정
                    val config = RecognitionConfig.newBuilder()
                        .setEncoding(AudioEncoding.LINEAR16) // 파일 인코딩 타입 설정
                        .setLanguageCode("ko-KR") // 한국어로 인식
                        .setSampleRateHertz(16000) // 샘플링 레이트
                        .build()

                    // 음성을 텍스트로 전사하는 요청 실행
                    val response = speechClient.recognize(config, audio)

                    // 응답 결과에서 텍스트 추출
                    for (result in response.resultsList) {
                        transcript += result.alternativesList[0].transcript
                    }
                }

                return@withContext transcript
            }
        }
    }
}
