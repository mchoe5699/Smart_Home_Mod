package com.mchoe.smarthome.logic;

/** 월패드 홈네트워크로 제어하는 세대 설비 */
public enum ControlKind {
    LIGHT("smart_light", "스마트 조명", true),
    THERMOSTAT("thermostat", "온도조절기", false),
    GAS_VALVE("gas_valve", "가스밸브", false),
    VENTILATION("ventilator", "환기장치", true),
    ELEVATOR_LINK("elevator_link", "엘리베이터 호출 연동기", false);

    public final String id;
    public final String korean;
    /** 천장 부착형 */
    public final boolean ceiling;

    ControlKind(String id, String korean, boolean ceiling) {
        this.id = id;
        this.korean = korean;
        this.ceiling = ceiling;
    }

    public static ControlKind byId(String id) {
        for (ControlKind k : values()) if (k.id.equals(id)) return k;
        return LIGHT;
    }
}
