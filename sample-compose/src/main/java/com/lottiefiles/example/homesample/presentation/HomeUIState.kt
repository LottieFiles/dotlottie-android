package com.lottiefiles.example.homesample.presentation

import com.lottiefiles.example.homesample.data.AnimationBundle
import com.lottiefiles.example.homesample.data.User

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
    lottieUrl = "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie",
    createdBy = User(
        id = "1", firstName = "John", lastName = "Doe", email = "username@email.com"
    )
)
