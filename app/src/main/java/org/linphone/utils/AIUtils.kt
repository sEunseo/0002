package org.linphone.utils

import androidx.annotation.AnyThread
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import java.io.File
import org.linphone.core.tools.Log

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
            return try {
                // 오디오 파일을 바이트 배열로 읽기
                val audioBytes = File(filePath).readBytes()

                // 프롬프트 설정: 오디오 데이터와 질문
                val prompt = content {
                    blob("audio/mpeg", audioBytes) // MIME 타입에 맞게 수정
                    text("Please transcribe this audio.")
                }

                // 전사문 생성
                val response = generativeModel.generateContent(prompt)

                // 결과 반환
                response.text ?: "Transcription failed."
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transcribe audio: ${e.message}")
                "Error in transcription."
            }
        }
    }
}
