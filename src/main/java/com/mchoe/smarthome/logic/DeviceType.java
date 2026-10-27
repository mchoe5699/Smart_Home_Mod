package com.mchoe.smarthome.logic;

/**
 * 기기 종류.
 * 세대 내부기기: 월패드, 비디오폰, 인터폰
 * 세대 외부기기: 도어카메라(영상+음성), 도어폰(음성), 로비폰, 경비실기
 *
 * 화면 좌표(screen)는 북쪽을 바라보는 모델 기준 픽셀 좌표 {x1, y1, x2, y2, 앞면 z}.
 */
public enum DeviceType {
    //            id               한글명         내부    카메라  영상표시 설정종류              벽부착  screen {x1,y1,x2,y2,z}       글자크기
    WALLPAD      ("wallpad",       "월패드",      true,  false, true,  SettingsKind.UNIT,  true,  new float[]{3.5f, 4.5f, 14.5f, 11.5f, 15f}, 0.085f),
    VIDEOPHONE   ("videophone",    "비디오폰",    true,  false, true,  SettingsKind.UNIT,  true,  new float[]{5.5f, 5f, 12.5f, 11f, 14.5f},   0.07f),
    INTERPHONE   ("interphone",    "인터폰",      true,  false, false, SettingsKind.UNIT,  true,  new float[]{6f, 9.5f, 10f, 12f, 14f},      0.045f),
    DOOR_CAMERA  ("door_camera",   "도어카메라",  false, true,  false, SettingsKind.DOOR,  true,  new float[]{6.5f, 4.6f, 9.5f, 5.9f, 14.5f}, 0.032f),
    DOOR_PHONE   ("door_phone",    "도어폰",      false, false, false, SettingsKind.DOOR,  true,  new float[]{6.5f, 5.6f, 9.5f, 6.9f, 14.5f}, 0.032f),
    LOBBY_PHONE  ("lobby_phone",   "로비폰",      false, true,  true,  SettingsKind.LOBBY, true,  new float[]{4f, 8.5f, 12f, 13.5f, 15f},    0.075f),
    GUARD_STATION("guard_station", "경비실기",    false, false, true,  SettingsKind.GUARD, false, new float[]{3f, 3f, 13f, 11f, 10f},        0.09f);

    public enum SettingsKind {
        /** 관할경비실 + 동 + 호 */ UNIT,
        /** 관할경비실 + 동 + 로비번호 */ LOBBY,
        /** 경비실번호 */ GUARD,
        /** 동 + 호 (도어카메라/도어폰이 연결될 세대) */ DOOR
    }

    public final String id;
    public final String korean;
    /** 세대 내부기기 여부 */
    public final boolean internal;
    /** 방문자 영상을 송출하는 기기(카메라 보유) */
    public final boolean camera;
    /** 영상을 표시할 수 있는 화면 보유 */
    public final boolean videoDisplay;
    public final SettingsKind settings;
    public final boolean wallMounted;
    public final float[] screen;
    public final float textScale;

    DeviceType(String id, String korean, boolean internal, boolean camera, boolean videoDisplay,
               SettingsKind settings, boolean wallMounted, float[] screen, float textScale) {
        this.id = id;
        this.korean = korean;
        this.internal = internal;
        this.camera = camera;
        this.videoDisplay = videoDisplay;
        this.settings = settings;
        this.wallMounted = wallMounted;
        this.screen = screen;
        this.textScale = textScale;
    }

    /** 문을 열 수 있는(레드스톤 출력) 출입 기기 */
    public boolean isEntrance() {
        return this == LOBBY_PHONE || this == DOOR_CAMERA || this == DOOR_PHONE;
    }

    public static DeviceType byId(String id) {
        for (DeviceType t : values()) if (t.id.equals(id)) return t;
        return WALLPAD;
    }
}
