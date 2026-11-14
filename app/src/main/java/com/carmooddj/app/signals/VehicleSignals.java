package com.carmooddj.app.signals;

import android.content.Context;
import android.util.Log;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.api.model.area.WindowArea;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.area.WindowValue;
import ai.pleos.playground.vehicle.constant.control.LightState;
import ai.pleos.playground.vehicle.constant.control.WiperState;
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit;
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener;
import ai.pleos.playground.vehicle.listener.LightStateListener;
import ai.pleos.playground.vehicle.listener.WiperStateListener;
import kotlin.Unit;

/**
 * Pleos Vehicle SDK(2.3.2)에서 와이퍼, 전조등, 공조 현재 온도를 읽습니다.
 * 와이퍼는 작동 상태(WiperState)와 스위치 위치(WiperSwitch)를 함께 보고,
 * 둘 중 하나라도 켜져 있으면 켜진 것으로 판단합니다.
 * 콜백은 SDK 스레드에서 올 수 있으므로 받는 쪽에서 메인 스레드로 넘겨야 합니다.
 */
public final class VehicleSignals {

    public interface Callback {
        void onWiper(Boolean on);

        void onTemperature(Float celsius);

        void onHeadlight(Boolean on);

        void onStatus(String message);

        /** 항목별 진단 정보: key = wiper / light / temp */
        void onDiag(String key, String message);
    }

    private static final String TAG = "CarDJ-Vehicle";

    private final Vehicle vehicle;
    private final Callback callback;
    private final WindowArea frontWindshield = new WindowArea(WindowValue.FRONT_WINDSHIELD);
    private final WindowArea rearWindshield = new WindowArea(WindowValue.REAR_WINDSHIELD);
    private Boolean rearSwitchOn;
    private final SeatArea driverSeat = new SeatArea(SeatValue.ROW_1_LEFT);
    private boolean initialized;
    private boolean wiperRegistered;
    private boolean lightRegistered;
    private boolean tempRegistered;

    private boolean capabilityLogged;
    private Boolean wiperStateOn;
    private Boolean wiperSwitchOn;

    private final WiperStateListener wiperListener = new WiperStateListener() {
        @Override
        public void onWiperStateUpdated(WiperState state) {
            Log.i(TAG, "wiper state listener: " + state);
            wiperStateOn = isOn(state);
            publishWiper("상태 " + state);
        }

        @Override
        public void onFailed(Exception e) {
            Log.w(TAG, "wiper listener failed", e);
            callback.onDiag("wiper", "구독 오류: " + e);
        }
    };

    private final LightStateListener lightListener = new LightStateListener() {
        @Override
        public void onLightStateUpdated(LightState state) {
            Log.i(TAG, "light listener: " + state);
            callback.onHeadlight(isOn(state));
            callback.onDiag("light", "하향등 " + state);
        }

        @Override
        public void onFailed(Exception e) {
            Log.w(TAG, "light listener failed", e);
            callback.onDiag("light", "구독 오류: " + e);
        }
    };

    private final HvacTemperatureListener temperatureListener = new HvacTemperatureListener() {
        @Override
        public void onHvacTemperatureUpdated(Float value) {
            Log.i(TAG, "temperature listener: " + value);
            callback.onTemperature(value);
            callback.onDiag("temp", "현재 온도 " + value);
        }

        @Override
        public void onFailed(Exception e) {
            Log.w(TAG, "temperature listener failed", e);
            callback.onDiag("temp", "구독 오류: " + e);
        }
    };

    public VehicleSignals(Context context, Callback callback) {
        this.vehicle = new Vehicle(context.getApplicationContext());
        this.callback = callback;
    }

    public boolean initialize() {
        if (initialized) return true;
        try {
            vehicle.initialize();
            initialized = true;
            callback.onStatus("Vehicle SDK 연결됨");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "initialize failed", t);
            callback.onStatus("Vehicle SDK 초기화 실패: " + t);
            return false;
        }
    }

    /** 이 차량이 와이퍼를 어떤 영역으로 지원하는지 로그로 남깁니다. */
    private void logCapabilities() {
        if (capabilityLogged) return;
        capabilityLogged = true;
        try {
            vehicle.getWiper().checkWiperStateCapabilities(
                    (config, areas) -> {
                        Log.i(TAG, "wiper state capability: " + config + " areas=" + areas);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "wiper state capability error", error);
                        return Unit.INSTANCE;
                    });
        } catch (Throwable t) {
            Log.w(TAG, "wiper state capability failed", t);
        }
        try {
            vehicle.getWiper().checkWiperSwitchCapabilities(
                    (config, areas) -> {
                        Log.i(TAG, "wiper switch capability: " + config + " areas=" + areas);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "wiper switch capability error", error);
                        return Unit.INSTANCE;
                    });
        } catch (Throwable t) {
            Log.w(TAG, "wiper switch capability failed", t);
        }
    }

    /** 구독을 시작합니다. 실패한 항목은 다음 호출 때 다시 시도합니다. */
    public void register() {
        if (!initialize()) return;
        logCapabilities();
        if (!wiperRegistered) {
            try {
                vehicle.getWiper().registerWiperState(frontWindshield, wiperListener);
                wiperRegistered = true;
            } catch (Throwable t) {
                Log.w(TAG, "wiper register failed", t);
                callback.onDiag("wiper", "구독 실패: " + t);
            }
        }
        if (!lightRegistered) {
            try {
                vehicle.getLight().registerLowBeamLightState(lightListener);
                lightRegistered = true;
            } catch (Throwable t) {
                Log.w(TAG, "light register failed", t);
                callback.onDiag("light", "구독 실패: " + t);
            }
        }
        if (!tempRegistered) {
            try {
                vehicle.getHVAC().registerHvacTemperatureCurrent(driverSeat, temperatureListener);
                tempRegistered = true;
            } catch (Throwable t) {
                Log.w(TAG, "temperature register failed", t);
                callback.onDiag("temp", "구독 실패: " + t);
            }
        }
    }

    /** 현재 값을 한 번 읽습니다. 주기적으로 호출합니다. */
    public void refresh() {
        register();
        if (!initialized) return;
        try {
            vehicle.getWiper().getCurrentWiperState(
                    frontWindshield,
                    state -> {
                        Log.i(TAG, "wiper state: " + state);
                        wiperStateOn = isOn(state);
                        publishWiper("상태 " + state);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "wiper state read error", error);
                        callback.onDiag("wiper", "상태 읽기 오류: " + error);
                        return Unit.INSTANCE;
                    });
        } catch (Throwable t) {
            Log.w(TAG, "wiper state read failed", t);
            callback.onDiag("wiper", "상태 읽기 실패: " + t);
        }
        try {
            vehicle.getWiper().getCurrentWiperSwitch(
                    frontWindshield,
                    value -> {
                        Log.i(TAG, "wiper switch: " + value);
                        wiperSwitchOn = isWiperSwitchOn(value);
                        publishWiper("스위치 " + value);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "wiper switch read error", error);
                        callback.onDiag("wiper", "스위치 읽기 오류: " + error);
                        return Unit.INSTANCE;
                    });
        } catch (Throwable t) {
            Log.w(TAG, "wiper switch read failed", t);
            callback.onDiag("wiper", "스위치 읽기 실패: " + t);
        }
        try {
            vehicle.getWiper().getCurrentWiperSwitch(
                    rearWindshield,
                    value -> {
                        Log.i(TAG, "rear wiper switch: " + value);
                        rearSwitchOn = isWiperSwitchOn(value);
                        publishWiper("뒷유리 스위치 " + value);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "rear wiper switch read error", error);
                        return Unit.INSTANCE;
                    });
        } catch (Throwable t) {
            Log.w(TAG, "rear wiper switch read failed", t);
        }
        try {
            vehicle.getLight().getCurrentLowBeamLightState(
                    state -> {
                        Log.i(TAG, "light: " + state);
                        callback.onHeadlight(isOn(state));
                        callback.onDiag("light", "하향등 " + state);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "light read error", error);
                        callback.onDiag("light", "읽기 오류: " + error);
                        return Unit.INSTANCE;
                    });
        } catch (Throwable t) {
            Log.w(TAG, "light read failed", t);
            callback.onDiag("light", "읽기 실패: " + t);
        }
        try {
            vehicle.getHVAC().getHvacTemperatureCurrent(
                    driverSeat,
                    TemperatureUnit.CELSIUS,
                    value -> {
                        Log.i(TAG, "temperature: " + value);
                        callback.onTemperature(toFloat(value));
                        callback.onDiag("temp", "현재 온도 " + value);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "temperature read error", error);
                        callback.onDiag("temp", "읽기 오류: " + error);
                        return Unit.INSTANCE;
                    });
        } catch (Throwable t) {
            Log.w(TAG, "temperature read failed", t);
            callback.onDiag("temp", "읽기 실패: " + t);
        }
    }

    public void release() {
        if (!initialized) return;
        try {
            if (wiperRegistered) vehicle.getWiper().unregisterWiperState(frontWindshield, wiperListener);
        } catch (Throwable ignored) {
        }
        try {
            if (lightRegistered) vehicle.getLight().unregisterLowBeamLightState(lightListener);
        } catch (Throwable ignored) {
        }
        try {
            if (tempRegistered) vehicle.getHVAC().unregisterHvacTemperatureCurrent(driverSeat, temperatureListener);
        } catch (Throwable ignored) {
        }
        wiperRegistered = false;
        lightRegistered = false;
        tempRegistered = false;
        try {
            vehicle.release();
        } catch (Throwable ignored) {
        }
        initialized = false;
    }

    /** 작동 상태 또는 스위치 중 하나라도 켜져 있으면 켜짐으로 봅니다. */
    private void publishWiper(String detail) {
        Boolean combined;
        if (Boolean.TRUE.equals(wiperStateOn) || Boolean.TRUE.equals(wiperSwitchOn)
                || Boolean.TRUE.equals(rearSwitchOn)) {
            combined = Boolean.TRUE;
        } else if (wiperStateOn == null && wiperSwitchOn == null) {
            combined = null;
        } else {
            combined = Boolean.FALSE;
        }
        callback.onWiper(combined);
        callback.onDiag("wiper", detail + " (상태=" + wiperStateOn + ", 앞 스위치=" + wiperSwitchOn
                + ", 뒤 스위치=" + rearSwitchOn + ")");
    }

    private static Boolean isOn(Object state) {
        if (state == null) return null;
        return "ON".equals(String.valueOf(state));
    }

    /** OFF, OTHER, SERVICE 외의 스위치 위치(AUTO, 간헐, 연속, MIST)는 작동 중으로 봅니다. */
    private static Boolean isWiperSwitchOn(Object value) {
        if (value == null) return null;
        String name = String.valueOf(value);
        return !("OFF".equals(name) || "OTHER".equals(name) || "SERVICE".equals(name));
    }

    private static Float toFloat(Object value) {
        if (value instanceof Number) return ((Number) value).floatValue();
        return null;
    }
}
