package org.linphone.utils

import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI

class AIUtils {
    val generativeModel = Firebase.vertexAI.generativeModel("gemini-1.5.-pro")
}