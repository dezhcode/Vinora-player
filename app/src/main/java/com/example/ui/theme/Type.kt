package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val JakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal),
    Font(R.font.vazirmatn, FontWeight.Medium),
    Font(R.font.vazirmatn, FontWeight.SemiBold),
    Font(R.font.vazirmatn, FontWeight.Bold),
)

val DigitalMonoFontFamily = FontFamily(
    Font(R.font.share_tech_mono, FontWeight.Normal),
)

fun plinthTypography(isPersian: Boolean): Typography {
    val primaryFamily = if (isPersian) VazirmatnFontFamily else JakartaFontFamily
    return Typography(
        displaySmall = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = if (isPersian) 0.sp else (-0.6).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = if (isPersian) 0.sp else (-0.4).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = if (isPersian) 0.sp else (-0.3).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = if (isPersian) 0.sp else (-0.2).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = DigitalMonoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.8.sp,
        ),
    )
}
