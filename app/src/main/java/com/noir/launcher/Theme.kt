package com.noir.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

object NoirColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8C8C8C)
    val Dim = Color(0xFF555555)
    val Hairline = Color(0x26FFFFFF)
}

/**
 * Police : sans-serif du système (Roboto sur la plupart des téléphones Android).
 * Pour une autre police, déposer un .ttf dans res/font/ et la remplacer ici
 * par FontFamily(Font(R.font.ma_police)).
 */
val NoirFont: FontFamily = FontFamily.SansSerif

object NoirType {
    val Clock = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Thin,
        fontSize = 92.sp,
        letterSpacing = (-3).sp,
        color = NoirColors.White,
    )
    val Date = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 17.sp,
        color = NoirColors.Muted,
    )
    val Search = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        color = NoirColors.White,
    )
    val Hint = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        color = NoirColors.Muted,
    )
    val Result = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        color = NoirColors.White,
    )
    val DayHeader = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.6.sp,
        color = NoirColors.Muted,
    )
    val EventTime = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = NoirColors.Muted,
    )
    val EventTitle = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        color = NoirColors.White,
    )
    val EventPlace = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        color = NoirColors.Muted,
    )
    val Body = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        color = NoirColors.Muted,
    )
    val Shortcut = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        color = NoirColors.White,
    )
    val WeatherSummary = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 17.sp,
        color = NoirColors.White,
    )
    val Legend = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = NoirColors.Dim,
    )
    val Title = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp,
        color = NoirColors.White,
    )
    val Featured = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        color = NoirColors.White,
    )
    val ArticleTitle = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        color = NoirColors.White,
    )
    val Poem = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 34.sp,
        color = NoirColors.White,
    )
    val PoemTitle = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        color = NoirColors.White,
    )
    val Chip = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = NoirColors.Muted,
    )
    val Action = TextStyle(
        fontFamily = NoirFont,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        color = NoirColors.White,
        textDecoration = TextDecoration.Underline,
    )
}

/** Zone cliquable sans effet visuel (pas de ripple sur fond noir). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tappable(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this.combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onLongClick = onLongClick,
    onClick = onClick,
)
