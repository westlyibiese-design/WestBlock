package com.westly.westblock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Header(score: Int, best: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "WEST BLOCK",
            style = TextStyle(
                color = Amber,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 3.sp,
                shadow = Shadow(color = Color(0xFF5A2E0E), offset = Offset(0f, 6f), blurRadius = 0f),
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("SCORE", score, Modifier.weight(1f))
            StatCard("BEST", best, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value > 0) {
            pop.snapTo(1.3f)
            pop.animateTo(1f, tween(220))
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom)))
            .border(1.dp, Rim, shape)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = Cream.copy(alpha = 0.65f),
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value.toString(),
                color = Cream,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
        }
    }
}

@Composable
fun ProgressToBest(score: Int, best: Int) {
    val target = if (best > 0) (score.toFloat() / best).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, tween(300), label = "progress")
    val shape = RoundedCornerShape(6.dp)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Beat your best", color = Cream.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(if (best > 0) best.toString() else "", color = Sand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(shape)
                .background(Track)
                .border(1.dp, Rim, shape)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Amber, AmberDeep)))
            )
        }
    }
}

@Composable
fun GameButton(
    label: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (enabled) background else background.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ComboBanner(visible: Boolean, text: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(tween(250)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Amber)
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            Text(text, color = BtnAmberText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun GameOverOverlay(
    visible: Boolean,
    score: Int,
    best: Int,
    newBest: Boolean,
    onNewGame: () -> Unit,
    onShare: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xA6000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier
                    .width(290.dp)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(Color(0xFF5A3A28), Color(0xFF3A261A))))
                    .border(2.dp, Amber, shape)
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Game Over!",
                    style = TextStyle(
                        color = Amber,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    ),
                )
                if (newBest && score > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("🏆 New best!", color = Sand, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                Text("Score", color = Cream.copy(alpha = 0.65f), fontSize = 12.sp, letterSpacing = 2.sp)
                Text(score.toString(), color = Cream, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Best  $best", color = Sand, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameButton("🆕 New Game", BtnAmber, BtnAmberText, Modifier.weight(1.4f), onClick = onNewGame)
                    GameButton("📤", BtnGreen, BtnGreenText, Modifier.weight(0.6f), onClick = onShare)
                }
            }
        }
    }
}
