package com.carmooddj.app.signals

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log

/**
 * Pleos NaviHelper SDK(3.0.5)에서 경로 안내 상태와 도착까지 남은 시간을 받습니다.
 * 콜백은 SDK 스레드에서 올 수 있으므로 받는 쪽에서 메인 스레드로 넘겨야 합니다.
 */
class NaviSignals(
    private val context: Context,
    private val callback: Callback
) : NaviHelperEventListener {

    interface Callback {
        /** 남은 시간(분). 경로 안내가 끝나면 active = false. */
        fun onRoute(active: Boolean, remainingMin: Int?)
        fun onStatus(message: String)
        fun onRaw(text: String)

        /** 남은 거리·시간이 바뀔 때마다 호출됩니다. */
        fun onTrack(line: String)
    }

    private var helper: NaviHelper? = null

    fun initialize(): Boolean {
        if (helper != null) return true
        var created: NaviHelper? = null
        return try {
            created = NaviHelper(context.applicationContext)
            created.initialize()
            created.addListener(this)
            helper = created
            callback.onStatus("NaviHelper SDK 연결됨")
            true
        } catch (t: Throwable) {
            runCatching { created?.release() }
            callback.onStatus("NaviHelper SDK 초기화 실패: $t")
            false
        }
    }

    /** 내비 앱에 현재 경로 상태를 요청합니다. 결과는 리스너 콜백으로 옵니다. */
    fun refresh() {
        val current = helper ?: return
        try {
            current.getRouteStateInfo()
        } catch (t: Throwable) {
            callback.onStatus("경로 상태 요청 실패: $t")
        }
        try {
            current.getDestinationInfo()
        } catch (t: Throwable) {
            Log.w(TAG, "getDestinationInfo failed", t)
        }
    }

    fun release() {
        val current = helper ?: return
        try {
            current.removeListener(this)
            current.release()
        } catch (t: Throwable) {
            Log.w(TAG, "release failed", t)
        } finally {
            helper = null
        }
    }

    /** 경로 안내 중인지 (RouteStateInfo.naviMode == ROUTE_GUIDING) */
    private var routing = false

    /**
     * 내비 데이터를 반영합니다.
     * - RouteStateInfo.naviMode 로 경로 안내 여부를 판단합니다.
     * - DestinationInfo.remainTime(초)을 남은 시간으로 씁니다.
     * 경로 안내 중이 아니면 remainTime 이 0 으로 오므로 무시합니다.
     */
    private val lastText = HashMap<String, String>()
    private var lastRemain: String? = null
    private var lastMode: String? = null
    private val startedAt = System.currentTimeMillis()

    private fun handle(source: String, info: Any?) {
        val text = info?.toString() ?: return
        // 같은 데이터가 반복해서 오므로 바뀌었을 때만 기록합니다.
        if (lastText[source] != text) {
            lastText[source] = text
            Log.i(TAG, "$source $text")
        }

        val mode = Regex("""naviMode=(\w+)""").find(text)?.groupValues?.get(1)
        if (mode != null && mode != lastMode) {
            lastMode = mode
            Log.i(TAG, "TRACK naviMode=$mode")
        }
        val dist = Regex("""remainDistance=(\d+)""").find(text)?.groupValues?.get(1)
        val time = Regex("""remainTime=(\d+)""").find(text)?.groupValues?.get(1)
        if (time != null) {
            val remain = "거리 ${dist}m · 시간 ${time}초"
            if (remain != lastRemain) {
                lastRemain = remain
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000
                Log.i(TAG, "TRACK +${elapsed}s remainDistance=$dist remainTime=$time")
                callback.onTrack("+${elapsed}초: $remain")
            }
        }

        // 경로 안내 중이면 naviMode=ROUTE_GUIDING, 평소에는 SAFE_GUIDING 으로 옵니다.
        // (isRouting 은 경로 계산 중 여부라 안내 중에도 false 입니다.)
        Regex("""naviMode=(\w+)""").find(text)?.let {
            routing = it.groupValues[1] == "ROUTE_GUIDING"
            if (!routing) callback.onRoute(false, null)
        }

        val remainSec = Regex("""remainTime=(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull()
        if (remainSec != null) {
            callback.onRaw("$source: 안내 중=$routing, remainTime=$remainSec")
            if (routing && remainSec > 0) {
                callback.onRoute(true, Math.ceil(remainSec / 60.0).toInt())
            }
        } else if (source == "onRouteStateInfo") {
            callback.onRaw("$source: 안내 중=$routing")
        }
    }

    private fun routeEnded(reason: String) {
        Log.i(TAG, reason)
        routing = false
        callback.onRoute(false, null)
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        routing = true
        handle("onRouteStarted", routeStartInfo)
        refresh()
    }

    @Deprecated("Deprecated by NaviHelperEventListener")
    override fun onRouteEnded() = routeEnded("onRouteEnded")

    override fun onRoutingCompleted() {
        refresh()
    }

    override fun onReRouteEnded() {
        refresh()
    }

    override fun onRouteOptionChanged(routeOption: RouteOption) {
        refresh()
    }

    override fun onRouteCancelled() = routeEnded("onRouteCancelled")

    override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
        refresh()
    }

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
        routeEnded("onDestinationArrived")

    override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
    }

    override fun onError(errorCode: NaviErrorCode) {
        callback.onStatus("내비 오류: $errorCode")
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
    }

    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
        handle("onRouteStateInfo", routeStateInfo)
    }

    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
        handle("onDestinationInfo", destinationInfo)
    }

    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
    }

    override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) {
    }

    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
    }

    override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
    }

    override fun onCameraAlert(cameraAlertList: List<CameraAlert>) {
    }

    override fun onDrivingInfo(drivingInfo: DrivingInfo) {
        handle("onDrivingInfo", drivingInfo)
    }

    override fun onBookMarkChanged() {
    }

    override fun onDestinationChanged() {
        refresh()
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        callback.onStatus("내비 상태: $status")
    }

    override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
    }

    override fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>) {
    }

    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
    }

    override fun onTripInfo(tripInfo: TripInfo) {
        handle("onTripInfo", tripInfo)
    }

    private companion object {
        const val TAG = "CarDJ-Navi"
    }
}
