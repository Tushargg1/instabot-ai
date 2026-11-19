import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { Plus, Trash2, Pencil, ToggleLeft, ToggleRight, ChevronDown, ChevronUp, MessageSquare } from 'lucide-react'

type MatchType = 'KEYWORD' | 'REGEX' | 'ALL'

interface DmRule {
  id: string
  name: string
  matchType: MatchType
  matchValue?: string
  replyText: string
  priority: number
  enabled: boolean
  createdAt: string
}

interface RuleForm {
  name: string
  matchType: MatchType
  matchValue: string
  replyText: string
  priority: number
  enabled: boolean
}

const EMPTY_FORM: RuleForm = {
  name: '',
  matchType: 'KEYWORD',
  matchValue: '',
  replyText: '',
  priority: 100,
  enabled: true,
}

const MATCH_TYPE_LABELS: Record<MatchType, string> = {
  KEYWORD: '🔑 Keyword',
  REGEX: '🔍 Regex',
  ALL: '🌐 Every DM',
}

export default function DmAutoReplyPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<RuleForm>(EMPTY_FORM)
  const [error, setError] = useState('')
  const [expandedId, setExpandedId] = useState<string | null>(null)

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { data: rulesRaw = [], isLoading } = useQuery<any[]>({
    queryKey: ['dm-rules'],
    queryFn: () => apiClient.getDmRules(),
  })
  const rules: DmRule[] = rulesRaw

  const createMutation = useMutation({
    mutationFn: (data: RuleForm) => apiClient.createDmRule(data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['dm-rules'] }); resetForm() },
    onError: (e: Error) => setError(e.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: RuleForm }) => apiClient.updateDmRule(id, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['dm-rules'] }); resetForm() },
    onError: (e: Error) => setError(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.deleteDmRule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dm-rules'] }),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      apiClient.toggleDmRule(id, enabled),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dm-rules'] }),
  })

  function resetForm() {
    setForm(EMPTY_FORM)
    setShowForm(false)
    setEditingId(null)
    setError('')
  }

  function startEdit(rule: DmRule) {
    setForm({
      name: rule.name,
      matchType: rule.matchType,
      matchValue: rule.matchValue ?? '',
      replyText: rule.replyText,
      priority: rule.priority,
      enabled: rule.enabled,
    })
    setEditingId(rule.id)
    setShowForm(true)
    setError('')
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (editingId) {
      updateMutation.mutate({ id: editingId, data: form })
    } else {
      createMutation.mutate(form)
    }
  }

  const isSaving = createMutation.isPending || updateMutation.isPending

  return (
    <div className="max-w-4xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <MessageSquare size={24} className="text-pink-600" />
            DM Auto-Reply Rules
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            Automatically reply to incoming Instagram DMs based on your rules. Rules run in priority order — first match wins.
          </p>
        </div>
        {!showForm && (
          <button
            onClick={() => { setShowForm(true); setEditingId(null); setForm(EMPTY_FORM) }}
            className="flex items-center gap-2 px-4 py-2 bg-pink-600 text-white rounded-lg text-sm font-medium hover:bg-pink-700"
          >
            <Plus size={16} /> Add Rule
          </button>
        )}
      </div>

      {/* How it works info box */}
      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-6 text-sm text-blue-800">
        <p className="font-semibold mb-1">⚡ How it works</p>
        <ul className="list-disc list-inside space-y-1 text-xs">
          <li><strong>KEYWORD</strong> — reply when the message contains your keyword (case-insensitive)</li>
          <li><strong>REGEX</strong> — reply when the message matches a regular expression</li>
          <li><strong>Every DM</strong> — reply to every incoming DM regardless of content</li>
          <li>Use <code className="bg-blue-100 px-1 rounded">{'{sender_id}'}</code> in your reply text to include the sender's Instagram ID</li>
          <li>Lower priority number = evaluated first. First matching rule sends the reply.</li>
        </ul>
      </div>

      {/* Add / Edit Form */}
      {showForm && (
        <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6 shadow-sm">
          <h3 className="font-semibold text-gray-900 mb-4">
            {editingId ? 'Edit Rule' : 'Create New Rule'}
          </h3>

          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Name + Priority row */}
            <div className="grid grid-cols-3 gap-4">
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 mb-1 font-medium">Rule Name *</label>
                <input
                  value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  placeholder="e.g. Welcome greeting"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1 font-medium">Priority</label>
                <input
                  type="number"
                  value={form.priority}
                  onChange={e => setForm(f => ({ ...f, priority: Number(e.target.value) }))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  min={1}
                />
              </div>
            </div>

            {/* Match Type */}
            <div>
              <label className="block text-xs text-gray-500 mb-1 font-medium">Match Type *</label>
              <div className="flex gap-2">
                {(['KEYWORD', 'REGEX', 'ALL'] as MatchType[]).map(t => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setForm(f => ({ ...f, matchType: t, matchValue: '' }))}
                    className={`flex-1 py-2 rounded-lg border text-sm font-medium transition-all ${
                      form.matchType === t
                        ? 'border-pink-500 bg-pink-50 text-pink-700'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300'
                    }`}
                  >
                    {MATCH_TYPE_LABELS[t]}
                  </button>
                ))}
              </div>
            </div>

            {/* Match Value (hidden for ALL) */}
            {form.matchType !== 'ALL' && (
              <div>
                <label className="block text-xs text-gray-500 mb-1 font-medium">
                  {form.matchType === 'KEYWORD' ? 'Keyword *' : 'Regex Pattern *'}
                </label>
                <input
                  value={form.matchValue}
                  onChange={e => setForm(f => ({ ...f, matchValue: e.target.value }))}
                  placeholder={
                    form.matchType === 'KEYWORD'
                      ? 'e.g. price, link, hi'
                      : 'e.g. ^(hello|hi|hey)\\b'
                  }
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono"
                  required
                />
              </div>
            )}

            {/* Reply Text */}
            <div>
              <label className="block text-xs text-gray-500 mb-1 font-medium">Reply Text *</label>
              <textarea
                value={form.replyText}
                onChange={e => setForm(f => ({ ...f, replyText: e.target.value }))}
                placeholder="Hi! Thanks for reaching out 👋 Use {sender_id} to personalize."
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm resize-none"
                required
              />
              <p className="text-xs text-gray-400 mt-1">
                Use <code className="bg-gray-100 px-1 rounded">{'{sender_id}'}</code> to include the sender's ID in your reply.
              </p>
            </div>

            {/* Enabled toggle */}
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => setForm(f => ({ ...f, enabled: !f.enabled }))}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                  form.enabled ? 'bg-green-500' : 'bg-gray-300'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                    form.enabled ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
              <span className="text-sm text-gray-700">{form.enabled ? 'Enabled' : 'Disabled'}</span>
            </div>

            {/* Actions */}
            <div className="flex gap-3 pt-2">
              <button
                type="submit"
                disabled={isSaving}
                className="px-5 py-2 bg-pink-600 text-white rounded-lg text-sm font-medium hover:bg-pink-700 disabled:opacity-50"
              >
                {isSaving ? 'Saving...' : editingId ? 'Update Rule' : 'Create Rule'}
              </button>
              <button
                type="button"
                onClick={resetForm}
                className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Rules list */}
      {isLoading ? (
        <div className="text-center py-12 text-gray-400">Loading rules…</div>
      ) : rules.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <MessageSquare size={48} className="mx-auto mb-3 opacity-30" />
          <p className="font-medium">No auto-reply rules yet</p>
          <p className="text-sm mt-1">Click <strong>Add Rule</strong> to create your first one.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {rules.map((rule) => (
            <div
              key={rule.id}
              className={`bg-white rounded-xl border transition-all ${
                rule.enabled ? 'border-gray-200' : 'border-gray-100 opacity-60'
              }`}
            >
              {/* Rule header row */}
              <div className="flex items-center gap-3 px-5 py-4">
                {/* Toggle */}
                <button
                  onClick={() => toggleMutation.mutate({ id: rule.id, enabled: !rule.enabled })}
                  className="shrink-0 text-gray-400 hover:text-gray-600"
                  title={rule.enabled ? 'Disable' : 'Enable'}
                >
                  {rule.enabled
                    ? <ToggleRight size={22} className="text-green-500" />
                    : <ToggleLeft size={22} />}
                </button>

                {/* Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-medium text-gray-900 text-sm">{rule.name}</span>
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-pink-100 text-pink-700 font-mono font-semibold">
                      {MATCH_TYPE_LABELS[rule.matchType]}
                    </span>
                    {rule.matchValue && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 font-mono">
                        {rule.matchValue}
                      </span>
                    )}
                    <span className="text-[10px] text-gray-400">priority {rule.priority}</span>
                  </div>
                  <p className="text-xs text-gray-500 mt-0.5 truncate">{rule.replyText}</p>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-1 shrink-0">
                  <button
                    onClick={() => setExpandedId(expandedId === rule.id ? null : rule.id)}
                    className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-50"
                    title="Preview"
                  >
                    {expandedId === rule.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  <button
                    onClick={() => startEdit(rule)}
                    className="p-2 text-gray-400 hover:text-blue-600 rounded-lg hover:bg-blue-50"
                    title="Edit"
                  >
                    <Pencil size={16} />
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(`Delete rule "${rule.name}"?`)) deleteMutation.mutate(rule.id)
                    }}
                    className="p-2 text-gray-400 hover:text-red-600 rounded-lg hover:bg-red-50"
                    title="Delete"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>

              {/* Expanded preview */}
              {expandedId === rule.id && (
                <div className="border-t border-gray-100 px-5 py-4 bg-gray-50 rounded-b-xl">
                  <p className="text-xs text-gray-500 font-medium mb-1">Full reply text:</p>
                  <pre className="text-sm text-gray-800 whitespace-pre-wrap font-sans bg-white border border-gray-200 rounded-lg p-3">
                    {rule.replyText}
                  </pre>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
