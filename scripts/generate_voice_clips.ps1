# 코칭 문구 음성 클립 생성 (개발용 임시 음성: Windows "Microsoft Heami Desktop")
# 문구를 바꾸면 MessageCatalog와 이 목록을 같이 고치고 다시 실행한다.
# 제출 전에는 라이선스가 확인된 음성(직접 녹음 등)으로 같은 파일명에 교체한다.
# 직접 녹음한 파일(같은 이름의 .mp3 등, wav가 아닌 것)이 있으면 -Force여도 건드리지 않는다(#39).
#
# 실행: powershell -ExecutionPolicy Bypass -File scripts/generate_voice_clips.ps1
#       이미 있는 파일은 건너뛴다. 문구를 바꿔 다시 만들려면 -Force

param([switch]$Force)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech

$outDir = Join-Path $PSScriptRoot '..\app\src\main\res\raw'
New-Item -ItemType Directory -Force $outDir | Out-Null

# 파일명(res/raw, 소문자·밑줄) = 문구 (MessageCatalog와 같아야 함)
$clips = [ordered]@{
    'coach_blinker_omission' = '차선 바꿀 땐 깜빡이 먼저 켜요!'
    'coach_hard_brake'       = '조금 더 일찍, 부드럽게 밟아볼까요?'
    'coach_praise'           = '깜빡이 아주 잘 켜고 있어요. 좋아요!'
    'coach_blinker_left_on'   = '깜빡이가 아직 켜져 있어요. 꺼 주세요.'
    'coach_turn_speed'        = '돌 때는 속도를 줄여요. 천천히요.'
    'coach_gear_shift_moving' = '기어는 완전히 멈춘 뒤에 바꿔요.'
    'preview_roundabout'       = '곧 회전교차로예요. 돌고 있는 차가 우선이니 왼쪽을 보고 천천히 들어가요.'
    'preview_school_zone'      = '곧 어린이보호구역이에요. 시속 30 이하로, 횡단보도 앞에서는 일단 멈춰요.'
    'preview_unprotected_left' = '곧 비보호좌회전이에요. 맞은편 직진 차가 지나간 뒤에 돌아요.'
    'preview_merge'            = '곧 합류해요. 깜빡이 켜고 가속 차로에서 속도를 맞춰요.'
    # 차로 사전 안내 (#65)
    'lane_left_turn'  = '곧 좌회전이에요. 깜빡이 켜고 미리 왼쪽 끝 차로로 옮겨두세요.'
    'lane_right_turn' = '곧 우회전이에요. 깜빡이 켜고 미리 오른쪽 끝 차로로 옮겨두세요.'
    'lane_uturn'      = '곧 유턴이에요. 미리 1차로로 옮기고 유턴 표지를 확인해요.'
    'lane_keep_left'  = '곧 왼쪽 길로 가요. 깜빡이 켜고 미리 왼쪽 차로로 옮겨두세요.'
    'lane_keep_right' = '곧 오른쪽 길로 가요. 깜빡이 켜고 미리 오른쪽 차로로 옮겨두세요.'
    'lane_straight'   = '곧 직진이에요. 1차로는 좌회전 전용일 수 있으니 2차로 이상에 있어요.'
    # 연속 회전 (#67)
    'chain_left_turn'  = '그다음 바로 좌회전이에요. 지나자마자 왼쪽 차로로 가세요.'
    'chain_right_turn' = '그다음 바로 우회전이에요. 지나자마자 오른쪽 차로로 가세요.'
    'chain_uturn'      = '그다음 바로 유턴이에요. 지나자마자 1차로로 가세요.'
    'chain_keep_left'  = '그다음 바로 왼쪽 길이에요. 지나자마자 왼쪽 차로로 가세요.'
    'chain_keep_right' = '그다음 바로 오른쪽 길이에요. 지나자마자 오른쪽 차로로 가세요.'
    # 지금 차로를 아는 경우·차로 기억 확인 (#69)
    'lane_left_l1' = '곧 좌회전이에요. 지금 1차로 그대로 가요.'
    'lane_left_l2' = '곧 좌회전이에요. 지금 2차로니까 깜빡이 켜고 1차로로 옮기세요.'
    'lane_left_l3' = '곧 좌회전이에요. 지금 3차로니까 깜빡이 켜고 1차로로 옮기세요.'
    'lane_left_l4' = '곧 좌회전이에요. 지금 4차로니까 깜빡이 켜고 1차로로 옮기세요.'
    'lane_uturn_l1' = '곧 유턴이에요. 지금 1차로 그대로 가요.'
    'lane_uturn_l2' = '곧 유턴이에요. 지금 2차로니까 깜빡이 켜고 1차로로 옮기세요.'
    'lane_uturn_l3' = '곧 유턴이에요. 지금 3차로니까 깜빡이 켜고 1차로로 옮기세요.'
    'lane_uturn_l4' = '곧 유턴이에요. 지금 4차로니까 깜빡이 켜고 1차로로 옮기세요.'
    'lane_straight_l1' = '곧 직진이에요. 1차로는 좌회전 전용일 수 있으니 2차로로 옮기세요.'
    'lane_straight_l2' = '곧 직진이에요. 지금 차로 그대로 가요.'
    'lane_keep_left_l1' = '곧 왼쪽 길로 가요. 지금 1차로 그대로 가요.'
    'lane_set_1' = '1차로로 기억할게요.'
    'lane_set_2' = '2차로로 기억할게요.'
    'lane_set_3' = '3차로로 기억할게요.'
    'lane_set_4' = '4차로로 기억할게요.'
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
    # 주차 코치 (domain/parking/ParkingPhrases와 같아야 함)
    'parking_reverse_start' = '후진 주차를 시작해요. 천천히 뒤로 가다가 뒷바퀴가 주차칸 첫 선에 오면 멈춰요.'
    'parking_parallel_start' = '평행 주차를 시작해요. 천천히 뒤로 가다가 뒷바퀴가 앞차 뒤범퍼에 오면 멈춰요.'
    'parking_full_lock' = '핸들을 주차칸 쪽으로 끝까지 돌려요.'
    'parking_full_lock_opposite' = '핸들을 반대쪽으로 끝까지 돌려요.'
    'parking_center' = '핸들을 가운데로 풀어요.'
    'parking_reverse_until_aligned' = '좋아요. 천천히 뒤로 가다가 차가 주차칸과 나란해지면 멈춰요.'
    'parking_reverse_finish' = '좋아요. 똑바로 뒤로 가다가 칸 끝에서 멈추고 P에 놓아요.'
    'parking_parallel_until_45' = '좋아요. 천천히 뒤로 가다가 차가 45도쯤 꺾이면 멈춰요.'
    'parking_parallel_until_passed' = '좋아요. 똑바로 뒤로 가다가 앞 범퍼가 앞차 뒤범퍼를 지나면 멈춰요.'
    'parking_parallel_until_aligned' = '좋아요. 천천히 뒤로 가다가 차가 나란해지면 멈춰요.'
    'parking_parallel_finish' = '좋아요. 바퀴가 똑바로예요. P에 놓아 주차를 마쳐요.'
    'parking_not_enough_lock' = '아직 덜 돌렸어요. 끝까지 돌려요.'
    'parking_not_centered' = '바퀴가 덜 풀렸어요. 조금 더 풀어요.'
    'parking_wrong_direction' = '반대쪽이에요. 핸들을 반대로 돌려요.'
    'parking_too_fast' = '천천히요. 브레이크를 살짝 밟아요.'
    'parking_done' = '주차 끝! 잘했어요.'
    'parking_cancelled' = '주차 코치를 마칠게요.'
    'parking_retry' = '앞으로 빼서 다시 해도 괜찮아요. R에 놓으면 처음부터 안내할게요.'
}

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SelectVoice('Microsoft Heami Desktop')
$synth.Rate = 0
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(22050, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)

foreach ($name in $clips.Keys) {
    $path = Join-Path $outDir "$name.wav"
    $recorded = Get-ChildItem -Path $outDir -Filter "$name.*" | Where-Object { $_.Extension -ne '.wav' }
    if ($recorded) { Write-Output "$name  (녹음본 $($recorded[0].Name) 유지)"; continue }
    if ((Test-Path $path) -and -not $Force) { continue }
    $synth.SetOutputToWaveFile($path, $format)
    $synth.Speak($clips[$name])
    $synth.SetOutputToNull()
    Write-Output "$name.wav  <- $($clips[$name])"
}
$synth.Dispose()
