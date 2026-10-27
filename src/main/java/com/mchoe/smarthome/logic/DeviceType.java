package com.mchoe.smarthome.logic;

/**
 * 기기 종류.
 * 세대 내부기기: 월패드, 비디오폰, 인터폰
 * 세대 외부기기: 도어카메라(영상+음성), 도어폰(음성), 로비폰, 경비실기
 *
 * screen: 북쪽을 바라보는 모델 기준 픽셀 좌표 {x1, y1, x2, y2, 앞면 z}.
 */
public enum DeviceType {
    //            id               한글명         내부    카메라  영상표시 설정종류              벽부착  screen                                      글자    카메라높이(px)
    WALLPAD      ("wallpad",       "월패드",      true,  false, true,  SettingsKind.UNIT,  true,  new float[]{1.5f, 4f, 14.5f, 11.7f, 15.25f},  0.085f, 0f),
    VIDEOPHONE   ("videophone",    "비디오폰",    true,  false, true,  SettingsKind.UNIT,  true,  new float[]{5f, 5f, 13f, 10.5f, 14.75f},      0.075f, 0f),
    INTERPHONE   ("interphone",    "인터폰",      true,  false, false, SettingsKind.UNIT,  true,  new float[]{5.6f, 10f, 8.6f, 12.4f, 15f},     0.045f, 0f),
    VIDEOPHONE_BASIC("videophone_basic", "비디오폰(기본형)", true, false, true, SettingsKind.UNIT, true, new float[]{5f, 5f, 13f, 10.5f, 14.75f}, 0.075f, 0f),
    INTERPHONE_BASIC("interphone_basic", "인터폰(단순형)", true, false, false, SettingsKind.UNIT, true, new float[]{5.6f, 10f, 8.6f, 12.4f, 15f}, 0.045f, 0f),
    DOOR_CAMERA  ("door_camera",   "도어카메라",  false, true,  false, SettingsKind.DOOR,  true,  new float[]{6.6f, 4.2f, 9.4f, 5.4f, 14.75f},  0.03f,  10.5f),
    DOOR_PHONE   ("door_phone",    "도어폰",      false, false, false, SettingsKind.DOOR,  true,  new float[]{6.2f, 5.6f, 9.8f, 6.8f, 14.75f},  0.03f,  0f),
    LOBBY_PHONE  ("lobby_phone",   "로비폰",      false, true,  false, SettingsKind.LOBBY, true,  new float[]{3.5f, 8.6f, 12.5f, 13.2f, 15f},   0.075f, 14.2f),
    GUARD_STATION("guard_station", "경비실기",    false, false, true,  SettingsKind.GUARD, false, new float[]{3f, 2.5f, 13f, 10.5f, 9f},        0.09f,  0f);

    public enum SettingsKind {
        /** 관할경비실 + 동 + 호 */ UNIT,
        /** 관할경비실 + 동 + 로비번호 */ LOBBY,
        /** 경비실번호 */ GUARD,
        /** 동 + 호 (도어카메라/도어폰이 연결될 세대) */ DOOR
    }

    public final String id;
    public final String korean;
    public final boolean internal;
    public final boolean camera;
    public final boolean videoDisplay;
    public final SettingsKind settings;
    public final boolean wallMounted;
    public final float[] screen;
    public final float textScale;
    /** 카메라 렌즈 높이 (모델 픽셀) */
    public final float cameraY;

    DeviceType(String id, String korean, boolean internal, boolean camera, boolean videoDisplay,
               SettingsKind settings, boolean wallMounted, float[] screen, float textScale, float cameraY) {
        this.id = id;
        this.korean = korean;
        this.internal = internal;
        this.camera = camera;
        this.videoDisplay = videoDisplay;
        this.settings = settings;
        this.wallMounted = wallMounted;
        this.screen = screen;
        this.textScale = textScale;
        this.cameraY = cameraY;
    }

    /** 세대를 직접 호출(다이얼)할 수 있는가. 기본형/단순형은 경비실 교환으로만 연결 */
    public boolean canDial() {
        return this != VIDEOPHONE_BASIC && this != INTERPHONE_BASIC;
    }

    /** 비디오폰 계열 */
    public boolean isVideophone() {
        return this == VIDEOPHONE || this == VIDEOPHONE_BASIC;
    }

    /** 인터폰 계열 */
    public boolean isInterphone() {
        return this == INTERPHONE || this == INTERPHONE_BASIC;
    }

    public boolean isEntrance() {
        return this == LOBBY_PHONE || this == DOOR_CAMERA || this == DOOR_PHONE;
    }

    public static DeviceType byId(String id) {
        for (DeviceType t : values()) if (t.id.equals(id)) return t;
        return WALLPAD;
    }
}
