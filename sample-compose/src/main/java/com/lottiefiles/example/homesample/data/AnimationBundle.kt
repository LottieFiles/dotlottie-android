package com.lottiefiles.example.homesample.data


data class AnimationBundle(
    var id: Int = 0,
    var name: String? = null,
    var downloads: Double? = null,
    var bgColor: String? = null,
    var lottieUrl: String? = null,
    var createdBy: User? = null,
    var createdAt: String? = null,
    var description: String? = "",
    var likesCount: Int = 0,
    var commentsCount: Int? = 0,
    var isSelected: Boolean = false,
    var status: Int? = null,
    var isLiked: Boolean = false,
    var gifUrl: String? = "",
    var videoUrl: String? = "",
    var url: String? = "",
    var imageUrl: String? = "",
    var type: String? = "",
    var animationJson: String? = ""

)
