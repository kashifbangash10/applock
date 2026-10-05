package com.applockerapp.presentation.themes

import com.applockerapp.R

/**
 * Central registry of all theme configurations.
 * Used by both ApplyThemeFragment (preview) and LockOverlayWindow (lock screen rendering).
 */
object ThemeConfig {

    data class Theme(
        val id: Int,
        val name: String,
        val drawableRes: Int,
        val isImage: Boolean,
        // Pattern dot colors
        val normalColor: Int,
        val selectedColor: Int,
        val lineColor: Int,
        // Text colors on overlay
        val primaryTextColor: Int,
        val secondaryTextColor: Int,
        // Emojis for dots
        val normalEmoji: String? = null,
        val selectedEmoji: String? = null
    )

    private val all: List<Theme> = listOf(
        // ── ANIMALS (1-10) ────────────────────────────────────────────
        Theme(1,  "White Kitten",    R.drawable.img_theme_kitten,      true,  0xFF8E98AA.toInt(), 0xFF38BDF8.toInt(), 0xAA38BDF8.toInt(), 0xFF1E2229.toInt(), 0xFF5C6479.toInt()),
        Theme(2,  "Black Panther",   R.drawable.img_theme_panther,     true,  0xFF5A3E85.toInt(), 0xFFE040FB.toInt(), 0x88E040FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0BEC5.toInt()),
        Theme(3,  "Bear & Owl",      R.drawable.img_theme_bear,        true,  0xFF37474F.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFECEFF1.toInt()),
        Theme(4,  "Two Kittens",     R.drawable.img_theme_two_kittens, true,  0xFFB0BEC5.toInt(), 0xFF00B0FF.toInt(), 0x8800B0FF.toInt(), 0xFF1C2D42.toInt(), 0xFF4A5C70.toInt()),
        Theme(5,  "Golden Lion",     R.drawable.img_theme_lion,        true,  0xFF37474F.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFECEFF1.toInt()),
        Theme(6,  "Puppy Love",      R.drawable.img_theme_puppy,       true,  0xFF6D4C41.toInt(), 0xFFFF8A65.toInt(), 0x88FF8A65.toInt(), 0xFFFFFFFF.toInt(), 0xFFD7CCC8.toInt()),
        Theme(7,  "Siberian Tiger",  R.drawable.img_theme_tiger,        true,  0xFF4E342E.toInt(), 0xFFFF8F00.toInt(), 0x88FF8F00.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE082.toInt()),
        Theme(8,  "Snowy Kitten",    R.drawable.img_theme_snowy_kitten, true,  0xFF8E98AA.toInt(), 0xFF00E5FF.toInt(), 0x8800E5FF.toInt(), 0xFF1E2229.toInt(), 0xFF5C6479.toInt()),
        Theme(9,  "Forest Bear",     R.drawable.img_theme_forest_bear,  true,  0xFF37474F.toInt(), 0xFF2E7D32.toInt(), 0x882E7D32.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8E6C9.toInt()),
        Theme(10, "King of Jungle",  R.drawable.img_theme_majestic_lion, true, 0xFF37474F.toInt(), 0xFF7E57C2.toInt(), 0x887E57C2.toInt(), 0xFFFFFFFF.toInt(), 0xFFD1C4E9.toInt()),

        // ── ANIME (11-20) ─────────────────────────────────────────────
        Theme(11, "Neon City",       R.drawable.img_theme_anime_city,  true,  0xFF5A3E85.toInt(), 0xFFE040FB.toInt(), 0x88E040FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0BEC5.toInt()),
        Theme(12, "Sakura Pink",     R.drawable.bg_gradient_pink,      false, 0xFF880E4F.toInt(), 0xFFFF80AB.toInt(), 0x88FF80AB.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt()),
        Theme(13, "Space Nebula",    R.drawable.bg_theme_neon,         false, 0xFF5A3E85.toInt(), 0xFFE040FB.toInt(), 0x88E040FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0BEC5.toInt()),
        Theme(14, "Dragon Fire",     R.drawable.bg_gradient_orange,    false, 0xFF4E342E.toInt(), 0xFFFF6F00.toInt(), 0x88FF6F00.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt()),
        Theme(15, "Crystal Sky",     R.drawable.bg_gradient_sky,       false, 0xFF0D47A1.toInt(), 0xFF40C4FF.toInt(), 0x8840C4FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt()),
        Theme(16, "Samurai Red",     R.drawable.bg_gradient_red,       false, 0xFF4A0010.toInt(), 0xFFFF5252.toInt(), 0x88FF5252.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCDD2.toInt()),
        Theme(17, "Spirit Forest",   R.drawable.bg_theme_forest,       false, 0xFF2E7D32.toInt(), 0xFF00E676.toInt(), 0x8800E676.toInt(), 0xFFE8F5E9.toInt(), 0xFFA5D6A7.toInt()),
        Theme(18, "Starfall",        R.drawable.bg_gradient_purple,    false, 0xFF4A148C.toInt(), 0xFFEA80FC.toInt(), 0x88EA80FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFE1BEE7.toInt()),
        Theme(19, "Mecha Dark",      R.drawable.bg_theme_panther,      false, 0xFF37474F.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFF90A4AE.toInt()),
        Theme(20, "Magic Realm",     R.drawable.bg_gradient_sunset,    false, 0xFF4E342E.toInt(), 0xFFFFD54F.toInt(), 0x88FFD54F.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt()),

        // ── EMOJI (21-30) ─────────────────────────────────────────────
        Theme(21, "Heart Beat",      R.drawable.bg_gradient_pink,      false, 0xFF880E4F.toInt(), 0xFFFF4081.toInt(), 0x88FF4081.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt(), "❤️", "🥰"),
        Theme(22, "Bubble Hearts",   R.drawable.bg_gradient_pink,      false, 0xFF880E4F.toInt(), 0xFFFF1744.toInt(), 0x88FF1744.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt(), "❤️", "💖"),
        Theme(23, "Sunset Love",     R.drawable.bg_gradient_sunset,    false, 0xFF4E342E.toInt(), 0xFFFFAB40.toInt(), 0x88FFAB40.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt(), "😍", "😘"),
        Theme(24, "Bomb Blast",      R.drawable.bg_gradient_orange,    false, 0xFF3E2723.toInt(), 0xFFFF3D00.toInt(), 0x88FF3D00.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt(), "💣", "💥"),
        Theme(25, "Rain Shower",     R.drawable.bg_gradient_sky,       false, 0xFF0D47A1.toInt(), 0xFF00E5FF.toInt(), 0x8800E5FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt(), "🌧️", "☔"),
        Theme(26, "Sky Flight",      R.drawable.bg_gradient_sky,       false, 0xFF0D47A1.toInt(), 0xFF29B6F6.toInt(), 0x8829B6F6.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt(), "☁️", "✈️"),
        Theme(27, "Neon Hearts",     R.drawable.bg_gradient_purple,    false, 0xFF4A148C.toInt(), 0xFFF50057.toInt(), 0x88F50057.toInt(), 0xFFFFFFFF.toInt(), 0xFFE1BEE7.toInt(), "💖", "💕"),
        Theme(28, "Sunset Fire",     R.drawable.bg_gradient_sunset,    false, 0xFF4E342E.toInt(), 0xFFFF3D00.toInt(), 0x88FF3D00.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt(), "🔥", "⚡"),
        Theme(29, "Folk Fusion",     R.drawable.bg_gradient_orange,    false, 0xFFE65100.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt(), "👳", "👩"),
        Theme(30, "Lion Wild",       R.drawable.bg_theme_forest,       false, 0xFF1B5E20.toInt(), 0xFFFFEB3B.toInt(), 0x88FFEE58.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8E6C9.toInt(), "🦁", "🌻"),

        // ── GRADIENTS (31-40) ─────────────────────────────────────────
        Theme(31, "Ocean Blue",      R.drawable.bg_theme_kitten,       false, 0xFF8E98AA.toInt(), 0xFF1E88E5.toInt(), 0x881E88E5.toInt(), 0xFF1E2229.toInt(), 0xFF5C6479.toInt()),
        Theme(32, "Neon Nebula",     R.drawable.bg_theme_neon,         false, 0xFF5A3E85.toInt(), 0xFFE040FB.toInt(), 0x88E040FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0BEC5.toInt()),
        Theme(33, "Midnight Slate",  R.drawable.bg_theme_panther,      false, 0xFF37474F.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFF90A4AE.toInt()),
        Theme(34, "Forest Green",    R.drawable.bg_theme_forest,       false, 0xFF2E7D32.toInt(), 0xFF00E676.toInt(), 0x8800E676.toInt(), 0xFFE8F5E9.toInt(), 0xFFA5D6A7.toInt()),
        Theme(35, "Rose Bliss",      R.drawable.bg_gradient_pink,      false, 0xFF880E4F.toInt(), 0xFFFF80AB.toInt(), 0x88FF80AB.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt()),
        Theme(36, "Ember Glow",      R.drawable.bg_gradient_orange,    false, 0xFF4E342E.toInt(), 0xFFFF8A65.toInt(), 0x88FF8A65.toInt(), 0xFFFFFFFF.toInt(), 0xFFD7CCC8.toInt()),
        Theme(37, "Sky Dream",       R.drawable.bg_gradient_sky,       false, 0xFF0D47A1.toInt(), 0xFF40C4FF.toInt(), 0x8840C4FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt()),
        Theme(38, "Crimson Night",   R.drawable.bg_gradient_red,       false, 0xFF4A0010.toInt(), 0xFFFF5252.toInt(), 0x88FF5252.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCDD2.toInt()),
        Theme(39, "Royal Purple",    R.drawable.bg_gradient_purple,    false, 0xFF4A148C.toInt(), 0xFFEA80FC.toInt(), 0x88EA80FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFE1BEE7.toInt()),
        Theme(40, "Golden Sunset",   R.drawable.bg_gradient_sunset,    false, 0xFF4E342E.toInt(), 0xFFFFD54F.toInt(), 0x88FFD54F.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt()),

        // ── HOT / TRENDING (41-50) ────────────────────────────────────
        Theme(41, "Wild Panther",    R.drawable.img_theme_panther,      true,  0xFF5A3E85.toInt(), 0xFFE040FB.toInt(), 0x88E040FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0BEC5.toInt()),
        Theme(42, "Neon Dreams",     R.drawable.bg_theme_neon,          false, 0xFF5A3E85.toInt(), 0xFFE040FB.toInt(), 0x88E040FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0BEC5.toInt()),
        Theme(43, "Lone Wolf",       R.drawable.img_theme_majestic_wolf, true, 0xFF1A237E.toInt(), 0xFF82B1FF.toInt(), 0x8882B1FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt()),
        Theme(44, "Pink Haze",       R.drawable.bg_gradient_pink,       false, 0xFF880E4F.toInt(), 0xFFFF80AB.toInt(), 0x88FF80AB.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt()),
        Theme(45, "Eagle Strike",    R.drawable.img_theme_majestic_eagle, true, 0xFF4E342E.toInt(), 0xFFFFAB40.toInt(), 0x88FFAB40.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt()),
        Theme(46, "Violet Space",    R.drawable.bg_gradient_purple,     false, 0xFF4A148C.toInt(), 0xFFEA80FC.toInt(), 0x88EA80FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFE1BEE7.toInt()),
        Theme(47, "Forest Magic",    R.drawable.img_theme_bear,         true,  0xFF37474F.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFECEFF1.toInt()),
        Theme(48, "Warm Sunset",     R.drawable.bg_gradient_sunset,     false, 0xFF4E342E.toInt(), 0xFFFFD54F.toInt(), 0x88FFD54F.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt()),
        Theme(49, "Buddy Puppy",     R.drawable.img_theme_puppy,        true,  0xFF6D4C41.toInt(), 0xFFFF8A65.toInt(), 0x88FF8A65.toInt(), 0xFFFFFFFF.toInt(), 0xFFD7CCC8.toInt()),
        Theme(50, "Sky High",        R.drawable.bg_gradient_sky,        false, 0xFF0D47A1.toInt(), 0xFF40C4FF.toInt(), 0x8840C4FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt()),

        // ── SPORTS (51-60) ────────────────────────────────────────────
        Theme(51, "C. Ronaldo 7",    R.drawable.img_theme_ronaldo,     true,  0xFFD50000.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFECB3.toInt(), "⚽", "🏆"),
        Theme(52, "Babar Azam",      R.drawable.img_theme_babar,       true,  0xFF0D5C3A.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8E6C9.toInt(), "🏏", "🟢"),
        Theme(53, "John Cena",       R.drawable.img_theme_cena,        true,  0xFF00B0FF.toInt(), 0xFFEA80FC.toInt(), 0x88EA80FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFE1BEE7.toInt(), "🥊", "💪"),
        Theme(54, "Virat Kohli",     R.drawable.img_theme_kohli,       true,  0xFF1565C0.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt(), "🏏", "👑"),
        Theme(55, "Lionel Messi",    R.drawable.img_theme_messi,       true,  0xFF0288D1.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFF1A237E.toInt(), 0xFFBBDEFB.toInt(), "⚽", "🐐"),
        Theme(56, "AB de Villiers",  R.drawable.img_theme_devilliers,  true,  0xFF2E7D32.toInt(), 0xFFFF1744.toInt(), 0x88FF1744.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8E6C9.toInt(), "🏏", "👽"),
        Theme(57, "Imran Khan",      R.drawable.img_theme_imran,       true,  0xFF0A5D34.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8E6C9.toInt(), "🏏", "🏆"),
        Theme(58, "MS Dhoni",        R.drawable.img_theme_dhoni,       true,  0xFFF57F17.toInt(), 0xFF0D47A1.toInt(), 0x880D47A1.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFF9C4.toInt(), "🏏", "🇮🇳"),
        Theme(59, "Neymar Jr",       R.drawable.img_theme_neymar,      true,  0xFF2E7D32.toInt(), 0xFFFFEB3B.toInt(), 0x88FFEB3B.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8E6C9.toInt(), "⚽", "🇧🇷"),
        Theme(60, "LeBron James",    R.drawable.img_theme_lebron,      true,  0xFF4A148C.toInt(), 0xFFFFD54F.toInt(), 0x88FFD54F.toInt(), 0xFFFFFFFF.toInt(), 0xFFE1BEE7.toInt(), "🏀", "👑"),

        // ── EXTRA ANIMALS (61-62) ─────────────────────────────────────
        Theme(61, "Markhor",         R.drawable.img_theme_markhor,     true,  0xFF6D4C41.toInt(), 0xFFFFD54F.toInt(), 0x88FFD54F.toInt(), 0xFFFFFFFF.toInt(), 0xFFD7CCC8.toInt(), "🐐", "🏔️"),
        Theme(62, "Penguin",         R.drawable.img_theme_penguin,     true,  0xFF0277BD.toInt(), 0xFFE0F7FA.toInt(), 0x88E0F7FA.toInt(), 0xFFFFFFFF.toInt(), 0xFFB3E5FC.toInt(), "🐧", "❄️"),

        // ── EXTRA SPORTS (63-64) ──────────────────────────────────────
        Theme(63, "Canelo Álvarez",  R.drawable.img_theme_canelo,      true,  0xFF4A0010.toInt(), 0xFFFFD700.toInt(), 0x88FFD700.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCDD2.toInt(), "🥊", "🏆"),
        Theme(64, "Stephen Curry",   R.drawable.img_theme_curry,       true,  0xFF0D47A1.toInt(), 0xFFFFD54F.toInt(), 0x88FFD54F.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBDEFB.toInt(), "🏀", "👑")
    )
    private val map: Map<Int, Theme> = all.associateBy { it.id }

    fun getById(id: Int): Theme = map[id] ?: map[1]!!

    fun getAll(): List<Theme> = all
}
