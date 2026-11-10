package com.jij6303.sidecoach.domain.tips

/**
 * 초보운전자가 헷갈려하는 차량 기능 도움말 (#29).
 * 차량 값을 읽지 않는 안내형이다. HVAC API는 pleos.car.permission.CAR_CLIMATE 승인 확정 후 검토.
 * 메뉴 이름은 Pleos 에뮬레이터의 차량 제어 앱 기준이고, 차종마다 다를 수 있어 "차에 따라" 표현을 둔다.
 */
enum class CarTip(
    val title: String,
    val situation: String,
    val steps: List<String>,
    /** 음성 요약. 녹음 클립과 같은 문구. 1~2문장. */
    val spoken: String,
    /** 찾아서 눌러야 할 버튼 (화면에 버튼 모양을 그려 보여준다). 없으면 빈 목록. */
    val buttons: List<CarButton> = emptyList(),
    /** 화면 메뉴에 있는 기능이면 찾아가는 길. */
    val whereToFind: String? = null,
) {
    WINDSHIELD_FOG(
        title = "앞유리에 김이 서릴 때",
        situation = "비 오는 날이나 겨울에 차 안팎 온도 차로 앞유리가 뿌옇게 돼요. 앞이 안 보이면 위험하니 바로 없애야 해요.",
        steps = listOf(
            "앞유리 성에 제거 버튼을 눌러요.",
            "내기 순환을 끄고 외기 유입으로 바꿔요. 차에 따라 성에 제거를 누르면 자동으로 바뀌어요.",
            "에어컨(A/C)을 함께 켜면 습기가 더 빨리 빠져요.",
            "바람 세기를 높이고, 추운 날에는 온도를 조금 올려요.",
        ),
        spoken = "앞유리 김은 성에 제거 버튼을 누르고 외기 유입으로 바꾸세요. 에어컨을 함께 켜면 더 빨리 걷혀요.",
        buttons = listOf(CarButton.FRONT_DEFROST, CarButton.FRESH_AIR, CarButton.AIR_CONDITIONER),
    ),
    REAR_WINDOW_FOG(
        title = "뒷유리에 김이 서릴 때",
        situation = "뒷유리가 뿌예지면 룸미러로 뒤가 보이지 않아요.",
        steps = listOf(
            "뒷유리 열선 버튼을 눌러요.",
            "열선은 일정 시간이 지나면 저절로 꺼져요. 다시 뿌예지면 한 번 더 눌러요.",
            "차에 따라 사이드미러 열선도 함께 켜져요.",
        ),
        spoken = "뒷유리 김은 뒷유리 열선 버튼을 누르세요. 시간이 지나면 저절로 꺼져요.",
        buttons = listOf(CarButton.REAR_DEFROST),
    ),
    RECIRCULATION(
        title = "내기 순환은 언제 켜요?",
        situation = "내기 순환은 바깥 공기를 막고 차 안 공기만 돌리는 기능이에요.",
        steps = listOf(
            "터널에 들어갈 때, 앞차 매연이나 냄새가 들어올 때 켜요.",
            "미세먼지가 심한 날에도 잠깐 켜 두면 좋아요.",
            "오래 켜 두면 공기가 탁해져 졸리고 김이 서려요. 상황이 지나면 외기 유입으로 돌려요.",
        ),
        spoken = "내기 순환은 터널이나 매연이 심할 때만 잠깐 켜세요. 오래 켜 두면 졸리고 김이 서려요.",
        buttons = listOf(CarButton.RECIRCULATION, CarButton.FRESH_AIR),
    ),
    FRESH_AIR(
        title = "외기 유입은 언제 켜요?",
        situation = "외기 유입은 바깥 공기를 차 안으로 들여보내는 기능이에요.",
        steps = listOf(
            "평소 주행에서는 외기 유입이 기본이에요.",
            "유리에 김이 서릴 때는 꼭 외기 유입으로 바꿔요.",
            "오래 운전해서 졸릴 때도 외기 유입으로 공기를 바꿔요.",
        ),
        spoken = "평소에는 외기 유입이 기본이에요. 김이 서리거나 졸릴 때는 꼭 외기로 바꾸세요.",
        buttons = listOf(CarButton.FRESH_AIR, CarButton.RECIRCULATION),
    ),
    AIR_CONDITIONER(
        title = "에어컨(A/C)은 여름에만?",
        situation = "에어컨은 시원하게 할 뿐 아니라 공기 속 습기를 빼 주는 역할도 해요.",
        steps = listOf(
            "겨울에 김이 서릴 때도 에어컨을 켜면 습기가 빨리 빠져요.",
            "히터를 틀면서 에어컨을 같이 켜도 괜찮아요. 따뜻한 바람은 그대로 나와요.",
            "외기 유입과 에어컨을 함께 쓰면 김 제거가 가장 빨라요.",
        ),
        spoken = "에어컨은 습기를 빼 줘서 겨울 김 서림에도 좋아요. 히터와 함께 켜도 괜찮아요.",
        buttons = listOf(CarButton.AIR_CONDITIONER),
    ),
    DRIVE_MODE(
        title = "가속 모드는 뭘 골라요?",
        situation = "가속 모드는 페달을 밟았을 때 차가 얼마나 힘차게 나가는지 정하는 설정이에요.",
        steps = listOf(
            "Comfort: 부드럽게 가속해요. 처음 운전할 때 추천해요.",
            "Normal: 기본 설정이에요.",
            "Sport: 조금만 밟아도 힘차게 나가요. 운전이 익숙해진 뒤에 써요.",
            "눈·빗길 모드: 미끄러운 날 켜면 바퀴가 헛돌지 않게 힘을 조절해 줘요. 켜면 가속 모드는 고를 수 없어요.",
        ),
        spoken = "처음에는 컴포트나 노멀을 쓰세요. 스포츠는 조금만 밟아도 힘차게 나가니 익숙해진 뒤에 써요.",
        whereToFind = "차량 → 주행 → 가속 모드",
    ),
    REGEN_BRAKING(
        title = "회생제동이 뭐예요?",
        situation = "전기차는 가속 페달에서 발을 떼면 모터가 전기를 만들면서 속도를 줄여요. 이걸 회생제동이라고 해요.",
        steps = listOf(
            "발을 떼면 브레이크를 살짝 밟은 것처럼 속도가 줄어요.",
            "단계가 높을수록 더 세게 줄어요. 차에 따라 운전대 뒤 패들로 단계를 바꿔요.",
            "처음에는 낮은 단계로 감각을 익혀요.",
            "회생제동이 있어도 멈출 때는 꼭 브레이크를 밟아요.",
        ),
        spoken = "회생제동은 페달에서 발을 떼면 모터가 속도를 줄이며 충전하는 기능이에요. 멈출 때는 꼭 브레이크를 밟으세요.",
        whereToFind = "운전대 뒤 패들 (차에 따라 다름)",
    ),
    PEDAL_MODE(
        title = "가속 페달 모드 (원페달·크립)",
        situation = "출발하고 멈출 때 차가 어떻게 움직일지 정하는 설정이에요.",
        steps = listOf(
            "크립 모드: 브레이크에서 발을 떼면 차가 천천히 앞으로 움직여요. 보통 자동차와 비슷해서 초보에게 익숙해요.",
            "원페달 모드: 가속 페달에서 발을 떼면 차가 스스로 세게 줄이고 멈출 수 있어요.",
            "원페달 모드에서도 급할 때는 바로 브레이크를 밟아요.",
            "오토 홀드: 켜 두면 멈춘 뒤 브레이크에서 발을 떼도 차가 그대로 서 있어요.",
        ),
        spoken = "처음에는 크립 모드를 추천해요. 원페달 모드는 발을 떼면 차가 스스로 멈추니 익숙해진 뒤에 써 보세요.",
        whereToFind = "차량 → 주행 → 가속 페달 모드",
    ),
    CHILD_LOCK(
        title = "차일드락이 뭐예요?",
        situation = "뒷좌석 문을 차 안에서 열지 못하게 잠그는 기능이에요.",
        steps = listOf(
            "아이가 뒷좌석에 탈 때 켜 두면 달리는 중에 문이 열리는 사고를 막아요.",
            "켜져 있으면 뒷문은 바깥에서만 열려요. 어른이 탈 때는 꺼 주세요.",
            "차에 따라 뒷문 옆면의 작은 레버로 설정하기도 해요.",
        ),
        spoken = "차일드락을 켜면 뒷문이 안쪽에서 열리지 않아요. 아이가 뒷좌석에 탈 때 켜 두세요.",
        buttons = listOf(CarButton.CHILD_LOCK),
        whereToFind = "차량 → 빠른 설정 → 차일드락",
    ),
}

/**
 * 도움말에 그려 보여줄 차량 버튼. 차량 UI 아이콘을 참고해 직접 그린다 (시스템 앱 그림 파일은 쓰지 않음).
 */
enum class CarButton(val label: String) {
    FRONT_DEFROST("앞유리\n성에 제거"),
    REAR_DEFROST("뒷유리 열선"),
    RECIRCULATION("내기 순환"),
    FRESH_AIR("외기 유입"),
    AIR_CONDITIONER("에어컨"),
    CHILD_LOCK("차일드락"),
}
