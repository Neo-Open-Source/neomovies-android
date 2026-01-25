package com.neo.neomovies.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

private const val ProgressThreshold = 0.35f

private val Int.forOutgoing: Int
    get() = (this * ProgressThreshold).toInt()

private val Int.forIncoming: Int
    get() = this - this.forOutgoing

fun materialSharedAxisX(
    initialOffsetX: (fullWidth: Int) -> Int,
    targetOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = 400,
): ContentTransform =
    materialSharedAxisXIn(
        initialOffsetX = initialOffsetX,
        durationMillis = durationMillis,
    ) togetherWith
        materialSharedAxisXOut(
            targetOffsetX = targetOffsetX,
            durationMillis = durationMillis,
        )

fun materialSharedAxisXIn(
    initialOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = 400,
): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        initialOffsetX = initialOffsetX,
    ) +
        fadeIn(
            animationSpec =
                tween(
                    durationMillis = durationMillis.forIncoming,
                    delayMillis = durationMillis.forOutgoing,
                    easing = LinearOutSlowInEasing,
                ),
        )

fun materialSharedAxisXOut(
    targetOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = 400,
): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        targetOffsetX = targetOffsetX,
    ) +
        fadeOut(
            animationSpec =
                tween(
                    durationMillis = durationMillis.forOutgoing,
                    delayMillis = 0,
                    easing = FastOutLinearInEasing,
                ),
        )
