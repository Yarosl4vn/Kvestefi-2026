package ru.readysquad.kvest.ui

import ru.readysquad.kvest.data.Goals
import ru.readysquad.kvest.util.Greetings

object OnboardingDraft {
    var step: Int = 0
    var name: String = ""
    var avatar: String = ""
    var gender: String = Greetings.MALE
    var subjects: MutableSet<String> = HashSet()
    var topics: MutableSet<String> = HashSet()
    var goal: Int = Goals.DEFAULT

    fun reset() {
        step = 0
        name = ""
        avatar = ""
        gender = Greetings.MALE
        subjects = HashSet()
        topics = HashSet()
        goal = Goals.DEFAULT
    }
}
