package com.lottiefiles.example.features.home.presentation

import com.lottiefiles.example.features.home.data.AnimationBundle
import com.lottiefiles.example.features.home.data.User

data class HomeUIState(
    val isLoading: Boolean = true,
    val featuredAnimations: List<AnimationBundle> = listOf(
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
    ),
    val popularAnimations: List<AnimationBundle> = listOf(
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
    ),
    val recentAnimations: List<AnimationBundle> = listOf(
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle,
        sampleAnimationBundle
    ),
    val photoCollages: List<AnimationBundle> = emptyList(),
    val socialPosts: List<AnimationBundle> = emptyList(),
    val travelList: List<AnimationBundle> = emptyList(),
    val marketingList: List<AnimationBundle> = emptyList(),
    val thankYouList: List<AnimationBundle> = emptyList(),
    val highlights: List<AnimationBundle> = emptyList(),
    val userAvatarUrl: String = ""
)

val sampleAnimationBundle = AnimationBundle(
    id = 1,
    name = "Sample Animation",
    bgColor = "#FFFFFF",
    jsonUrl = "https://lottie.host/e55c67db-398a-4b32-9c21-d5ccc374658c/C6ZURJ4vJP.json",
    lottieUrl = "https://lottie.host/d205a0a0-3e1c-4501-a036-7719e9668616/5sOh6gibkX.lottie",
    createdBy = User(
        id = "1", firstName = "John", lastName = "Doe", email = "username@email.com"
    )
)
