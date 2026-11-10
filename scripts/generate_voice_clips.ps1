# 코칭 문구 음성 클립 생성 (개발용 임시 음성: Windows "Microsoft Heami Desktop")
# 문구를 바꾸면 MessageCatalog와 이 목록을 같이 고치고 다시 실행한다.
# 제출 전에는 라이선스가 확인된 음성(직접 녹음 등)으로 같은 파일명에 교체한다.
#
# 실행: powershell -ExecutionPolicy Bypass -File scripts/generate_voice_clips.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech

$outDir = Join-Path $PSScriptRoot '..\app\src\main\res\raw'
New-Item -ItemType Directory -Force $outDir | Out-Null

# 파일명(res/raw, 소문자·밑줄) = 문구 (MessageCatalog와 같아야 함)
$clips = [ordered]@{
    'coach_blinker_omission' = '차선 바꿀 땐 깜빡이 먼저 켜요!'
    'coach_hard_brake'       = '조금 더 일찍, 부드럽게 밟아볼까요?'
    'coach_praise'           = '깜빡이 아주 잘 켜고 있어요. 좋아요!'
    'preview_roundabout'       = '곧 회전교차로예요. 돌고 있는 차가 우선이니 왼쪽을 보고 천천히 들어가요.'
    'preview_school_zone'      = '곧 어린이보호구역이에요. 시속 30 이하로, 횡단보도 앞에서는 일단 멈춰요.'
    'preview_unprotected_left' = '곧 비보호좌회전이에요. 맞은편 직진 차가 지나간 뒤에 돌아요.'
    'preview_merge'            = '곧 합류해요. 깜빡이 켜고 가속 차로에서 속도를 맞춰요.'
    'tutorial_left_signal' = '왼쪽 깜빡이를 켜보세요. 레버를 아래로 내리면 돼요.'
    'tutorial_left_signal_next' = '잘했어요! 다음은 왼쪽 깜빡이를 켜보세요. 레버를 아래로 내리면 돼요.'
    'tutorial_right_signal' = '오른쪽 깜빡이를 켜보세요. 레버를 위로 올리면 돼요.'
    'tutorial_right_signal_next' = '잘했어요! 다음은 오른쪽 깜빡이를 켜보세요. 레버를 위로 올리면 돼요.'
    'tutorial_brake' = '브레이크 페달을 밟아보세요. 오른발로 왼쪽 페달이에요.'
    'tutorial_brake_next' = '잘했어요! 다음은 브레이크 페달을 밟아보세요. 오른발로 왼쪽 페달이에요.'
    'tutorial_done' = '튜토리얼 끝! 이제 내 차가 조금 더 익숙해졌죠?'
    'tip_windshield_fog' = '앞유리 김은 성에 제거 버튼을 누르고 외기 유입으로 바꾸세요. 에어컨을 함께 켜면 더 빨리 걷혀요.'
    'tip_rear_window_fog' = '뒷유리 김은 뒷유리 열선 버튼을 누르세요. 시간이 지나면 저절로 꺼져요.'
    'tip_recirculation' = '내기 순환은 터널이나 매연이 심할 때만 잠깐 켜세요. 오래 켜 두면 졸리고 김이 서려요.'
    'tip_fresh_air' = '평소에는 외기 유입이 기본이에요. 김이 서리거나 졸릴 때는 꼭 외기로 바꾸세요.'
    'tip_air_conditioner' = '에어컨은 습기를 빼 줘서 겨울 김 서림에도 좋아요. 히터와 함께 켜도 괜찮아요.'
    'tip_drive_mode' = '처음에는 컴포트나 노멀을 쓰세요. 스포츠는 조금만 밟아도 힘차게 나가니 익숙해진 뒤에 써요.'
    'tip_regen_braking' = '회생제동은 페달에서 발을 떼면 모터가 속도를 줄이며 충전하는 기능이에요. 멈출 때는 꼭 브레이크를 밟으세요.'
    'tip_pedal_mode' = '처음에는 크립 모드를 추천해요. 원페달 모드는 발을 떼면 차가 스스로 멈추니 익숙해진 뒤에 써 보세요.'
    'tip_child_lock' = '차일드락을 켜면 뒷문이 안쪽에서 열리지 않아요. 아이가 뒷좌석에 탈 때 켜 두세요.'
    'report_great'           = '오늘 운전 아주 잘했어요. 성적표를 확인해 보세요.'
    'report_good'            = '오늘 운전 잘했어요. 성적표에서 연습할 점을 확인해 보세요.'
    'report_practice'        = '수고했어요. 성적표에서 다음 연습 과제를 확인해 보세요.'
}

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SelectVoice('Microsoft Heami Desktop')
$synth.Rate = 0
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(22050, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)

foreach ($name in $clips.Keys) {
    $path = Join-Path $outDir "$name.wav"
    $synth.SetOutputToWaveFile($path, $format)
    $synth.Speak($clips[$name])
    $synth.SetOutputToNull()
    Write-Output "$name.wav  <- $($clips[$name])"
}
$synth.Dispose()
