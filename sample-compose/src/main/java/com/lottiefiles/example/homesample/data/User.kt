package com.lottiefiles.example.homesample.data


data class User(
    var id: String = "",
    var firstName: String? = "",
    var lastName: String? = "",
    var email: String? = "",
    var avatarUrl: String? = "",
    var username: String? = "",
    var isHireable: Boolean = false,
    var bio: String? = "",
    var city: String? = "",
    var dribbbleUsername: String? = "",
    var twitterUsername: String? = "",
    var instagramUsername: String? = "",
    var behanceUsername: String? = "",
)