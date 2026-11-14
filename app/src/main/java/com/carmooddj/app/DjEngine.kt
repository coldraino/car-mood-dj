package com.carmooddj.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 차량 상황에 따라 정해지는 선곡 분위기. */
enum class Mood(
    val label: String,
    val headline: String,
    val detail: String,
    val mix: String,
    val color: Long
) {
    NORMAL(
        "일반 주행",
        "평소처럼 달리는 중",
        "특별한 상황이 감지되면 음악이 알아서 바뀌어요.",
        "기본 드라이브 믹스",
        0xFFC9D1D9
    ),
    NIGHT(
        "야간",
        "밤길을 달리는 중",
        "전조등 점등을 감지해 차분한 밤 선곡으로 바꿨어요.",
        "밤 드라이브 믹스",
        0xFFB79CFF
    ),
    RAIN(
        "비",
        "비가 오기 시작했어요",
        "와이퍼 작동을 감지해 비 오는 날 선곡으로 바꿨어요.",
        "비 오는 날 믹스",
        0xFF5AA9FF
    ),
    SNOW(
        "눈",
        "눈이 오기 시작했어요",
        "와이퍼 작동과 낮은 온도를 함께 감지했어요.",
        "눈 오는 날 믹스",
        0xFFBFEAF5
    ),
    ARRIVING(
        "도착 임박",
        "곧 도착해요",
        "도착 3분 전이라 신나는 곡으로 분위기를 올렸어요.",
        "도착 직전 신나는 믹스",
        0xFFFFA24A
    )
}

/** 차량에서 읽은 신호. null 은 아직 값을 받지 못했다는 뜻입니다. */
data class Signals(
    val wiperOn: Boolean? = null,
    val tempC: Float? = null,
    val headlightOn: Boolean? = null,
    val remainingMin: Int? = null,
    val navActive: Boolean = false
)

/** 신호 → 분위기 판정 규칙. 위에 있을수록 우선합니다. */
object MoodRules {
    const val ARRIVAL_MINUTES = 3
    const val SNOW_TEMP_C = 2f

    /**
     * @param allowSnow 눈 판정 사용 여부.
     * 차량 SDK에 외기 온도가 없어 실제 신호로는 비와 눈을 구분할 수 없으므로,
     * 눈은 시연 모드에서만 판정합니다. (상용화 시 외기 온도·날씨 정보 연동 예정)
     */
    fun decide(s: Signals, allowSnow: Boolean = false): Mood {
        val remaining = s.remainingMin
        val temp = s.tempC
        return when {
            s.navActive && remaining != null && remaining <= ARRIVAL_MINUTES -> Mood.ARRIVING
            allowSnow && s.wiperOn == true && temp != null && temp <= SNOW_TEMP_C -> Mood.SNOW
            s.wiperOn == true -> Mood.RAIN
            s.headlightOn == true -> Mood.NIGHT
            else -> Mood.NORMAL
        }
    }
}

/** 화면이 구독하는 앱 상태. */
class DjState {
    /** 차량 SDK에서 받은 실제 신호 */
    var real by mutableStateOf(Signals())

    /** 시연 모드에서 직접 조작하는 신호 */
    var demo by mutableStateOf(Signals(wiperOn = false, tempC = 14f, headlightOn = false))

    var demoMode by mutableStateOf(false)
    var demoCountdown by mutableStateOf(false)
    var playing by mutableStateOf(true)
    var variation by mutableStateOf(0)

    var vehicleStatus by mutableStateOf("Vehicle SDK 연결 대기")
    var naviStatus by mutableStateOf("NaviHelper SDK 연결 대기")
    var naviRaw by mutableStateOf("")

    /** 내비 남은 거리·시간 변화 기록 (최근 6개) */
    var naviTrack by mutableStateOf(listOf<String>())

    /** 항목별 진단 정보 (wiper / light / temp) */
    var diag by mutableStateOf(mapOf<String, String>())
}
