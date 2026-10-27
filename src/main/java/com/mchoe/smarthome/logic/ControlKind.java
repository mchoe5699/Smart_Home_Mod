package com.mchoe.smarthome.logic;

/** 월패드 홈네트워크로 제어하는 세대 설비 */
public enum ControlKind {
    LIGHT("smart_light", "스마트 조명", true),
    THERMOSTAT("thermostat", "온도조절기", false),
    GAS_VALVE("gas_valve", "가스밸브", false),
    VENTILATION("ventilator", "환기장치", true),
    ELEVATOR_LINK("elevator_link", "엘리베이터 호출 연동기", false),
    /** 집 외부 벽에 붙이는 동체감지기 */
    MOTION_OUTDOOR("motion_sensor_outdoor", "외부 동체감지기", false),
    /** 세대 내부에 설치하는 동체감지기 */
    MOTION_INDOOR("motion_sensor_indoor", "실내 동체감지기", false);

    public final String id;
    public final String korean;
    /** 천장 부착형 */
    public final boolean ceiling;

    ControlKind(String id, String korean, boolean ceiling) {
        this.id = id;
        this.korean = korean;
        this.ceiling = ceiling;
    }

    public boolean isMotion() {
        return this == MOTION_OUTDOOR || this == MOTION_INDOOR;
    }

    public static ControlKind byId(String id) {
        for (ControlKind k : values()) if (k.id.equals(id)) return k;
        return LIGHT;
    }
}
