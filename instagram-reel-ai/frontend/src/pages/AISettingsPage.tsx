import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { Save, Eye, EyeOff, Zap, ExternalLink } from 'lucide-react'

// Complete AI Provider database with ALL models, free tiers, limits
const AI_PROVIDERS = [
  {
    id: 'groq',
    name: 'Groq',
    logo: '⚡',
    tagline: 'Fastest AI inference. 100% FREE.',
    signupUrl: 'https://console.groq.com',
    apiKeyPlaceholder: 'gsk_...',
    recommendation: 95,
    freeTier: {
      available: true,
      creditCard: false,
      limits: '30 RPM | 14,400 RPD | 6,000 TPM (70B) | 25MB audio',
      reelsPerDay: '~500+ reels/day (free)',
    },
    paidTier: 'From $0.05/1M tokens (Llama 8B) to $0.59/1M (70B)',
    transcriptionModels: [
      { id: 'whisper-large-v3-turbo', name: 'Whisper Large v3 Turbo', cost: 'FREE', speed: '240x realtime', accuracy: '97%', context: '25MB/file', limits: '20 RPM | 2,000 RPD', recommended: true, note: 'Best balance of speed + quality' },
      { id: 'whisper-large-v3', name: 'Whisper Large v3', cost: 'FREE', speed: '189x realtime', accuracy: '99%', context: '25MB/file', limits: '20 RPM | 2,000 RPD', recommended: false, note: 'Highest accuracy, slightly slower' },
      { id: 'distil-whisper-large-v3-en', name: 'Distil Whisper v3 (EN)', cost: 'FREE', speed: '300x realtime', accuracy: '93%', context: '25MB/file', limits: '20 RPM | 2,000 RPD', recommended: false, note: 'English only, fastest' },
    ],
    summaryModels: [
      { id: 'llama-3.3-70b-versatile', name: 'Llama 3.3 70B Versatile', cost: 'FREE', quality: '92/100', speed: '275 tok/s', context: '128K', limits: '30 RPM | 14,400 RPD | 6,000 TPM', recommended: true, note: 'Best free summarizer' },
      { id: 'llama-3.1-8b-instant', name: 'Llama 3.1 8B Instant', cost: 'FREE', quality: '78/100', speed: '750 tok/s', context: '128K', limits: '30 RPM | 14,400 RPD | 20,000 TPM', recommended: false, note: 'Ultra fast, simpler tasks' },
      { id: 'llama-4-scout-17b-16e-instruct', name: 'Llama 4 Scout 17B', cost: 'FREE', quality: '88/100', speed: '400 tok/s', context: '128K', limits: '15 RPM | 3,600 RPD | 4,000 TPM', recommended: false, note: 'Latest Llama, great reasoning' },
      { id: 'mixtral-8x7b-32768', name: 'Mixtral 8x7B', cost: 'FREE', quality: '85/100', speed: '480 tok/s', context: '32K', limits: '30 RPM | 14,400 RPD | 5,000 TPM', recommended: false, note: 'Good for multilingual' },
      { id: 'gemma2-9b-it', name: 'Gemma 2 9B IT', cost: 'FREE', quality: '80/100', speed: '500 tok/s', context: '8K', limits: '30 RPM | 14,400 RPD | 15,000 TPM', recommended: false, note: 'Google model, compact' },
      { id: 'qwen-qwq-32b', name: 'Qwen QwQ 32B', cost: 'FREE', quality: '89/100', speed: '350 tok/s', context: '128K', limits: '30 RPM | 14,400 RPD | 6,000 TPM', recommended: false, note: 'Strong reasoning model' },
      { id: 'deepseek-r1-distill-llama-70b', name: 'DeepSeek R1 Distill 70B', cost: 'FREE', quality: '90/100', speed: '250 tok/s', context: '128K', limits: '30 RPM | 14,400 RPD | 6,000 TPM', recommended: false, note: 'Reasoning-focused' },
    ],
    pros: ['100% free, no credit card', 'Fastest inference (LPU hardware)', 'Whisper transcription included', 'All major open models', '~500 reels/day free'],
    cons: ['Rate limited (30 RPM)', 'Open-source models only', 'No GPT/Claude'],
  },
  {
    id: 'gemini',
    name: 'Google Gemini',
    logo: '💎',
    tagline: 'Generous free tier. 1M token context window.',
    signupUrl: 'https://aistudio.google.com/apikey',
    apiKeyPlaceholder: 'AIzaSy...',
    recommendation: 72,
    freeTier: {
      available: true,
      creditCard: false,
      limits: '2.5 Pro: 5 RPM, 100 RPD | Flash: 10 RPM, 250 RPD | Flash-Lite: 15 RPM, 1000 RPD | 250K TPM all',
      reelsPerDay: '~100-1000 reels/day (free, model dependent)',
    },
    paidTier: 'From $0.01/1M tokens (Flash-Lite) to $1.25/1M (2.5 Pro)',
    transcriptionModels: [
      { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash (audio input)', cost: 'FREE (250 RPD)', speed: 'Medium', accuracy: '91%', context: '1M tokens', limits: '10 RPM | 250 RPD | 250K TPM', recommended: true, note: 'Native multimodal, send audio directly' },
    ],
    summaryModels: [
      { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash', cost: 'FREE', quality: '90/100', speed: 'Fast', context: '1M tokens', limits: '10 RPM | 250 RPD | 250K TPM', recommended: true, note: 'Best free Gemini for throughput' },
      { id: 'gemini-2.5-flash-lite', name: 'Gemini 2.5 Flash-Lite', cost: 'FREE', quality: '83/100', speed: 'Ultra fast', context: '1M tokens', limits: '15 RPM | 1,000 RPD | 250K TPM', recommended: false, note: 'Highest free daily limit' },
      { id: 'gemini-2.5-pro', name: 'Gemini 2.5 Pro', cost: 'FREE', quality: '95/100', speed: 'Medium', context: '1M tokens', limits: '5 RPM | 100 RPD | 250K TPM', recommended: false, note: 'Best quality, very low free RPD' },
    ],
    pros: ['Generous free tier', '1M token context (full transcripts)', 'Native audio understanding', 'No credit card'],
    cons: ['Different API format (not OpenAI compatible)', 'Region restricted', 'Transcription less proven than Whisper', 'Lower free RPD than Groq'],
  },
  {
    id: 'openai',
    name: 'OpenAI',
    logo: '🤖',
    tagline: 'Best quality. No meaningful free tier for API.',
    signupUrl: 'https://platform.openai.com',
    apiKeyPlaceholder: 'sk-...',
    recommendation: 65,
    freeTier: {
      available: false,
      creditCard: true,
      limits: 'NO free API tier. $5 minimum credit required. ChatGPT app free but API is paid-only.',
      reelsPerDay: 'Unlimited (pay per use, ~$0.01/reel)',
    },
    paidTier: 'Whisper: $0.006/min | GPT-4o: $2.50/1M in + $10/1M out | GPT-4o-mini: $0.15/1M in',
    transcriptionModels: [
      { id: 'whisper-1', name: 'Whisper v2 (hosted)', cost: '$0.006/min', speed: '1x realtime', accuracy: '97%', context: '25MB/file', limits: 'Paid | 50 RPM | no RPD cap', recommended: true, note: 'Industry standard' },
      { id: 'gpt-4o-audio-preview', name: 'GPT-4o Audio (native)', cost: '$100/1M audio tokens', speed: 'Fast', accuracy: '95%', context: '128K', limits: 'Paid | 500 RPM', recommended: false, note: 'Expensive, native understanding' },
    ],
    summaryModels: [
      { id: 'gpt-4o-mini', name: 'GPT-4o Mini', cost: '$0.15/1M in + $0.60/1M out', quality: '88/100', speed: 'Fast', context: '128K', limits: 'Paid | 500 RPM | 200K TPM', recommended: true, note: 'Best value OpenAI' },
      { id: 'gpt-4o', name: 'GPT-4o', cost: '$2.50/1M in + $10/1M out', quality: '95/100', speed: 'Medium', context: '128K', limits: 'Paid | 500 RPM | 30K TPM', recommended: false, note: 'Highest quality, expensive' },
      { id: 'gpt-3.5-turbo', name: 'GPT-3.5 Turbo', cost: '$0.50/1M in + $1.50/1M out', quality: '80/100', speed: 'Fast', context: '16K', limits: 'Paid | 3,500 RPM | 200K TPM', recommended: false, note: 'Legacy, cheapest OpenAI' },
      { id: 'o1-mini', name: 'o1-mini (reasoning)', cost: '$3/1M in + $12/1M out', quality: '93/100', speed: 'Slow', context: '128K', limits: 'Paid | 500 RPM | 30K TPM', recommended: false, note: 'Deep reasoning, expensive' },
    ],
    pros: ['Best overall quality', 'Most reliable', 'Largest ecosystem', 'Structured output'],
    cons: ['NO free API tier', '$5 minimum', 'Expensive at scale', 'Rate limited on free credits (discontinued)'],
  },
  {
    id: 'deepseek',
    name: 'DeepSeek',
    logo: '🐋',
    tagline: 'GPT-4 quality at 1/20th the price. $2 free credits.',
    signupUrl: 'https://platform.deepseek.com',
    apiKeyPlaceholder: 'sk-...',
    recommendation: 60,
    freeTier: {
      available: true,
      creditCard: false,
      limits: '$2 free credits on signup. No monthly free tier.',
      reelsPerDay: '~2000 reels with $2 credit (summarization only)',
    },
    paidTier: 'V3: $0.14/1M in + $0.28/1M out | R1: $0.55/1M in + $2.19/1M out',
    transcriptionModels: [],
    summaryModels: [
      { id: 'deepseek-chat', name: 'DeepSeek V3', cost: '$0.14/1M in + $0.28/1M out', quality: '93/100', speed: 'Medium', context: '64K', limits: '60 RPM | 1M TPD | no RPD cap', recommended: true, note: 'Insanely cheap, GPT-4 level' },
      { id: 'deepseek-reasoner', name: 'DeepSeek R1 (reasoning)', cost: '$0.55/1M in + $2.19/1M out', quality: '96/100', speed: 'Slow', context: '64K', limits: '60 RPM | 500K TPD', recommended: false, note: 'Best reasoning, more expensive' },
    ],
    pros: ['$2 free credits', 'Cheapest paid (20x less than GPT-4)', 'Excellent quality', 'No credit card for free'],
    cons: ['No audio transcription', 'China-based (latency/privacy)', 'Occasional downtime', 'Free credits run out fast'],
  },
  {
    id: 'together',
    name: 'Together AI',
    logo: '🔗',
    tagline: '$1 free credit. Many open-source models.',
    signupUrl: 'https://api.together.xyz',
    apiKeyPlaceholder: 'your-key...',
    recommendation: 50,
    freeTier: {
      available: true,
      creditCard: false,
      limits: '$1 free credit on signup. Expires in 30 days.',
      reelsPerDay: '~500 reels with $1 credit (summarization only)',
    },
    paidTier: 'From $0.10/1M tokens (small) to $0.90/1M (70B)',
    transcriptionModels: [],
    summaryModels: [
      { id: 'meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo', name: 'Llama 3.1 70B Turbo', cost: '$0.88/1M tok', quality: '91/100', speed: 'Fast', context: '128K', limits: '600 RPM | no RPD cap', recommended: true, note: 'Fast and capable' },
      { id: 'meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo', name: 'Llama 3.1 8B Turbo', cost: '$0.18/1M tok', quality: '78/100', speed: 'Ultra fast', context: '128K', limits: '600 RPM | no RPD cap', recommended: false, note: 'Very cheap, basic tasks' },
      { id: 'mistralai/Mixtral-8x7B-Instruct-v0.1', name: 'Mixtral 8x7B', cost: '$0.60/1M tok', quality: '84/100', speed: 'Fast', context: '32K', limits: '600 RPM | no RPD cap', recommended: false, note: 'Good multilingual' },
      { id: 'Qwen/Qwen2.5-72B-Instruct-Turbo', name: 'Qwen 2.5 72B Turbo', cost: '$0.90/1M tok', quality: '92/100', speed: 'Fast', context: '128K', limits: '600 RPM | no RPD cap', recommended: false, note: 'Very strong, pricier' },
    ],
    pros: ['$1 free credit', 'Many model choices', 'High rate limits', 'Good infrastructure'],
    cons: ['No transcription', 'Credits expire in 30 days', 'Pricier than Groq free', 'No permanent free tier'],
  },
  {
    id: 'mistral',
    name: 'Mistral AI',
    logo: '🌬️',
    tagline: 'European AI. Free tier with good limits.',
    signupUrl: 'https://console.mistral.ai',
    apiKeyPlaceholder: 'your-key...',
    recommendation: 55,
    freeTier: {
      available: true,
      creditCard: false,
      limits: '1 RPM | 500K tokens/month | Experiments only',
      reelsPerDay: '~15 reels/day (free, very limited)',
    },
    paidTier: 'From $0.10/1M (Small) to $2/1M (Large)',
    transcriptionModels: [],
    summaryModels: [
      { id: 'mistral-small-latest', name: 'Mistral Small', cost: 'Free tier: 1 RPM', quality: '82/100', speed: 'Fast', context: '32K', limits: 'Free: 1 RPM | 500K tok/mo | Paid: 300 RPM', recommended: true, note: 'Only model on free tier' },
      { id: 'mistral-large-latest', name: 'Mistral Large', cost: '$2/1M in + $6/1M out', quality: '93/100', speed: 'Medium', context: '128K', limits: 'Paid only | 300 RPM', recommended: false, note: 'Paid, frontier quality' },
      { id: 'codestral-latest', name: 'Codestral', cost: '$0.30/1M in + $0.90/1M out', quality: '85/100', speed: 'Fast', context: '256K', limits: 'Paid only | 300 RPM', recommended: false, note: 'Code-focused' },
    ],
    pros: ['EU-based (GDPR)', 'Free tier available', 'Good quality models'],
    cons: ['Very limited free (1 RPM!)', 'No transcription', 'Small free allowance', 'Better options exist'],
  },
  {
    id: 'anthropic',
    name: 'Anthropic (Claude)',
    logo: '🧠',
    tagline: 'Best reasoning. No free API tier.',
    signupUrl: 'https://console.anthropic.com',
    apiKeyPlaceholder: 'sk-ant-...',
    recommendation: 45,
    freeTier: {
      available: false,
      creditCard: true,
      limits: 'NO free tier. $5 minimum credit. Claude.ai chat is free but API is paid.',
      reelsPerDay: '~200 reels/$5 credit',
    },
    paidTier: 'Haiku: $0.25/1M in | Sonnet: $3/1M in | Opus: $15/1M in',
    transcriptionModels: [],
    summaryModels: [
      { id: 'claude-3-5-haiku-latest', name: 'Claude 3.5 Haiku', cost: '$0.25/1M in + $1.25/1M out', quality: '86/100', speed: 'Ultra fast', context: '200K', limits: 'Paid | 5,000 RPM | 400K TPM', recommended: true, note: 'Cheapest Claude, good quality' },
      { id: 'claude-sonnet-4-latest', name: 'Claude Sonnet 4', cost: '$3/1M in + $15/1M out', quality: '96/100', speed: 'Medium', context: '200K', limits: 'Paid | 4,000 RPM | 80K TPM', recommended: false, note: 'Top tier reasoning' },
      { id: 'claude-opus-4-latest', name: 'Claude Opus 4', cost: '$15/1M in + $75/1M out', quality: '98/100', speed: 'Slow', context: '200K', limits: 'Paid | 4,000 RPM | 40K TPM', recommended: false, note: 'Best overall quality, very expensive' },
    ],
    pros: ['Best reasoning quality', 'Large context (200K)', 'Very safe/helpful', 'Fast Haiku model'],
    cons: ['NO free tier', '$5 minimum', 'No transcription', 'Expensive at scale'],
  },
]

export default function SettingsPage() {
  const queryClient = useQueryClient()
  const [showSecrets, setShowSecrets] = useState(false)
  const [successMsg, setSuccessMsg] = useState('')
  const [selectedProvider, setSelectedProvider] = useState('groq')
  const [showAllModels, setShowAllModels] = useState(false)

  const { data: profile } = useQuery({ queryKey: ['profile'], queryFn: () => apiClient.getProfile() as Promise<{ username: string; role: string; email?: string; displayName?: string }> })
  const { data: settings } = useQuery({ queryKey: ['settings'], queryFn: () => apiClient.getSettings() })

  const [form, setForm] = useState({
    openaiApiKey: '', openaiTranscriptionModel: '', openaiSummaryModel: '',
  })

  const [profileForm, setProfileForm] = useState({ email: '', displayName: '' })

  const saveMutation = useMutation({
    mutationFn: (data: Record<string, string>) => apiClient.saveSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings'] })
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      setSuccessMsg('Settings saved!'); setTimeout(() => setSuccessMsg(''), 3000)
      setForm(f => ({ ...f, openaiApiKey: '' }))
    },
  })

  const profileMutation = useMutation({
    mutationFn: (data: { email?: string; displayName?: string }) => apiClient.updateProfile(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['profile'] }); setSuccessMsg('Profile updated!'); setTimeout(() => setSuccessMsg(''), 3000) },
  })

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault()
    const nonEmpty: Record<string, string> = {}
    Object.entries(form).forEach(([k, v]) => { if (v) nonEmpty[k] = v })
    if (Object.keys(nonEmpty).length === 0) return
    saveMutation.mutate(nonEmpty)
  }

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault()
    const data: { email?: string; displayName?: string } = {}
    if (profileForm.email) data.email = profileForm.email
    if (profileForm.displayName) data.displayName = profileForm.displayName
    if (Object.keys(data).length > 0) profileMutation.mutate(data)
  }

  const currentProvider = AI_PROVIDERS.find(p => p.id === selectedProvider)!

  return (
    <div className="max-w-5xl">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Settings</h2>

      {successMsg && <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm">✓ {successMsg}</div>}

      {/* Profile */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Profile</h3>
        <div className="grid grid-cols-2 gap-3 mb-4 text-sm">
          <div><span className="text-gray-500">Username:</span> <span className="ml-1 font-medium">{profile?.username}</span></div>
          <div><span className="text-gray-500">Role:</span> <span className="ml-1 font-medium">{profile?.role}</span></div>
        </div>
        <form onSubmit={handleSaveProfile} className="flex gap-3 items-end">
          <div className="flex-1"><label className="block text-xs text-gray-500 mb-1">Email</label><input type="email" value={profileForm.email} onChange={e => setProfileForm(p => ({ ...p, email: e.target.value }))} placeholder={profile?.email || 'email'} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" /></div>
          <div className="flex-1"><label className="block text-xs text-gray-500 mb-1">Display Name</label><input type="text" value={profileForm.displayName} onChange={e => setProfileForm(p => ({ ...p, displayName: e.target.value }))} placeholder={profile?.displayName || 'name'} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" /></div>
          <button type="submit" className="px-4 py-2 bg-gray-800 text-white rounded-lg text-sm">Update</button>
        </form>
      </div>

      {/* AI Provider Selection */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2"><Zap size={20} className="text-yellow-500" />Choose AI Provider</h3>
          <div className="flex gap-2">
            <span className={`px-3 py-1 rounded-full text-xs font-medium ${settings?.openai_api_key ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
              AI: {settings?.openai_api_key ? '✓ Connected' : '○ Not set'}
            </span>
          </div>
        </div>

        {/* Provider Cards Grid */}
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-2 mb-6">
          {AI_PROVIDERS.map(p => (
            <button key={p.id} onClick={() => setSelectedProvider(p.id)}
              className={`p-3 rounded-lg border-2 text-left transition-all text-xs ${selectedProvider === p.id ? 'border-blue-500 bg-blue-50 shadow' : 'border-gray-200 hover:border-gray-300'}`}>
              <div className="text-xl mb-1">{p.logo}</div>
              <div className="font-semibold text-gray-900">{p.name}</div>
              <div className={`text-[10px] mt-1 font-medium ${p.freeTier.available ? 'text-green-600' : 'text-red-500'}`}>
                {p.freeTier.available ? '✓ FREE' : '✗ PAID'}
              </div>
              <div className="mt-1 text-[10px] text-gray-500">{p.recommendation}% rec</div>
            </button>
          ))}
        </div>

        {/* Selected Provider Detail */}
        <div className="bg-gradient-to-r from-gray-50 to-blue-50 rounded-xl p-5 mb-5 border border-gray-200">
          <div className="flex items-start justify-between mb-3">
            <div>
              <h4 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                {currentProvider.logo} {currentProvider.name}
                <span className={`px-2 py-0.5 text-[10px] rounded-full font-bold ${currentProvider.freeTier.available ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                  {currentProvider.freeTier.available ? 'FREE TIER' : 'PAID ONLY'}
                </span>
              </h4>
              <p className="text-sm text-gray-600 mt-1">{currentProvider.tagline}</p>
            </div>
            <a href={currentProvider.signupUrl} target="_blank" rel="noopener noreferrer"
              className="flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700">
              Get Key <ExternalLink size={12} />
            </a>
          </div>

          {/* Free Tier Details */}
          <div className="bg-white rounded-lg p-3 mb-3 border border-gray-200">
            <p className="text-xs font-bold text-gray-700 mb-1">📊 Free Tier Limits:</p>
            <p className="text-sm text-gray-800 font-mono">{currentProvider.freeTier.limits}</p>
            <p className="text-xs text-blue-600 mt-1 font-medium">≈ {currentProvider.freeTier.reelsPerDay}</p>
          </div>

          <div className="grid grid-cols-2 gap-3 mb-3">
            <div>
              <p className="text-xs font-bold text-green-700 mb-1">✅ Pros</p>
              {currentProvider.pros.map(p => <p key={p} className="text-xs text-gray-700">• {p}</p>)}
            </div>
            <div>
              <p className="text-xs font-bold text-orange-700 mb-1">⚠️ Cons</p>
              {currentProvider.cons.map(c => <p key={c} className="text-xs text-gray-700">• {c}</p>)}
            </div>
          </div>
          {currentProvider.paidTier && <p className="text-[10px] text-gray-500">Paid: {currentProvider.paidTier}</p>}
        </div>

        {/* Transcription Models Table */}
        {currentProvider.transcriptionModels.length > 0 ? (
          <div className="mb-5">
            <h4 className="font-semibold text-gray-800 mb-2 text-sm">🎙️ Transcription Models</h4>
            <div className="overflow-x-auto rounded-lg border border-gray-200">
              <table className="w-full text-xs">
                <thead className="bg-gray-50"><tr>
                  <th className="px-3 py-2 text-left text-gray-500">Model</th>
                  <th className="px-3 py-2 text-left text-gray-500">Cost</th>
                  <th className="px-3 py-2 text-left text-gray-500">Speed</th>
                  <th className="px-3 py-2 text-left text-gray-500">Accuracy</th>
                  <th className="px-3 py-2 text-left text-gray-500">Limit</th>
                  <th className="px-3 py-2 text-left text-gray-500">Note</th>
                  <th className="px-3 py-2 text-left text-gray-500"></th>
                </tr></thead>
                <tbody className="divide-y divide-gray-100">
                  {currentProvider.transcriptionModels.map(m => (
                    <tr key={m.id} className={m.recommended ? 'bg-green-50' : 'hover:bg-gray-50'}>
                      <td className="px-3 py-2 font-medium">{m.name} {m.recommended && <span className="text-green-600">★</span>}</td>
                      <td className="px-3 py-2"><span className={m.cost === 'FREE' ? 'text-green-600 font-bold' : 'text-gray-600'}>{m.cost}</span></td>
                      <td className="px-3 py-2 text-gray-600">{m.speed}</td>
                      <td className="px-3 py-2 text-gray-600">{m.accuracy}</td>
                      <td className="px-3 py-2 text-gray-500">{m.limits}</td>
                      <td className="px-3 py-2 text-gray-500 italic">{m.note}</td>
                      <td className="px-3 py-2">
                        <button onClick={() => setForm(f => ({ ...f, openaiTranscriptionModel: m.id }))}
                          className={`px-2 py-1 rounded text-[10px] font-medium ${form.openaiTranscriptionModel === m.id ? 'bg-blue-600 text-white' : 'bg-gray-200 hover:bg-gray-300'}`}>
                          {form.openaiTranscriptionModel === m.id ? '✓' : 'Use'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <div className="mb-5 p-3 bg-yellow-50 border border-yellow-200 rounded-lg text-yellow-700 text-xs">
            ⚠️ {currentProvider.name} has no transcription. Pair with <strong>Groq</strong> (free Whisper) for audio.
          </div>
        )}

        {/* Summary Models Table */}
        <div className="mb-5">
          <div className="flex items-center justify-between mb-2">
            <h4 className="font-semibold text-gray-800 text-sm">📝 Summarization Models</h4>
            {currentProvider.summaryModels.length > 3 && (
              <button onClick={() => setShowAllModels(!showAllModels)} className="text-xs text-blue-600 hover:underline">
                {showAllModels ? 'Show less' : `Show all ${currentProvider.summaryModels.length} models`}
              </button>
            )}
          </div>
          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="w-full text-xs">
              <thead className="bg-gray-50"><tr>
                <th className="px-3 py-2 text-left text-gray-500">Model</th>
                <th className="px-3 py-2 text-left text-gray-500">Cost</th>
                <th className="px-3 py-2 text-left text-gray-500">Quality</th>
                <th className="px-3 py-2 text-left text-gray-500">Speed</th>
                <th className="px-3 py-2 text-left text-gray-500">Context</th>
                <th className="px-3 py-2 text-left text-gray-500">Limit</th>
                <th className="px-3 py-2 text-left text-gray-500">Note</th>
                <th className="px-3 py-2 text-left text-gray-500"></th>
              </tr></thead>
              <tbody className="divide-y divide-gray-100">
                {(showAllModels ? currentProvider.summaryModels : currentProvider.summaryModels.slice(0, 4)).map(m => (
                  <tr key={m.id} className={m.recommended ? 'bg-green-50' : 'hover:bg-gray-50'}>
                    <td className="px-3 py-2 font-medium">{m.name} {m.recommended && <span className="text-green-600">★</span>}</td>
                    <td className="px-3 py-2"><span className={m.cost.includes('FREE') ? 'text-green-600 font-bold' : 'text-gray-600'}>{m.cost}</span></td>
                    <td className="px-3 py-2 text-gray-600">{m.quality}</td>
                    <td className="px-3 py-2 text-gray-600">{m.speed}</td>
                    <td className="px-3 py-2 text-gray-500">{m.context}</td>
                    <td className="px-3 py-2 text-gray-500">{m.limits}</td>
                    <td className="px-3 py-2 text-gray-500 italic">{m.note}</td>
                    <td className="px-3 py-2">
                      <button onClick={() => setForm(f => ({ ...f, openaiSummaryModel: m.id }))}
                        className={`px-2 py-1 rounded text-[10px] font-medium ${form.openaiSummaryModel === m.id ? 'bg-blue-600 text-white' : 'bg-gray-200 hover:bg-gray-300'}`}>
                        {form.openaiSummaryModel === m.id ? '✓' : 'Use'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Save API Key */}
        <form onSubmit={handleSaveSettings} className="border-t border-gray-200 pt-4">
          <div className="flex gap-3 items-end">
            <div className="flex-1">
              <label className="block text-xs text-gray-600 mb-1 font-medium">{currentProvider.name} API Key</label>
              <input type="password" value={form.openaiApiKey} onChange={e => setForm(f => ({ ...f, openaiApiKey: e.target.value }))}
                placeholder={currentProvider.apiKeyPlaceholder} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            </div>
            <button type="submit" disabled={saveMutation.isPending}
              className="flex items-center gap-2 px-5 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
              <Save size={14} />{saveMutation.isPending ? 'Saving...' : 'Save All'}
            </button>
          </div>
          {settings && Object.keys(settings).length > 0 && (
            <div className="mt-3">
              <button type="button" onClick={() => setShowSecrets(!showSecrets)} className="flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700">
                {showSecrets ? <EyeOff size={12} /> : <Eye size={12} />} {showSecrets ? 'Hide' : 'Show'} saved config
              </button>
              {showSecrets && (
                <div className="mt-2 p-3 bg-gray-50 rounded-lg space-y-1">
                  {Object.entries(settings).map(([k, v]) => <div key={k} className="flex justify-between text-xs"><span className="text-gray-500 font-mono">{k}</span><span className="text-gray-800 font-mono">{v}</span></div>)}
                </div>
              )}
            </div>
          )}
        </form>
      </div>

      {/* Recommendation Card */}
      <div className="bg-gradient-to-r from-green-50 via-blue-50 to-purple-50 rounded-xl border border-blue-200 p-6 mb-6">
        <h3 className="font-bold text-gray-900 mb-4">🏆 Recommendation Summary</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-white rounded-lg p-4 border-2 border-green-300 shadow-sm">
            <p className="text-[10px] font-bold text-green-600 mb-1">🥇 BEST FREE (Personal Use)</p>
            <p className="font-bold text-gray-900">⚡ Groq — 95%</p>
            <p className="text-xs text-gray-500 mt-1">Whisper Turbo + Llama 3.3 70B</p>
            <p className="text-xs text-green-600 font-medium mt-1">$0/month • ~500 reels/day</p>
            <div className="mt-2 w-full bg-gray-200 rounded-full h-1.5"><div className="bg-green-500 h-1.5 rounded-full" style={{ width: '95%' }}></div></div>
          </div>
          <div className="bg-white rounded-lg p-4 border border-gray-200">
            <p className="text-[10px] font-bold text-blue-600 mb-1">🥈 BEST QUALITY (Budget OK)</p>
            <p className="font-bold text-gray-900">🤖 OpenAI — 65%</p>
            <p className="text-xs text-gray-500 mt-1">Whisper + GPT-4o Mini</p>
            <p className="text-xs text-blue-600 font-medium mt-1">~$0.005/reel • Unlimited</p>
            <div className="mt-2 w-full bg-gray-200 rounded-full h-1.5"><div className="bg-blue-500 h-1.5 rounded-full" style={{ width: '65%' }}></div></div>
          </div>
          <div className="bg-white rounded-lg p-4 border border-gray-200">
            <p className="text-[10px] font-bold text-purple-600 mb-1">🥉 BEST HYBRID (Free + Quality)</p>
            <p className="font-bold text-gray-900">💎 Gemini Flash — 72%</p>
            <p className="text-xs text-gray-500 mt-1">Native audio + summarization</p>
            <p className="text-xs text-purple-600 font-medium mt-1">$0 • ~250 reels/day</p>
            <div className="mt-2 w-full bg-gray-200 rounded-full h-1.5"><div className="bg-purple-500 h-1.5 rounded-full" style={{ width: '72%' }}></div></div>
          </div>
        </div>
      </div>

      {/* Instagram is connected through Meta OAuth, never by pasting account secrets. */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6 flex items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold text-gray-900">📷 Instagram Connection</h3>
          <p className="text-sm text-gray-500 mt-1">Authorize your Professional account securely through Meta. No Instagram password or access token is entered here.</p>
        </div>
        <Link to="/instagram" className="shrink-0 px-4 py-2 bg-pink-600 text-white rounded-lg text-sm font-medium hover:bg-pink-700">
          Connect Instagram
        </Link>
      </div>

      {/* Security */}
      <div className="bg-yellow-50 rounded-xl border border-yellow-200 p-5">
        <h3 className="font-semibold text-yellow-800 mb-2 text-sm">🔒 Security</h3>
        <ul className="text-xs text-yellow-700 space-y-1">
          <li>• All API keys encrypted with AES-256-GCM</li>
          <li>• Only last 4 characters visible after saving</li>
          <li>• Per-user data isolation</li>
          <li>• Keys never logged or exposed to frontend</li>
        </ul>
      </div>
    </div>
  )
}
