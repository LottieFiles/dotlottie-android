package com.lottiefiles.example.features.home.data


data class AnimationBundle(
    var id: Int = 0,
    var name: String? = null,
    var bgColor: String? = null,
    var lottieUrl: String? = null,
    var jsonUrl: String? = null,
    var createdBy: User? = null,
)

data class User(
    var id: String = "",
    var firstName: String? = "",
    var lastName: String? = "",
    var email: String? = "",
    var avatarUrl: String? = "",
    var username: String? = "",
)
