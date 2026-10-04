package com.westly.westblock.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.westblock.game.Board
import com.westly.westblock.game.Engine
import com.westly.westblock.game.Piece
import com.westly.westblock.game.SIZE
import com.westly.westblock.vm.FloatPts
import com.westly.westblock.vm.GameViewModel
import kotlin.math.roundToInt

private val BoardPad = 6.dp
private val CellGap = 3.dp
private val TrayHeight = 112.dp
private val GhostLift = 30.dp
private val BoardTraySpace = 20.dp

private class DragState(val idx: Int, val finger: Offset)

private class Preview(val cells: List<Pair<Int, Int>>, val ok: Boolean)

@Composable
fun GameScreen(vm: GameViewModel) {
    val density = LocalDensity.current
    val context = LocalContext.current

    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf<DragState?>(null) }

    // ---- geometry (all in px) ----
    val padPx = with(density) { BoardPad.toPx() }
    val gapPx = with(density) { CellGap.toPx() }
    val liftPx = with(density) { GhostLift.toPx() }
    val trayPx = with(density) { TrayHeight.toPx() }
    val spacePx = with(density) { BoardTraySpace.toPx() }
    val minSidePx = with(density) { 160.dp.toPx() }
    val sidePx = maxOf(
        minSidePx,
        minOf(areaSize.width.toFloat(), areaSize.height.toFloat() - trayPx - spacePx),
    )
    val cellPx = (sidePx - 2 * padPx - (SIZE - 1) * gapPx) / SIZE

    fun ghostOrigin(d: DragState, piece: Piece): Offset {
        val size = Engine.pieceSize(piece.shape)
        val gw = size.second * (cellPx + gapPx) - gapPx
        val gh = size.first * (cellPx + gapPx) - gapPx
        return Offset(d.finger.x - gw / 2f, d.finger.y - gh - liftPx)
    }

    fun targetCell(origin: Offset): Pair<Int, Int> {
        val c0 = ((origin.x - (boardOrigin.x + padPx)) / (cellPx + gapPx)).roundToInt()
        val r0 = ((origin.y - (boardOrigin.y + padPx)) / (cellPx + gapPx)).roundToInt()
        return Pair(r0, c0)
    }

    val d = drag
    val dragPiece = if (d != null) vm.tray.getOrNull(d.idx) else null

    var preview: Preview? = null
    if (d != null && dragPiece != null) {
        val cell = targetCell(ghostOrigin(d, dragPiece))
        val snap = Engine.findSnapCell(vm.board, dragPiece, cell.first, cell.second)
        val base = snap ?: cell
        preview = Preview(
            cells = dragPiece.shape.map { Pair(base.first + it.r, base.second + it.c) },
            ok = snap != null,
        )
    }

    val shareScore = {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "🏆 I scored ${vm.score} on WEST BLOCK! Can you beat me?")
            }
            context.startActivity(Intent.createChooser(send, "Share your score"))
        } catch (e: Exception) {
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(vm.score, vm.best)
            Spacer(Modifier.height(10.dp))
            ProgressToBest(vm.score, vm.best)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { areaSize = it },
                contentAlignment = Alignment.Center,
            ) {
                if (areaSize != IntSize.Zero) {
                    val sideDp = with(density) { sidePx.toDp() }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BoardView(
                            modifier = Modifier
                                .size(sideDp)
                                .onGloballyPositioned { boardOrigin = it.positionInRoot() },
                            sidePx = sidePx,
                            board = vm.board,
                            preview = preview,
                            clearing = vm.clearing,
                            clearTick = vm.clearTick,
                            floating = vm.floating,
                        )
                        Spacer(Modifier.height(BoardTraySpace))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TrayHeight),
                        ) {
                            vm.tray.forEachIndexed { idx, piece ->
                                TraySlot(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    idx = idx,
                                    piece = piece,
                                    hidden = drag?.idx == idx,
                                    cellPx = cellPx,
                                    onStart = { slotIdx, pos ->
                                        if (vm.gameOver || vm.isBusy) {
                                            false
                                        } else {
                                            vm.onTouch()
                                            vm.onPickUp()
                                            drag = DragState(slotIdx, pos)
                                            true
                                        }
                                    },
                                    onMove = { pos ->
                                        val cur = drag
                                        if (cur != null) drag = DragState(cur.idx, pos)
                                    },
                                    onEnd = {
                                        val cur = drag
                                        drag = null
                                        val p = if (cur != null) vm.tray.getOrNull(cur.idx) else null
                                        if (cur != null && p != null) {
                                            val cell = targetCell(ghostOrigin(cur, p))
                                            vm.place(cur.idx, cell.first, cell.second)
                                        }
                                    },
                                    onCancel = { drag = null },
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GameButton(
                    label = "🔀 Shuffle (${vm.shufflesLeft})",
                    background = BtnBlue,
                    textColor = BtnBlueText,
                    modifier = Modifier.weight(1.6f),
                    enabled = vm.shufflesLeft > 0 && !vm.gameOver,
                    onClick = { vm.shuffle() },
                )
                GameButton(
                    label = "🆕 New Game",
                    background = BtnAmber,
                    textColor = BtnAmberText,
                    modifier = Modifier.weight(1.4f),
                    onClick = { vm.newGame() },
                )
                GameButton(
                    label = if (vm.soundOn) "🔊" else "🔇",
                    background = BtnBrown,
                    textColor = Cream,
                    modifier = Modifier.weight(0.6f),
                    onClick = { vm.toggleSound() },
                )
                GameButton(
                    label = "📤",
                    background = BtnGreen,
                    textColor = BtnGreenText,
                    modifier = Modifier.weight(0.6f),
                    onClick = { shareScore() },
                )
            }
        }

        // ---- dragged piece, drawn above everything ----
        if (d != null && dragPiece != null) {
            val o = ghostOrigin(d, dragPiece)
            val size = Engine.pieceSize(dragPiece.shape)
            val gw = size.second * (cellPx + gapPx) - gapPx
            val gh = size.first * (cellPx + gapPx) - gapPx
            Canvas(
                Modifier
                    .offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                    .size(with(density) { gw.toDp() }, with(density) { gh.toDp() })
                    .graphicsLayer {
                        alpha = 0.92f
                        scaleX = 1.06f
                        scaleY = 1.06f
                    }
            ) {
                val color = Color(dragPiece.color)
                for (cell in dragPiece.shape) {
                    drawBlock(
                        cell.c * (cellPx + gapPx),
                        cell.r * (cellPx + gapPx),
                        cellPx,
                        color,
                    )
                }
            }
        }

        ComboBanner(
            visible = vm.comboVisible,
            text = vm.comboText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 64.dp),
        )

        GameOverOverlay(
            visible = vm.gameOver,
            score = vm.score,
            best = vm.best,
            newBest = vm.newBestThisGame,
            onNewGame = { vm.newGame() },
            onShare = { shareScore() },
        )
    }
}

// ----------------------------------------------------------------------------
// Board
// ----------------------------------------------------------------------------

@Composable
private fun BoardView(
    modifier: Modifier,
    sidePx: Float,
    board: Board,
    preview: Preview?,
    clearing: Set<Int>,
    clearTick: Int,
    floating: List<FloatPts>,
) {
    val density = LocalDensity.current
    val padPx = with(density) { BoardPad.toPx() }
    val gapPx = with(density) { CellGap.toPx() }
    val cellPx = (sidePx - 2 * padPx - (SIZE - 1) * gapPx) / SIZE

    val flash = remember { Animatable(0f) }
    LaunchedEffect(clearTick) {
        if (clearTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(290))
        }
    }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = 18.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(PanelTop, PanelBottom)),
                cornerRadius = CornerRadius(corner, corner),
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Gold, Rim)),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 2.dp.toPx()),
            )
            val f = flash.value
            val emptyRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            for (r in 0 until SIZE) {
                for (c in 0 until SIZE) {
                    val x = padPx + c * (cellPx + gapPx)
                    val y = padPx + r * (cellPx + gapPx)
                    val v = board[r, c]
                    if (v == 0) {
                        drawRoundRect(
                            color = EmptyCell,
                            topLeft = Offset(x, y),
                            size = Size(cellPx, cellPx),
                            cornerRadius = emptyRadius,
                        )
                    } else if (clearing.contains(r * SIZE + c)) {
                        val grow = f * 3.dp.toPx()
                        drawBlock(
                            x - grow,
                            y - grow,
                            cellPx + 2 * grow,
                            lerp(Color(v), Color.White, f * 0.85f),
                        )
                    } else {
                        drawBlock(x, y, cellPx, Color(v))
                    }
                }
            }
            if (preview != null) {
                val tint = if (preview.ok) PreviewOk else PreviewBad
                for (cell in preview.cells) {
                    val r = cell.first
                    val c = cell.second
                    if (r in 0 until SIZE && c in 0 until SIZE) {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(padPx + c * (cellPx + gapPx), padPx + r * (cellPx + gapPx)),
                            size = Size(cellPx, cellPx),
                            cornerRadius = emptyRadius,
                        )
                    }
                }
            }
        }

        for (item in floating) {
            key(item.id) {
                FloatingText(
                    text = item.text,
                    cx = padPx + (item.c + 0.5f) * (cellPx + gapPx),
                    cy = padPx + (item.r + 0.5f) * (cellPx + gapPx),
                )
            }
        }
    }
}

@Composable
private fun FloatingText(text: String, cx: Float, cy: Float) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (cx - 45.dp.toPx()).roundToInt(),
                    (cy - 15.dp.toPx() - 55.dp.toPx() * t.value).roundToInt(),
                )
            }
            .size(90.dp, 30.dp)
            .graphicsLayer {
                alpha = 1f - t.value
                scaleX = 1f + 0.3f * t.value
                scaleY = 1f + 0.3f * t.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Cream,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                shadow = androidx.compose.ui.graphics.Shadow(Amber, Offset.Zero, 12f),
            ),
        )
    }
}

// ----------------------------------------------------------------------------
// Tray
// ----------------------------------------------------------------------------

@Composable
private fun TraySlot(
    modifier: Modifier,
    idx: Int,
    piece: Piece?,
    hidden: Boolean,
    cellPx: Float,
    onStart: (Int, Offset) -> Boolean,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    val slotRoot = remember { mutableStateOf(Offset.Zero) }
    val startCb = rememberUpdatedState(onStart)
    val moveCb = rememberUpdatedState(onMove)
    val endCb = rememberUpdatedState(onEnd)
    val cancelCb = rememberUpdatedState(onCancel)

    var m = modifier.onGloballyPositioned { slotRoot.value = it.positionInRoot() }
    if (piece != null) {
        m = m.pointerInput(piece.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val accepted = startCb.value.invoke(idx, slotRoot.value + down.position)
                if (!accepted) return@awaitEachGesture
                down.consume()
                val finished = drag(down.id) { change ->
                    change.consume()
                    moveCb.value.invoke(slotRoot.value + change.position)
                }
                if (finished) endCb.value.invoke() else cancelCb.value.invoke()
            }
        }
    }

    Canvas(m) {
        if (piece == null) return@Canvas
        val size2 = Engine.pieceSize(piece.shape)
        val rows = size2.first
        val cols = size2.second
        val gap = CellGap.toPx()
        val slotPad = 8.dp.toPx()
        val fitW = (size.width - 2 * slotPad - (cols - 1) * gap) / cols
        val fitH = (size.height - 2 * slotPad - (rows - 1) * gap) / rows
        val pc = minOf(cellPx * 0.68f, fitW, fitH).coerceAtLeast(10.dp.toPx())
        val w = cols * pc + (cols - 1) * gap
        val h = rows * pc + (rows - 1) * gap
        val ox = (size.width - w) / 2f
        val oy = (size.height - h) / 2f
        val color = Color(piece.color)
        val a = if (hidden) 0.3f else 1f
        for (cell in piece.shape) {
            drawBlock(ox + cell.c * (pc + gap), oy + cell.r * (pc + gap), pc, color, a)
        }
    }
}

// ----------------------------------------------------------------------------
// Block drawing: rounded tile with a soft top-to-bottom gradient and a glossy shine
// ----------------------------------------------------------------------------

private fun DrawScope.drawBlock(x: Float, y: Float, s: Float, base: Color, alpha: Float = 1f) {
    val light = lerp(base, Color.White, 0.28f)
    val dark = lerp(base, Color.Black, 0.32f)
    val radius = CornerRadius(s * 0.2f, s * 0.2f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(light, base, dark), startY = y, endY = y + s),
        topLeft = Offset(x, y),
        size = Size(s, s),
        cornerRadius = radius,
        alpha = alpha,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f * alpha),
        topLeft = Offset(x + s * 0.12f, y + s * 0.08f),
        size = Size(s * 0.76f, s * 0.22f),
        cornerRadius = CornerRadius(s * 0.11f, s * 0.11f),
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.2f * alpha),
        topLeft = Offset(x, y),
        size = Size(s, s),
        cornerRadius = radius,
        style = Stroke(width = s * 0.04f),
    )
}
