package com.example.overdex.ui.screens

data class ReadmePage(
    val title: String,
    val content: String
)

val readmePages = listOf(
    ReadmePage("PAGE 1", "Placeholder content for page 1."),
    ReadmePage("PAGE 2", "Placeholder content for page 2."),
    ReadmePage("PAGE 3", "Placeholder content for page 3.")
)
