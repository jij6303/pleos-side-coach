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
