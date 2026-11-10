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
    'tutorial_parking_brake' = '주차 브레이크를 채워보세요. 이미 채워져 있으면 풀었다가 다시 채워요.'
    'tutorial_parking_brake_next' = '잘했어요! 다음은 주차 브레이크를 채워보세요. 이미 채워져 있으면 풀었다가 다시 채워요.'
    'tutorial_done' = '튜토리얼 끝! 이제 내 차가 조금 더 익숙해졌죠?'
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
