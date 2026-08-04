package com.arise.app

data class AppItem(
    val name: String,
    val packageName: String,
    var isSelected: Boolean = false
)
