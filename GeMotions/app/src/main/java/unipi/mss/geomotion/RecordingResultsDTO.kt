package unipi.mss.geomotion

class RecordingsResultDTO {
    private var emotion: String = "Unknown"
    private val listOfRecordings: MutableList<HashMap<String, String>> = mutableListOf()

    fun getEmotion(): String {
        return emotion
    }

    fun setEmotion(emotion: String) {
        this.emotion = emotion
    }

    fun addRecording(record: HashMap<String, String>) {
        listOfRecordings.add(record)
    }
}

