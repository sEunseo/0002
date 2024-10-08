package org.linphone.ui.main.recordings.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.utils.AIUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class RecordingModel @WorkerThread constructor(
    val filePath: String,
    val fileName: String,
    isLegacy: Boolean = false
) {
    companion object {
        private const val TAG = "[Recording Model]"
    }

    val sipUri: String
    val displayName: String
    val timestamp: Long
    val month: String
    val dateTime: String
    val formattedDuration: String
    val duration: Int

    // Transcription 저장
    var transcription: String = ""

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        if (isLegacy) {
            // 레거시 파일 처리
            val username = fileName.split("_")[0]
            val sipAddress = coreContext.core.interpretUrl(username, false)
            sipUri = sipAddress?.asStringUriOnly() ?: username
            displayName = sipAddress?.let {
                val contact = coreContext.contactsManager.findContactByAddress(it)
                contact?.name ?: LinphoneUtils.getDisplayName(it)
            } ?: sipUri

            val parsedDate = fileName.split("_")[1]
            timestamp = try {
                val date = SimpleDateFormat("dd-MM-yyyy-HH-mm-ss", Locale.getDefault()).parse(
                    parsedDate
                )
                date?.time ?: 0L
            } catch (e: Exception) {
                Log.e("$TAG Failed to parse legacy timestamp [$parsedDate]")
                0L
            }
        } else {
            // 새로운 형식의 파일 처리
            val withoutHeader = fileName.substring(LinphoneUtils.RECORDING_FILE_NAME_HEADER.length)
            val indexOfSeparator = withoutHeader.indexOf(
                LinphoneUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR
            )
            sipUri = withoutHeader.substring(0, indexOfSeparator)
            val sipAddress = Factory.instance().createAddress(sipUri)
            displayName = sipAddress?.let {
                val contact = coreContext.contactsManager.findContactByAddress(it)
                contact?.name ?: LinphoneUtils.getDisplayName(it)
            } ?: sipUri

            val parsedTimestamp = withoutHeader.substring(
                indexOfSeparator + LinphoneUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR.length,
                withoutHeader.lastIndexOf(".")
            )
            timestamp = parsedTimestamp.toLong()
        }

        // 날짜 및 시간 정보 설정
        month = TimestampUtils.month(timestamp, timestampInSecs = false)
        val date = TimestampUtils.toString(
            timestamp,
            timestampInSecs = false,
            onlyDate = true,
            shortDate = false
        )
        val time = TimestampUtils.timeToString(timestamp, timestampInSecs = false)
        dateTime = "$date - $time"

        // 녹음 파일의 재생 시간 처리
        val audioPlayer = coreContext.core.createLocalPlayer(null, null, null)
        if (audioPlayer != null) {
            audioPlayer.open(filePath)
            duration = audioPlayer.duration
            formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration)
        } else {
            duration = 0
            formattedDuration = "??:??"
        }

        coroutineScope.launch {
            transcribeRecording()
        }
    }

    // 전사 기능
    // 전사 기능
    @UiThread
    suspend fun transcribeRecording() {
        try {
            Log.i("$TAG Generating transcription for [$filePath]")
            transcription = AIUtils.generateText(filePath)
            Log.i("$TAG Transcription complete: $transcription")

            // 전사된 텍스트 파일로 저장
            val transcriptionFilePath = filePath.replace(".wav", "_transcription.txt")
            val transcriptionFile = FileUtils.getFileStoragePath(
                fileName = transcriptionFilePath,
                isRecording = true,
                overrideExisting = true
            )
            val success = FileUtils.dumpStringToFile(transcription, transcriptionFile)

            if (success) {
                Log.i("$TAG Transcription saved at: ${transcriptionFile.absolutePath}")
            } else {
                Log.e("$TAG Failed to save transcription at: ${transcriptionFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("$TAG Failed to transcribe recording: ${e.message}")
        }
    }

    // 녹음 파일 삭제
    @UiThread
    suspend fun delete() {
        Log.i("$TAG Deleting call recording [$filePath]")
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
                Log.i("$TAG Deleted [$filePath]")
            } else {
                Log.e("$TAG File [$filePath] does not exist")
            }
        }
    }
}
