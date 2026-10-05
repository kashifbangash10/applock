package com.applockerapp.presentation.themes

/**
 * Data model for a single theme card in the Theme Store grid.
 *
 * @param id Unique theme identifier used to apply the theme.
 * @param name Display name shown on the card.
 * @param drawableRes The drawable resource for the background image or gradient.
 * @param isImage Whether the drawable is a photo (centerCrop) or shape gradient (fitXY).
 * @param isPro Whether the theme requires a Pro upgrade to apply.
 */
data class ThemeItem(
    val id: Int,
    val name: String,
    val drawableRes: Int,
    val isImage: Boolean = true,
    val isPro: Boolean = false
)
