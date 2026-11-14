package com.carmooddj.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0E1116)
private val Panel = Color(0xFF171B22)
private val Panel2 = Color(0xFF232A34)
private val TextMain = Color(0xFFF2F4F7)
private val TextSub = Color(0xFF9AA3AF)
private val TextSoft = Color(0xFFC9D1D9)
private val Arrive = Color(0xFFFFA24A)
private val ArriveDim = Color(0xFF6B4320)

@Composable
fun DjApp(state: DjState, synth: SynthPlayer) {
    val signals = if (state.demoMode) state.demo else state.real
    val mood = MoodRules.decide(signals, allowSnow = state.demoMode)
    val accent = Color(mood.color)

    LaunchedEffect(mood) { synth.setMood(mood) }
    LaunchedEffect(state.playing) { synth.setPlaying(state.playing) }
    LaunchedEffect(state.variation) { synth.setVariation(state.variation) }

    // 시연 모드: 도착까지 남은 시간을 4초에 1분씩 줄입니다.
    LaunchedEffect(state.demoMode, state.demoCountdown) {
        while (state.demoMode && state.demoCountdown) {
            delay(4000)
            val remaining = state.demo.remainingMin
            if (remaining == null || remaining <= 0) {
                state.demo = state.demo.copy(navActive = false, remainingMin = null)
                state.demoCountdown = false
            } else {
                state.demo = state.demo.copy(remainingMin = remaining - 1)
            }
        }
    }

    // 경로 진행률 계산용: 안내 시작 이후 가장 컸던 남은 시간을 전체 시간으로 봅니다.
    var totalMin by remember { mutableStateOf(0) }
    LaunchedEffect(signals.navActive, signals.remainingMin) {
        val remaining = signals.remainingMin
        if (!signals.navActive || remaining == null) {
            totalMin = 0
        } else if (remaining > totalMin) {
            totalMin = remaining
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
    ) {
        val wide = maxWidth >= 900.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Header(state, accent)
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Situation(mood, accent)
                        NowPlaying(state, mood, accent)
                    }
                    Column(modifier = Modifier.width(380.dp)) {
                        SignalPanel(signals, mood, accent, state.demoMode)
                    }
                }
            } else {
                Situation(mood, accent)
                NowPlaying(state, mood, accent)
                SignalPanel(signals, mood, accent, state.demoMode)
            }
            Timeline(signals, totalMin, mood, accent)
            if (state.demoMode) {
                DemoPanel(state, accent)
            }
            StatusLines(state)
        }
    }
}

@Composable
private fun Header(state: DjState, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = "나만의 차량용 DJ",
            color = TextMain,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Pill(
            text = if (state.demoMode) "시연 모드 켜짐" else "시연 모드",
            selected = state.demoMode,
            accent = accent
        ) {
            state.demoMode = !state.demoMode
            if (!state.demoMode) state.demoCountdown = false
        }
    }
}

@Composable
private fun Situation(mood: Mood, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "지금 상황 · ${mood.label}", color = accent, fontSize = 18.sp)
        Text(
            text = mood.headline,
            color = TextMain,
            fontSize = 48.sp,
            lineHeight = 58.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = mood.detail, color = TextSub, fontSize = 22.sp, lineHeight = 32.sp)
    }
}

@Composable
private fun NowPlaying(state: DjState, mood: Mood, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (state.playing) "재생 중" else "일시정지됨",
                color = TextSub,
                fontSize = 16.sp
            )
            Text(
                text = mood.mix,
                color = TextMain,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "데모 트랙 0${state.variation % 4 + 1} · 앱에서 합성한 음원",
                color = TextSub,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.width(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill(text = "이전", selected = false, accent = accent) {
                state.variation = (state.variation + 3) % 4
            }
            Pill(
                text = if (state.playing) "일시정지" else "재생",
                selected = true,
                accent = accent
            ) {
                state.playing = !state.playing
            }
            Pill(text = "다음", selected = false, accent = accent) {
                state.variation = (state.variation + 1) % 4
            }
        }
    }
}

@Composable
private fun SignalPanel(signals: Signals, mood: Mood, accent: Color, demo: Boolean) {
    val wiperActive = mood == Mood.RAIN || mood == Mood.SNOW
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "차량이 감지한 신호", color = TextSub, fontSize = 18.sp)
        SignalRow("와이퍼", onOff(signals.wiperOn), wiperActive, accent)
        SignalRow(
            // 시연 모드의 온도는 바깥 기온을 흉내 낸 값, 실제 모드는 차량 실내 온도입니다.
            if (demo) "바깥 기온 (시연)" else "실내 온도",
            signals.tempC?.let { "${Math.round(it)}°C" } ?: "값 없음",
            mood == Mood.SNOW,
            accent
        )
        SignalRow("전조등", onOff(signals.headlightOn), mood == Mood.NIGHT, accent)
        SignalRow(
            "도착까지",
            if (signals.navActive && signals.remainingMin != null) {
                "${signals.remainingMin}분"
            } else {
                "경로 없음"
            },
            mood == Mood.ARRIVING,
            accent
        )
    }
}

private fun onOff(value: Boolean?): String = when (value) {
    true -> "켜짐"
    false -> "꺼짐"
    null -> "값 없음"
}

@Composable
private fun SignalRow(label: String, value: String, active: Boolean, accent: Color) {
    val shape = RoundedCornerShape(18.dp)
    var modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Panel)
    if (active) {
        modifier = modifier.border(2.dp, accent, shape)
    }
    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (active) TextSoft else TextSub,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = if (active) accent else TextMain,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Timeline(signals: Signals, totalMin: Int, mood: Mood, accent: Color) {
    val remaining = signals.remainingMin
    val active = signals.navActive && remaining != null
    val arriving = mood == Mood.ARRIVING
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (active) "경로 안내 중 · 도착까지 ${remaining}분" else "경로 안내 없음",
                color = TextSoft,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (arriving) {
                    "신나는 곡으로 전환됨"
                } else {
                    "도착 ${MoodRules.ARRIVAL_MINUTES}분 전부터 신나는 곡"
                },
                color = Arrive,
                fontSize = 18.sp
            )
        }
        val total = if (totalMin > 0) totalMin else 1
        val done = if (active && remaining != null) {
            ((total - remaining).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(modifier = Modifier.weight(0.88f)) {
                if (done > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(done.coerceAtMost(1f))
                            .height(14.dp)
                            .background(if (arriving) Panel2 else accent)
                    )
                }
                if (done < 0.99f) {
                    Box(
                        modifier = Modifier
                            .weight(1f - done.coerceAtMost(0.99f))
                            .height(14.dp)
                            .background(Panel2)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(0.12f)
                    .height(14.dp)
                    .background(if (arriving) Arrive else ArriveDim)
            )
        }
    }
}

@Composable
private fun DemoPanel(state: DjState, accent: Color) {
    val demo = state.demo
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "시연 모드 · 차량 신호를 직접 바꿔 봅니다 (실제 차량 값은 무시됩니다)",
            color = TextSoft,
            fontSize = 18.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "날씨", color = TextSub, fontSize = 18.sp, modifier = Modifier.width(96.dp))
            Pill("와이퍼 ${onOff(demo.wiperOn)}", demo.wiperOn == true, accent) {
                state.demo = state.demo.copy(wiperOn = state.demo.wiperOn != true)
            }
            Pill("바깥 기온 14°C", demo.tempC == 14f, accent) {
                state.demo = state.demo.copy(tempC = 14f)
            }
            Pill("바깥 기온 -2°C", demo.tempC == -2f, accent) {
                state.demo = state.demo.copy(tempC = -2f)
            }
            Pill("전조등 ${onOff(demo.headlightOn)}", demo.headlightOn == true, accent) {
                state.demo = state.demo.copy(headlightOn = state.demo.headlightOn != true)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "도착까지", color = TextSub, fontSize = 18.sp, modifier = Modifier.width(96.dp))
            Pill("10분", demo.navActive && demo.remainingMin == 10, accent) {
                state.demo = state.demo.copy(navActive = true, remainingMin = 10)
            }
            Pill("5분", demo.navActive && demo.remainingMin == 5, accent) {
                state.demo = state.demo.copy(navActive = true, remainingMin = 5)
            }
            Pill("3분", demo.navActive && demo.remainingMin == 3, accent) {
                state.demo = state.demo.copy(navActive = true, remainingMin = 3)
            }
            Pill("경로 없음", !demo.navActive, accent) {
                state.demo = state.demo.copy(navActive = false, remainingMin = null)
                state.demoCountdown = false
            }
            Pill(
                if (state.demoCountdown) "카운트다운 멈춤" else "카운트다운 시작",
                state.demoCountdown,
                accent
            ) {
                if (!state.demoCountdown && !state.demo.navActive) {
                    state.demo = state.demo.copy(navActive = true, remainingMin = 6)
                }
                state.demoCountdown = !state.demoCountdown
            }
        }
    }
}

@Composable
private fun StatusLines(state: DjState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = state.vehicleStatus, color = TextSub, fontSize = 14.sp)
        for ((key, label) in listOf("wiper" to "와이퍼", "light" to "전조등", "temp" to "온도")) {
            Text(
                text = "$label: ${state.diag[key] ?: "응답 없음"}",
                color = TextSub,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(text = state.naviStatus, color = TextSub, fontSize = 14.sp)
        if (state.naviTrack.isNotEmpty()) {
            Text(
                text = "내비 변화 기록: " + state.naviTrack.joinToString("  →  "),
                color = TextSub,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.naviRaw.isNotEmpty()) {
            Text(
                text = state.naviRaw,
                color = TextSub,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Panel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Bg else TextMain,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
