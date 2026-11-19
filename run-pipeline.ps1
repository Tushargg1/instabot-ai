$ErrorActionPreference = 'Continue'
$envFile = "c:\Users\tusha\OneDrive\Desktop\instabot\instagram-reel-ai\.env"
$audio = "c:\Users\tusha\OneDrive\Desktop\instabot\pipeline-test\reel.mp3"
$outDir = "c:\Users\tusha\OneDrive\Desktop\instabot\pipeline-test"

function Get-EnvVal($name) {
    $line = (Get-Content $envFile | Select-String "^$name=").Line
    if ($line) { return ($line -replace "^$name=", '').Trim() }
    return ''
}
$key = Get-EnvVal 'OPENAI_API_KEY'
$transcriptionModel = Get-EnvVal 'OPENAI_TRANSCRIPTION_MODEL'
$summaryModel = Get-EnvVal 'OPENAI_SUMMARY_MODEL'

# ---------- STAGE: TRANSCRIBING (mirrors GroqTranscriptionService) ----------
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromMinutes(5)
$client.DefaultRequestHeaders.Authorization =
[System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $key)

$content = [System.Net.Http.MultipartFormDataContent]::new()
$content.Add([System.Net.Http.StringContent]::new($transcriptionModel), 'model')
$content.Add([System.Net.Http.StringContent]::new('verbose_json'), 'response_format')

$bytes = [System.IO.File]::ReadAllBytes($audio)
$fileContent = [System.Net.Http.ByteArrayContent]::new($bytes)
$fileContent.Headers.ContentType =
[System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('audio/mpeg')
$content.Add($fileContent, 'file', 'reel.mp3')

$resp = $client.PostAsync('https://api.groq.com/openai/v1/audio/transcriptions', $content).Result
$bodyText = $resp.Content.ReadAsStringAsync().Result

Write-Output "TRANSCRIPTION_STATUS=$([int]$resp.StatusCode)"
if ([int]$resp.StatusCode -ne 200) {
    Write-Output "TRANSCRIPTION_ERROR=$bodyText"
    exit 1
}

$tr = $bodyText | ConvertFrom-Json
$transcript = $tr.text
Write-Output "LANGUAGE=$($tr.language)"
Write-Output "DURATION=$($tr.duration)"
Write-Output "TRANSCRIPT_CHARS=$($transcript.Length)"
$transcript | Out-File "$outDir\transcript.txt" -Encoding utf8
Write-Output '===TRANSCRIPT_START==='
Write-Output $transcript
Write-Output '===TRANSCRIPT_END==='

# ---------- STAGE: SUMMARIZING (mirrors GroqSummaryService) ----------
$systemPrompt = @'
You are the summarization engine for a personal Instagram Reel knowledge base.
Analyze the supplied transcript.
Rules:
- Do not invent facts.
- Separate what the speaker claims from what can be confidently inferred.
- Preserve important technical names, products, people and companies.
- If the transcript is incomplete or noisy, say so.
- Do not treat promotional claims as verified facts.
- Identify claims that should be independently verified.
- Keep the summary concise but useful (80-180 words).
- Prefer concrete information over generic statements.

Return ONLY valid JSON matching this exact schema (no markdown, no explanation):
{"title":"string","summary":"string","keyPoints":["string"],"actionItems":["string"],"topics":["string"],"toolsOrProducts":["string"],"peopleOrCompanies":["string"],"claimsToVerify":["string"],"language":"string","confidence":0.0}
'@

$payload = @{
    model           = $summaryModel
    messages        = @(
        @{ role = 'system'; content = $systemPrompt },
        @{ role = 'user'; content = "Transcript:`n$transcript" }
    )
    temperature     = 0.3
    max_tokens      = 2048
    response_format = @{ type = 'json_object' }
} | ConvertTo-Json -Depth 6

$sc = [System.Net.Http.StringContent]::new($payload, [System.Text.Encoding]::UTF8, 'application/json')
$sresp = $client.PostAsync('https://api.groq.com/openai/v1/chat/completions', $sc).Result
$sbody = $sresp.Content.ReadAsStringAsync().Result

Write-Output "SUMMARY_STATUS=$([int]$sresp.StatusCode)"
if ([int]$sresp.StatusCode -ne 200) {
    Write-Output "SUMMARY_ERROR=$sbody"
    exit 1
}

$sjson = $sbody | ConvertFrom-Json
$summaryContent = $sjson.choices[0].message.content
$summaryContent | Out-File "$outDir\summary.json" -Encoding utf8
Write-Output '===SUMMARY_START==='
Write-Output $summaryContent
Write-Output '===SUMMARY_END==='
